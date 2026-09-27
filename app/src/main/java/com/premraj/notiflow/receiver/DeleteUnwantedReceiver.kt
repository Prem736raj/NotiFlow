package com.premraj.notiflow.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.premraj.notiflow.appGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeleteUnwantedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DELETE_UNWANTED) return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 8001)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.cancel(notificationId)

        val days = intent.getIntExtra(EXTRA_DAYS, 15)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val graph = context.appGraph
                val deleted = graph.store.deleteUnwantedOldMessages(days)
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Deleted $deleted unwanted messages (recharges & promotions)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DELETE_UNWANTED = "com.premraj.notiflow.ACTION_DELETE_UNWANTED"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_DAYS = "extra_days"
    }
}

