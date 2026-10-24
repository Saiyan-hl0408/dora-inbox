package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressItem
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class PddFullLogisticsReaderTest {
    private val item=ExpressItem("TEST-ORDER","PDD","申通快递","TEST00000001",source="pdd")
    private val point=DetailPoint("已签收 签收人凭取货码签收","2026-10-24 14:59:18")
    @Test fun oneFullPageProvidesBothStatusAndLocation()=runBlocking {
        var calls=0
        val result=PddFullLogisticsReader.read(item,emptyList()) {
            calls++;PddDetailPayload.Snapshot(listOf(point),item.mailNo,"申通快递","学校校区菜鸟驿站")
        }!!
        assertEquals(1,calls);assertEquals(listOf(point),result.points);assertEquals("学校校区菜鸟驿站",result.station)
    }
    @Test fun cachedLocationSurvivesTerseFullPageWithoutAnotherLookup()=runBlocking {
        val result=PddFullLogisticsReader.read(item.copy(pickupStation="学校校区菜鸟驿站"),emptyList()) {PddDetailPayload.Snapshot(listOf(point))}!!
        assertEquals("学校校区菜鸟驿站",result.station)
    }
    @Test fun failedPageCannotTurnOldCachedTraceIntoSuccessfulNewRead()=runBlocking {
        assertTrue(PddFullLogisticsReader.read(item,listOf(point)){PddDetailPayload.Snapshot()}!!.points.isEmpty())
        assertNull(PddFullLogisticsReader.read(item,listOf(point)){null})
    }
    @Test fun officialLinkCannotCrossOrdersOrLeaveOfficialLogisticsPage() {
        val link="/goods_express.html?order_sn=TEST-ORDER&tracking_number=TEST00000001"
        assertEquals("https://mobile.yangkeduo.com$link",PddFullLogisticsReader.officialLink(link,item.id))
        for(raw in listOf(link.replace("TEST-ORDER","OTHER"),"https://evil.invalid$link","https://mobile.yangkeduo.com/order.html","https://x@mobile.yangkeduo.com$link"))assertEquals("",PddFullLogisticsReader.officialLink(raw,item.id))
    }
    @Test fun cancellationStillStopsTheFullPageRead()=runBlocking {
        try {PddFullLogisticsReader.read(item,emptyList()){throw CancellationException("stop")};fail("Cancellation must propagate")}
        catch(_:CancellationException){}
    }
    @Test fun previousParcelsPageCannotOverwriteTheNextOrdersWaybillOrHistory()=runBlocking {
        val next=item.copy(id="NEXT-ORDER",mailNo="TEST00000002")
        val stale=PddDetailPayload.Snapshot(listOf(point),item.mailNo,"申通快递","旧订单的驿站")
        assertNull("A previous order page must be rejected, not assigned to the next order",
            PddFullLogisticsReader.read(next,emptyList()){stale})
    }
    @Test fun verifiedCurrentOrderCanChangeToItsOfficialPostalWaybill()=runBlocking {
        val current=item.copy(queryChannel=item.id)
        val result=PddFullLogisticsReader.read(current,listOf(point)) {
            PddDetailPayload.Snapshot(listOf(point.copy(context="运输中 末端邮政已揽收")),"EMS000000001","邮政EMS",orderId=item.id)
        }!!
        assertEquals("EMS000000001",result.waybill)
        assertEquals(1,result.points.size)
        assertEquals("运输中 末端邮政已揽收",result.points.single().context)
    }
    @Test fun matchingWaybillStillCannotBlessTheWrongOrder()=runBlocking {
        assertNull(PddFullLogisticsReader.read(item,emptyList()) {
            PddDetailPayload.Snapshot(listOf(point),item.mailNo,orderId="OTHER-ORDER")
        })
    }
}
