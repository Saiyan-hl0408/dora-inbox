// Synthetic phone-shaped rejection fixtures only; not an account or contact.
package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class PickupPayloadTest {
    @Test fun cainiaoOfficialDepotCardExposesDeliveryCodeSeparatelyFromTraces() {
        assertEquals("8-1-3026",PickupPayload.code("""{"mailNo":"TEST123456","serviceProvider":{"type":"2","providerName":"测试驿站","deliveryCode":"8-1-3026"},"fullTraceDetail":[{"desc":"快递已签收"}]}""","TEST123456"))
        assertNull(PickupPayload.code("""{"mailNo":"TEST123456","serviceProvider":{"type":"1","deliveryCode":"123456"}}""","TEST123456"))
    }
    @Test fun explicitFieldDoesNotRequireATraceDescription() {
        assertEquals("2-3-4567",PickupPayload.code("""{"mailNo":"TEST123456","lastMile":{"pickupCode":"2-3-4567"}}""","TEST123456"))
    }
    @Test fun rejectsOtherParcelsOrderIdsPhonesAndMaskedCodes() {
        assertNull(PickupPayload.code("""{"mailNo":"OTHER12345","pickupCode":"654321"}""","TEST123456"))
        assertNull(PickupPayload.code("""{"orderId":"654321","packageItems":[{"desc":"取件码123456"}]}""","TEST123456"))
        assertNull(PickupPayload.code("""{"pickupCode":"19900000000"}""","TEST123456"))
        assertNull(PickupPayload.code("""{"pickupCode":"12****"}""","TEST123456"))
    }
    @Test fun traceFallbackAndMissingFieldAreHonest() {
        assertEquals("A-1068",PickupPayload.code("""{"fullTraceDetail":[{"desc":"自提码A-1068"}]}""","TEST123456"))
        assertNull(PickupPayload.code("""{"fullTraceDetail":[{"desc":"已签收"}]}""","TEST123456"))
    }
}
