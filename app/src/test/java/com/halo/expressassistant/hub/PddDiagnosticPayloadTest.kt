// Synthetic phone-shaped rejection fixtures only; not an account or contact.
package com.halo.expressassistant.hub

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PddDiagnosticPayloadTest {
    @Test fun unknownStructureKeepsKeysAndStatusButNoPrivateValues() {
        val raw="""{"result_data":{"order_items":[{"order_sn":"1234567890123456789","goods_name":"secret product","mobile":"19900000000","address":"secret address","PDDAccessToken":"SYNTHETIC_DIAGNOSTIC_REDACTION_SENTINEL","logistics_status":"运输中，送往secret address","shipping_name":"邮政电商标快","status_code":17,"create_time":1792840000,"unknown_field":{"state":"abc"}}],"hasMore":true},"error_code":40001}"""
        val shape=PddDiagnosticPayload.schema(raw).toString()
        listOf("secret","1234567890123456789","19900000000").forEach {assertFalse(shape.contains(it))}
        listOf("result_data","order_items","unknown_field","运输中","hasMore","40001","1792840000").forEach {assertTrue(it,shape.contains(it))}
    }
    @Test fun urlsCredentialsAndPersonalErrorTextAreRedacted() {
        val input="cookie=abc access_token=def 手机号：19900000000\n收货地址：secret-address\nhttps://example.invalid/orders?uid=123456789012"
        val safe=PddDiagnosticPayload.cleanText(input)
        listOf("abc","def","19900000000","secret-address","example.invalid").forEach {assertFalse(it,safe.contains(it))}
        assertEquals("/orders",PddDiagnosticPayload.endpoint("https://example.invalid/orders?token=secret"))
        assertEquals(PddDiagnosticPayload.ref("order-a"),PddDiagnosticPayload.ref("order-a"))
        assertNotEquals(PddDiagnosticPayload.ref("order-a"),PddDiagnosticPayload.ref("order-b"))
        assertEquals(PddDiagnosticPayload.ref("order-a"),PddDiagnosticPayload.cleanText(PddDiagnosticPayload.ref("order-a")))
    }
    @Test fun largeUnknownArrayIsSampledAndNonJsonIsNeverCopied() {
        val raw=buildJsonObject {put("orders",JsonArray((1..1000).map {buildJsonObject {put("order_sn","PRIVATE-$it");put("status",3)}}))}.toString()
        val shape=PddDiagnosticPayload.schema(raw).toString()
        assertTrue(shape.contains("1000"));assertFalse(shape.contains("PRIVATE"));assertTrue(shape.length<2000)
        assertFalse(PddDiagnosticPayload.schema("<html>private account</html>").toString().contains("private"))
    }
    @Test fun failedRequestsCannotBeAcceptedAsEmptyCompletedOrders() {
        val raw="""[{"u":"/proxy/api/order_list_v4","b":"{}","http":0,"elapsed":12000,"event":"timeout","format":"string","r":"{\"type\":\"unreceived\",\"page\":3}"}]"""
        val page=PddCapturedResponse.decodePages(raw).single()
        assertFalse(page.matchesPhase(true));assertEquals("timeout",page.event);assertEquals(12000L,page.elapsedMs)
        assertEquals(3,page.page)
    }
}
