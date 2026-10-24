package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class ActiveRefreshFeedbackTest {
    private fun item(id:String="active")=ExpressItem(id,"PDD","圆通快递","YT0000000012345",source="pdd",orderStatus="待收货",logisticsStatus="运输中",latestTime="2026-10-24 10:00:00")
    @Test fun returningToForegroundRespectsFreshnessInsteadOfOneMinute() {
        assertFalse(ForegroundSyncPolicy.shouldSync(true,true,120_000,1_000))
        assertFalse(ForegroundSyncPolicy.shouldSync(true,true,30_000,1_000))
        assertFalse(ForegroundSyncPolicy.shouldSync(true,false,120_000,1_000))
        assertFalse(ForegroundSyncPolicy.shouldSync(false,false,120_000,1_000))
        assertTrue(ForegroundSyncPolicy.shouldSync(true,true,1_000_000,1_000))
        assertTrue(ForegroundSyncPolicy.shouldSync(false,false,1_000_000,0))
    }
    @Test fun eachSyncRechecksAnActiveTraceEvenIfItWasFetchedWithinFifteenMinutes() {
        assertTrue(PddDetailQueryPolicy.needsRefresh(item(),120_000,119_000,14))
        assertFalse(PddDetailQueryPolicy.needsRefresh(item().copy(logisticsStatus="已签收"),120_000,119_000,PddDetailQueryPolicy.VERSION))
        assertTrue(PddDetailQueryPolicy.needsRefresh(item().copy(logisticsStatus="已签收"),120_000,119_000,14)) // Repair previously missing location once.
    }
    @Test fun allActiveOrdersAreSelectedAndClosedBackfillStaysBounded() {
        val active=(1..6).map {item("active-$it")}
        val closed=(1..10).map {item("closed-$it").copy(logisticsStatus="已签收")}
        val selected=PddDetailQueryPolicy.normalSelection(active+closed)
        assertTrue(selected.containsAll(active))
        assertEquals(4,selected.count {!PddDetailQueryPolicy.active(it)})
    }
    @Test fun laterActiveOrdersGetTheirOwnTimeoutInsteadOfAnExhaustedHistoryBudget() {
        assertEquals(25_000L,PddDetailQueryPolicy.timeout(item(),0,80_000,false))
        assertEquals(0L,PddDetailQueryPolicy.timeout(item().copy(logisticsStatus="已签收"),0,80_000,false))
    }
    @Test fun explicitPddStationRoutesToPddEvenIfCainiaoCanQueryTheCode() {
        val item=item().copy(pickupStation="多多买菜代收点",pickupCodeSource="taobao-cainiao-school",pickupQueryAccountId="tb-owner")
        assertEquals("pdd",PickupUiPolicy.identity(item))
        assertEquals("",PickupUiPolicy.account(item,"jd"))
        assertEquals("pdd",PickupUiPolicy.identity(SyncCacheMerge.merge(item,item().copy(pickupStation="代收点"))))
    }
    @Test fun genericStationAndPddShoppingSourceAreNotProofOfPddIdentityService() {
        assertEquals("",PickupUiPolicy.identity(item().copy(pickupStation="代收点",pickupCode="706-3-5034")))
    }
}
