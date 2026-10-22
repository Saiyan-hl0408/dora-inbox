package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

data class SyncWindow(val days:Int=HistoryRangePolicy.DEFAULT_DAYS,val now:Long=System.currentTimeMillis(),val sinceOverride:Long?=null,val exhaustive:Boolean=false) {
    val since=sinceOverride ?: ParcelTime.cutoff(days,now)
    val pageBudget=if(exhaustive)Int.MAX_VALUE-1 else when {days<=7->3;days<=30->8;days<=180->24;else->40}
    fun include(item:ExpressItem):Boolean {
        return when(ParcelPresentation.stage(item)) {
            ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.TRANSIT -> true
            else -> ParcelPresentation.eventTime(item)>=since
        }
    }
    fun reachedOldHistory(items:List<ExpressItem>):Boolean=items.isNotEmpty() && items.all {
        ParcelPresentation.stage(it) in setOf(ParcelPresentation.Stage.CLOSED,ParcelPresentation.Stage.AFTERSALE) && ParcelPresentation.eventTime(it) in 1 until since
    }
    fun reachedOrderBoundary(page:List<ExpressItem>):Boolean=page.isNotEmpty() && page.all {it.orderCreatedAt in 1 until since}
}
