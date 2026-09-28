package com.premraj.notiflow.work

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.premraj.notiflow.MainActivity
import com.premraj.notiflow.R
import com.premraj.notiflow.appGraph
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.util.NotificationChannels
import com.premraj.notiflow.util.NotificationPosting

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_NOTIFICATION_ID, -1L)
        if (id <= 0) return Result.failure()
        val item = applicationContext.appGraph.store.get(id) ?: return Result.success()
        if (item.state != NotificationState.LATER) return Result.success()

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_NOTIFICATION_ID, id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NotificationPosting.safeNotificationId(id, 20_000),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isSensitive = item.category in setOf(
            NotificationCategory.OTP,
            NotificationCategory.PAYMENT
        )
        val hideContent = applicationContext.appGraph.preferences.hideSensitivePreviews && isSensitive

        val publicVersion = NotificationCompat.Builder(applicationContext, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notiflow)
            .setContentTitle("NotiFlow reminder")
            .setContentText("Open NotiFlow to view reminder details")
            .build()

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notiflow)
            .setContentTitle(if (hideContent) "NotiFlow reminder" else "Reminder · ${item.appName}")
            .setContentText(if (hideContent) "Open NotiFlow to view this sensitive reminder" else item.displayTitle)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (hideContent) "Sensitive notification content is hidden." else item.displayBody
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()

        if (!NotificationPosting.canPost(applicationContext, NotificationChannels.REMINDERS)) {
            return Result.success()
        }

        val notificationId = NotificationPosting.safeNotificationId(id, 10_000)
        return runCatching {
            postNotification(notificationId, notification)
            applicationContext.appGraph.store.setState(id, NotificationState.ACTIVE)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(id: Int, notification: android.app.Notification) {
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }

    companion object {
        const val KEY_NOTIFICATION_ID = "notification_id"
    }
}
