package com.premraj.notiflow.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object NotificationPosting {
    fun canPost(context: Context, channelId: String? = null): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channelId != null) {
            val channel = manager.getNotificationChannel(channelId) ?: return true
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }

        return true
    }

    fun safeNotificationId(id: Long, offset: Int = 0): Int {
        val folded = ((id xor (id ushr 32)).toInt() and 0x3FFFFFFF)
        return (folded + offset) and 0x7FFFFFFF
    }
}

