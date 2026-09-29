package com.premraj.notiflow.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class UserPreferences internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences("notiflow_preferences", Context.MODE_PRIVATE)
    )

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var digestEnabled: Boolean
        get() = prefs.getBoolean(KEY_DIGEST_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DIGEST_ENABLED, value).apply()

    var digestHour: Int
        get() = prefs.getInt(KEY_DIGEST_HOUR, 19)
        set(value) = prefs.edit().putInt(KEY_DIGEST_HOUR, value.coerceIn(0, 23)).apply()

    var digestMinute: Int
        get() = prefs.getInt(KEY_DIGEST_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_DIGEST_MINUTE, value.coerceIn(0, 59)).apply()

    var lastDigestAt: Long
        get() = prefs.getLong(KEY_LAST_DIGEST_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_DIGEST_AT, value).apply()

    var autoCleanupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CLEANUP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CLEANUP, value).apply()

    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, 30)
        set(value) = prefs.edit().putInt(KEY_RETENTION_DAYS, value.coerceIn(1, 365)).apply()

    var localAiEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCAL_AI, false) &&
            prefs.getBoolean(KEY_LOCAL_AI_CONSENT, false)
        set(value) = prefs.edit()
            .putBoolean(KEY_LOCAL_AI, value)
            .putBoolean(KEY_LOCAL_AI_CONSENT, value)
            .apply()

    var modelPath: String?
        get() = prefs.getString(KEY_MODEL_PATH, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_MODEL_PATH, value).apply()

    var hideSensitivePreviews: Boolean
        get() = prefs.getBoolean(KEY_HIDE_SENSITIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_SENSITIVE, value).apply()

    var autoCopyOtp: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COPY_OTP, false) &&
            prefs.getBoolean(KEY_AUTO_COPY_OTP_CONSENT, false)
        set(value) = prefs.edit()
            .putBoolean(KEY_AUTO_COPY_OTP, value)
            .putBoolean(KEY_AUTO_COPY_OTP_CONSENT, value)
            .apply()

    var antiRevokeEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANTI_REVOKE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ANTI_REVOKE_ENABLED, value).apply()

    var expenseTrackerEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXPENSE_TRACKER_ENABLED, false) &&
            prefs.getBoolean(KEY_EXPENSE_TRACKER_CONSENT, false)
        set(value) = prefs.edit()
            .putBoolean(KEY_EXPENSE_TRACKER_ENABLED, value)
            .putBoolean(KEY_EXPENSE_TRACKER_CONSENT, value)
            .apply()

    var unwantedCleanupPromptEnabled: Boolean
        get() = prefs.getBoolean(KEY_UNWANTED_PROMPT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_UNWANTED_PROMPT_ENABLED, value).apply()

    var unwantedCleanupDays: Int
        get() = prefs.getInt(KEY_UNWANTED_CLEANUP_DAYS, 15)
        set(value) = prefs.edit().putInt(KEY_UNWANTED_CLEANUP_DAYS, value.coerceIn(10, 30)).apply()

    var lastUnwantedPromptAt: Long
        get() = prefs.getLong(KEY_LAST_UNWANTED_PROMPT_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UNWANTED_PROMPT_AT, value).apply()

    var modelSetupPromptDismissed: Boolean
        get() = prefs.getBoolean(KEY_MODEL_PROMPT_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_MODEL_PROMPT_DISMISSED, value).apply()

    var voiceReaderEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_READER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_READER_ENABLED, value).apply()

    var drivingModeActive: Boolean
        get() = prefs.getBoolean(KEY_DRIVING_MODE_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_DRIVING_MODE_ACTIVE, value).apply()

    var voiceTriggerCondition: VoiceTriggerCondition
        get() {
            val name = prefs.getString(KEY_VOICE_TRIGGER_CONDITION, VoiceTriggerCondition.HEADPHONES_ONLY.name)
            return runCatching { VoiceTriggerCondition.valueOf(name ?: "") }.getOrDefault(VoiceTriggerCondition.HEADPHONES_ONLY)
        }
        set(value) = prefs.edit().putString(KEY_VOICE_TRIGGER_CONDITION, value.name).apply()

    var voiceReaderFilter: VoiceReaderFilter
        get() {
            val name = prefs.getString(KEY_VOICE_READER_FILTER, VoiceReaderFilter.MESSAGES_ONLY.name)
            return runCatching { VoiceReaderFilter.valueOf(name ?: "") }.getOrDefault(VoiceReaderFilter.MESSAGES_ONLY)
        }
        set(value) = prefs.edit().putString(KEY_VOICE_READER_FILTER, value.name).apply()

    var voiceReadingDetail: VoiceReadingDetail
        get() {
            val name = prefs.getString(KEY_VOICE_READING_DETAIL, VoiceReadingDetail.FULL_MESSAGE.name)
            return runCatching { VoiceReadingDetail.valueOf(name ?: "") }.getOrDefault(VoiceReadingDetail.FULL_MESSAGE)
        }
        set(value) = prefs.edit().putString(KEY_VOICE_READING_DETAIL, value.name).apply()

    var voiceSpeechRate: Float
        get() = prefs.getFloat(KEY_VOICE_SPEECH_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_SPEECH_RATE, value.coerceIn(0.5f, 2.0f)).apply()

    var manualFocusActive: Boolean
        get() = prefs.getBoolean(KEY_MANUAL_FOCUS_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_MANUAL_FOCUS_ACTIVE, value).apply()

    var manualFocusType: FocusProfileType
        get() {
            val name = prefs.getString(KEY_MANUAL_FOCUS_TYPE, FocusProfileType.WORK.name)
            return runCatching { FocusProfileType.valueOf(name ?: "") }.getOrDefault(FocusProfileType.WORK)
        }
        set(value) = prefs.edit().putString(KEY_MANUAL_FOCUS_TYPE, value.name).apply()

    var sleepFocusScheduled: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_FOCUS_SCHEDULED, false)
        set(value) = prefs.edit().putBoolean(KEY_SLEEP_FOCUS_SCHEDULED, value).apply()

    var workFocusScheduled: Boolean
        get() = prefs.getBoolean(KEY_WORK_FOCUS_SCHEDULED, false)
        set(value) = prefs.edit().putBoolean(KEY_WORK_FOCUS_SCHEDULED, value).apply()

    var focusBlockedCount: Int
        get() = prefs.getInt(KEY_FOCUS_BLOCKED_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_FOCUS_BLOCKED_COUNT, value).apply()

    var focusActivatedAt: Long
        get() = prefs.getLong(KEY_FOCUS_ACTIVATED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_FOCUS_ACTIVATED_AT, value).apply()

    var focusSessionToken: String?
        get() = prefs.getString(KEY_FOCUS_SESSION_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_FOCUS_SESSION_TOKEN, value).apply()

    var appThemeMode: AppThemeMode
        get() {
            val name = prefs.getString(KEY_APP_THEME_MODE, AppThemeMode.SYSTEM.name)
            return runCatching { AppThemeMode.valueOf(name ?: "") }.getOrDefault(AppThemeMode.SYSTEM)
        }
        set(value) = prefs.edit().putString(KEY_APP_THEME_MODE, value.name).apply()

    var useDynamicColors: Boolean
        get() = prefs.getBoolean(KEY_USE_DYNAMIC_COLORS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_DYNAMIC_COLORS, value).apply()

    var modelDownloadAutoStarted: Boolean
        get() = prefs.getBoolean(KEY_MODEL_AUTO_DOWNLOAD_STARTED, false)
        set(value) = prefs.edit().putBoolean(KEY_MODEL_AUTO_DOWNLOAD_STARTED, value).apply()

    fun quietPackages(): Set<String> = prefs.getStringSet(KEY_QUIET_PACKAGES, emptySet())?.toSet().orEmpty()

    fun quietCategories(): Set<NotificationCategory> =
        prefs.getStringSet(KEY_QUIET_CATEGORIES, emptySet()).orEmpty().mapNotNull {
            runCatching { NotificationCategory.valueOf(it) }.getOrNull()
        }.toSet()

    fun setQuietCategory(category: NotificationCategory, quiet: Boolean) {
        val set = quietCategories().mapTo(mutableSetOf()) { it.name }
        if (quiet) set += category.name else set -= category.name
        prefs.edit().putStringSet(KEY_QUIET_CATEGORIES, set).apply()
    }

    fun setQuietPackage(packageName: String, quiet: Boolean) {
        val set = quietPackages().toMutableSet()
        if (quiet) set += packageName else set -= packageName
        prefs.edit().putStringSet(KEY_QUIET_PACKAGES, set).apply()
    }

    fun excludedPackages(): Set<String> = prefs.getStringSet(KEY_EXCLUDED_PACKAGES, emptySet())?.toSet().orEmpty()

    fun setExcludedPackage(packageName: String, excluded: Boolean) {
        val set = excludedPackages().toMutableSet()
        if (excluded) set += packageName else set -= packageName
        prefs.edit().putStringSet(KEY_EXCLUDED_PACKAGES, set).apply()
    }

    fun vipRules(): Set<String> = prefs.getStringSet(KEY_VIP_RULES, emptySet())?.toSet().orEmpty()

    fun removeVipToken(token: String) {
        val set = vipRules().toMutableSet()
        set -= token
        prefs.edit().putStringSet(KEY_VIP_RULES, set).apply()
    }

    fun setVip(packageName: String, sender: String?, enabled: Boolean) {
        val token = vipToken(packageName, sender)
        val set = vipRules().toMutableSet()
        if (enabled) set += token else set -= token
        prefs.edit().putStringSet(KEY_VIP_RULES, set).apply()
    }

    fun isVip(packageName: String, sender: String?): Boolean {
        val rules = vipRules()
        return vipToken(packageName, sender) in rules || vipToken(packageName, null) in rules
    }

    fun learnedPreferences(): List<LearnedPreference> {
        val raw = prefs.getString(KEY_LEARNED, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        LearnedPreference(
                            packageName = obj.getString("package"),
                            sender = obj.optString("sender").takeIf { it.isNotBlank() },
                            category = obj.optString("category").takeIf { it.isNotBlank() }
                                ?.let { runCatching { NotificationCategory.valueOf(it) }.getOrNull() },
                            priority = obj.optString("priority").takeIf { it.isNotBlank() }
                                ?.let { runCatching { NotificationPriority.valueOf(it) }.getOrNull() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun upsertLearnedPreference(preference: LearnedPreference) {
        val current = learnedPreferences().associateBy { it.key }.toMutableMap()
        val previous = current[preference.key]
        current[preference.key] = LearnedPreference(
            packageName = preference.packageName,
            sender = preference.sender,
            category = preference.category ?: previous?.category,
            priority = preference.priority ?: previous?.priority
        )
        saveLearned(current.values.toList())
    }

    fun matchingPreference(packageName: String, sender: String?): LearnedPreference? {
        val normalizedSender = sender.orEmpty().trim().lowercase()
        val all = learnedPreferences()
        return all.firstOrNull {
            it.packageName == packageName && it.sender.orEmpty().trim().lowercase() == normalizedSender
        } ?: all.firstOrNull { it.packageName == packageName && it.sender.isNullOrBlank() }
    }

    fun removeLearnedPreference(key: String) {
        saveLearned(learnedPreferences().filterNot { it.key == key })
    }

    fun removeLearnedPreference(packageName: String, sender: String?) {
        val key = "$packageName|${sender.orEmpty().trim().lowercase()}"
        removeLearnedPreference(key)
    }

    fun clearLearnedPreferences() {
        prefs.edit().remove(KEY_LEARNED).apply()
    }

    fun clearAllProductPreferences() {
        prefs.edit().clear().apply()
    }

    private fun saveLearned(items: List<LearnedPreference>) {
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(JSONObject().apply {
                put("package", p.packageName)
                put("sender", p.sender.orEmpty())
                put("category", p.category?.name.orEmpty())
                put("priority", p.priority?.name.orEmpty())
            })
        }
        prefs.edit().putString(KEY_LEARNED, arr.toString()).apply()
    }

    private fun vipToken(packageName: String, sender: String?): String =
        "$packageName|${sender.orEmpty().trim().lowercase()}"

    private companion object {
        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_DIGEST_ENABLED = "digest_enabled"
        const val KEY_DIGEST_HOUR = "digest_hour"
        const val KEY_DIGEST_MINUTE = "digest_minute"
        const val KEY_LAST_DIGEST_AT = "last_digest_at"
        const val KEY_AUTO_CLEANUP = "auto_cleanup"
        const val KEY_RETENTION_DAYS = "retention_days"
        const val KEY_LOCAL_AI = "local_ai_enabled"
        const val KEY_LOCAL_AI_CONSENT = "local_ai_explicit_consent"
        const val KEY_MODEL_PATH = "model_path"
        const val KEY_HIDE_SENSITIVE = "hide_sensitive_previews"
        const val KEY_AUTO_COPY_OTP = "auto_copy_otp"
        const val KEY_AUTO_COPY_OTP_CONSENT = "auto_copy_otp_explicit_consent"
        const val KEY_ANTI_REVOKE_ENABLED = "anti_revoke_enabled"
        const val KEY_EXPENSE_TRACKER_ENABLED = "expense_tracker_enabled"
        const val KEY_EXPENSE_TRACKER_CONSENT = "expense_tracker_explicit_consent"
        const val KEY_UNWANTED_PROMPT_ENABLED = "unwanted_prompt_enabled"
        const val KEY_UNWANTED_CLEANUP_DAYS = "unwanted_cleanup_days"
        const val KEY_LAST_UNWANTED_PROMPT_AT = "last_unwanted_prompt_at"
        const val KEY_MODEL_PROMPT_DISMISSED = "model_prompt_dismissed"
        const val KEY_QUIET_PACKAGES = "quiet_packages"
        const val KEY_QUIET_CATEGORIES = "quiet_categories"
        const val KEY_VIP_RULES = "vip_rules"
        const val KEY_EXCLUDED_PACKAGES = "excluded_packages"
        const val KEY_LEARNED = "learned_preferences"
        const val KEY_VOICE_READER_ENABLED = "voice_reader_enabled"
        const val KEY_DRIVING_MODE_ACTIVE = "driving_mode_active"
        const val KEY_VOICE_TRIGGER_CONDITION = "voice_trigger_condition"
        const val KEY_VOICE_READER_FILTER = "voice_reader_filter"
        const val KEY_VOICE_READING_DETAIL = "voice_reading_detail"
        const val KEY_VOICE_SPEECH_RATE = "voice_speech_rate"
        const val KEY_MANUAL_FOCUS_ACTIVE = "manual_focus_active"
        const val KEY_MANUAL_FOCUS_TYPE = "manual_focus_type"
        const val KEY_SLEEP_FOCUS_SCHEDULED = "sleep_focus_scheduled"
        const val KEY_WORK_FOCUS_SCHEDULED = "work_focus_scheduled"
        const val KEY_FOCUS_BLOCKED_COUNT = "focus_blocked_count"
        const val KEY_FOCUS_ACTIVATED_AT = "focus_activated_at"
        const val KEY_FOCUS_SESSION_TOKEN = "focus_session_token"
        const val KEY_APP_THEME_MODE = "app_theme_mode"
        const val KEY_USE_DYNAMIC_COLORS = "use_dynamic_colors"
        const val KEY_MODEL_AUTO_DOWNLOAD_STARTED = "model_auto_download_started"
    }
}

