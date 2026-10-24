package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class October24FeedbackRegressionTest {
    private fun parcel() = ExpressItem("feedback", "PDD", "圆通快递", "YT0000000123456",
        source="pdd", accountId="pdd-owner", orderStatus="待收货")

    @Test fun choosingJdForPddParcelMustNotPassTheTaobaoLookupAccount() {
        val item=parcel().copy(pickupQueryAccountId="taobao-owner",pickupCodeSource="taobao-cainiao-school")
        assertEquals("",PickupUiPolicy.account(item,"jd"))
    }

    @Test fun explicitIdentityServiceSurvivesShoppingListRefresh() {
        val old=parcel().copy(identityChannel="pdd",pickupCode="706-3-5034",pickupCodeSource="pdd-official")
        val merged=SyncCacheMerge.merge(old,parcel())
        assertEquals("pdd",PickupUiPolicy.identity(merged))
    }

    @Test fun explicitTransitHeadingFromOrderListIsEnoughToDisplayInTransit() {
        val raw="""{"orders":[{"order_sn":"feedback","tracking_number":"YT0000000123456","order_time":"2026-10-22 10:06:10","order_status_prompt":"运输中"}]}"""
        val item=PddOrderPayload.read(listOf("/order_list_v4" to raw)).first.single()
        assertEquals("运输中",item.logisticsStatus)
        assertTrue(TransitGroup.includes(item))
    }

    @Test fun screenshotStyleDescriptionBeforeTimestampProducesTransitNodes() {
        val text="""运输中
圆通快递: YT0000000123456
订单编号: feedback
您的快件离开【广州转运中心】，已发往【成都转运中心】。预计【10月24日】到达【成都市】，预计将在【24日晚上】为您更新快件状态
2026-10-23 02:44:45
您的快件已经到达【广州转运中心】【物流问题请致电专属热线】
2026-10-23 02:43:19
商家已发货，正在通知圆通快递取件
2026-10-22 13:10:42
您的订单开始拣货
2026-10-22 11:30:53
收起"""
        val snapshot=PddDetailPayload.read(buildJsonObject {put("text",text)}.toString())
        assertTrue("Missing screenshot's latest transport event",snapshot.points.any {
            it.time=="2026-10-23 02:44:45" && it.context.contains("离开【广州转运中心】")
        })
        val resolved=PddLogistics.apply(parcel(),snapshot.points)
        assertEquals(ParcelPresentation.Stage.TRANSIT,ParcelPresentation.stage(resolved))
        assertEquals(ParcelTime.parse("2026-10-23 02:44:45"),resolved.statusUpdatedAt)
        assertFalse(resolved.latestText.contains("订单编号"))
        assertFalse(resolved.latestText.contains("YT0000000123456"))
    }

    @Test fun descriptionBeforeTimestampKeepsExplicitCurrentPickupHeading() {
        val text="待取件\n圆通快递: YT0000000123456\n您的快件已送达【代收点】，请凭取件码领取\n2026-10-24 10:00:00\n您的快件离开【转运中心】，已发往【派送网点】\n2026-10-23 10:00:00\n收起"
        val snapshot=PddDetailPayload.read(buildJsonObject {put("text",text)}.toString())
        assertEquals(2,snapshot.points.size)
        val resolved=PddLogistics.apply(parcel(),snapshot.points)
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(resolved))
    }

    @Test fun cachedTransitRemainsRefreshableAfterTheIncrementalCheckpointMovesPastIt() {
        val item=parcel().copy(logisticsStatus="运输中",stateName="运输中",orderCreatedAt=ParcelTime.parse("2026-10-10"),latestTime="2026-10-20 10:00:00")
        assertTrue(PddPagingPolicy.refreshInScope(item,ParcelTime.parse("2026-10-24"),false,freshAwaitingReceipt=true))
    }
}
