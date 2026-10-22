package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CainiaoPickupTest {
    private val number="JT0000000012345"
    private fun item(source:String="pdd")=ExpressItem("order","JT","极兔",number,source=source,accountId="shopping",stateName="待取件",logisticsStatus="待取件")
    private fun response(status:String="3",label:String="取件码",pop:String="null",code:String="8-1-1234")="""{"ret":["SUCCESS::调用成功"],"data":{"result":{"securityPop":$pop,"stationMessageAndPackages":[{"stationMessage":{"stationName":"错误站点"},"waybillPickUpResp":{"certainPickupWaybills":[{"waybillNo":"OTHER000000000","pickupCode":"1-1-1111","status":"3","packageStatus":"取件码"}]}},{"stationMessage":{"stationName":"测试学校驿站","stationAddress":"测试地址"},"waybillPickUpResp":{"certainPickupWaybills":[{"waybillNo":"$number","pickupCode":"$code","status":"$status","packageStatus":"$label","signTime":"2026-10-07 19:30:40"}]}}]}}}"""
    @Test fun matchExactWaybillAcrossStationGroups() {
        val result=CainiaoPickup.read(response(),number)
        assertEquals(CainiaoPickup.Outcome.FOUND,result.outcome)
        assertEquals("8-1-1234",result.code);assertEquals("测试学校驿站",result.station)
        assertEquals(CainiaoPickup.Outcome.NO_MATCH,CainiaoPickup.read(response(),"JT9999999999999").outcome)
    }
    @Test fun historicalCodeNeverBecomesCurrentPickupCode() {
        val result=CainiaoPickup.read(response("4","已签收"),number)
        assertEquals(CainiaoPickup.Outcome.COLLECTED,result.outcome);assertEquals("",result.code)
        val after=CainiaoPickup.apply(item(),result,"tb",1)
        assertEquals("",after.pickupCode)
        assertEquals(ParcelPresentation.stage(item()),ParcelPresentation.stage(after))
        assertEquals("COLLECTED",after.pickupQueryOutcome)
    }
    @Test fun securityPopBlocksEvenWhenCodeIsPresent() {
        for(pop in listOf("true","\"suspicious\"","{\"type\":\"confirmPhone\"}")) {
            val result=CainiaoPickup.read(response(pop=pop),number)
            assertEquals(CainiaoPickup.Outcome.VERIFY,result.outcome);assertEquals("",result.code)
            val updated=CainiaoPickup.apply(item(),result,"tb",1)
            assertTrue(updated.pickupVerificationNeeded);assertEquals("",updated.pickupCode)
            assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(updated))
        }
        assertEquals(CainiaoPickup.Outcome.FOUND,CainiaoPickup.read(response(pop="false"),number).outcome)
    }
    @Test fun unknownStatusesAndInvalidCodesAreNotAccepted() {
        assertEquals(CainiaoPickup.Outcome.NO_CODE,CainiaoPickup.read(response("999","未知"),number).outcome)
        assertEquals(CainiaoPickup.Outcome.NO_CODE,CainiaoPickup.read(response(code=number),number).outcome)
        assertEquals(CainiaoPickup.Outcome.FAILED,CainiaoPickup.read("{}",number).outcome)
        assertEquals(CainiaoPickup.Outcome.LOGIN,CainiaoPickup.read("""{"ret":["FAIL_SYS_SESSION_EXPIRED"]}""",number).outcome)
    }
    @Test fun candidatesExcludeCollectedTransitPackingOrderIdsAndExistingCodes() {
        assertTrue(CainiaoPickup.candidate(item()));assertTrue(CainiaoPickup.candidate(item("taobao")))
        assertFalse(CainiaoPickup.candidate(item(),true))
        assertFalse(CainiaoPickup.candidate(item().copy(logisticsStatus="运输中")))
        assertFalse(CainiaoPickup.candidate(item().copy(logisticsStatus="已签收")))
        assertFalse(CainiaoPickup.candidate(item().copy(isOrderNumber=true)))
        assertFalse(CainiaoPickup.candidate(item().copy(pickupCode="1-1-1000")))
        assertFalse(CainiaoPickup.candidate(item("jd")))
        assertFalse(CainiaoPickup.candidate(item().copy(companyName="京东物流")))
    }
    @Test fun supplementKeepsCommerceClassificationAndAccountOwnership() {
        val before=item().copy(orderStatus="待确认收货",orderCreatedAt=1234,latestText="原平台物流")
        val after=CainiaoPickup.apply(before,CainiaoPickup.read(response(),number),"tb-account",9000)
        assertEquals(before.orderStatus,after.orderStatus);assertEquals(before.latestText,after.latestText)
        assertEquals("shopping",after.accountId);assertEquals("tb-account",after.pickupQueryAccountId)
        assertEquals("taobao-cainiao-school",after.pickupCodeSource);assertEquals(9000,after.pickupCodeCheckedAt)
        assertEquals(before.copy(logisticsStatus="已签收"),CainiaoPickup.apply(before.copy(logisticsStatus="已签收"),CainiaoPickup.read(response(),number),"tb",1))
    }
    @Test fun platformRefreshPreservesSupplementMetadata() {
        for(source in listOf("taobao","pdd")) {
            val old=CainiaoPickup.apply(item(source),CainiaoPickup.read(response(),number),"tb",9000)
            val merged=SyncCacheMerge.merge(old,item(source))
            assertEquals(old.pickupCode,merged.pickupCode);assertEquals(old.pickupCodeSource,merged.pickupCodeSource)
            assertEquals(old.pickupQueryAccountId,merged.pickupQueryAccountId);assertEquals(old.pickupCodeCheckedAt,merged.pickupCodeCheckedAt)
        }
    }
    @Test fun taobaoCurrentSignedHeadingBeatsOldPickupInstructions() {
        val signed=item("taobao").copy(logisticsStatus="已签收",latestText="凭取件码领取",orderStatus="待确认收货")
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(signed))
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(signed.copy(logisticsStatus="待取件")))
    }
    @Test fun commitKeepsCodesAddedWhilePlatformSyncWasRunning() {
        val fresh=item().copy(orderStatus="等待收货")
        val duringSync=CainiaoPickup.apply(item(),CainiaoPickup.read(response(),number),"tb",9000)
        val saved=SyncCacheMerge.withLatest(listOf(duringSync),listOf(fresh)).single()
        assertEquals("8-1-1234",saved.pickupCode);assertEquals("tb",saved.pickupQueryAccountId)
        assertEquals(fresh.orderStatus,saved.orderStatus)
        assertEquals("",SyncCacheMerge.withLatest(listOf(duringSync.copy(accountId="other")),listOf(fresh)).single().pickupCode)
    }
    @Test fun explicitManualJdFallbackCanSaveButNeverAutoQueryCainiao() {
        val jd=item().copy(companyName="京东物流")
        assertFalse(CainiaoPickup.candidate(jd));assertTrue(CainiaoPickup.canApply(jd))
        assertEquals("8-1-1234",CainiaoPickup.apply(jd,CainiaoPickup.read(response(),number),"tb",1).pickupCode)
    }
    @Test fun cookieIdentityDoesNotConfuseTwoTaobaoAccounts() {
        assertFalse(CookiePairs.sameTaobaoAccount(mapOf("unb" to "a","cookie1" to "same"),mapOf("unb" to "b","cookie1" to "same")))
        assertTrue(CookiePairs.sameTaobaoAccount(mapOf("unb" to "a"),mapOf("unb" to "a")))
        assertFalse(CookiePairs.sameTaobaoAccount(emptyMap(),emptyMap()))
    }
    @Test fun retryResumesWhenStatusChangesAndOldStorageStillDecodes() {
        assertFalse(CainiaoPickup.mayAttempt(10,20,true));assertTrue(CainiaoPickup.mayAttempt(10,20,false))
        assertTrue(CainiaoPickup.mayAttempt(20,20,true))
        val old=Json.decodeFromString<ExpressItem>("""{"id":"a","companyCode":"JT","companyName":"极兔","mailNo":"$number"}""")
        assertEquals("",old.pickupCodeSource);assertFalse(old.pickupVerificationNeeded)
        assertTrue(CainiaoPickup.pageUrl("123456").endsWith("&stationCode=123456"))
        assertFalse(CainiaoPickup.pageUrl("123&token=bad").contains("token"))
    }
}
