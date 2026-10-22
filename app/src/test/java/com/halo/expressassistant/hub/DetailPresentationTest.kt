package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.DetailPoint
import org.junit.Assert.*
import org.junit.Test

class DetailPresentationTest {
    private fun parcel()=ExpressItem("id","","极兔","JT123456789",source="pdd",orderStatus="待收货",logisticsStatus="待取件",pickupCode="A-1234",latestText="已到站，等待取件")
    @Test fun signedOrLocalCollectionNeverShowsOldCodeAsCurrentlyUsable() {
        val pending=parcel()
        assertEquals("A-1234",DetailPresentation.summary(pending).currentCode)
        val signed=DetailPresentation.summary(pending.copy(logisticsStatus="已签收"))
        assertEquals("",signed.currentCode);assertEquals("A-1234",signed.historicalCode)
        val local=DetailPresentation.summary(pending,collectedAt=20L)
        assertEquals("",local.currentCode);assertEquals("本机标记已取",local.status)
    }
    @Test fun orderAndLocalIdsAreNeverCopiedAsLogisticsWaybills() {
        assertNull(DetailPresentation.summary(parcel().copy(isOrderNumber=true)).waybill)
        assertNull(DetailPresentation.summary(parcel().copy(mailNo="LOCAL-12345678")).waybill)
        assertEquals("JT123456789",DetailPresentation.summary(parcel()).waybill)
    }
    @Test fun orderOrObservationTimeDoesNotInventAnArrivalEvent() {
        val p=DetailPresentation.summary(parcel().copy(orderCreatedAt=10000,observedAt=20000,latestTime="",recipientPhoneTail="1234"))
        assertEquals("",p.eventTime);assertEquals("1234",p.phoneTail);assertEquals("",p.station)
    }
    @Test fun commerceCompletionIsNotPresentedAsOfficialLogisticsSignature() {
        val commerce=parcel().copy(logisticsStatus="",orderStatus="交易成功",pickupCode="")
        assertEquals("订单已完成 · 物流签收待核实",DetailPresentation.summary(commerce).status)
    }
    @Test fun officialBoundaryRejectsLookalikeAndUnsafeOrigins() {
        assertTrue(DetailPresentation.allowedOfficialUrl("jd","https://kd.jd.com/express/orderDetail"))
        assertFalse(DetailPresentation.allowedOfficialUrl("jd","https://jd.com.attacker.test/"))
        assertFalse(DetailPresentation.allowedOfficialUrl("taobao","http://m.taobao.com/"))
        assertFalse(DetailPresentation.allowedOfficialUrl("pdd","file:///data/local.html"))
        assertTrue(DetailPresentation.allowedOfficialUrl("pdd","https://m.pinduoduo.net/mdkd/package"))
        assertFalse(DetailPresentation.allowedOfficialUrl("pdd","https://m.pinduoduo.net.attacker.test/mdkd/package"))
    }
    @Test fun officialOrderRoutesReuseFetcherEndpointsAndNeverGuessAnOrderFromAWaybill() {
        val pdd=parcel().copy(queryChannel="260920-123456789012345")
        assertEquals("https://mobile.yangkeduo.com/order.html?order_sn=260920-123456789012345",DetailPresentation.officialUrl(pdd))
        assertEquals("https://mobile.yangkeduo.com/orders.html?type=0",DetailPresentation.officialUrl(parcel()))
        val tb=parcel().copy(source="taobao",queryChannel="3316401266021000074")
        assertEquals("https://pages-g.m.taobao.com/wow/z/app/mtb/logisticsV2/h5-detail?x-ssr=true&bizOrderId=3316401266021000074",DetailPresentation.officialUrl(tb))
    }
    @Test fun failedOrEmptyRefreshKeepsUsableCachedNodes() {
        val nodes=listOf(DetailPoint("已到站，等待取件","2026-10-20 10:00:00"))
        val loading=DetailLoadPolicy.start(DetailLoadPolicy.cached(nodes))
        assertTrue(loading.refreshing);assertEquals(nodes,loading.points)
        val failed=DetailLoadPolicy.failure(loading)
        assertTrue(failed.failed);assertFalse(failed.refreshing);assertEquals(nodes,failed.points)
        assertEquals(nodes,DetailLoadPolicy.success(loading,listOf(DetailPoint("",""))).points)
    }
    @Test fun partialFreshResponseDoesNotEraseOlderCachedEvents() {
        val old=DetailPoint("快件已揽收","2026-10-19 09:00:00")
        val latest=DetailPoint("已到站，等待取件","2026-10-20 10:00:00")
        val state=DetailLoadPolicy.success(DetailLoadPolicy.cached(listOf(old)),listOf(latest))
        assertEquals(listOf(latest,old),state.points)
    }
    @Test fun officialWaybillBeforeFirstTraceCanBeAdoptedWithoutDiscardingCachedSummary() {
        val order=parcel().copy(source="taobao",mailNo="3316401266021000074",isOrderNumber=true)
        val cached=DetailLoadPolicy.cached(listOf(DetailPoint("商家已发货","2026-10-19 09:00:00")))
        assertTrue(DetailLoadPolicy.mayApplyMetadata(order,"YT1234567890123",emptyList()))
        assertFalse(DetailLoadPolicy.mayApplyMetadata(order,order.mailNo,emptyList()))
        assertEquals(cached.points,DetailLoadPolicy.success(DetailLoadPolicy.start(cached),emptyList()).points)
    }
}
