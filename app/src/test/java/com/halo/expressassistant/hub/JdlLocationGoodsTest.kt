package com.halo.expressassistant.hub

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class JdlLocationGoodsTest {
    private fun order(code:Int=9)=Json.parseToJsonElement("""{
      "baseOrderInfo":{"customOrderNo":"JDXFIXTURE001","cusBaseInfo":{"orderStatus":$code}},
      "shipmentInfo":{"endStation":{"stationName":"订单地址附近配送站","addressInfo":{"address":"下单时的家庭地址"}}},
      "consigneeInfo":{"addressInfo":{"address":"另一个下单地址"}},
      "goodsInfos":[{"goodsName":"测试耳机","extendProps":{"imgPath":"//img.example.test/item.jpg"}}],
      "cargoInfos":[{"cargoName":"普通包裹"}]
    }""").jsonObject
    private fun trace()=Json.parseToJsonElement("""[{"waybillTraceList":[
      {"descr":"您的快件已到达【测试校园京东服务站】，请凭取件码及时取件。","operateTime":"2026-10-08 09:56:00"}
    ]}]""").jsonArray
    @Test fun actualPickupStationOverridesOrderAddressAndUnrelatedEndStation() {
        val item=JdlPayload.parse(order(),"root","test",trace=trace())
        assertEquals("测试校园京东服务站",item.pickupStation)
        assertEquals("",item.pickupAddress)
        val copy=PickupCardInfo.copyText(item)
        assertTrue(copy.contains("测试校园京东服务站"));assertFalse(copy.contains("下单"))
    }
    @Test fun deliveredHistoryNeverUsesOrderingAddressAsDeliveryLocation() {
        val input=JsonObject(order(4).filterKeys {it!="shipmentInfo"})
        assertEquals("",JdlPayload.parse(input,"root","test").pickupAddress)
    }
    @Test fun officialGoodsImageUsesGoodsInfosExtendPropsImgPath() {
        val goods=JdlPayload.goods(order())!!
        assertEquals("https://img.example.test/item.jpg",goods.imageUrl)
        assertEquals("测试耳机",goods.name)
    }
    @Test fun hiddenPickupTraceCannotOverrideVisibleStation() {
        val visible=trace()[0].jsonObject["waybillTraceList"]!!.jsonArray
        val hidden=Json.parseToJsonElement("""{"descr":"待自提，已到达【隐藏站点】","operateTime":"2026-10-09 09:56:00","hideStatus":1}""")
        val traces=buildJsonArray {add(buildJsonObject {put("waybillTraceList",JsonArray(visible+hidden))})}
        assertEquals("测试校园京东服务站",JdlPayload.parse(order(),"root","test",trace=traces).pickupStation)
    }
    @Test fun correctedEmptyAddressMustClearLegacyOrderingAddressInCache() {
        val fresh=JdlPayload.parse(order(),"root","test",trace=trace())
        val old=fresh.copy(pickupAddress="旧订单家庭地址",pickupLocationVersion=0)
        assertEquals("",SyncCacheMerge.merge(old,fresh).pickupAddress)
        assertFalse(PickupCardInfo.copyText(old).contains("家庭地址"))
    }
    @Test fun unconfirmedDepotAddressIsNotCopiedWhenArrivalTraceIsUnavailable() {
        val item=JdlPayload.parse(order(),"root","test")
        assertEquals("",item.pickupAddress)
        assertFalse(PickupCardInfo.copyText(item).contains("家庭地址"))
    }
}
