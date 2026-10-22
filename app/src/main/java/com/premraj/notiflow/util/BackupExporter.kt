package com.premraj.notiflow.util

import android.content.Context
import com.premraj.notiflow.data.AutomationAction
import com.premraj.notiflow.data.AutomationRule
import com.premraj.notiflow.data.ExpenseTransaction
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.data.UserPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupExporter {

    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val SALT_LENGTH = 16
    private const val LEGACY_ITERATIONS = 10_000
    private const val ITERATIONS = 600_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val KEY_LENGTH = 256
    private const val HEADER_LENGTH = 8
    private val HEADER_MAGIC = byteArrayOf('N'.code.toByte(), 'F'.code.toByte(), 'B'.code.toByte(), 2)

    fun exportExpensesToCsv(transactions: List<ExpenseTransaction>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("ID,Timestamp,Type,Amount,MerchantOrParty,Category,AccountRef,BalanceAfter\n")
        transactions.forEach { t ->
            val dateStr = dateFormat.format(Date(t.timestamp))
            val safeMerchant = sanitizeCsvText(t.merchantOrParty ?: "Unknown")
            val safeAccount = sanitizeCsvText(t.accountRef ?: "")
            val balanceStr = t.balanceAfter?.toString() ?: ""
            sb.append("${t.id},\"$dateStr\",${t.transactionType.name},${t.amount},\"$safeMerchant\",${t.expenseCategory.name},\"$safeAccount\",\"$balanceStr\"\n")
        }
        return sb.toString()
    }

    private fun sanitizeCsvText(value: String): String {
        val trimmedLeading = value.dropWhile { it.isWhitespace() }
        val formulaSafe = if (
            trimmedLeading.startsWith("=") ||
            trimmedLeading.startsWith("+") ||
            trimmedLeading.startsWith("-") ||
            trimmedLeading.startsWith("@")
        ) {
            "'$value"
        } else {
            value
        }
        return formulaSafe.replace("\"", "\"\"")
    }

    const val MIN_PASSWORD_LENGTH = 12

    class WeakPasswordException(message: String = "Passphrase must be at least 12 characters long") : IllegalArgumentException(message)
    class CorruptArchiveException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
    class InvalidPasswordException(message: String = "Incorrect passphrase for encrypted archive", cause: Throwable? = null) : SecurityException(message, cause)

    fun createEncryptedBackup(
        notifications: List<NotificationItem>,
        preferences: UserPreferences,
        password: String
    ): String = createEncryptedBackup(
        notifications = notifications,
        vipRules = preferences.vipRules(),
        automationRules = preferences.automationRules(),
        password = password
    )

    fun createEncryptedBackup(
        notifications: List<NotificationItem>,
        vipRules: Set<String>,
        password: String
    ): String = createEncryptedBackup(notifications, vipRules, emptyList(), password)

    fun createEncryptedBackup(
        notifications: List<NotificationItem>,
        vipRules: Set<String>,
        automationRules: List<AutomationRule>,
        password: String
    ): String {
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw WeakPasswordException("Passphrase must be at least $MIN_PASSWORD_LENGTH characters long")
        }

        val root = JSONObject()
        root.put("version", 3)
        root.put("format", "notiflow-archive-v3")
        root.put("app_version", "0.1.0")
        root.put("item_count", notifications.size)
        root.put("created_at", System.currentTimeMillis())

        val notifArray = JSONArray()
        notifications.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("notification_key", item.notificationKey)
            obj.put("package", item.packageName)
            obj.put("app_name", item.appName)
            obj.put("title", item.title.orEmpty())
            obj.put("body", item.body.orEmpty())
            obj.put("sender", item.sender.orEmpty())
            obj.put("posted_at", item.postedAt)
            obj.put("updated_at", item.updatedAt)
            obj.put("removed_at", item.removedAt ?: JSONObject.NULL)
            obj.put("is_active", item.isActiveOnSystem)
            obj.put("category", item.category.name)
            obj.put("priority", item.priority.name)
            obj.put("confidence", item.confidence)
            obj.put("category_overridden", item.categoryOverridden)
            obj.put("priority_overridden", item.priorityOverridden)
            obj.put("state", item.state.name)
            obj.put("remind_at", item.remindAt ?: JSONObject.NULL)
            obj.put("pinned", item.pinned)
            obj.put("is_vip", item.isVip)
            obj.put("read", item.read)
            obj.put("amount_hint", item.amountHint ?: JSONObject.NULL)
            obj.put("is_removed_by_source", item.isRemovedBySource)
            notifArray.put(obj)
        }
        root.put("notifications", notifArray)

        val vipArray = JSONArray()
        vipRules.forEach { vipArray.put(it) }
        root.put("vip_rules", vipArray)

        val ruleArray = JSONArray()
        automationRules.forEach { rule ->
            ruleArray.put(JSONObject().apply {
                put("id", rule.id)
                put("keyword", rule.keyword)
                put("package", rule.packageName.orEmpty())
                put("action", rule.action.name)
                put("enabled", rule.enabled)
            })
        }
        root.put("automation_rules", ruleArray)

        val jsonString = root.toString()
        return encrypt(jsonString, password)
    }

    fun decryptBackup(encryptedPayload: String, password: String): Result<JSONObject> = runCatching {
        if (password.isBlank()) throw WeakPasswordException("Passphrase cannot be blank")
        val jsonString = try {
            decrypt(encryptedPayload, password)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw InvalidPasswordException("Incorrect passphrase", e)
        } catch (e: javax.crypto.BadPaddingException) {
            throw InvalidPasswordException("Incorrect passphrase", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptArchiveException("Invalid or malformed archive payload", e)
        } catch (t: Throwable) {
            throw CorruptArchiveException("Failed to decrypt archive", t)
        }

        try {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 1)
            if (version > 3) {
                throw CorruptArchiveException("Unsupported archive version: $version")
            }
            root
        } catch (e: org.json.JSONException) {
            throw CorruptArchiveException("Malformed JSON in decrypted archive", e)
        }
    }

    fun notificationItemsFromArchive(root: JSONObject): List<NotificationItem> {
        val array = root.optJSONArray("notifications") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val packageName = obj.optString("package").ifBlank { "unknown" }
                val postedAt = obj.optLong("posted_at", 0L).takeIf { it > 0L } ?: continue
                val key = obj.optString("notification_key").ifBlank {
                    "restored:$packageName:$postedAt:$index"
                }
                add(
                    NotificationItem(
                        id = 0L,
                        notificationKey = key,
                        packageName = packageName,
                        appName = obj.optString("app_name").ifBlank { packageName },
                        title = obj.optString("title").takeIf { it.isNotBlank() },
                        body = obj.optString("body").takeIf { it.isNotBlank() },
                        sender = obj.optString("sender").takeIf { it.isNotBlank() },
                        postedAt = postedAt,
                        updatedAt = obj.optLong("updated_at", postedAt),
                        removedAt = obj.nullableLong("removed_at"),
                        isActiveOnSystem = false,
                        category = enumOrNull<NotificationCategory>(obj.optString("category"))
                            ?: NotificationCategory.OTHER,
                        priority = enumOrNull<NotificationPriority>(obj.optString("priority"))
                            ?: NotificationPriority.NORMAL,
                        confidence = obj.optDouble("confidence", 0.0).toFloat(),
                        categoryOverridden = obj.optBoolean("category_overridden", false),
                        priorityOverridden = obj.optBoolean("priority_overridden", false),
                        state = enumOrNull<NotificationState>(obj.optString("state"))
                            ?: NotificationState.ARCHIVED,
                        remindAt = obj.nullableLong("remind_at"),
                        pinned = obj.optBoolean("pinned", false),
                        isVip = obj.optBoolean("is_vip", false),
                        read = obj.optBoolean("read", true),
                        amountHint = obj.nullableDouble("amount_hint"),
                        isRemovedBySource = obj.optBoolean("is_removed_by_source", false)
                    )
                )
            }
        }
    }

    fun vipRulesFromArchive(root: JSONObject): Set<String> {
        val array = root.optJSONArray("vip_rules") ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    fun automationRulesFromArchive(root: JSONObject): List<AutomationRule> {
        val array = root.optJSONArray("automation_rules") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val keyword = obj.optString("keyword").trim().takeIf { it.isNotBlank() } ?: continue
                val action = runCatching {
                    AutomationAction.valueOf(obj.optString("action"))
                }.getOrNull() ?: continue
                add(
                    AutomationRule(
                        id = obj.optString("id").takeIf { it.isNotBlank() }
                            ?: "restored-rule-$index",
                        keyword = keyword,
                        packageName = obj.optString("package").takeIf { it.isNotBlank() },
                        action = action,
                        enabled = obj.optBoolean("enabled", true)
                    )
                )
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key)

    fun writeEncryptedBackupToFile(
        context: Context,
        encryptedPayload: String,
        fileName: String = "notiflow-backup-${System.currentTimeMillis()}.enc"
    ): File {
        val backupsDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val backupFile = File(backupsDir, fileName)
        backupFile.writeText(encryptedPayload, Charsets.UTF_8)
        return backupFile
    }

    fun createEncryptedBackupFile(
        context: Context,
        notifications: List<NotificationItem>,
        preferences: UserPreferences,
        password: String,
        fileName: String = "notiflow-backup-${System.currentTimeMillis()}.enc"
    ): File {
        val encrypted = createEncryptedBackup(notifications, preferences, password)
        return writeEncryptedBackupToFile(context, encrypted, fileName)
    }

    private fun encrypt(plainText: String, password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(HEADER_LENGTH + salt.size + iv.size + cipherText.size)
        System.arraycopy(HEADER_MAGIC, 0, combined, 0, HEADER_MAGIC.size)
        ByteBuffer.wrap(combined, HEADER_MAGIC.size, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(ITERATIONS)
        val offset = HEADER_LENGTH
        System.arraycopy(salt, 0, combined, offset, salt.size)
        System.arraycopy(iv, 0, combined, offset + salt.size, iv.size)
        System.arraycopy(cipherText, 0, combined, offset + salt.size + iv.size, cipherText.size)

        return Base64Compat.encodeToString(combined)
    }

    private fun decrypt(encryptedBase64: String, password: String): String {
        val combined = Base64Compat.decode(encryptedBase64)
        val hasHeader = combined.size >= HEADER_LENGTH &&
            HEADER_MAGIC.indices.all { combined[it] == HEADER_MAGIC[it] }
        val iterations = if (hasHeader) {
            ByteBuffer.wrap(combined, HEADER_MAGIC.size, 4).order(ByteOrder.BIG_ENDIAN).int
                .also { require(it in LEGACY_ITERATIONS..MAX_ITERATIONS) { "Invalid key derivation cost" } }
        } else {
            LEGACY_ITERATIONS
        }
        val offset = if (hasHeader) HEADER_LENGTH else 0
        if (combined.size < offset + SALT_LENGTH + IV_LENGTH) {
            throw IllegalArgumentException("Invalid payload length")
        }

        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        val cipherText = ByteArray(combined.size - offset - SALT_LENGTH - IV_LENGTH)

        System.arraycopy(combined, offset, salt, 0, SALT_LENGTH)
        System.arraycopy(combined, offset + SALT_LENGTH, iv, 0, IV_LENGTH)
        System.arraycopy(combined, offset + SALT_LENGTH + IV_LENGTH, cipherText, 0, cipherText.size)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val decrypted = cipher.doFinal(cipherText)

        return String(decrypted, Charsets.UTF_8)
    }
}

