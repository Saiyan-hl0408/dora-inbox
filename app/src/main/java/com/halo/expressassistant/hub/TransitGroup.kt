package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

/** Presentation group includes paid orders preparing shipment, while retaining their real substatus. */
object TransitGroup {
    fun includes(item:ExpressItem):Boolean {
        if(ParcelEligibility.isVirtual(item))return false
        return when(ParcelPresentation.stage(item)) {
            ParcelPresentation.Stage.TRANSIT -> true
            ParcelPresentation.Stage.NOT_SHIPPED -> (item.source=="jd" && item.stateNum==101) ||
                ParcelStatusAliases.packing.any {(item.orderStatus+" "+item.stateName).contains(it)}
            else -> false
        }
    }
    fun rank(item:ExpressItem):Int=when {
        ParcelStatusAliases.delivering.any {(item.logisticsStatus+" "+item.stateName).contains(it)} || item.state==5 -> 0
        ParcelPresentation.stage(item)==ParcelPresentation.Stage.TRANSIT -> 1
        else -> 2
    }
    fun ordered(items:List<ExpressItem>)=items.filter(::includes).sortedWith(compareBy<ExpressItem> {rank(it)}
        .thenByDescending {it.statusUpdatedAt.takeIf {at->at>0} ?: it.orderCreatedAt})
    fun label(item:ExpressItem):String {
        if(rank(item)==0)return "派送中"
        if(ParcelPresentation.stage(item)==ParcelPresentation.Stage.TRANSIT)return "运输中"
        val text=item.orderStatus+" "+item.stateName
        return when {
            listOf("打包","备货").any(text::contains) -> "打包中"
            listOf("出库").any(text::contains) -> "待出库"
            else -> "待发货"
        }
    }
}
