package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class PddLogisticsRegressionTest {
    @Test fun latestOfficialSignedStatusWinsOverResidualPickupInstructions() {
        val milk=ExpressItem("milk","","韵达","465740832195331",source="pdd",state=3,
            stateName="已签收",orderStatus="已签收，待确认收货",
            latestText="【代收点】您的快件已投递，收件人凭取件码在【菜鸟驿站(已签收签收人凭取货码签收)】领取",
            latestTime="2026-10-07 19:30:40",completedAt=1791372640000L)
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(milk))
        assertEquals(1791372640000L,ParcelPresentation.eventTime(milk))
        assertTrue(ParcelPresentation.historySubtitle(milk,null).contains("已签收 · 待确认收货"))
    }
    @Test fun officialPendingPickupStillWinsOverStationSignedBody() {
        val item=ExpressItem("pending","","极兔","JT-test",source="pdd",state=3,
            stateName="待取件",orderStatus="待收货",latestText="代收点已签收，请凭取件码领取")
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(item))
    }
    @Test fun missingPddSignatureTimeCannotBeReplacedWithOrderCreation() {
        val item=ExpressItem("signed","","邮政","test",source="pdd",state=3,stateName="已签收",
            orderStatus="已评价",orderCreatedAt=1788772451000L)
        assertEquals(0L,ParcelPresentation.eventTime(item))
    }
}
