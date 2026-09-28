package com.premraj.notiflow.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.premraj.notiflow.data.UserPreferences
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WorkScheduler(
    private val context: Context,
    private val preferences: UserPreferences
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun scheduleReminder(notificationId: Long, atMillis: Long) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_NOTIFICATION_ID to notificationId))
            .addTag(REMINDER_TAG)
            .build()
        workManager.enqueueUniqueWork(
            "notiflow-reminder-$notificationId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReminder(notificationId: Long) {
        workManager.cancelUniqueWork("notiflow-reminder-$notificationId")
    }

    fun cancelAllReminders() {
        workManager.cancelAllWorkByTag(REMINDER_TAG)
    }

    fun scheduleDigest(updateExisting: Boolean = true) {
        if (!preferences.digestEnabled) {
            workManager.cancelUniqueWork(DIGEST_WORK)
            return
        }
        workManager.enqueueUniqueWork(
            DIGEST_WORK,
            if (updateExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            digestRequest()
        )
    }

    /**
     * Called by DigestWorker after a successful/best-effort daily run. Recomputing
     * the next local clock target avoids cumulative 24-hour periodic-work drift.
     */
    fun scheduleNextDigest() {
        if (!preferences.digestEnabled) return
        workManager.enqueueUniqueWork(
            DIGEST_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            digestRequest()
        )
    }

    fun cancelDigest() {
        workManager.cancelUniqueWork(DIGEST_WORK)
    }

    fun ensureCleanupScheduled() {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(24, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork(
            CLEANUP_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun digestRequest() = OneTimeWorkRequestBuilder<DigestWorker>()
        .setInitialDelay(
            millisUntilNext(preferences.digestHour, preferences.digestMinute),
            TimeUnit.MILLISECONDS
        )
        .build()

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(1_000L)
    }

    private companion object {
        const val DIGEST_WORK = "notiflow-digest"
        const val CLEANUP_WORK = "notiflow-cleanup"
        const val REMINDER_TAG = "notiflow-reminders"
    }
}

