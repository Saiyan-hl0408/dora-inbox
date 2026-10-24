package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class PddPagingPolicyTest {
    private val since=ParcelTime.parse("2026-09-09")
    private fun item(date:String)=ExpressItem("synthetic","","","TEST12345678",source="pdd",stateName="运输中",latestText="快件运输中",orderCreatedAt=ParcelTime.parse(date))
    @Test fun knownOldUnfinishedOrdersContinueOutsideThePurchaseBoundary() {
        assertTrue(PddPagingPolicy.refreshInScope(item("2025-01-01"),since,false))
        assertTrue(PddPagingPolicy.refreshInScope(item("2026-10-01"),since,false))
    }
    @Test fun olderActuallyUnfinishedCachedParcelStillUpdatesIndividually() {
        assertTrue(PddPagingPolicy.refreshInScope(item("2026-08-01"),since,true))
        assertFalse(PddPagingPolicy.refreshInScope(item("2026-08-01").copy(state=3,stateName="已签收",latestText="已签收"),since,true))
    }
    @Test fun nestedLastPageFlagsAreReadWithoutUsingRecommendations() {
        assertEquals(false,PddPagingPolicy.hasMore("""{"data":{"has_more":false},"recommend":{"has_more":true}}"""))
        assertEquals(true,PddPagingPolicy.hasMore("""{"result":{"hasMore":1}}"""))
        assertNull(PddPagingPolicy.hasMore("""{"recommend":{"has_more":false}}"""))
    }
    @Test fun allOrdersAndAwaitingReceiptBothStopAtTheRequestedBoundary() {
        val window=SyncWindow(30,ParcelTime.parse("2026-10-08"),exhaustive=true)
        val oldPending=listOf(item("2025-01-01").copy(orderStatus="待收货"))
        assertTrue(window.reachedOrderBoundary(oldPending))
        assertFalse(window.reachedOrderBoundary(listOf(item("2026-10-01"))))
    }
}
