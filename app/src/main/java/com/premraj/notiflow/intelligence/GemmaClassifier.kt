package com.premraj.notiflow.intelligence

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.premraj.notiflow.data.ClassificationResult
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Optional private on-device refinement layer.
 * The app intentionally does not bundle a several-hundred-MB model in the APK.
 * A compatible Gemma .task model can be imported into app-private storage by the user.
 */
class GemmaClassifier(
    private val context: Context,
    private val preferences: UserPreferences
) {
    private val mutex = Mutex()
    private val engineGuard = Any()
    private var inference: LlmInference? = null
    private var loadedPath: String? = null

    val isModelAvailable: Boolean
        get() = preferences.modelPath?.let { File(it).isFile } == true

    suspend fun refine(
        appName: String,
        title: String?,
        body: String?,
        fallback: ClassificationResult
    ): ClassificationResult? {
        if (!preferences.localAiEnabled || !isModelAvailable) return null
        if (fallback.confidence >= 0.86f && fallback.category != NotificationCategory.OTHER) return null

        return withContext(Dispatchers.Default) {
            mutex.withLock {
                runCatching {
                    val response = generate(
                        """
                        You classify phone notifications. Return ONLY one compact JSON object.
                        Allowed categories: OTP, PAYMENT, DELIVERY, MESSAGE, WORK_STUDY, REMINDER_EVENT, SOCIAL, PROMOTION, SPAM, OTHER.
                        Allowed priorities: HIGH, NORMAL, LOW.
                        Be conservative: HIGH only for likely time-sensitive or security-critical items.
                        App: ${sanitize(appName)}
                        Title: ${sanitize(title)}
                        Text: ${sanitize(body)}
                        Output: {"category":"OTHER","priority":"NORMAL","confidence":0.70}
                        """.trimIndent()
                    )
                    parse(response, fallback)
                }.getOrNull()
            }
        }
    }

    suspend fun summarize(lines: List<String>, fallback: String): String {
        if (!preferences.localAiEnabled || !isModelAvailable || lines.isEmpty()) return fallback
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                runCatching {
                    generate(
                        """
                        Summarize these phone notifications in one short sentence. Mention counts or concrete useful events. Do not invent anything.
                        ${lines.take(24).joinToString("\n") { "- ${sanitize(it)}" }}
                        """.trimIndent()
                    ).trim().take(400).ifBlank { fallback }
                }.getOrDefault(fallback)
            }
        }
    }

    fun reset() = synchronized(engineGuard) {
        runCatching { inference?.close() }
        inference = null
        loadedPath = null
    }

    private fun generate(prompt: String): String = synchronized(engineGuard) {
        ensureEngineLocked().generateResponse(prompt)
    }

    private fun ensureEngineLocked(): LlmInference {
        val path = preferences.modelPath ?: error("No local model selected")
        if (inference != null && loadedPath == path) return inference!!
        runCatching { inference?.close() }
        inference = null
        loadedPath = null
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(192)
            .build()
        return LlmInference.createFromOptions(context, options).also {
            inference = it
            loadedPath = path
        }
    }

    private fun parse(raw: String, fallback: ClassificationResult): ClassificationResult {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return fallback
        val json = JSONObject(raw.substring(start, end + 1))
        val category = runCatching { NotificationCategory.valueOf(json.optString("category")) }.getOrDefault(fallback.category)
        val priority = runCatching { NotificationPriority.valueOf(json.optString("priority")) }.getOrDefault(fallback.priority)
        val confidence = json.optDouble("confidence", fallback.confidence.toDouble()).toFloat().coerceIn(0f, 1f)
        return fallback.copy(category = category, priority = priority, confidence = confidence, reason = "On-device AI")
    }

    private fun sanitize(value: String?): String = value.orEmpty().replace("\n", " ").take(600)
}
