package com.premraj.notiflow

import android.app.Application
import com.premraj.notiflow.data.NotificationStore
import com.premraj.notiflow.data.UserPreferences
import com.premraj.notiflow.util.NotificationChannels
import com.premraj.notiflow.widget.NotificationWidget
import com.premraj.notiflow.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotiFlowApplication : Application() {
    lateinit var graph: AppGraph
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        NotificationChannels.create(this)
        graph.workScheduler.ensureCleanupScheduled()
        if (graph.preferences.digestEnabled) graph.workScheduler.scheduleDigest(updateExisting = false)
        appScope.launch {
            graph.store.initialLoadComplete.first { it }
            graph.store.items.collect { items ->
                NotificationWidget.update(this@NotiFlowApplication, items)
            }
        }
    }
}

class AppGraph(application: Application) {
    val preferences = UserPreferences(application)
    val store = NotificationStore(application)
    val workScheduler = WorkScheduler(application, preferences)
}

val android.content.Context.appGraph: AppGraph
    get() = (applicationContext as NotiFlowApplication).graph

