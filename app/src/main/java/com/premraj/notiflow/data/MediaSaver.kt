package com.premraj.notiflow.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.premraj.notiflow.appGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class MediaScanResult(
    val scanned: Int = 0,
    val saved: Int = 0,
    val duplicates: Int = 0,
    val skippedSize: Int = 0,
    val failures: Int = 0,
    val savedBytes: Long = 0L,
    val disabled: Boolean = false,
    val missingPermission: Boolean = false
) {
    val summary: String
        get() = when {
            disabled -> "Media saving is off"
            missingPermission -> "Media saving needs photo and video permission"
            scanned == 0 -> "No new WhatsApp media found"
            saved == 0 && duplicates == 0 -> "No new WhatsApp media found"
            saved == 0 -> "All ${scanned} recent items were already saved"
            else -> "Saved $saved new file(s) ($duplicates already in NotiFlow)"
        }
}

object MediaSaver {

    private val scanMutex = Mutex()
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val SCAN_LIMIT = 1500
    private const val MAX_FILE_BYTES = 100L * 1024 * 1024
    private const val RECENT_WINDOW_SECONDS = 2L * 24 * 60 * 60
    private const val HOST_FILTER = "%/com.whatsapp%"

    val observedUris = listOf(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    )

    fun hasMediaReadAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val images = granted(context, Manifest.permission.READ_MEDIA_IMAGES)
            val videos = granted(context, Manifest.permission.READ_MEDIA_VIDEO)
            val partial = granted(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
            return (images && videos) || partial
        }
        return granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun requestedPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    internal fun isRelevantPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val normalized = path.replace('\\', '/')
        if (!(normalized.contains("/com.whatsapp/") || normalized.contains("/com.whatsapp.w4b/"))) return false
        if (normalized.contains("/.")) return false
        val segments = normalized.lowercase(Locale.ROOT).split('/')
        if (segments.any { it.startsWith("whatsapp stickers") }) return false
        val fileName = segments.lastOrNull().orEmpty()
        if (fileName.isBlank() || fileName.startsWith(".")) return false
        if (fileName.endsWith(".tmp") || fileName.endsWith(".part")) return false
        return true
    }

    internal fun extensionFor(mimeType: String?): String = when (mimeType?.lowercase(Locale.ROOT)) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/jpeg" -> "jpg"
        "video/mp4" -> "mp4"
        "video/3gpp" -> "3gp"
        "video/webm" -> "webm"
        "audio/mp4" -> "m4a"
        else -> "bin"
    }

    internal fun sanitizedBaseName(displayName: String): String {
        val base = displayName.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
        return base.take(60).takeIf { it.isNotBlank() } ?: "media"
    }

    internal fun hostPackageOf(path: String): String = when {
        path.contains("/com.whatsapp.w4b/") -> "com.whatsapp.w4b"
        else -> "com.whatsapp"
    }

    internal fun mediaDirectory(context: Context): File =
        File(context.applicationContext.filesDir, "saved_media").apply { mkdirs() }

    suspend fun scan(context: Context, store: NotificationStore, preferences: UserPreferences): MediaScanResult {
        val appContext = context.applicationContext
        if (!preferences.saveMediaEnabled) {
            return MediaScanResult(disabled = true)
        }
        if (!hasMediaReadAccess(appContext)) {
            return MediaScanResult(missingPermission = true)
        }

        val watermarkSeconds = preferences.lastMediaScanWatermark
        val scanCutoffSeconds = System.currentTimeMillis() / 1000
        val floorSeconds = max(0L, watermarkSeconds - RECENT_WINDOW_SECONDS)

        return scanMutex.withLock {
            var scanned = 0
            var saved = 0
            var duplicates = 0
            var skippedSize = 0
            var failures = 0
            var savedBytes = 0L
            var highestAdded = watermarkSeconds

            for (collection in observedUris) {
                val kind = if (collection == MediaStore.Video.Media.EXTERNAL_CONTENT_URI) {
                    SavedMediaKind.VIDEO
                } else {
                    SavedMediaKind.IMAGE
                }

                @Suppress("DEPRECATION")
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATA
                )
                @Suppress("DEPRECATION")
                val selection =
                    "${MediaStore.MediaColumns.DATA} LIKE ? AND ${MediaStore.MediaColumns.DATE_ADDED} < ?"
                val args = arrayOf(HOST_FILTER, scanCutoffSeconds.toString())
                @Suppress("DEPRECATION")
                val order = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

                val cursorResult = runCatching {
                    appContext.contentResolver.query(collection, projection, selection, args, order)
                }.getOrNull()

                if (cursorResult == null) {
                    failures += 1
                    continue
                }

                cursorResult.use { cursor ->
                    while (cursor.moveToNext()) {
                        if (saved >= SCAN_LIMIT) break
                        scanned += 1

                        val dateAdded = cursor.getLong(4)
                        highestAdded = max(highestAdded, dateAdded)
                        if (dateAdded <= floorSeconds) continue

                        @Suppress("DEPRECATION")
                        val path = cursor.getStringOrNull(5)
                        if (!isRelevantPath(path)) continue

                        val sourceUri = ContentUris.withAppendedId(collection, cursor.getLong(0)).toString()
                        if (store.hasSavedMediaForUri(sourceUri)) continue

                        val size = cursor.getLongOrNull(3) ?: 0L
                        if (size <= 0L || size > MAX_FILE_BYTES) {
                            skippedSize += 1
                            continue
                        }

                        val displayName = cursor.getStringOrNull(1)?.takeIf { it.isNotBlank() }
                            ?: path?.substringAfterLast('/') ?: "media"
                        val mimeType = cursor.getStringOrNull(2)
                        val capturedAtMillis = dateAdded * 1000L

                        val outcome = copyToPrivateStorage(
                            context = appContext,
                            store = store,
                            sourceUri = sourceUri,
                            packageName = hostPackageOf(path ?: ""),
                            displayName = displayName,
                            mimeType = mimeType,
                            kind = kind,
                            sizeBytes = size,
                            capturedAtMillis = capturedAtMillis
                        )
                        when (outcome) {
                            CopyOutcome.SAVED -> {
                                saved += 1
                                savedBytes += size
                            }
                            CopyOutcome.DUPLICATE -> duplicates += 1
                            CopyOutcome.FAILED -> failures += 1
                        }
                    }
                }
            }

            preferences.lastMediaScanWatermark = max(highestAdded, scanCutoffSeconds - 60)
            MediaScanResult(
                scanned = scanned,
                saved = saved,
                duplicates = duplicates,
                skippedSize = skippedSize,
                failures = failures,
                savedBytes = savedBytes
            )
        }
    }

    private enum class CopyOutcome { SAVED, DUPLICATE, FAILED }

    private suspend fun copyToPrivateStorage(
        context: Context,
        store: NotificationStore,
        sourceUri: String,
        packageName: String,
        displayName: String,
        mimeType: String?,
        kind: SavedMediaKind,
        sizeBytes: Long,
        capturedAtMillis: Long
    ): CopyOutcome {
        val dir = mediaDirectory(context)
        val tempFile = File.createTempFile("notf_", ".tmp", dir)
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var totalRead = 0L
            var complete = false
            var overMax = false

            val streamed: Boolean? = runCatching {
                context.contentResolver.openInputStream(Uri.parse(sourceUri))
            }.getOrNull()?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        if (totalRead + read > MAX_FILE_BYTES) {
                            overMax = true
                            break
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        totalRead += read
                    }
                    complete = true
                    true
                }
            }

            if (streamed != true || !complete || overMax || totalRead <= 0L) {
                tempFile.delete()
                return CopyOutcome.FAILED
            }

            val hash = digest.digest().joinToString(separator = "") { String.format(Locale.US, "%02x", it) }
            val hashShort = hash.take(12)
            val extension = runCatching { extensionFor(mimeType) }.getOrDefault("bin")
            val finalFile = File(dir, "${capturedAtMillis}_${hashShort}.${extension}")

            if (store.hasSavedMediaForHash(hash)) {
                tempFile.delete()
                return CopyOutcome.DUPLICATE
            }

            if (finalFile.exists() || !tempFile.renameTo(finalFile)) {
                tempFile.delete()
                return CopyOutcome.FAILED
            }

            val linkedNotificationId = runCatching {
                store.findNotificationIdForMedia(packageName, capturedAtMillis)
            }.getOrNull()

            val rowId = runCatching {
                store.insertSavedMedia(
                    SavedMedia(
                        id = 0L,
                        packageName = packageName,
                        displayName = sanitizedBaseName(displayName) + "." + extension,
                        kind = kind,
                        mimeType = mimeType,
                        sizeBytes = totalRead,
                        contentHash = hash,
                        savedPath = finalFile.absolutePath,
                        originalUri = sourceUri,
                        capturedAt = capturedAtMillis,
                        savedAt = System.currentTimeMillis(),
                        notificationId = linkedNotificationId
                    )
                )
            }.getOrDefault(-1L)

            if (rowId <= 0L) {
                finalFile.delete()
                CopyOutcome.DUPLICATE
            } else {
                CopyOutcome.SAVED
            }
        } catch (error: Throwable) {
            tempFile.delete()
            CopyOutcome.FAILED
        }
    }

    fun deletePhysicalFile(path: String): Boolean =
        runCatching { File(path).let { it.delete() && !it.exists() } }.getOrDefault(false)

    fun createObserver(context: Context): ContentObserver {
        val appContext = context.applicationContext
        return object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (selfChange || uri == null) return
                observerScope.launch {
                    runCatching {
                        val graph = appContext.appGraph
                        scan(appContext, graph.store, graph.preferences)
                    }
                }
            }
        }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun Cursor.getLongOrNull(index: Int): Long? =
        if (index >= 0 && !isNull(index)) getLong(index) else null
}
