package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ParcelPresentationTest {
    private fun parcel(mail: String, state: Int = 3, text: String = "") = ExpressItem(mail,"","极兔",mail,state=state,latestText=text)
    @Test fun signedStationNeedsExplicitPendingStatusToStayPending() {
        assertEquals(ParcelPresentation.Stage.CLOSED, ParcelPresentation.stage(parcel("a",text="已签收，放在菜鸟驿站")))
        assertEquals(ParcelPresentation.Stage.PICKUP, ParcelPresentation.stage(parcel("a",text="已签收至驿站，等待取件")))
        assertEquals(ParcelPresentation.Stage.CLOSED, ParcelPresentation.stage(parcel("b")))
        assertEquals(ParcelPresentation.Stage.CLOSED, ParcelPresentation.stage(parcel("c",text="本人签收")))
    }
    @Test fun courierCollectionIsNotRecipientPickup() {
        assertEquals(ParcelPresentation.Stage.TRANSIT,ParcelPresentation.stage(parcel("a",state=1,text="快递员已取件，正在送往转运中心")))
    }
    @Test fun recentUsesCollectionTimeAndSupportsAllHistory() {
        val items=(1..5).map { parcel("$it") }
        val times=mapOf("1" to 5L,"2" to 1L,"3" to 3L,"4" to 4L,"5" to 2L)
        assertEquals(listOf("1","4","3"),ParcelPresentation.recent(items,times).map { it.mailNo })
        assertEquals(5,ParcelPresentation.recent(items,times,null).size)
    }
    @Test fun newTokenIsAddedAndExistingTokensAreReplaced() {
        assertEquals("cookie2=a=b; _m_h5_tk=new_123",CookiePairs.merge("cookie2=a=b",mapOf("_m_h5_tk" to "new_123")))
        assertEquals("_m_h5_tk=new_123",CookiePairs.merge("_m_h5_tk=old; _m_h5_tk=older",mapOf("_m_h5_tk" to "new_123")))
    }
    @Test fun matchedOrderWithoutCodeFallsBackToWaybill() = runBlocking {
        var fallbackCalled=false
        assertEquals("A-1068",PickupAttempts.resolve(listOf({ null },{fallbackCalled=true; "A-1068"})))
        assertTrue(fallbackCalled)
        assertEquals("123456",PickupAttempts.resolve(listOf({error("upstream failed")},{"123456"})))
    }
    @Test fun cancellationDoesNotFallThroughToAnotherAccount() = runBlocking {
        var fallbackCalled=false
        try { PickupAttempts.resolve(listOf({throw CancellationException()},{fallbackCalled=true; "123456"})); fail() }
        catch (_: CancellationException) { }
        assertFalse(fallbackCalled)
    }
}
