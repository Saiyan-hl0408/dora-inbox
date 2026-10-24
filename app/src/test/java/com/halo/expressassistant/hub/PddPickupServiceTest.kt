package com.halo.expressassistant.hub

import com.halo.expressassistant.data.*
import org.junit.Assert.*
import org.junit.Test

class PddPickupServiceTest {
    private val no="YT0000000123456"
    private val at=ParcelTime.parse("2026-10-24 15:30:00")
    private fun item()=ExpressItem("pdd-order","PDD","圆通快递",no,source="pdd",accountId="owner",logisticsStatus="待取件",pickupCode="OLD-123",pickupCodeSource="taobao-cainiao-school",pickupQueryAccountId="tb-owner")
    private fun pending(code:String="706-3-5034")=PddPickupPayload.read("""{"success":true,"result":{"count":1,"combine_list":[{"station_name":"某大学食堂代收点","combine_track_list":[{"tracking_number":"$no","pick_code":"取件码 $code","pickup_status":0}],"station_delivery_track_list":[{"tracking_number":"DELIVERY12345678","pick_code":"999-999"}]}]}}""")
    private fun collected(time:String="2026-10-24 14:58:59")=PddPickupPayload.read("""{"success":true,"error_code":"0","result":{"count":0,"package_out_list":[{"address":"某大学食堂代收点","operate_time":"$time","shipping_id":85,"shipping_name":"圆通快递","tracking_number":"$no","pic_url":"","jump_url":""}]}}""")
    @Test fun realObservedRecentOutSchemaHasItsOwnEventTime() {
        val result=collected();assertEquals(PddPickupPayload.Outcome.SUCCESS,result.outcome)
        assertEquals(0,result.pendingCount);assertEquals(ParcelTime.parse("2026-10-24 14:58:59"),result.parcels.single().completedAt)
    }
    @Test fun syntheticPendingGroupExcludesHomeDeliveryAndSetsPddServiceDespiteGenericStation() {
        val snapshot=pending();assertEquals(1,snapshot.parcels.size)
        val resolved=PddPickupPayload.apply(item(),snapshot,"owner",at)
        assertEquals("706-3-5034",resolved.pickupCode);assertEquals("pdd",PickupUiPolicy.identity(resolved))
        assertEquals("pdd-official",resolved.pickupCodeSource);assertFalse(CainiaoPickup.candidate(resolved))
        assertEquals("",PickupUiPolicy.account(resolved,"jd"))
    }
    @Test fun recentOutClosesPendingAndClearsCurrentCode() {
        val resolved=PddPickupPayload.apply(item(),collected(),"owner",at)
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(resolved))
        assertEquals("",resolved.pickupCode);assertEquals("OLD-123",resolved.historicalPickupCode)
        assertEquals(ParcelTime.parse("2026-10-24 14:58:59"),resolved.completedAt)
    }
    @Test fun emptySuccessAndMissingWaybillNeverMeanCollected() {
        val empty=PddPickupPayload.read("""{"success":true,"result":{"count":0,"package_out_list":[]}}""")
        assertEquals(item(),PddPickupPayload.apply(item(),empty,"owner",at))
    }
    @Test fun failedAndUnauthenticatedResponsesCannotChangeExistingRecords() {
        for(raw in listOf("bad", """{"error_code":40001} """, """{"success":false,"result":{"package_out_list":[]}}""")) {
            assertEquals(item(),PddPickupPayload.apply(item(),PddPickupPayload.read(raw),"owner",at))
        }
        assertEquals(PddPickupPayload.Outcome.LOGIN,PddPickupPayload.read("""{"error_code":40001} """).outcome)
    }
    @Test fun cannotAffectAnotherAccountOrAnOrderPlaceholder() {
        for(other in listOf(item().copy(accountId="other"),item().copy(source="taobao"),item().copy(isOrderNumber=true)))assertEquals(other,PddPickupPayload.apply(other,collected(),"owner",at))
    }
    @Test fun staleCachedTracesAndCommerceListCannotReopenCollectedPackage() {
        val closed=PddPickupPayload.apply(item(),collected(),"owner",at)
        val trace=PddLogistics.apply(closed,listOf(DetailPoint("待取件 请凭取件码领取","2026-10-23 10:00:00")))
        val merged=SyncCacheMerge.merge(trace,item())
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(merged));assertEquals("",merged.pickupCode)
        assertEquals(closed.completedAt,merged.completedAt)
    }
    @Test fun directPendingCodeSurvivesOlderOfficialDetailCodeAndCacheMerge() {
        val current=PddPickupPayload.apply(item(),pending(),"owner",at)
        val trace=PddLogistics.apply(current,listOf(DetailPoint("待取件 取件码：888-888","2026-10-23 10:00:00")))
        assertEquals("706-3-5034",trace.pickupCode)
        assertEquals("706-3-5034",SyncCacheMerge.merge(trace,item()).pickupCode)
    }
    @Test fun missingCollectedTimeDoesNotUseObservationTimeAsPickupTime() {
        val resolved=PddPickupPayload.apply(item(),collected(""),"owner",at)
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(resolved));assertEquals(0L,resolved.completedAt)
    }
    @Test fun olderSnapshotsCannotReplaceNewerEvidence() {
        val closed=PddPickupPayload.apply(item(),collected(),"owner",at)
        assertEquals(closed,PddPickupPayload.apply(closed,pending(),"owner",at-1))
        val reopened=PddPickupPayload.apply(closed,pending(),"owner",at+1)
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(reopened));assertEquals(0L,reopened.completedAt)
    }
    @Test fun currentPendingWinsAConflictingRecentOutRow() {
        val snapshot=PddPickupPayload.read("""{"success":true,"result":{"count":1,"combine_list":[{"combine_track_list":[{"tracking_number":"$no","pick_code":"123-456"}]}],"package_out_list":[{"tracking_number":"$no","operate_time":"2026-10-24 12:00:00"}]}}""")
        assertEquals("PENDING",snapshot.parcels.single().state)
    }
    @Test fun serviceProofSelectsPddEvenWithoutCodeWhileCainiaoRemainsFallback() {
        val resolved=PddPickupPayload.apply(item(),pending(""),"owner",at)
        assertEquals("pdd",PickupUiPolicy.identity(resolved));assertEquals("OLD-123",resolved.pickupCode)
        assertEquals("taobao",PickupUiPolicy.identity(item()))
    }
    @Test fun newlyMissingOfficialCodeCannotResurrectPreviousOfficialCodeButKeepsManualCode() {
        val old=PddPickupPayload.apply(item(),pending(),"owner",at)
        val noCode=PddPickupPayload.apply(old,pending(""),"owner",at+1)
        assertEquals("",noCode.pickupCode);assertEquals("",SyncCacheMerge.merge(old,noCode).pickupCode)
        val manual=PddPickupPayload.apply(item().copy(pickupCode="222-333",pickupCodeSource="local"),pending(),"owner",at)
        assertEquals("222-333",manual.pickupCode);assertEquals("706-3-5034",manual.pddPickupCode)
    }
    @Test fun missingPendingRowOnlyRetiresItsOverrideSoNewLogisticsCanCloseIt() {
        val pending=PddPickupPayload.apply(item(),pending(),"owner",at)
        val missing=PddPickupPayload.apply(pending,PddPickupPayload.read("""{"success":true,"result":{"count":0}}"""),"owner",at+1000)
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(missing))
        assertEquals("",missing.pddPickupServiceState)
        val actual=PddLogistics.apply(missing,listOf(DetailPoint("已取件 收件人已领取","2026-10-24 15:31:00")))
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(actual))
    }
}
