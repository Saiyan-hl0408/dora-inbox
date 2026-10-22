package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class PddBoundaryAndBindingRegressionTest {
    private val now=ParcelTime.parse("2026-10-13 12:00:00")
    private fun old(status:String)=ExpressItem("old","","","ORDER-PDD-old",source="pdd",isOrderNumber=true,orderStatus=status,stateName=status,
        orderCreatedAt=ParcelTime.parse("2025-06-01 12:00:00"))
    @Test fun orderListStopsAtSevenDayBoundaryEvenWithoutAnySignatureTime() {
        val page=listOf(old("待评价"),old("已签收，待确认收货"))
        assertTrue(PddPagingPolicy.listPhaseBoundary(page,SyncWindow(7,now).since,false,false))
    }
    @Test fun awaitingReceiptPhaseCannotWalkOldOrdersToTheEnd() {
        assertTrue(PddPagingPolicy.listPhaseBoundary(listOf(old("待收货")),SyncWindow(7,now).since,true,false))
    }
    @Test fun selectedRangeIsUsedAndDoesNotCutOffAnInRangePage() {
        val page=listOf(old("待评价").copy(orderCreatedAt=ParcelTime.parse("2026-09-20")))
        assertTrue(PddPagingPolicy.listPhaseBoundary(page,SyncWindow(7,now).since,false,false))
        assertFalse(PddPagingPolicy.listPhaseBoundary(page,SyncWindow(30,now).since,false,false))
    }
    @Test fun pddBindingIsQueuedDuringJdReadAndRunsExactlyOnceAfterIt() {
        val queue=BindingSyncQueue()
        queue.request("pdd");queue.request("pdd")
        assertEquals(setOf("pdd"),queue.pending())
        assertTrue(queue.take(working=true,foreground=true).isEmpty())
        assertEquals(setOf("pdd"),queue.pending())
        assertEquals(setOf("pdd"),queue.take(working=false,foreground=true))
        assertTrue(queue.take(working=false,foreground=true).isEmpty())
    }
    @Test fun queuedBindingWaitsForForegroundAndCombinesMultipleChannels() {
        val queue=BindingSyncQueue();queue.request("jd");queue.request("pdd")
        assertTrue(queue.take(false,false).isEmpty())
        assertEquals(setOf("jd","pdd"),queue.take(false,true))
    }
    @Test fun staleCheckpointCannotExpandASevenDayQueryToMonthsAgo() {
        val since=PddPagingPolicy.requestedSince(7,now,ParcelTime.parse("2025-06-01"))
        assertEquals(SyncWindow(7,now).since,since)
        val last=ParcelTime.parse("2026-10-12 12:00:00")
        assertEquals(last,PddPagingPolicy.requestedSince(30,now,last))
        assertEquals(SyncWindow(30,now).since,PddPagingPolicy.requestedSince(30,now,last,expanding=true))
    }
    @Test fun boundaryPageDoesNotImportOldHistoryButKeepsCachedUnfinishedOrders() {
        val cached=old("运输中").copy(id="active",logisticsStatus="运输中",stateName="运输中",latestText="运输中",isOrderNumber=false)
        val scope=PddListScope(SyncWindow(7,now).since,listOf(cached))
        assertEquals(listOf("active"),scope.select(listOf(old("待评价"),cached)).map {it.id})
    }
    @Test fun oneBoundaryPageMayVerifyLateSignatureButCannotAdmitLastYearHistory() {
        val scope=PddListScope(SyncWindow(7,now).since,emptyList(),SyncWindow(180,now).since)
        assertTrue(scope.retain(old("已签收，待确认收货").copy(orderCreatedAt=ParcelTime.parse("2026-10-04"))))
        assertFalse(scope.retain(old("已签收，待确认收货")))
    }
    @Test fun cachedRecentlySignedOrderStillUpdatesCommerceStateOnTheBoundaryPage() {
        val cached=old("已签收，待确认收货").copy(completedAt=ParcelTime.parse("2026-10-12"))
        assertTrue(PddListScope(SyncWindow(7,now).since,listOf(cached)).retain(cached.copy(completedAt=0,orderStatus="待评价")))
    }
    @Test fun invalidOrCancelledBindingRequestNeverStartsASync() {
        val queue=BindingSyncQueue();queue.request("settings");assertTrue(queue.pending().isEmpty())
        queue.request("pdd");queue.clear();assertTrue(queue.take(false,true).isEmpty())
    }
    @Test fun changingRangeDuringAReadWaitsForCleanupAndLastChoiceWins() {
        val queue=BindingSyncQueue();queue.request("pdd");queue.requestRange(30);queue.requestRange(7)
        assertNull(queue.takeNext(true,true))
        assertNull(queue.takeNext(false,false))
        assertEquals(7,queue.takeNext(false,true)?.historyDays)
        assertNull(queue.takeNext(false,true))
        assertTrue(queue.pending().isEmpty())
    }
    @Test fun replayTwentyEightAvailablePagesStopsAfterTheFirstOutOfRangePageInBothTabs() {
        val since=SyncWindow(7,now).since
        for(activePhase in listOf(false,true)) {
            val scope=PddListScope(since,emptyList())
            var fetched=0;var retained=0
            for(pageIndex in 0 until 28) {
                val time=if(pageIndex==0)now else since-86400000L*pageIndex
                val prompt=if(activePhase)"待收货" else "待评价"
                val rows=(0..2).joinToString(",") {index->"""{"order_sn":"$pageIndex-$index","order_time":$time,"order_status_prompt":"$prompt"}"""}
                val page=PddOrderPayload.read(listOf("/order_list_v4" to """{"orders":[$rows]}""")).first
                fetched++;retained+=scope.select(page).size
                if(PddPagingPolicy.listPhaseBoundary(page,since,activePhase,false))break
            }
            assertEquals(2,fetched)
            assertEquals(3,retained)
        }
    }
}
