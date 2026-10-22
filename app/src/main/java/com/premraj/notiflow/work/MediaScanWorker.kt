package com.premraj.notiflow.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.premraj.notiflow.appGraph
import com.premraj.notiflow.data.MediaSaver

class MediaScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.appGraph
        if (!graph.preferences.saveMediaEnabled) return Result.success()
        runCatching {
            MediaSaver.scan(applicationContext, graph.store, graph.preferences)
        }
        return Result.success()
    }
}
