package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class LatestLogisticsTest {
    @Test fun newestRecipientCollectionCannotBeOverwrittenByAnOlderPickupNode() {
        val collected=DetailPoint(context="包裹已取件",time="2026-10-08 10:00:00",formattedTime="")
        val pending=DetailPoint(context="包裹已到站，等待取件",time="2026-10-07 10:00:00",formattedTime="")
        for(points in listOf(listOf(collected,pending),listOf(pending,collected))) {
            val latest=LatestLogistics.point(points)!!
            val item=ExpressItem("synthetic","","测试快递","TEST123456",source="pdd",orderStatus="待收货",stateName="待收货",latestText=latest.context,latestTime=latest.time,pickupCode="2-3-4567")
            assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(item))
        }
    }
}
