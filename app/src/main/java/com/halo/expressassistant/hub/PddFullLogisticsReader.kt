package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressItem
import java.net.URI
import java.net.URLDecoder

/** Full logistics experiment: one page read supplies both status and pickup location. */
object PddFullLogisticsReader {
    fun officialLink(raw:String,orderId:String):String=runCatching {
        val uri=URI("https://mobile.yangkeduo.com/").resolve(raw)
        if(uri.scheme!="https" || uri.host!="mobile.yangkeduo.com" || uri.path!="/goods_express.html" || uri.userInfo!=null || uri.port !in setOf(-1,443))return@runCatching ""
        val params=uri.rawQuery.orEmpty().split('&').map {it.split('=',limit=2)}
        val order=params.firstOrNull {it[0]=="order_sn"}?.getOrNull(1)?.let {URLDecoder.decode(it,"UTF-8")}
        if(order!=null && order!=orderId)return@runCatching ""
        uri.toString()
    }.getOrDefault("")

    suspend fun read(item:ExpressItem,cached:List<DetailPoint>,full:suspend ()->PddDetailPayload.Snapshot?):PddDetailPayload.Snapshot? {
        val result=full() ?: return null
        val expectedOrder=item.queryChannel.ifBlank {item.id}
        if(result.orderId.isNotBlank() && result.orderId!=expectedOrder)return null
        if(!item.isOrderNumber && result.waybill.isNotBlank() && !result.waybill.equals(item.mailNo,true) && result.orderId!=expectedOrder)return null
        if(result.points.isEmpty())return result
        val sameParcel=result.waybill.isBlank() || result.waybill.equals(item.mailNo,true)
        val points=PddOrderPagePayload.withHistory(if(sameParcel)cached else emptyList(),result.points)
        return result.copy(points=points,station=result.station.ifBlank {PddPickupLocation.history(points)}
            .ifBlank {PddPickupLocation.sanitized(item.pickupStation).ifBlank {PddPickupLocation.sanitized(item.pddPickupStation)}})
    }
}
