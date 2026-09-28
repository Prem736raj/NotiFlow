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
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.intelligence.LocalIntelligence
import com.premraj.notiflow.util.NotificationChannels
import com.premraj.notiflow.util.NotificationPosting

class DigestWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.appGraph
        if (!graph.preferences.digestEnabled) return Result.success()

        val now = System.currentTimeMillis()
        val since = graph.preferences.lastDigestAt.takeIf { it > 0L } ?: now - 24 * 60 * 60_000L
        val items = graph.store.itemsBetween(since, now).filter {
            it.state == NotificationState.ACTIVE && !it.pinned
        }
        if (items.isEmpty()) {
            graph.preferences.lastDigestAt = now
            graph.workScheduler.scheduleNextDigest()
            return Result.success()
        }

        val important = items.filter { LocalIntelligence.effectivePriority(it, now) == NotificationPriority.HIGH }
        val lowValue = items.filter { LocalIntelligence.effectivePriority(it, now) != NotificationPriority.HIGH }
        if (lowValue.isEmpty()) {
            graph.preferences.lastDigestAt = now
            graph.workScheduler.scheduleNextDigest()
            return Result.success()
        }

        val counts = lowValue.groupingBy { it.category }.eachCount()
        val structured = counts.entries
            .sortedByDescending { it.value }
            .take(4)
            .joinToString(", ") { "${it.value} ${shortLabel(it.key)}" }
            .ifBlank { "${lowValue.size} notifications" }
        val fallback = buildString {
            append(structured.replaceFirstChar { it.uppercase() })
            if (important.isNotEmpty()) append(". ${important.size} important item${if (important.size == 1) "" else "s"} also arrived.")
        }
        val summary = if (graph.preferences.hideSensitivePreviews) {
            fallback
        } else {
            graph.gemma.summarize(
                lowValue.take(24).map { "${it.appName}: ${it.displayTitle} — ${it.displayBody}" },
                fallback
            )
        }

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_DIGEST, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            7001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicVersion = NotificationCompat.Builder(applicationContext, NotificationChannels.DIGESTS)
            .setSmallIcon(R.drawable.ic_notiflow)
            .setContentTitle("NotiFlow digest")
            .setContentText("${lowValue.size} notifications are ready to review")
            .build()

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.DIGESTS)
            .setSmallIcon(R.drawable.ic_notiflow)
            .setContentTitle("Your NotiFlow digest")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setNumber(lowValue.size)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()

        if (!NotificationPosting.canPost(applicationContext, NotificationChannels.DIGESTS)) {
            graph.workScheduler.scheduleNextDigest()
            return Result.success()
        }

        return runCatching {
            postNotification(7001, notification)
            graph.preferences.lastDigestAt = now
        }.fold(
            onSuccess = {
                graph.workScheduler.scheduleNextDigest()
                Result.success()
            },
            onFailure = { Result.retry() }
        )
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(id: Int, notification: android.app.Notification) {
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }

    private fun shortLabel(category: NotificationCategory): String = when (category) {
        NotificationCategory.DELIVERY -> "deliveries"
        NotificationCategory.PAYMENT -> "payments"
        NotificationCategory.MESSAGE -> "messages"
        NotificationCategory.PROMOTION -> "promotions"
        NotificationCategory.SOCIAL -> "social updates"
        NotificationCategory.OTP -> "verification codes"
        NotificationCategory.WORK_STUDY -> "work/study items"
        NotificationCategory.REMINDER_EVENT -> "events"
        NotificationCategory.SPAM -> "low-value alerts"
        NotificationCategory.OTHER -> "other notifications"
    }
}
