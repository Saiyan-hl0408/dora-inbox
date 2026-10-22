package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class SyncCacheMergeTest {
    @Test fun partialPageSaveKeepsResolvedWaybillAndUserSettings() {
        val old=ExpressItem("synthetic","","极兔","TEST123456",source="pdd",latestText="包裹已到站",latestTime="2026-10-08 12:00:00",pickupCode="2-3-4567",tracked=true)
        val prompt=old.copy(mailNo="ORDER-PDD-synthetic",companyName="",latestText="待收货",latestTime="",pickupCode="",tracked=false,isOrderNumber=true)
        val merged=SyncCacheMerge.merge(old,prompt)
        assertEquals(old.mailNo,merged.mailNo)
        assertFalse(merged.isOrderNumber)
        assertTrue(merged.tracked)
        assertEquals(old.latestText,merged.latestText)
        assertEquals(old.pickupCode,merged.pickupCode)
    }
    @Test fun partialPageSaveDoesNotKeepAnOutdatedPendingStatus() {
        val old=ExpressItem("synthetic","","极兔","TEST123456",source="pdd",orderStatus="待收货",state=0)
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(SyncCacheMerge.merge(old,old.copy(orderStatus="已完成",state=3))))
    }
    @Test fun slowOrStalledPaginationDoesNotPretendToFinishARequestedYear() {
        val progress=PagingProgress()
        for(page in 1..60)assertFalse(progress.stalled(page))
        repeat(4){assertFalse(progress.stalled(60))}
        assertTrue(progress.stalled(60))
        progress.reset()
        assertFalse(progress.stalled(60))
    }
}
