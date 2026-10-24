package com.halo.expressassistant.hub

/** Manual refresh bypasses the automatic cooldown, and known unfinished parcels always query their current official state. */
object JdlQueryPolicy {
    fun localOnly(automatic:Boolean,historyImport:Boolean,expanding:Boolean,now:Long,lastSuccess:Long,lastAttempt:Long,hasActive:Boolean=false):Boolean {
        if(historyImport)return false
        if(!automatic || hasActive)return false
        fun recent(at:Long,ttl:Long)=at>0 && now>=at && now-at<ttl
        return recent(lastSuccess,5*60*1000L) || recent(lastAttempt,60*1000L)
    }
}
