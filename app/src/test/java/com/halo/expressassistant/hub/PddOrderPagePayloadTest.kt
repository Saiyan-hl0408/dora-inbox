package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class PddOrderPagePayloadTest {
    @Test fun summaryUpdateKeepsOlderTimelineAndReplacesTheSameTimestamp() {
        val cached=listOf(com.halo.expressassistant.data.DetailPoint("运输中 离开转运中心","2026-10-23 10:00:00"),
            com.halo.expressassistant.data.DetailPoint("待取件 到达代收点","2026-10-24 10:00:00"))
        val at=ParcelTime.parse("2026-10-24 10:00:00")
        val fresh=listOf(com.halo.expressassistant.data.DetailPoint("已取件 当前明确结果",at.toString()))
        val result=PddOrderPagePayload.withHistory(cached,fresh)
        assertEquals(2,result.size)
        assertEquals(fresh.single(),result.first())
        assertTrue(result.contains(cached.first()))
    }
    @Test fun realTopBannerStructureFinishesUnshippedWithoutLaunchingAWebView() {
        val html="""<script>window.rawData={"data":{"orderSn":"owned","errorRedirectUrl":"orders.html","topBanner":{"orderStatusPrompt":"待发货"},"orderGoods":{"goodsName":"a } quoted \" name"}}};</script>"""
        assertEquals("待发货",PddOrderPagePayload.read(html,"owned")?.orderStatus)
        assertNull(PddOrderPagePayload.read(html,"another-order"))
    }
    @Test fun shippedOrderRequiresADatedPhysicalNodeOtherwiseBrowserFallback() {
        assertNull(PddOrderPagePayload.read("""window.rawData={"data":{"orderSn":"owned","topBanner":{"orderStatusPrompt":"待收货"}}};""","owned"))
        val result=PddOrderPagePayload.read("""window.rawData={"data":{"orderSn":"owned","express":{"statusPrompt":"运输中","timestamp":"2026-10-24 10:00:00","lastTrace":"您的快件已离开转运中心"}}};""","owned")
        assertEquals(1,result?.points?.size)
        assertTrue(result!!.points.single().context.contains("离开转运中心"))
    }
    @Test fun loginRedirectEmptyOrMalformedPayloadFallsBackWithoutInventingProgress() {
        assertNull(PddOrderPagePayload.read("window.rawData={\"data\":null};","owned"))
        assertNull(PddOrderPagePayload.read("window.rawData={\"data\":{","owned"))
        assertNull(PddOrderPagePayload.read("""window.rawData={"data":{"errorRedirectUrl":"/login.html","statusPrompt":"待发货"}};""","owned"))
    }
    @Test fun actualCollectedNodeSentenceIsAcceptedWithoutWaitingForAnotherWholePage() {
        for(text in listOf("您的快件已取走【代收点】","您的快件已领取，收件人在代收点取件","已签收，签收人凭取货码签收")) {
            val result=PddOrderPagePayload.read("""window.rawData={"data":{"orderSn":"owned","express":{"statusPrompt":"$text","lastTrace":"$text","timestamp":1792825139000}}};""","owned")
            assertNotNull(text,result)
            assertEquals(ParcelPresentation.Stage.CLOSED,PddLogistics.stage(PddLogistics.status(result!!.points.single().context)))
        }
        assertNull(PddOrderPagePayload.read("""window.rawData={"data":{"orderSn":"owned","express":{"statusPrompt":"您的快件离开转运中心，请在到站后领取","timestamp":1792825139000}}};""","owned"))
    }
}
