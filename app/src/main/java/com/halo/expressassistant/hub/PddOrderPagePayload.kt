package com.halo.expressassistant.hub

import kotlinx.serialization.json.*

/** Only the current order's SSR object, never page recommendations or embedded script text. */
object PddOrderPagePayload {
    fun withHistory(cached:List<com.halo.expressassistant.data.DetailPoint>,summary:List<com.halo.expressassistant.data.DetailPoint>):List<com.halo.expressassistant.data.DetailPoint> {
        val byTime=linkedMapOf<String,com.halo.expressassistant.data.DetailPoint>()
        for(point in cached+summary) {
            val at=ParcelTime.parse(point.time)
            byTime[if(at>0)at.toString() else "${point.time}|${point.context}"]=point
        }
        return byTime.values.sortedByDescending {ParcelTime.parse(it.time)}
    }
    fun rawData(html:String):String? {
        val start=Regex("window\\.rawData\\s*=\\s*\\{").find(html)?.range?.last ?: return null
        var depth=0;var quoted=false;var escaped=false;var end=-1
        for(i in start until html.length) {
            val c=html[i]
            if(quoted) {if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}
            else when(c) {'"'->quoted=true;'{'->depth++;'}'->{depth--;if(depth==0){end=i+1;break}}}
        }
        if(end<0)return null
        return html.substring(start,end)
    }
    fun read(html:String,orderId:String):PddDetailPayload.Snapshot? {
        val raw=rawData(html) ?: return null
        val data=runCatching {Json.parseToJsonElement(raw).jsonObject["data"] as? JsonObject}.getOrNull() ?: return null
        fun JsonObject.value(key:String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if(Regex("login|passport",RegexOption.IGNORE_CASE).containsMatchIn(data.value("errorRedirectUrl")))return null
        val returnedOrder=data.value("orderSn").ifBlank {(data["queryParams"] as? JsonObject)?.value("order_sn").orEmpty()}
        if(returnedOrder!=orderId)return null
        val snapshot=PddDetailPayload.read(raw)
        // A recognized unshipped/cancelled order needs no logistics page. Shipped orders
        // only finish here with a real dated logistics node; otherwise use the official WebView.
        return snapshot.takeIf {PddDetailQueryPolicy.hasLogistics(it.points) ||
            it.orderStatus in setOf("待发货","待成团","待付款","已取消","退款成功","交易关闭","订单关闭")}
    }
}
