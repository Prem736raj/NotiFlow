package com.premraj.notiflow

import android.app.Application
import com.premraj.notiflow.data.NotificationStore
import com.premraj.notiflow.data.UserPreferences
import com.premraj.notiflow.util.NotificationChannels
import com.premraj.notiflow.work.WorkScheduler

class NotiFlowApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        NotificationChannels.create(this)
        graph.workScheduler.ensureCleanupScheduled()
        if (graph.preferences.digestEnabled) graph.workScheduler.scheduleDigest(updateExisting = false)
    }
}

class AppGraph(application: Application) {
    val preferences = UserPreferences(application)
    val store = NotificationStore(application)
    val workScheduler = WorkScheduler(application, preferences)
}

val android.content.Context.appGraph: AppGraph
    get() = (applicationContext as NotiFlowApplication).graph

