package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class PickupUiPolicyTest {
    private fun pending()=ExpressItem("pdd-order","YUNDA","韵达快递","YD0000000123",source="pdd",accountId="shopping-owner",
        originalName="真实商品",logisticsStatus="待取件",stateName="待取件",latestText="快件已到【校园代收点】，请及时领取",
        pickupStation="校园代收点",pickupAddress="真实取件地址",orderStatus="待收货")

    @Test fun shoppingSourceCannotChooseIdentityService() {
        assertEquals("",PickupUiPolicy.identity(pending()))
        assertEquals("",PickupUiPolicy.identity(pending().copy(source="taobao")))
        assertEquals("",PickupUiPolicy.identity(pending().copy(source="jd")))
    }
    @Test fun actualCainiaoServiceWinsAndKeepsItsOwnAccount() {
        val item=pending().copy(identityChannel="pdd",pickupCode="8-1-1005",pickupCodeSource="taobao-cainiao-school",pickupQueryAccountId="code-owner")
        assertEquals("taobao",PickupUiPolicy.identity(item))
        assertEquals("code-owner",PickupUiPolicy.account(item,"taobao"))
    }
    @Test fun noMatchKeepsPlatformLogisticsAndOnlyOffersOptionalPddEntry() {
        val before=pending()
        val after=CainiaoPickup.apply(before,CainiaoPickup.Result(CainiaoPickup.Outcome.NO_MATCH,before.mailNo),"cainiao-owner",123L)
        assertTrue(PickupUiPolicy.offerPdd(after))
        assertEquals(before.originalName,after.originalName)
        assertEquals(before.mailNo,after.mailNo)
        assertEquals(before.latestText,after.latestText)
        assertEquals(before.pickupStation,after.pickupStation)
        assertEquals(before.pickupAddress,after.pickupAddress)
        assertEquals(ParcelPresentation.stage(before),ParcelPresentation.stage(after))
        assertEquals("NO_MATCH",after.pickupQueryOutcome)
    }
    @Test fun errorsKeepTheirOwnMeaningWhileOfferingOptionalPddEntry() {
        for(outcome in listOf("","FAILED","LOGIN","VERIFY")) {
            val item=pending().copy(pickupQueryOutcome=outcome)
            assertTrue(PickupUiPolicy.offerPdd(item))
            assertFalse(PickupUiPolicy.missingLabel(item).contains("暂未查到"))
        }
        assertFalse(PickupUiPolicy.offerPdd(pending().copy(source="taobao",pickupQueryOutcome="NO_MATCH")))
        assertFalse(PickupUiPolicy.offerPdd(pending().copy(pickupCode="1-2-3456",pickupQueryOutcome="NO_MATCH")))
        assertFalse(PickupUiPolicy.offerPdd(pending().copy(logisticsStatus="运输中",stateName="运输中",latestText="快件运输中",pickupQueryOutcome="NO_MATCH")))
    }
    @Test fun supplementalOutcomeSurvivesShoppingRefresh() {
        val old=pending().copy(pickupQueryOutcome="NO_MATCH",pickupCodeCheckedAt=123L)
        val merged=SyncCacheMerge.merge(old,pending().copy(originalName="1"))
        assertEquals("NO_MATCH",merged.pickupQueryOutcome)
        assertEquals("真实商品",merged.originalName)
    }
    @Test fun carrierRoutingDoesNotAssumeAllJdShoppingUsesJdExpress() {
        assertFalse(CainiaoPickup.jdCarrier(pending().copy(source="jd")))
        assertTrue(CainiaoPickup.jdCarrier(pending().copy(source="pdd",companyName="京东快递")))
    }
}
