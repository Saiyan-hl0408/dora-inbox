package com.halo.expressassistant.hub

/** One persisted range feeds both history display and subsequent platform reads. */
object HistorySelectionPolicy {
    fun resolve(historyDays:Int?,syncDays:Int?):Int =
        HistoryRangePolicy.clamp(historyDays ?: syncDays ?: HistoryRangePolicy.DEFAULT_DAYS)

    data class Selection(val days:Int,val readSelectedRange:Boolean)

    /** Coverage is a coarse list boundary, not proof that individual detail fields are complete. */
    fun select(days:Int,needsExpansion:Boolean) =
        Selection(HistoryRangePolicy.clamp(days),needsExpansion)
}
