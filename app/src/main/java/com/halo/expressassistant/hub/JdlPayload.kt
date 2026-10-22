package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.JdGoods
import kotlinx.serialization.json.*

/** Receiver receiveCode only: sender pickupCode/signVerifyCode are different codes. */
object JdlPayload {
    private fun JsonObject.obj(key:String)=this[key] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.str(key:String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun address(value:JsonElement?):String = when(value) {
        is JsonPrimitive->value.contentOrNull.orEmpty()
        is JsonObject->value.str("fullAddress").ifBlank {value.str("address").ifBlank {
            listOf("provinceName","cityName","countyName","townName","detailAddress").map {value.str(it)}.filter {it.isNotBlank()}.joinToString("")
        }}
        else->""
    }
    fun parse(order:JsonObject,accountId:String,label:String,tail:String="",trace:JsonArray=JsonArray(emptyList())):ExpressItem {
        val base=order.obj("baseOrderInfo");val status=base.obj("cusBaseInfo")
        val shipment=order.obj("shipmentInfo");val station=shipment.obj("endStation")
        val recipient=order.obj("consigneeInfo")
        val number=base.str("customOrderNo")
        require(number.matches(Regex("[A-Za-z0-9]{8,32}"))){"京东物流未返回有效运单"}
        val code=status.str("orderStatus").toIntOrNull()
        val name=status.str("orderStatusName").ifBlank {when(code){1->"待揽收";2->"运输中";3->"派送中";4->"已送达";5->"已拒收";6->"已取消";7->"终止配送";8->"已揽收";9->"待自提";else->"状态待核实"}}
        val stateNum=when(code){1->101;2,8->104;3->105;4->107;5,6,7->108;9->106;else->0}
        val received=ParcelTime.parse(base.str("receivedTime"))
        val points=trace.flatMap {group->((group as? JsonObject)?.get("waybillTraceList") as? JsonArray).orEmpty()}
            .mapNotNull {it as? JsonObject}.filter {it.str("hideStatus")!="1"}
            .filter {ParcelTime.parse(it.str("operateTime"))>0}
        val latest=points.maxByOrNull {ParcelTime.parse(it.str("operateTime"))}
        // deliveryEndTime is a promise/countdown, not proof of actual delivery.
        val updated=latest?.let {ParcelTime.parse(it.str("operateTime"))} ?: ParcelTime.parse(base.str("lastOperatorTime"))
        val signed=points.filter {p->listOf("已送达","已签收","签收成功").any(p.str("descr")::contains) && !listOf("待自提","待取件","请上门提货","驿站签收").any(p.str("descr")::contains)}
            .maxOfOrNull {ParcelTime.parse(it.str("operateTime"))} ?: 0L
        fun waiting(p:JsonObject)=listOf("待自提","待取件","请上门提货","已到站","到达自提点","凭取件码","及时取件").any(p.str("descr")::contains)
        val movingAt=points.filter {p->!waiting(p) && listOf("运输中","派送中","已揽收","到达分拣","发往","转运").any(p.str("descr")::contains)}.maxOfOrNull {ParcelTime.parse(it.str("operateTime"))} ?: 0
        val arrival=points.filter {p->waiting(p) && ParcelTime.parse(p.str("operateTime"))>=movingAt}
            .minOfOrNull {ParcelTime.parse(it.str("operateTime"))} ?: 0L
        // The arrival trace names the place the recipient should actually visit.
        // endStation can refer to a different delivery depot/order address.
        val arrivalPoints=points.filter {waiting(it) && ParcelTime.parse(it.str("operateTime"))>=movingAt}.sortedByDescending {ParcelTime.parse(it.str("operateTime"))}
        val actualStation=arrivalPoints.asSequence().mapNotNull {point->
            Regex("""(?:已到达|到达|存入|存放至|存放在|已放至|已放在)\s*[【\[]([^】\]]{2,100})[】\]]""").find(point.str("descr"))?.groupValues?.get(1)?.trim()
        }.firstOrNull().orEmpty()
        val endName=station.str("stationName")
        val locationName=actualStation.ifBlank {endName}
        val locationAddress=if(actualStation.isNotBlank() && actualStation==endName)address(station["addressInfo"]) else ""
        val recipientTail=recipient.str("consigneeMobile").let {phone->if(phone.matches(Regex("1[0-9]{10}")))phone.takeLast(4) else Regex("(?:^|[*＊])([0-9]{4})$").find(phone)?.groupValues?.get(1)}
            ?: tail.takeIf {it.matches(Regex("[0-9]{4}"))}.orEmpty()
        return ExpressItem(id=number,companyCode="jd",companyName="京东快递",mailNo=number,source="jd",
            accountId=accountId,accountLabel=label,queryChannel=JdLogisticsSource.detailUrl(number),identityChannel="jd",
            state=when(code){4->3;5,6,7->4;3,9->5;else->0},stateNum=stateNum,stateName=name,orderStatus=name,
            latestText=listOf(name,latest?.str("descr").orEmpty()).filter {it.isNotBlank()}.joinToString(" · "),
            latestTime=updated.takeIf {it>0}?.toString().orEmpty(),orderCreatedAt=received,statusUpdatedAt=updated,
            completedAt=if(code==4)signed else 0,arrivedAt=if(code==9)arrival else 0,
            pickupCode=shipment.str("receiveCode"),pickupStation=locationName,
            pickupAddress=locationAddress,deliveryAddress=address(recipient["addressInfo"]),recipientPhoneTail=recipientTail,pickupLocationVersion=1,logisticsVersion=21)
    }
    fun goods(order:JsonObject,summary:JsonObject?):JdGoods? {
        val detail=goods(order);val listed=summary?.let {goods(it)}
        return if(detail==null)listed else GoodsCache.merge(listed,detail)
    }
    fun goods(order:JsonObject):JdGoods? {
        val base=order.obj("baseOrderInfo")
        val official=(order["goodsInfos"] as? JsonArray)?.mapNotNull {it as? JsonObject}.orEmpty()
        val cargo=(order["cargoInfos"] as? JsonArray)?.mapNotNull {it as? JsonObject}.orEmpty()
        val name=official.map {it.str("goodsName")}.filter {it.isNotBlank()}.distinct().joinToString("、").ifBlank {
            base.str("tradeName").ifBlank {cargo.map {it.str("cargoName").ifBlank {it.str("goodsName")}}.filter {it.isNotBlank()}.distinct().joinToString("、")}
        }
        val images=official.map {it.obj("extendProps").str("imgPath")}+base.str("tradeImageUrl")
        val image=images.firstNotNullOfOrNull {raw->
            val normalized=when {raw.startsWith("//")->"https:$raw";raw.startsWith("http://")->"https:"+raw.removePrefix("http:");else->raw}
            normalized.takeIf {it.startsWith("https://") && it.length>8}
        }.orEmpty()
        return if(name.isNotBlank() || image.isNotBlank())JdGoods(name=name,imageUrl=image,count="") else null
    }
}
