package com.premraj.notiflow.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.premraj.notiflow.appGraph

class CleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.appGraph
        if (graph.preferences.autoCleanupEnabled) {
            graph.store.cleanup(graph.preferences.retentionDays)
        }
        return Result.success()
    }
}
