package com.halo.expressassistant

import android.content.Context
import androidx.work.*
import com.halo.expressassistant.data.Store
import java.util.concurrent.TimeUnit

object BackgroundSyncScheduler {
    const val WORK_NAME="express_tracking"
    fun configure(context:Context) {
        val manager=WorkManager.getInstance(context.applicationContext)
        if(!Store.backgroundSyncEnabled(context)){manager.cancelUniqueWork(WORK_NAME);return}
        val minutes=Store.backgroundSyncInterval(context).toLong()
        val request=PeriodicWorkRequestBuilder<TrackingWorker>(minutes,TimeUnit.MINUTES)
            .setInitialDelay(minutes,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
    }
}
