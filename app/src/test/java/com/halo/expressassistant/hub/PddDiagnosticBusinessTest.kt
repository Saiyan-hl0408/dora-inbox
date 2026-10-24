// Synthetic phone-shaped rejection fixtures only; not an account or contact.
package com.halo.expressassistant.hub

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PddDiagnosticBusinessTest {
    @Test fun orderWaybillProductAndUnrecognizedStatusRemainOriginalValues() {
        val raw="""{"order_sn":"261022-123456789012345","tracking_number":"19900000000","goods_name":"测试充电器","status_prompt":"平台新增状态","created_at":1792850000}"""
        val result=PddDiagnosticBusiness.read(raw).jsonObject
        assertEquals("261022-123456789012345",result["order_sn"]!!.jsonPrimitive.content)
        assertEquals("19900000000",result["tracking_number"]!!.jsonPrimitive.content)
        assertEquals("测试充电器",result["goods_name"]!!.jsonPrimitive.content)
        assertEquals("平台新增状态",result["status_prompt"]!!.jsonPrimitive.content)
    }
    @Test fun credentialsAndRecipientDetailsAreRemovedAtAnyDepth() {
        val raw="""{"data":{"PDDAccessToken":"secret-value","cookie":"session=hidden","anti_content":"signed-secret","phone":"19900000000","shipping_address":{"name":"private-name","address":"private-address"}},"order_id":"ORDER-A"}"""
        val clean=PddDiagnosticBusiness.read(raw).toString()
        for(secret in listOf("secret-value","session=hidden","signed-secret","19900000000","private-name","private-address"))assertFalse(secret,clean.contains(secret))
        assertTrue(clean.contains("ORDER-A"));assertTrue(clean.contains("shipping_address"))
    }
    @Test fun publicPickupAddressAndUnknownNestedFieldsRemainAvailable() {
        val raw="""{"pickup_station":{"name":"学校驿站","address":"学校公共代收点","new_field":{"unknown_status":"custom-value"}}}"""
        val clean=PddDiagnosticBusiness.read(raw).toString()
        assertTrue(clean.contains("学校公共代收点"));assertTrue(clean.contains("custom-value"))
    }
    @Test fun businessDataDoesNotUseSchemaSamplingOrDepthTruncation() {
        val rows=buildJsonArray {repeat(100){add(buildJsonObject {put("order_id","ORDER-$it");put("new_field_$it",it)})}}
        val clean=PddDiagnosticBusiness.read(buildJsonObject {put("orders",rows)}.toString()).jsonObject["orders"]!!.jsonArray
        assertEquals(100,clean.size);assertEquals("ORDER-99",clean.last().jsonObject["order_id"]!!.jsonPrimitive.content)
    }
    @Test fun fullTraceDescriptionAndTimeSurviveWithContactPhoneRemoved() {
        val raw="""{"traces":[{"time":"2026-10-24 15:14:41","info":"包裹已入库中转仓，将尽快发出，请联系19900000000"}]}"""
        val clean=PddDiagnosticBusiness.read(raw).toString()
        assertTrue(clean.contains("2026-10-24 15:14:41"));assertTrue(clean.contains("包裹已入库中转仓，将尽快发出"));assertFalse(clean.contains("19900000000"))
    }
    @Test fun formRequestKeepsPagingAndOrderKeysWithoutSignature() {
        val clean=PddDiagnosticBusiness.read("type=unreceived&page=2&order_sn=ORDER-A&anti_content=secret-signature").toString()
        assertTrue(clean.contains("unreceived"));assertTrue(clean.contains("ORDER-A"));assertTrue(clean.contains("page"));assertFalse(clean.contains("secret-signature"))
    }
    @Test fun fieldTraceShowsTheExactSelectedDatePathAndInvalidPrimaryValue() {
        val raw="""{"data":{"orders":[{"order_sn":"ORDER-A","order_time":"-","create_time":1792850000,"order_status_prompt":"新结构状态"}]}}"""
        val row=PddDiagnosticBusiness.inputs(PddDiagnosticBusiness.read(raw)).single().jsonObject
        val created=row["createdAt"]!!.jsonObject
        assertEquals("$.data.orders[0].order_time",created["selectedPath"]!!.jsonPrimitive.content)
        assertEquals(0L,created["parsedTime"]!!.jsonPrimitive.long)
        assertTrue(created["candidates"]!!.jsonObject.containsKey("create_time"))
    }
    @Test fun fieldTraceRetainsAnUnknownAlternativeDateFieldForDiagnosis() {
        val raw="""{"orders":[{"orderSn":"ORDER-A","new_order_date":"2026-10-24 12:00:00"}]}"""
        val row=PddDiagnosticBusiness.inputs(PddDiagnosticBusiness.read(raw)).single().jsonObject
        assertEquals("",row["createdAt"]!!.jsonObject["selectedPath"]!!.jsonPrimitive.content)
        assertTrue(row["otherTimeStatusFields"]!!.jsonObject.containsKey("new_order_date"))
    }
}
