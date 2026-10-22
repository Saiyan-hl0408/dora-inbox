// Synthetic phone-shaped rejection fixtures only; not an account or contact.
package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressItem
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PddPickupPriorityTest {
    private fun item()=ExpressItem("pdd-order","JT","极兔","JT0000000012345",source="pdd",accountId="pdd-a",stateName="待取件",logisticsStatus="待取件")
    @Test fun explicitPddCodeDoesNotPreventHigherPriorityCainiaoLookup() {
        assertTrue(CainiaoPickup.candidate(item().copy(pickupCode="12345",pickupCodeSource="pdd-official")))
    }
    @Test fun cainiaoCodeCanReplaceAnExplicitPddCode() {
        val pdd=item().copy(pickupCode="12345",pickupCodeSource="pdd-official")
        val result=CainiaoPickup.Result(CainiaoPickup.Outcome.FOUND,pdd.mailNo,"8-1-1234")
        val after=CainiaoPickup.apply(pdd,result,"tb-a",100)
        assertEquals("8-1-1234",after.pickupCode);assertEquals("taobao-cainiao-school",after.pickupCodeSource)
    }
    @Test fun orderRefreshCannotReplaceTheHigherPriorityCainiaoCode() {
        val cn=item().copy(pickupCode="8-1-1234",pickupCodeSource="taobao-cainiao-school",pickupQueryOutcome="FOUND")
        val pdd=item().copy(pickupCode="12345",pickupCodeSource="pdd-official")
        assertEquals("8-1-1234",SyncCacheMerge.merge(cn,pdd).pickupCode)
    }
    @Test fun explicitPddTraceCodeHasProvenance() {
        val parsed=PddLogistics.apply(item(),listOf(DetailPoint("待取件 取件码：12345","2026-10-21 10:00:00")))
        assertEquals("12345",parsed.pickupCode);assertEquals("pdd-official",parsed.pickupCodeSource)
    }
    @Test fun suffixInstructionsAreNotCodesButRealFiveDigitCodesRemainValid() {
        for(label in listOf("订单号后5位","快递单号后五位","运单末五位","手机号后四位")) {
            val text="取件码：12345（$label）"
            val snapshot=PddDetailPayload.read(buildJsonObject {put("text","$text\n极兔速递 : JT0000000012345\n待取件 2026-10-21 10:00:00\n请领取")}.toString())
            assertEquals(label,"",snapshot.pickupCode)
            assertEquals(label,"",PddLogistics.apply(item(),listOf(DetailPoint("待取件 $text","2026-10-21 10:00:00"))).pickupCode)
        }
        assertEquals("12345",PddLogistics.apply(item(),listOf(DetailPoint("待取件 取件码：12345","2026-10-21 10:00:00"))).pickupCode)
    }
    @Test fun noCurrentCodeAlwaysOffersOfficialPddCollectionFallback() {
        for(outcome in listOf("","NO_CODE","NO_MATCH","LOGIN","VERIFY","FAILED"))assertTrue(outcome,PickupUiPolicy.offerPdd(item().copy(pickupQueryOutcome=outcome)))
    }
    @Test fun equalCodeNeverInfersPddIdentityService() {
        assertEquals("taobao",PickupUiPolicy.identity(item().copy(pickupCode="12345",pickupCodeSource="taobao-cainiao-school")))
        assertEquals("",PickupUiPolicy.identity(item().copy(pickupCode="12345",pickupCodeSource="pdd-official")))
    }
    @Test fun pddEvidenceSurvivesCainiaoSelectionAndCanBeFallbackAgain() {
        val pdd=PddPickupCodes.apply(item(),"12345",10)
        val found=CainiaoPickup.apply(pdd,CainiaoPickup.Result(CainiaoPickup.Outcome.FOUND,pdd.mailNo,"8-1-1234"),"tb",20)
        assertEquals("12345",found.pddPickupCode)
        val noCode=CainiaoPickup.apply(found,CainiaoPickup.Result(CainiaoPickup.Outcome.NO_CODE,pdd.mailNo),"tb",30)
        assertEquals("12345",noCode.pickupCode);assertEquals("pdd-official",noCode.pickupCodeSource)
        assertEquals("12345",SyncCacheMerge.merge(found,noCode).pickupCode)
    }
    @Test fun normalMissingCainiaoResultKeepsPddCodeAndNoIdentityGuess() {
        for(outcome in listOf(CainiaoPickup.Outcome.NO_MATCH,CainiaoPickup.Outcome.NO_CODE,CainiaoPickup.Outcome.LOGIN,CainiaoPickup.Outcome.FAILED,CainiaoPickup.Outcome.VERIFY)) {
            val pdd=PddPickupCodes.apply(item(),"12345",10)
            val after=CainiaoPickup.apply(pdd,CainiaoPickup.Result(outcome,pdd.mailNo),"tb",20)
            assertEquals(outcome.name,"12345",after.pickupCode)
            assertEquals("",PickupUiPolicy.identity(after))
        }
    }
    @Test fun cainiaoCollectedCannotResurrectAnOldPddCode() {
        val pdd=PddPickupCodes.apply(item(),"12345",10)
        val ended=CainiaoPickup.apply(pdd,CainiaoPickup.Result(CainiaoPickup.Outcome.COLLECTED,pdd.mailNo),"tb",20)
        assertEquals("",ended.pickupCode)
        assertEquals("",SyncCacheMerge.merge(ended,pdd).pickupCode)
    }
    @Test fun unsupportedIdAndProductTextCannotCreatePickupCodes() {
        assertEquals("",PddPickupCodes.fromText("订单编号：12345",item().mailNo))
        assertEquals("",PddPickupCodes.fromText("请凭手机号后四位1234取件",item().mailNo))
        assertEquals("",PddPickupCodes.fromText("取件码：${item().mailNo}",item().mailNo))
        assertEquals("",PddPickupCodes.validValue("19900000000",item().mailNo))
    }
    @Test fun pddRefreshDoesNotOverwriteManuallyEnteredCode() {
        val local=item().copy(pickupCode="9-9-9999",pickupCodeSource="local")
        val observed=PddPickupCodes.apply(local,"12345",10)
        assertEquals("9-9-9999",observed.pickupCode);assertEquals("12345",observed.pddPickupCode)
    }
    @Test fun codeAndStationStayWithTheirActualProvider() {
        val pdd=PddPickupCodes.apply(item().copy(pickupStation="多多站点"),"12345",10)
        val cn=CainiaoPickup.apply(pdd,CainiaoPickup.Result(CainiaoPickup.Outcome.FOUND,pdd.mailNo,"8-1-1234","菜鸟站点","菜鸟地址"),"tb",20)
        val merged=SyncCacheMerge.merge(cn,pdd.copy(pickupStation="平台概要地点"))
        assertEquals("菜鸟站点",merged.pickupStation)
        val fallback=CainiaoPickup.apply(cn,CainiaoPickup.Result(CainiaoPickup.Outcome.NO_MATCH,pdd.mailNo),"tb",30)
        assertEquals("多多站点",fallback.pickupStation);assertEquals("",fallback.pickupAddress)
        assertEquals("多多站点",SyncCacheMerge.merge(cn,fallback).pickupStation)
        assertEquals("",SyncCacheMerge.merge(cn,fallback).pickupAddress)
    }
}
