package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class UnifiedActiveRefreshTest {
    private fun item(source:String,status:String)=ExpressItem("old-order","","测试快递","TEST0000012345",source=source,
        orderStatus="待收货",logisticsStatus=status,orderCreatedAt=1,queryChannel="old-order")
    @Test fun allThreePlatformsContinueKnownUnfinishedOrders() {
        for(source in listOf("jd","taobao","pdd"))for(status in listOf("运输中","待取件"))assertTrue(ActiveParcelRefresh.includes(item(source,status)))
        for(source in listOf("jd","taobao","pdd"))assertTrue(ActiveParcelRefresh.includes(item(source,"").copy(orderStatus="待发货",stateName="待发货")))
    }
    @Test fun completedCancelledAndVirtualOrdersAreNotActive() {
        for(source in listOf("jd","taobao","pdd")) {
            assertFalse(ActiveParcelRefresh.includes(item(source,"已签收").copy(orderStatus="已完成",state=3)))
            assertFalse(ActiveParcelRefresh.includes(item(source,"").copy(orderStatus="已取消",state=4)))
            assertFalse(ActiveParcelRefresh.includes(item(source,"").copy(isOrderNumber=true,mailNo="ORDER-X",originalName="电子会员")))
        }
    }
    @Test fun jdAutomaticSyncDoesNotHideBehindRecentCacheWhenActive() {
        assertFalse(JdlQueryPolicy.localOnly(true,false,false,100000,99000,99000,hasActive=true))
        assertTrue(JdlQueryPolicy.localOnly(true,false,false,100000,99000,99000,hasActive=false))
    }
    @Test fun manualPddRefreshCanRetryAfterFailureImmediately() {
        assertTrue(PddDetailQueryPolicy.mayAttempt(100000,200000,true,manual=true))
        assertFalse(PddDetailQueryPolicy.mayAttempt(100000,200000,true,manual=false))
    }
    @Test fun detailFailureOrDeferredRetryCannotReportFullPlatformSuccess() {
        assertNull(SyncStatusText.detailsError(null,0,0))
        assertTrue(SyncStatusText.detailsError(null,1,0)!!.contains("未更新"))
        assertTrue(SyncStatusText.detailsError(null,0,1)!!.contains("等待自动重试"))
        assertTrue(SyncStatusText.detailsError("订单未读完",1,0)!!.contains("订单未读完"))
    }
    @Test fun oldUnverifiedAndNotShippedPddOrdersStillEnterTheUpdateQueue() {
        for(status in listOf("待发货","待收货")) {
            val old=item("pdd","").copy(isOrderNumber=true,mailNo="ORDER-PDD-old",orderStatus=status,stateName=status)
            assertTrue(PddPagingPolicy.refreshInScope(old,999999,false))
            assertTrue(PddDetailQueryPolicy.needsRefresh(old,999999,999998,14))
        }
    }
    @Test fun packingOrderCanAdvanceWithoutFabricatingALogisticsEvent() {
        val old=item("pdd","").copy(orderStatus="待发货",stateName="待发货",isOrderNumber=true,mailNo="ORDER-PDD-old")
        val awaiting=PddOrderProgress.apply(old,"待收货")
        assertEquals("待收货",awaiting.orderStatus)
        assertEquals(ParcelPresentation.Stage.UNVERIFIED,ParcelPresentation.stage(awaiting))
        assertEquals("",awaiting.latestTime)
        assertEquals(ParcelPresentation.Stage.AFTERSALE,ParcelPresentation.stage(PddOrderProgress.apply(old,"已取消")))
        assertEquals("待收货",PddDetailPayload.read("""{"data":{"orderStatusPrompt":"待收货"}}""").orderStatus)
    }
}
