package com.halo.expressassistant.hub

import com.halo.expressassistant.data.*
import org.junit.Assert.*
import org.junit.Test

class PddPayloadTest {
    @Test fun bothObservedListStructuresUseExplicitPhysicalNodesForTransitAndPickup() {
        for((status,stage) in listOf("运输中" to ParcelPresentation.Stage.TRANSIT,"待取件" to ParcelPresentation.Stage.PICKUP)) {
            val camel="""{"orders":[{"orderSn":"owned","orderTime":1792825139,"orderStatusPrompt":"待收货","express":{"statusPrompt":"$status","timestamp":1792825139,"lastTrace":"当前物流说明"}}]}"""
            val snake="""{"orders":[{"order_sn":"owned","order_time":1792825139,"order_status_prompt":"待收货","express":{"status_prompt":"$status","timestamp":1792825139,"last_trace":"当前物流说明"}}]}"""
            for(raw in listOf(camel,snake)) {
                val item=PddOrderPayload.read(listOf("/api/order_list_v4" to raw),"owner").first.single()
                assertEquals(stage,ParcelPresentation.stage(item))
                assertTrue(item.orderCreatedAt>0)
                assertTrue(PddPagingPolicy.refreshInScope(item,0,false))
            }
        }
        val unknown=PddOrderPayload.read(listOf("/api/order_list_v4" to """{"orders":[{"order_sn":"unknown","order_status":3,"order_status_prompt":"待收货"}]}""")).first.single()
        assertEquals(ParcelPresentation.Stage.UNVERIFIED,ParcelPresentation.stage(unknown))
        assertEquals(0L,unknown.completedAt)
    }
    @Test fun headerPickupCodeIsDifferentFromWaybillTailOrMaskedPhoneHint() {
        fun snapshot(header:String)=PddDetailPayload.read(kotlinx.serialization.json.buildJsonObject {
            put("text",kotlinx.serialization.json.JsonPrimitive("$header\n韵达快递 : 465740832195331\n待取件 2026-10-07 11:21:18\n请领取"))
        }.toString())
        assertEquals("3-2-1868",snapshot("取件码\n3-2-1868").pickupCode)
        val tail=snapshot("取件出示快递单号后五位\n95331")
        assertEquals("取件出示快递单号后五位 95331",tail.hint)
        assertEquals("",tail.pickupCode)
        assertEquals("",snapshot("取件出示手机号\n180****7747").pickupCode)
        assertEquals("",snapshot("取件码：465740832195331").pickupCode)
    }
    @Test fun snakeCaseAndSsrOrdersKeepCommerceConfirmationSeparate() {
        val raw="""{"orders":[{"order_sn":"basket","tracking_number":"YT8899935545895","order_time":1788602492,"receive_time":1789731405,"order_status_prompt":"已评价","order_goods":[{"goods_name":"黑色挂篮","thumb_url":"https://example.test/image.jpg","goods_number":1}]}],"recommend":{"order_sn":"wrong"}}"""
        val (items,goods)=PddOrderPayload.read(listOf("/proxy/api/order_list_v4" to raw),"account","账号")
        val item=items.single()
        assertEquals("basket",item.id)
        assertEquals("YT8899935545895",item.mailNo)
        assertEquals("account",item.accountId)
        assertEquals(0L,item.completedAt)
        assertEquals(1789731405000L,item.orderCompletedAt)
        assertEquals("x1",goods[item.mailNo]?.count)
        assertFalse(PddPagingPolicy.historyBoundary(items,ParcelTime.parse("2026-09-13")))
        assertTrue(PddPagingPolicy.refreshInScope(item,ParcelTime.parse("2026-09-13"),false))
        val ssr="""{"orders":[{"orderSn":"milk","trackingNumber":"465740832195331","orderTime":1791100972,"orderStatusPrompt":"已签收，待确认收货","orderGoods":[{"goodsName":"牛奶","thumbUrl":"https://example.test/milk.jpg","goodsNumber":1}]}]}"""
        val milk=PddOrderPayload.read(listOf("ordersStore/order_list_v4" to ssr)).first.single()
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(milk))
        assertEquals(0L,milk.completedAt)
        assertTrue(PddPagingPolicy.refreshInScope(milk,ParcelTime.parse("2026-09-13"),false))
    }
    @Test fun officialLogisticsHeadingsAndTimestampsBeatOlderPickupText() {
        val text="""取件出示快递单号后五位 81717
菜鸟驿站
城市科技学院巴南校区菜鸟驿站
极兔速递 : JT5528929881717
订单编号: 260923-390993026560225
收货地址: 测试收货地址
已签收 2026-10-07 19:30:42
包裹已签收！（凭取件码）
待取件 2026-09-25 17:05:23
快件送达驿站暂存，请及时取件。
展开
推荐商品 未发货秒退"""
        val payload=kotlinx.serialization.json.buildJsonObject {put("text",kotlinx.serialization.json.JsonPrimitive(text))}.toString()
        val snapshot=PddDetailPayload.read(payload)
        assertEquals("极兔速递",snapshot.company)
        assertEquals("JT5528929881717",snapshot.waybill)
        assertEquals("城市科技学院巴南校区菜鸟驿站",snapshot.station)
        assertEquals(2,snapshot.points.size)
        val item=ExpressItem("hanger","","",snapshot.waybill,source="pdd",orderStatus="待评价",state=3)
        val resolved=PddLogistics.apply(item,snapshot.points)
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(resolved))
        assertEquals(ParcelTime.parse("2026-10-07 19:30:42"),resolved.completedAt)
        assertEquals(ParcelTime.parse("2026-09-25 17:05:23"),resolved.arrivedAt)
        assertTrue(resolved.pickupCode.isBlank())
    }
    @Test fun headingsOnSeparateDomLinesAlsoProduceSignedHistory() {
        val raw="""{"text":"邮政快递包裹 : 9819275075382\n已签收\n2026-09-10 13:23:21\n您的快件已取走\n待取件\n2026-09-10 10:20:27\n凭取件码领取\n展开"}"""
        val snapshot=PddDetailPayload.read(raw)
        assertEquals(2,snapshot.points.size)
        val resolved=PddLogistics.apply(ExpressItem("socks","","",snapshot.waybill,source="pdd"),snapshot.points)
        assertEquals(1789017801000L,resolved.completedAt)
        assertFalse(SyncWindow(30,ParcelTime.parse("2026-10-12")).include(resolved))
    }
    @Test fun onlyGroundedOfficialLogisticsLinksAreFollowed() {
        val snapshot=PddDetailPayload.read("""{"data":{"mobile":"180****7747","shippingAddress":"测试地址","orderButtons":[{"briefPrompt":"查看物流","typeValue":{"url":"goods_express.html?tracking_number=9819275075382&order_sn=socks"}}]}}""")
        assertEquals("9819275075382",snapshot.waybill)
        assertEquals("7747",snapshot.phoneTail)
        assertTrue(snapshot.nextUrl.startsWith("https://mobile.yangkeduo.com/goods_express.html?"))
        assertEquals("",PddDetailPayload.read("""{"data":{"orderButtons":[{"briefPrompt":"查看物流","typeValue":{"url":"https://example.test/goods_express.html"}}]}}""").nextUrl)
    }
    @Test fun genericCommerceRefreshCannotRegressVerifiedPhysicalState() {
        val old=PddLogistics.apply(ExpressItem("milk","","","465740832195331",source="pdd"),listOf(DetailPoint("已签收 凭取件码领取","2026-10-07 19:30:40")))
        val fresh=ExpressItem("milk","","","465740832195331",source="pdd",orderStatus="待收货",latestText="待收货",state=0)
        val merged=SyncCacheMerge.merge(old,fresh)
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(merged))
        assertEquals(old.completedAt,merged.completedAt)
        val stale=listOf(DetailPoint("待取件 请领取","2026-10-07 11:21:18"))
        assertEquals(old.completedAt,PddLogistics.apply(old,stale).completedAt)
    }
    @Test fun activePickupCannotBeDroppedBecauseItWasOrderedLongAgo() {
        val item=ExpressItem("active","","","TEST123456",source="pdd",logisticsStatus="待取件",stateName="待取件",orderCreatedAt=ParcelTime.parse("2025-01-01"))
        assertTrue(PddPagingPolicy.refreshInScope(item,ParcelTime.parse("2026-09-13"),false))
        assertFalse(PddPagingPolicy.historyBoundary(listOf(item),ParcelTime.parse("2026-09-13")))
        val unverified=item.copy(logisticsStatus="",stateName="待收货",orderStatus="待收货",latestText="待收货",isOrderNumber=true)
        assertTrue(PddPagingPolicy.refreshInScope(unverified,ParcelTime.parse("2026-09-13"),false,freshAwaitingReceipt=true))
        assertTrue(PddPagingPolicy.refreshInScope(unverified,ParcelTime.parse("2026-09-13"),false))
    }
    @Test fun refundedPhysicalParcelsAndVirtualMonthlyCardsAreNotNormalSignedHistory() {
        val physical=ExpressItem("refund","","","TEST123456",source="pdd",logisticsStatus="已签收",orderStatus="退款成功",state=3)
        assertEquals(ParcelPresentation.Stage.AFTERSALE,ParcelPresentation.stage(physical))
        val virtual=PddOrderPayload.read(listOf("/proxy/api/order_list_v4" to """{"orders":[{"order_sn":"card","order_status_prompt":"交易成功","order_goods":[{"goods_name":"拼多多三个月省钱月卡"}]}]}""")).first.single()
        assertTrue(ParcelEligibility.isVirtual(virtual))
        assertFalse(PddPagingPolicy.refreshInScope(virtual,0,false))
    }
}
