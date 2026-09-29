package com.premraj.notiflow.intelligence

import android.content.Context
import com.premraj.notiflow.data.ClassificationResult
import com.premraj.notiflow.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clean stub / fallback implementation without heavy native MediaPipe tasks-genai dependencies.
 * Strips 53MB of native bloat while preserving full API compatibility for offline rules-first operation.
 */
class GemmaClassifier(
    @Suppress("UNUSED_PARAMETER") private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val preferences: UserPreferences
) {
    val isModelAvailable: Boolean
        get() = false

    suspend fun refine(
        appName: String,
        title: String?,
        body: String?,
        fallback: ClassificationResult
    ): ClassificationResult? = null

    suspend fun summarize(lines: List<String>, fallback: String): String = fallback

    suspend fun reset() = withContext(Dispatchers.Default) {
        // No-op stub when native runtime is stripped
    }
}
