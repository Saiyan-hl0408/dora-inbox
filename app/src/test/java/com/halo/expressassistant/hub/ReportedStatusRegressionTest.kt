package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class ReportedStatusRegressionTest {
    @Test fun collectedPddParcelDoesNotRequireShoppingReceiptConfirmation() {
        val item=parcel(0,"包裹已取件，感谢使用").copy(source="pdd",orderStatus="待收货",stateName="待收货")
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(item))
    }
    @Test fun historicalStationCodeDoesNotOverrideRecipientCollection() {
        val item=parcel(0,"您已取件，原取件码：2-3-4567","2-3-4567").copy(source="pdd",orderStatus="待收货",stateName="待收货")
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(item))
    }
    private fun parcel(state:Int,text:String,code:String="")=ExpressItem("test","","快递","TEST123456",state=state,latestText=text,pickupCode=code,latestTime=System.currentTimeMillis().toString())
    @Test fun signedParcelDoesNotRemainPendingJustBecauseTraceMentionsStation() {
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(parcel(3,"已签收，放在菜鸟驿站")))
    }
    @Test fun refundedAndReturnedParcelsDoNotRemainPendingWithOldPickupCode() {
        assertNotEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(parcel(4,"包裹已退回发件人，原快递柜", "123456")))
        assertNotEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(parcel(3,"订单退款成功，驿站包裹退回", "123456")))
    }
    @Test fun platformSignedParcelAppearsInRecentWithoutLocalCollectionAction() {
        assertEquals(1,ParcelPresentation.recent(listOf(parcel(3,"已签收")),emptyMap()).size)
    }
}
