package com.premraj.notiflow.intelligence

import android.content.Context
import android.util.Log
import com.premraj.notiflow.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class ModelDownloadStatus {
    IDLE,
    DOWNLOADING,
    READY,
    FAILED
}

class ModelDownloader(
    private val context: Context,
    private val preferences: UserPreferences
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow(ModelDownloadStatus.IDLE)
    val status: StateFlow<ModelDownloadStatus> = _status.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val modelFile: File
        get() = File(context.filesDir, "models/notiflow_ai_classifier.bin")

    init {
        checkCurrentStatus()
    }

    fun checkCurrentStatus() {
        val existingPath = preferences.modelPath
        if (existingPath != null && File(existingPath).isFile && File(existingPath).length() > 0) {
            _status.value = ModelDownloadStatus.READY
            _progress.value = 1.0f
        } else if (modelFile.isFile && modelFile.length() > 0) {
            preferences.modelPath = modelFile.absolutePath
            preferences.localAiEnabled = true
            _status.value = ModelDownloadStatus.READY
            _progress.value = 1.0f
        }
    }

    fun autoStartDownloadIfNeeded() {
        checkCurrentStatus()
        if (_status.value == ModelDownloadStatus.READY || _status.value == ModelDownloadStatus.DOWNLOADING) {
            return
        }

        preferences.modelDownloadAutoStarted = true
        _status.value = ModelDownloadStatus.DOWNLOADING
        _progress.value = 0.05f

        scope.launch {
            try {
                downloadModel()
            } catch (e: Exception) {
                Log.w("ModelDownloader", "Auto download deferred/failed: ${e.message}")
                _status.value = ModelDownloadStatus.FAILED
            }
        }
    }

    private suspend fun downloadModel() = withContext(Dispatchers.IO) {
        val parentDir = modelFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        val modelUrl = "https://huggingface.co/litert-community/Gemma-2b-it-cpu-int4/resolve/main/gemma-2b-it-cpu-int4.bin"

        val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IllegalStateException("Server returned HTTP $responseCode")
        }

        val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: (50L * 1024 * 1024)
        val tempFile = File(modelFile.parentFile, "${modelFile.name}.download")

        connection.inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastProgressUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 300) {
                        _progress.value = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f)
                        lastProgressUpdate = now
                    }
                }
            }
        }

        if (tempFile.exists() && tempFile.length() > 0) {
            if (modelFile.exists()) modelFile.delete()
            tempFile.renameTo(modelFile)
            preferences.modelPath = modelFile.absolutePath
            preferences.localAiEnabled = true
            _progress.value = 1.0f
            _status.value = ModelDownloadStatus.READY
            Log.i("ModelDownloader", "AI model auto-download complete at: ${modelFile.absolutePath}")
        } else {
            _status.value = ModelDownloadStatus.FAILED
        }
    }
}
