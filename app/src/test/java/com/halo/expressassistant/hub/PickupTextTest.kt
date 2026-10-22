// Synthetic phone-shaped rejection fixtures only; not an account or contact.
package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class PickupTextTest {
    @Test fun depotSynonymsAreRecognized() {
        assertEquals(listOf("A-1068"),PickupText.parse("包裹自提码：A-1068").codes)
        assertEquals(listOf("8-1-3026"),PickupText.parse("驿站码：8-1-3026").codes)
        assertEquals(listOf("123456"),PickupText.parse("快递取件验证码：123456").codes)
    }
    @Test fun shelfCodesAndEightDigitLockerCodesArePreserved() {
        assertEquals(listOf("6-4-3006"),PickupText.parse("取件码6-4-3006").codes)
        assertEquals(listOf("46976863"),PickupText.parse("快递柜取件码46976863").codes)
    }
    @Test fun separatesWaybillAndPickupCode() {
        val p=PickupText.parse("【京东】快递单号JD123456789012，已到星光驿站，取件码：2-3-4567")
        assertEquals("JD123456789012",p.waybill)
        assertEquals(listOf("2-3-4567"),p.codes)
        assertEquals("京东",p.platform)
    }
    @Test fun trackingNumberIsNotPickupCode() {
        val p=PickupText.parse("您的快递已发货，运单号：123456789012345，请留意物流")
        assertTrue(p.codes.isEmpty())
        assertEquals("123456789012345",p.waybill)
    }
    @Test fun otpAndPromotionAreNotPickupMessages() {
        assertFalse(PickupText.hasParcelContext("验证码为123456，五分钟内有效"))
        assertTrue(PickupText.parse("验证码123456，满300减50").codes.isEmpty())
    }
    @Test fun normalizesUnicodeDashesAndKeepsLetters() {
        assertEquals(listOf("A1-2-3456"),PickupText.parse("菜鸟取件码【A1－2—3456】").codes)
    }
    @Test fun rejectsMaskedCodesAndPhoneNumbers() {
        assertTrue(PickupText.parse("包裹取件码12****，请联系驿站").codes.isEmpty())
        assertTrue(PickupText.parse("凭19900000000取件").codes.isEmpty())
    }
    @Test fun noAddressIsStillUseful() {
        assertEquals(listOf("654321"),PickupText.parse("取件码654321").codes)
    }
    @Test fun multipleExplicitCodesArePreserved() {
        assertEquals(listOf("1-2-3456","2-3-4567"),PickupText.parse("包裹1取件码1-2-3456，包裹2取件码2-3-4567").codes)
    }
}
