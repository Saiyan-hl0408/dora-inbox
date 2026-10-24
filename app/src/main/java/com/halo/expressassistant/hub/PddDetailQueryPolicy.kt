package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.DetailPoint

object PddDetailQueryPolicy {
    const val VERSION=16
    const val NORMAL_BATCH=4
    const val NORMAL_BUDGET_MS=40000L
    const val RETRY_DELAY_MS=10*60*1000L
    fun mayReadDetails(listWasRead:Boolean,count:Int,error:String?)=listWasRead && (count>0 || error==null)
    fun hasLogistics(points:List<DetailPoint>)=points.any {it.context.isNotBlank() && ParcelTime.parse(it.time)>0}
    fun fingerprint(item:ExpressItem)="${item.mailNo}|${item.orderStatus}|${item.logisticsStatus}"
    fun mayAttempt(now:Long,retryAt:Long,sameStatus:Boolean,manual:Boolean=false)=manual || !sameStatus || now>=retryAt
    fun priority(item:ExpressItem)=when(ParcelPresentation.stage(item)) {
        ParcelPresentation.Stage.PICKUP -> 0
        ParcelPresentation.Stage.UNVERIFIED,ParcelPresentation.Stage.TRANSIT -> 1
        ParcelPresentation.Stage.CLOSED -> if(awaitingPhysicalTime(item))1 else if(missingLocation(item))2 else 3
        else -> 2
    }
    private fun awaitingPhysicalTime(item:ExpressItem)=item.source=="pdd" && item.completedAt==0L &&
        (item.orderStatus.contains("待确认收货") || item.orderStatus.contains("已签收") && item.orderStatus.contains("待收货"))
    fun active(item:ExpressItem)=item.pddReadRepairRequired || ActiveParcelRefresh.includes(item) || awaitingPhysicalTime(item)
    fun missingLocation(item:ExpressItem)=item.source=="pdd" && item.pickupStation.isBlank() && item.pddPickupStation.isBlank() && item.pickupAddress.isBlank() &&
        ParcelPresentation.stage(item) in setOf(ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.CLOSED)
    fun needsRefresh(item:ExpressItem,now:Long,fetchedAt:Long,version:Int)=
        active(item) || item.isOrderNumber || item.latestTime.isBlank() || version<14 || (missingLocation(item) && version<VERSION) || now-fetchedAt>=15*60*1000L
    fun normalSelection(items:List<ExpressItem>)=items.filter(::active)+items.filterNot(::active).take(NORMAL_BATCH)
    fun timeout(item:ExpressItem,startedAt:Long,now:Long,historyImport:Boolean)=
        if(active(item))25000L else remainingTimeout(startedAt,now,historyImport)
    fun remainingTimeout(startedAt:Long,now:Long,historyImport:Boolean):Long=if(historyImport)25000L
        else (NORMAL_BUDGET_MS-(now-startedAt)).coerceIn(0,25000L)
}
