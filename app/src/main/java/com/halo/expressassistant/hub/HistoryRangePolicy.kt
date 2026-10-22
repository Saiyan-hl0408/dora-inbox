package com.halo.expressassistant.hub

object HistoryRangePolicy {
    const val DEFAULT_DAYS=7
    const val MAX_DAYS=180
    val options=listOf(3,7,15,30,90,180)
    fun clamp(days:Int)=if(days<=0)MAX_DAYS else days.coerceAtMost(MAX_DAYS)
}
