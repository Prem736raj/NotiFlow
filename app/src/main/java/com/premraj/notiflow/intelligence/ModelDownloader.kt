package com.premraj.notiflow.intelligence

import android.content.Context
import com.premraj.notiflow.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class ModelDownloadStatus {
    IDLE,
    DOWNLOADING,
    READY,
    FAILED
}

/**
 * Tracks an explicitly configured local model.
 *
 * Network model acquisition is intentionally disabled until NotiFlow has a
 * verified model identity, integrity metadata, consent UX, and resumable
 * download contract. Rules-only classification remains the default.
 */
class ModelDownloader(
    context: Context,
    private val preferences: UserPreferences
) {
    @Suppress("unused")
    private val appContext = context.applicationContext

    private val _status = MutableStateFlow(ModelDownloadStatus.IDLE)
    val status: StateFlow<ModelDownloadStatus> = _status.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    init {
        checkCurrentStatus()
    }

    fun checkCurrentStatus() {
        val configured = preferences.modelPath
            ?.let(::File)
            ?.takeIf { it.isFile && it.length() > 0 }

        if (configured != null) {
            _status.value = ModelDownloadStatus.READY
            _progress.value = 1f
        } else {
            _status.value = ModelDownloadStatus.IDLE
            _progress.value = 0f
        }
    }

    /**
     * Kept for source compatibility with older callers. This method never
     * starts network traffic and never changes the user's AI preference.
     */
    fun autoStartDownloadIfNeeded() {
        checkCurrentStatus()
    }
}
