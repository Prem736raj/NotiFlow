package com.premraj.notiflow.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val REMINDERS = "reminders"
    const val DIGESTS = "digests"
    const val OTP_ALERTS = "otp_alerts"
    const val CLEANUP_ALERTS = "cleanup_alerts"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    REMINDERS,
                    "Saved notification reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications you explicitly asked NotiFlow to remind you about"
                },
                NotificationChannel(
                    DIGESTS,
                    "Notification digests",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Scheduled summaries of low-priority notification activity"
                },
                NotificationChannel(
                    OTP_ALERTS,
                    "Auto-copied verification codes",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Instant confirmation when an OTP is automatically copied to your clipboard"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    CLEANUP_ALERTS,
                    "Unwanted message cleanup prompts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Periodic prompts to delete old unwanted recharge and promo messages"
                }
            )
        )
    }
}

