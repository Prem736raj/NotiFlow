package com.premraj.notiflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.premraj.notiflow.MainActivity
import com.premraj.notiflow.R
import com.premraj.notiflow.appGraph
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.intelligence.LocalIntelligence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotiFlowWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val items = appContext.appGraph.store.allItems()
                NotificationWidget.update(appContext, items)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object NotificationWidget {
    fun update(context: Context, notifications: List<NotificationItem>) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val component = ComponentName(context, NotiFlowWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val pending = notifications.filter {
            it.state == NotificationState.ACTIVE &&
                (it.isActiveOnSystem || it.pinned) &&
                (LocalIntelligence.effectivePriority(it) != NotificationPriority.LOW || it.pinned)
        }
        val high = pending.count { LocalIntelligence.effectivePriority(it) == NotificationPriority.HIGH }
        val later = notifications.count { it.state == NotificationState.LATER }

        val summary = when {
            pending.isEmpty() && later == 0 -> "Inbox clear"
            pending.isEmpty() -> "$later waiting for later"
            later == 0 -> "${pending.size} ${if (pending.size == 1) "alert" else "alerts"} waiting"
            else -> "${pending.size} waiting · $later later"
        }
        val meta = when {
            high > 0 -> "$high important · Tap to review"
            pending.isNotEmpty() -> "Tap to triage"
            else -> "Tap to open NotiFlow"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val views = RemoteViews(context.packageName, R.layout.notification_widget).apply {
            setTextViewText(R.id.widget_summary, summary)
            setTextViewText(R.id.widget_meta, meta)
            setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        }
        manager.updateAppWidget(ids, views)
    }
}
