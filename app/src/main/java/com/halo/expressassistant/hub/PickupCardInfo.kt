package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

object PickupCardInfo {
    fun legacyJdLocation(item:ExpressItem)=item.source=="jd" && item.queryChannel.startsWith("https://kd.jd.com/express/orderDetail") && item.pickupLocationVersion==0
    fun address(item:ExpressItem)=if(legacyJdLocation(item))"" else item.pickupAddress
    fun station(item:ExpressItem)=if(legacyJdLocation(item))"" else item.pickupStation
    fun copyText(item:ExpressItem,stationFallback:String=""):String = listOf(
        station(item).ifBlank {stationFallback}.takeIf {it.isNotBlank()}?.let {"取件地点：$it"},
        address(item).takeIf {it.isNotBlank()}?.let {"地址：$it"},
        item.pickupCode.takeIf {it.isNotBlank()}?.let {"取件码：$it"},
        item.pickupHint.takeIf {item.pickupCode.isBlank() && it.isNotBlank()}?.let {"取件提示：$it"},
        item.mailNo.takeUnless {item.isOrderNumber || it.startsWith("LOCAL-") || it.startsWith("ORDER-")}?.let {"运单号：$it"}
    ).filterNotNull().joinToString("\n")
}
