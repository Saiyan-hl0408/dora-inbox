package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class SyncWindowTest {
    @Test fun explicitSixMonthImportDoesNotStopAtFortyPagesBeforeTheDateBoundary() {
        val history=SyncWindow(180,now,exhaustive=true)
        val junePage=listOf(parcel(3,"2026-06-23").copy(orderCreatedAt=ParcelTime.parse("2026-06-23")))
        assertFalse(history.reachedOrderBoundary(junePage))
        assertTrue("Six-month import must reach the date boundary",history.pageBudget>40)
    }
    @Test fun thirtyDayCacheAfterFourDaysOnlyNeedsTheFourDayGap() {
        val lastSync=ParcelTime.parse("2026-10-04 18:00:00")
        val incremental=SyncWindow(6,now,lastSync)
        assertEquals(lastSync,incremental.since)
        assertTrue(incremental.reachedOrderBoundary(listOf(parcel(0,"").copy(orderCreatedAt=ParcelTime.parse("2026-10-03 12:00:00")))))
        assertFalse(incremental.reachedOrderBoundary(listOf(parcel(0,"").copy(orderCreatedAt=ParcelTime.parse("2026-10-06 12:00:00")))))
    }
    @Test fun mixedHistoryDoesNotStopScanningUnfinishedParcels() {
        val window=SyncWindow(now=now)
        assertFalse(window.reachedOldHistory(listOf(parcel(3,"2026-09-01"),parcel(5,"2026-10-08"))))
    }
    private val now=ParcelTime.parse("2026-10-08 18:00:00")
    private fun parcel(state:Int,time:String)=ExpressItem("test","","快递","TEST123456",state=state,latestTime=time)
    @Test fun defaultWindowUsesShanghaiCalendarDaysAndExcludesOldSigned() {
        val w=SyncWindow(now=now)
        assertTrue(w.include(parcel(3,"2026-10-02 00:00:00")))
        assertFalse(w.include(parcel(3,"2026-10-01 23:59:59")))
        assertTrue(w.include(parcel(5,"2026-09-01 00:00:00").copy(stateName="待取件")))
        assertFalse(w.include(parcel(3,"")))
        assertEquals(3,w.pageBudget)
    }
    @Test fun dateFiltersIncludePlatformHistoryWithoutLocalActions() {
        val items=listOf(parcel(3,"2026-10-08 12:00:00"),parcel(3,"2026-10-03 12:00:00"),parcel(3,"2026-09-20 12:00:00"))
        assertEquals(1,ParcelPresentation.recent(items,emptyMap(),null,3,now).size)
        assertEquals(2,ParcelPresentation.recent(items,emptyMap(),null,7,now).size)
        assertEquals(3,ParcelPresentation.recent(items,emptyMap(),null,30,now).size)
    }
    @Test fun realDatesAndArrivalAgeDoNotInventMissingDates() {
        assertEquals("到站 2 天",ParcelTime.arrivalLabel(ParcelTime.parse("2026-10-06 23:00:00"),now))
        assertEquals("今天到站",ParcelTime.arrivalLabel(ParcelTime.parse("2026-10-08 12:00:00"),now))
        assertEquals("",ParcelTime.arrivalLabel(0,now))
        assertEquals(0,ParcelTime.parse("交易成功"))
        assertEquals(ParcelTime.parse("2026-10-07 14:00"),ParcelTime.parse("昨天 14:00",now))
    }
    @Test fun stalePickupCodeDoesNotOverrideRefundOrPlatformCompletion() {
        assertEquals(ParcelPresentation.Stage.AFTERSALE,ParcelPresentation.stage(parcel(3,"2026-10-08").copy(orderStatus="退款成功",pickupCode="123456")))
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(parcel(3,"2026-10-08").copy(orderStatus="已完成",pickupCode="123456")))
    }
}
