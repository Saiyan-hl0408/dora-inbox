package com.halo.expressassistant.hub

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class JdlPayloadTest {
    private fun order(status:Int=9,receiveCode:String="TEST-01")=Json.parseToJsonElement("""{
      "baseOrderInfo":{"customOrderNo":"JDXTEST000001","receivedTime":1791388800000,"cusBaseInfo":{"orderStatus":$status,"deliveryEndTime":1893456000000}},
      "shipmentInfo":{"receiveCode":"$receiveCode","pickupCode":"SENDER-ONLY","endStation":{"stationName":"测试校园服务站","addressInfo":{"address":"测试路一号"}}},
      "consigneeInfo":{"consigneeMobile":"138****1234","addressInfo":{"address":"收件地址"}},
      "extendProps":{"signVerifyCode":"SIGN-ONLY"},"cargoInfos":[{"cargoName":"测试包裹"}]
    }""").jsonObject
    private fun pickupTrace()=Json.parseToJsonElement("""[{"waybillTraceList":[{"descr":"您的快件已到达【测试校园服务站】，请凭取件码及时取件。","operateTime":"2026-10-08 09:56:00"}]}]""").jsonArray
    @Test fun receiverCodeAndStationComeFromReceiverFields() {
        val item=JdlPayload.parse(order(),"root","账号","9999",pickupTrace())
        assertEquals("TEST-01",item.pickupCode)
        assertEquals("1234",item.recipientPhoneTail)
        assertEquals("测试校园服务站",item.pickupStation)
        assertEquals("测试路一号",item.pickupAddress)
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(item))
        assertTrue(item.queryChannel.contains("deliveryId=JDXTEST000001"))
    }
    @Test fun senderAndSignatureCodesNeverBecomeReceiverPickupCodes() {
        assertEquals("",JdlPayload.parse(order(receiveCode=""),"root","账号").pickupCode)
    }
    @Test fun historicalCodeDoesNotMakeDeliveredParcelPending() {
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(JdlPayload.parse(order(4),"root","账号")))
    }
    @Test fun unknownOfficialStatusDoesNotBecomeTransitFromAnOldCode() {
        assertEquals(ParcelPresentation.Stage.UNVERIFIED,ParcelPresentation.stage(JdlPayload.parse(order(999),"root","账号")))
    }
    @Test fun deliveryPromiseIsNotAnArrivalOrCompletionTimestamp() {
        val item=JdlPayload.parse(order(4),"root","账号")
        assertEquals(0L,item.completedAt);assertEquals(0L,item.arrivedAt);assertEquals("",item.latestTime)
    }
    @Test fun usesLatestVisibleTraceEvenIfGroupsAreNotChronological() {
        val traces=Json.parseToJsonElement("""[{"waybillTraceList":[
          {"descr":"待自提","operateTime":"2026-10-07 12:00:00"},
          {"descr":"已送达","operateTime":"2026-10-08 12:00:00"},
          {"descr":"隐藏内容","operateTime":"2026-10-09 12:00:00","hideStatus":1}
        ]}]""").jsonArray
        val item=JdlPayload.parse(order(4),"root","账号",trace=traces)
        assertTrue(item.latestText.contains("已送达"));assertFalse(item.latestText.contains("待自提"))
        assertEquals(ParcelTime.parse("2026-10-08 12:00:00"),item.completedAt)
    }
    @Test fun copiedInformationIncludesLocationAndCodeWithoutAccountOrPhone() {
        val text=PickupCardInfo.copyText(JdlPayload.parse(order(),"private-root","private-label",trace=pickupTrace()))
        assertTrue(text.contains("测试校园服务站"));assertTrue(text.contains("测试路一号"));assertTrue(text.contains("TEST-01"))
        assertFalse(text.contains("138"));assertFalse(text.contains("private-"))
    }
    @Test fun officialLastOperatorTimestampIsOnlyStatusUpdateWhenTraceIsUnavailable() {
        val original=order(4)
        val base=original.getValue("baseOrderInfo").jsonObject+ ("lastOperatorTime" to JsonPrimitive(1791388800000L))
        val item=JdlPayload.parse(JsonObject(original+("baseOrderInfo" to JsonObject(base))),"root","账号")
        assertEquals(0L,item.completedAt)
        assertEquals(1791388800000L,item.statusUpdatedAt)
    }
}
