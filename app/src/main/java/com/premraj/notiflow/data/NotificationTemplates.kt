package com.premraj.notiflow.data

/**
 * Built-in notification presets and templates.
 *
 * Implements the P2 roadmap requirement:
 * "Templates rather than complicated automation: 'Keep bank and OTP alerts visible',
 * 'Batch promotions', 'Remind me about delivery updates', 'Work hours'.
 * Every preset needs opt-in, preview, cancel/undo and fail-safe behavior."
 */
data class NotificationTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val details: String,
    val isEnabled: Boolean
)

object NotificationTemplates {

    const val ID_BANK_PROTECT = "bank_otp_protect"
    const val ID_PROMO_DIGEST = "promo_digest"
    const val ID_DELIVERY_REMIND = "delivery_remind"
    const val ID_WORK_HOURS = "work_hours_quiet"

    fun getAvailableTemplates(preferences: UserPreferences): List<NotificationTemplate> {
        val quietCategories = preferences.quietCategories()
        val isPromoDigested = NotificationCategory.PROMOTION in quietCategories
        val isBankProtected = preferences.isTemplateEnabled(ID_BANK_PROTECT, default = true)
        val isDeliveryRemind = preferences.isTemplateEnabled(ID_DELIVERY_REMIND, default = true)
        val isWorkHours = preferences.workFocusScheduled

        return listOf(
            NotificationTemplate(
                id = ID_BANK_PROTECT,
                title = "Keep bank & OTP alerts visible",
                subtitle = "Protect sensitive alerts from status-bar quieting or accidental dismissal.",
                details = "Ensures all OTP codes and banking transactions are always classified as High Priority, never silenced by focus modes, and never bulk-cleared.",
                isEnabled = isBankProtected
            ),
            NotificationTemplate(
                id = ID_PROMO_DIGEST,
                title = "Batch promotions into digest",
                subtitle = "Contain shopping deals and coupons so they do not interrupt your day.",
                details = "Automatically assigns promotional notifications to the low-priority quiet digest. They will be collected into your daily summary.",
                isEnabled = isPromoDigested
            ),
            NotificationTemplate(
                id = ID_DELIVERY_REMIND,
                title = "Delivery follow-up reminders",
                subtitle = "Easily set reminders when delivery or courier notices arrive.",
                details = "Highlights 1-tap follow-up options for parcel tracking, arrival notices, and pickup deadlines.",
                isEnabled = isDeliveryRemind
            ),
            NotificationTemplate(
                id = ID_WORK_HOURS,
                title = "Work hours quiet mode",
                subtitle = "Quiets distracting notifications during active work hours (9 AM - 6 PM).",
                details = "Automatically activates Work Focus profile during business hours while keeping calls, VIPs, and OTPs completely accessible.",
                isEnabled = isWorkHours
            )
        )
    }

    fun applyTemplate(preferences: UserPreferences, templateId: String, enable: Boolean) {
        preferences.setTemplateEnabled(templateId, enable)
        when (templateId) {
            ID_BANK_PROTECT -> {
                // Bank protect ensures OTP and Payment categories are NOT in quiet categories
                if (enable) {
                    preferences.setQuietCategory(NotificationCategory.OTP, quiet = false)
                    preferences.setQuietCategory(NotificationCategory.PAYMENT, quiet = false)
                }
            }
            ID_PROMO_DIGEST -> {
                preferences.setQuietCategory(NotificationCategory.PROMOTION, quiet = enable)
                preferences.setQuietCategory(NotificationCategory.SPAM, quiet = enable)
            }
            ID_DELIVERY_REMIND -> {
                // Enabled state flag recorded in preferences
            }
            ID_WORK_HOURS -> {
                preferences.workFocusScheduled = enable
            }
        }
    }
}
