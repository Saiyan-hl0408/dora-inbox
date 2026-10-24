package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class PddForwardingWarehouseTest {
    private val forwarding="包裹已入库[圆通快递]，将尽快发出，请耐心等待"
    private fun item()=ExpressItem("order","","圆通快递","sample",source="pdd",orderStatus="待收货")
    private fun point(text:String,time:String="2026-10-24 15:14:41")=DetailPoint(text,time)
    @Test fun inboundWarehouseStillShippingIsTransit() {
        assertEquals("运输中",PddLogistics.status(forwarding))
    }
    @Test fun loadingAtTransferWarehouseIsTransit() {
        assertEquals("运输中",PddLogistics.status("已送至中转仓，正在装车发往西藏，请耐心等待"))
    }
    @Test fun transferWarehouseRelabelIsTransit() {
        assertEquals("运输中",PddLogistics.status("您的包裹已送达[拼多多中转仓]，将由中转仓换单发往目的地"))
    }
    @Test fun newerForwardingNodeCorrectsStalePickup() {
        val stale=item().copy(logisticsStatus="待取件",logisticsVersion=1,stateNum=106,statusUpdatedAt=ParcelTime.parse("2026-10-24 13:34:03"))
        val result=PddLogistics.apply(stale,listOf(point(forwarding)),"待取件")
        assertEquals("运输中",result.logisticsStatus)
        assertEquals(ParcelPresentation.Stage.TRANSIT,ParcelPresentation.stage(result))
    }
    @Test fun cachedLatestForwardingTextDoesNotRenderAsPickup() {
        val cached=item().copy(logisticsStatus="待取件",latestText=forwarding,latestTime="2026-10-24 15:14:41")
        assertEquals(ParcelPresentation.Stage.TRANSIT,ParcelPresentation.stage(cached))
    }
    @Test fun actualRecipientStationRemainsPickup() {
        val result=PddLogistics.apply(item(),listOf(point("待取件 已入库学校菜鸟驿站，请凭取件码领取")))
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(result))
    }
    @Test fun olderWarehouseHistoryDoesNotOverrideCurrentPickup() {
        val result=PddLogistics.apply(item(),listOf(point("待取件 已入库学校驿站，等待取件"),point(forwarding,"2026-10-23 10:00:00")))
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(result))
    }
    @Test fun olderWarehouseHistoryDoesNotOverrideCollected() {
        val result=PddLogistics.apply(item(),listOf(point("已取件 本人签收"),point(forwarding,"2026-10-23 10:00:00")))
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(result))
    }
    @Test fun olderForwardingResponseCannotRollBackNewerPickup() {
        val current=item().copy(logisticsStatus="待取件",logisticsVersion=1,statusUpdatedAt=ParcelTime.parse("2026-10-24 16:00:00"))
        assertEquals(current,PddLogistics.apply(current,listOf(point(forwarding))))
    }
    @Test fun warehouseWordAloneIsNotEvidenceOfContinuedShipping() {
        assertFalse(PddLogistics.forwarding("快件已入库"))
        assertFalse(PddLogistics.forwarding("已入库，等待取件"))
    }
    @Test fun conflictingPickupHeadingCannotHideExplicitOnwardShipping() {
        val text="待取件 $forwarding"
        assertEquals("运输中",PddLogistics.status(text))
        assertEquals(ParcelPresentation.Stage.TRANSIT,ParcelPresentation.stage(PddLogistics.apply(item(),listOf(point(text)),"待取件")))
    }
}
