package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

/** Explicit current-order status may advance a not-yet-shipped order without inventing a trace. */
object PddOrderProgress {
    private val known=setOf("待发货","待收货","待确认收货","待成团","待付款","已取消","退款成功","交易关闭","订单关闭")
    fun recognized(value:String)=value.trim().takeIf {it in known}.orEmpty()
    fun apply(item:ExpressItem,status:String):ExpressItem {
        val explicit=recognized(status)
        if(explicit.isBlank())return item
        val cancelled=listOf("取消","退款","关闭").any(explicit::contains)
        if(cancelled)return item.copy(orderStatus=explicit,state=4,stateName=explicit,pickupCode="",historicalPickupCode=item.pickupCode.ifBlank {item.historicalPickupCode})
        if(ParcelPresentation.stage(item) !in setOf(ParcelPresentation.Stage.NOT_SHIPPED,ParcelPresentation.Stage.UNVERIFIED))return item
        return item.copy(orderStatus=explicit,state=if(explicit in setOf("待收货","待确认收货"))0 else 1,
            stateName=explicit,latestText=explicit,latestTime="",logisticsStatus="",observedAt=System.currentTimeMillis())
    }
}
