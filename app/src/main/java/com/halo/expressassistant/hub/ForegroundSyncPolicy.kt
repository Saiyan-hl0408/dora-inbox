package com.halo.expressassistant.hub

object ForegroundSyncPolicy {
    const val MIN_INTERVAL_MS=15*60_000L
    fun shouldSync(didSync:Boolean,hasActive:Boolean,now:Long,lastAttempt:Long):Boolean =
        (!didSync || hasActive) && (lastAttempt<=0 || now-lastAttempt>=MIN_INTERVAL_MS)
}
