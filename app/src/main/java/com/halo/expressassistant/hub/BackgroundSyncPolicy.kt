package com.halo.expressassistant.hub

/** One freshness window for scheduled work and returning to the foreground. */
object BackgroundSyncPolicy {
    val intervals=listOf(15,30,60)
    const val DEFAULT_MINUTES=15
    const val FAILURE_RETRY_MS=10*60*1000L
    fun interval(minutes:Int)=minutes.takeIf {it in intervals} ?: DEFAULT_MINUTES
    fun due(now:Long,lastSuccess:Long,lastAttempt:Long,minutes:Int):Boolean {
        if(lastAttempt>lastSuccess && lastAttempt>0 && now-lastAttempt<FAILURE_RETRY_MS)return false
        return lastSuccess<=0 || now-lastSuccess>=interval(minutes)*60_000L || now<lastSuccess
    }
}
