package com.halo.expressassistant.hub

/** A calendar-day bound applies even during an explicit history expansion. */
object TbPagingPolicy {
    fun window(days:Int,now:Long=System.currentTimeMillis())=SyncWindow(HistoryRangePolicy.clamp(days),now)
    fun pageLimit(days:Int)=when {HistoryRangePolicy.clamp(days)<=7->3;days<=30->8;else->24}
    fun inRange(createdAt:Long,since:Long,now:Long)=createdAt in since..now
    fun reachedBoundary(dates:List<Long>,since:Long)=dates.isNotEmpty() && dates.all {it in 1 until since}
}
