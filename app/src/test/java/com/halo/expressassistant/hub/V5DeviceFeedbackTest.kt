package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

/** Synthetic values only. Reproduces wording/status patterns reported on the device. */
class V5DeviceFeedbackTest {
    @Test fun awaitingSelfPickupIsPendingEvenWithoutAPickupCode() {
        val item=ExpressItem("synthetic-self-pickup","","京东快递","TESTJD123456",source="jd",state=0,
            orderStatus="待自提",stateName="待自提",latestText="待自提")
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(item))
    }
    @Test fun deliveredToPickupPointAndPleaseCollectIsPendingPickup() {
        val item=ExpressItem("synthetic","","京东快递","TESTJD123456",source="jd",state=1,stateNum=103,
            orderStatus="订单已出库",stateName="订单已出库",latestText="您的订单在京东【测试校园服务站】投递完成，请上门提货")
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(item))
    }
    @Test fun couponWithoutWaybillMustNotAppearAsInTransit() {
        val item=ExpressItem("synthetic-coupon","","","ORDER-JD-synthetic-coupon",source="jd",state=0,stateNum=105,
            orderStatus="等待处理",stateName="等待处理",latestText="赠品 限3天内点击领取支付券",isOrderNumber=true)
        assertNotEquals(ParcelPresentation.Stage.TRANSIT,ParcelPresentation.stage(item))
        assertNotEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(item))
    }
    @Test fun unidentifiedOldOrderMustNotBeInventedAsInTransit() {
        val item=ExpressItem("synthetic-old","","","ORDER-PDD-synthetic-old",source="pdd",state=0,stateNum=105,
            orderStatus="3",stateName="3",latestText="3",isOrderNumber=true,orderCreatedAt=ParcelTime.parse("2025-01-01"))
        assertNotEquals(ParcelPresentation.Stage.TRANSIT,ParcelPresentation.stage(item))
        assertNotEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(item))
    }
}
