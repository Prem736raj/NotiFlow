package com.premraj.notiflow.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.premraj.notiflow.MainActivity
import com.premraj.notiflow.R
import com.premraj.notiflow.appGraph
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.util.NotificationChannels

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
            id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notiflow)
            .setContentTitle("Reminder · ${item.appName}")
            .setContentText(item.displayTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.displayBody))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(applicationContext).notify((10_000 + id).toInt(), notification)
        }
        return Result.success()
    }

    companion object {
        const val KEY_NOTIFICATION_ID = "notification_id"
    }
}
