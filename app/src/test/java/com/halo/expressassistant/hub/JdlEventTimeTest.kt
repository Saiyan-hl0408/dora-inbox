package com.halo.expressassistant.hub

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class JdlEventTimeTest {
    private fun order(code:Int)=Json.parseToJsonElement("""{"baseOrderInfo":{"customOrderNo":"SYNTH0000001","receivedTime":"2026-10-01 12:00:00","lastOperatorTime":"2026-10-08 18:00:00","cusBaseInfo":{"orderStatus":$code}}}""").jsonObject
    @Test fun laterAdministrativeTraceDoesNotChangeSigningDate() {
        val trace=Json.parseToJsonElement("""[{"waybillTraceList":[{"descr":"已送达","operateTime":"2026-10-06 10:00:00"},{"descr":"服务评价提醒","operateTime":"2026-10-08 18:00:00"}]}]""").jsonArray
        assertEquals(ParcelTime.parse("2026-10-06 10:00:00"),JdlPayload.parse(order(4),"root","test",trace=trace).completedAt)
    }
    @Test fun pendingSortUsesArrivalAndTransitSortUsesOrderDate() {
        val trace=Json.parseToJsonElement("""[{"waybillTraceList":[{"descr":"待自提，请上门提货","operateTime":"2026-10-02 10:00:00"},{"descr":"待自提提醒已发送","operateTime":"2026-10-08 18:00:00"}]}]""").jsonArray
        val pending=JdlPayload.parse(order(9),"root","test",trace=trace)
        assertEquals(ParcelTime.parse("2026-10-02 10:00:00"),ParcelPresentation.eventTime(pending))
        assertEquals(ParcelTime.parse("2026-10-01 12:00:00"),ParcelPresentation.eventTime(JdlPayload.parse(order(2),"root","test")))
    }
    @Test fun calendarThreeDaysIncludesTheWholeSixthDay() {
        val now=ParcelTime.parse("2026-10-08 22:00:00")
        assertEquals(ParcelTime.parse("2026-10-06 00:00:00"),SyncWindow(3,now).since)
    }
    @Test fun unsignedAdministrativeDateIsNotUsedAsConfirmedSigningTime() {
        val item=JdlPayload.parse(order(4),"root","test").copy(completedAt=0,latestTime="2026-10-08 18:00:00")
        assertEquals(0L,ParcelPresentation.eventTime(item))
    }
    @Test fun pendingStationAddressDoesNotFallBackToRecipientsHome() {
        val value=buildJsonObject {
            order(9).forEach {put(it.key,it.value)}
            put("consigneeInfo",buildJsonObject {put("addressInfo",buildJsonObject {put("address","收件人家庭地址")})})
        }
        assertEquals("",JdlPayload.parse(value,"root","test").pickupAddress)
    }
}
