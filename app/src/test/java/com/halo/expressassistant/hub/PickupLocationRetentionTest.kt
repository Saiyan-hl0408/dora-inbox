package com.halo.expressassistant.hub

import com.halo.expressassistant.data.*
import org.junit.Assert.*
import org.junit.Test

class PickupLocationRetentionTest {
    private fun old()=ExpressItem("owned-order","PDD","申通快递","TEST0000012345",source="pdd",accountId="owner",
        pickupStation="学校校区菜鸟驿站",pickupAddress="学校取件区",pickupCode="1-2-3456",pickupCodeSource="taobao-cainiao-school",pickupCodeCheckedAt=100)
    @Test fun absentCodeDoesNotErasePreviouslyKnownLocation() {
        for(outcome in listOf(CainiaoPickup.Outcome.NO_CODE,CainiaoPickup.Outcome.NO_MATCH,CainiaoPickup.Outcome.COLLECTED)) {
            val fresh=CainiaoPickup.apply(old(),CainiaoPickup.Result(outcome,old().mailNo),"tb-owner",200)
            assertEquals("学校校区菜鸟驿站",fresh.pickupStation)
            assertEquals("学校取件区",fresh.pickupAddress)
            assertEquals("",fresh.pickupCode)
        }
    }
    @Test fun blankFreshLookupCannotEraseLocationDuringMerge() {
        val fresh=old().copy(pickupStation="",pickupAddress="",pickupCode="",pickupQueryOutcome="NO_MATCH",pickupCodeCheckedAt=200)
        val merged=SyncCacheMerge.merge(old(),fresh)
        assertEquals(old().pickupStation,merged.pickupStation);assertEquals(old().pickupAddress,merged.pickupAddress);assertEquals("",merged.pickupCode)
    }
    @Test fun explicitOfficialLocationWinsAndLatestSignedSummaryKeepsPriorArrival() {
        assertEquals("学校校区菜鸟驿站",PddPickupLocation.header("菜鸟驿站 | 学校校区菜鸟驿站\n申通快递：TEST0000012345"))
        val points=listOf(DetailPoint("已签收，签收人凭取货码签收。","2026-10-24 14:59:18"),DetailPoint("待取件 快件已由学校校区菜鸟驿站代收，请及时取件。","2026-10-23 11:04:24"))
        assertEquals("学校校区菜鸟驿站",PddPickupLocation.history(points))
        assertEquals("",PddPickupLocation.history(listOf(DetailPoint("已签收，签收人本人","2026-10-24 15:00:00"))))
    }
    @Test fun collectedLookupMayRestoreLocationEvenThoughItMustNotRestoreTheOldCode() {
        val empty=old().copy(pickupStation="",pickupAddress="")
        val result=CainiaoPickup.apply(empty,CainiaoPickup.Result(CainiaoPickup.Outcome.COLLECTED,empty.mailNo,station="学校校区菜鸟驿站",address="学校取件区"),"tb-owner",200)
        assertEquals("学校校区菜鸟驿站",result.pickupStation)
        assertEquals("学校取件区",result.pickupAddress)
        assertEquals("",result.pickupCode)
    }
}
