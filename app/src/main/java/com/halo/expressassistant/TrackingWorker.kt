package com.halo.expressassistant

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.halo.expressassistant.hub.BackgroundSyncRunner

/** Scheduled bound-platform sync; controlled only by the explicit background setting. */
class TrackingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        BackgroundSyncRunner.run(applicationContext)
        return Result.success()
    }

}
