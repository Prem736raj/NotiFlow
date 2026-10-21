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
        get() = prefs.getBoolean(KEY_DIGEST_ENABLED, false)
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
        get() = prefs.getBoolean(KEY_AUTO_CLEANUP, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CLEANUP, value).apply()

    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, 30)
        set(value) = prefs.edit().putInt(KEY_RETENTION_DAYS, value.coerceIn(1, 365)).apply()

    var hideSensitivePreviews: Boolean
        get() = prefs.getBoolean(KEY_HIDE_SENSITIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_SENSITIVE, value).apply()

    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()

    var autoCopyOtp: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COPY_OTP, false) &&
            prefs.getBoolean(KEY_AUTO_COPY_OTP_CONSENT, false)
        set(value) = prefs.edit()
            .putBoolean(KEY_AUTO_COPY_OTP, value)
            .putBoolean(KEY_AUTO_COPY_OTP_CONSENT, value)
            .apply()

    var expenseTrackerEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXPENSE_TRACKER_ENABLED, false) &&
            prefs.getBoolean(KEY_EXPENSE_TRACKER_CONSENT, false)
        set(value) = prefs.edit()
            .putBoolean(KEY_EXPENSE_TRACKER_ENABLED, value)
            .putBoolean(KEY_EXPENSE_TRACKER_CONSENT, value)
            .apply()

    var appThemeMode: AppThemeMode
        get() {
            val name = prefs.getString(KEY_APP_THEME_MODE, AppThemeMode.SYSTEM.name)
            return runCatching { AppThemeMode.valueOf(name ?: "") }.getOrDefault(AppThemeMode.SYSTEM)
        }
        set(value) = prefs.edit().putString(KEY_APP_THEME_MODE, value.name).apply()

    var useDynamicColors: Boolean
        get() = prefs.getBoolean(KEY_USE_DYNAMIC_COLORS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_DYNAMIC_COLORS, value).apply()

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
        const val KEY_HIDE_SENSITIVE = "hide_sensitive_previews"
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        const val KEY_AUTO_COPY_OTP = "auto_copy_otp"
        const val KEY_AUTO_COPY_OTP_CONSENT = "auto_copy_otp_explicit_consent"
        const val KEY_EXPENSE_TRACKER_ENABLED = "expense_tracker_enabled"
        const val KEY_EXPENSE_TRACKER_CONSENT = "expense_tracker_explicit_consent"
        const val KEY_QUIET_PACKAGES = "quiet_packages"
        const val KEY_QUIET_CATEGORIES = "quiet_categories"
        const val KEY_VIP_RULES = "vip_rules"
        const val KEY_EXCLUDED_PACKAGES = "excluded_packages"
        const val KEY_LEARNED = "learned_preferences"
        const val KEY_APP_THEME_MODE = "app_theme_mode"
        const val KEY_USE_DYNAMIC_COLORS = "use_dynamic_colors"
    }
}

