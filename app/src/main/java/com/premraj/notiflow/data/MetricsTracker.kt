package com.premraj.notiflow.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Privacy-preserving, local on-device metrics tracker.
 *
 * Implements the coarse event funnel defined in the growth roadmap.
 * Strictly records coarse integer counters only.
 * NEVER stores or transmits notification titles, message bodies, sender identities,
 * OTP codes, financial details, or personal information.
 */
object MetricsTracker {

    private const val PREFS_NAME = "notiflow_metrics"

    const val EVENT_DISCLOSURE_SHOWN = "disclosure_shown"
    const val EVENT_PERMISSION_ENABLED = "permission_enabled"
    const val EVENT_CAPTURE_VERIFIED = "capture_verified"
    const val EVENT_NOTIFICATION_MARKED_LATER = "notification_marked_later"
    const val EVENT_REMINDER_SCHEDULED = "reminder_scheduled"
    const val EVENT_REMINDER_DELIVERED = "reminder_delivered"
    const val EVENT_NOTIFICATION_RETRIEVED = "notification_retrieved"
    const val EVENT_LISTENER_DISCONNECTED = "listener_disconnected"
    const val EVENT_PERMISSION_REVOKED = "permission_revoked"

    val KNOWN_EVENTS = listOf(
        EVENT_DISCLOSURE_SHOWN,
        EVENT_PERMISSION_ENABLED,
        EVENT_CAPTURE_VERIFIED,
        EVENT_NOTIFICATION_MARKED_LATER,
        EVENT_REMINDER_SCHEDULED,
        EVENT_REMINDER_DELIVERED,
        EVENT_NOTIFICATION_RETRIEVED,
        EVENT_LISTENER_DISCONNECTED,
        EVENT_PERMISSION_REVOKED
    )

    internal var prefsProvider: ((Context) -> SharedPreferences)? = null

    private fun prefs(context: Context): SharedPreferences =
        prefsProvider?.invoke(context) ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordEvent(context: Context, event: String) {
        val sp = prefs(context)
        val current = sp.getLong(event, 0L)
        sp.edit().putLong(event, current + 1L).apply()
    }

    fun getEventCount(context: Context, event: String): Long {
        return prefs(context).getLong(event, 0L)
    }

    fun getAllEventCounts(context: Context): Map<String, Long> {
        val sp = prefs(context)
        return KNOWN_EVENTS.associateWith { sp.getLong(it, 0L) }
    }

    fun clearMetrics(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
