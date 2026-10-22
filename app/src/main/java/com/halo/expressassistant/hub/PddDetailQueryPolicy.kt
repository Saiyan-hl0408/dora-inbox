package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.DetailPoint

object PddDetailQueryPolicy {
    const val NORMAL_BATCH=4
    const val NORMAL_BUDGET_MS=40000L
    const val RETRY_DELAY_MS=10*60*1000L
    fun hasLogistics(points:List<DetailPoint>)=points.any {it.context.isNotBlank() && ParcelTime.parse(it.time)>0}
    fun fingerprint(item:ExpressItem)="${item.mailNo}|${item.orderStatus}|${item.logisticsStatus}"
    fun mayAttempt(now:Long,retryAt:Long,sameStatus:Boolean)=!sameStatus || now>=retryAt
    fun priority(item:ExpressItem)=when(ParcelPresentation.stage(item)) {
        ParcelPresentation.Stage.PICKUP -> 0
        ParcelPresentation.Stage.UNVERIFIED,ParcelPresentation.Stage.TRANSIT -> 1
        else -> 2
    }
    fun remainingTimeout(startedAt:Long,now:Long,historyImport:Boolean):Long=if(historyImport)25000L
        else (NORMAL_BUDGET_MS-(now-startedAt)).coerceIn(0,25000L)
}
