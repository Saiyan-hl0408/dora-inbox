package com.halo.expressassistant.hub

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/** Own the complete list + logistics + pickup run, including the gap after SyncEngine. */
object SyncRunCoordinator {
    private val running=AtomicBoolean(false)
    fun isBusy()=running.get()
    fun acquire(context:Context,recordAttempt:Boolean=true):Boolean {
        if(!running.compareAndSet(false,true))return false
        if(recordAttempt)HubData.prefs(context).edit().putLong("sync_run_attempt_at",System.currentTimeMillis()).apply()
        return true
    }
    fun finish(context:Context,success:Boolean) {
        if(success)HubData.prefs(context).edit().putLong("sync_run_success_at",System.currentTimeMillis()).apply()
        running.set(false)
    }
    fun due(context:Context):Boolean {
        val prefs=HubData.prefs(context)
        return BackgroundSyncPolicy.due(System.currentTimeMillis(),prefs.getLong("sync_run_success_at",0),
            prefs.getLong("sync_run_attempt_at",0),com.halo.expressassistant.data.Store.backgroundSyncInterval(context))
    }
}
