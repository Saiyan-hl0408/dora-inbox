package com.halo.expressassistant.hub

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PddCapturedResponseTest {
    @Test fun searchRefreshAndOtherTabsCannotCompleteTheCurrentList() {
        fun entry(type:String)=buildJsonObject {put("u","https://mobile.yangkeduo.com/proxy/api/aristotle/order_list_v4");put("b","{\"orders\":[]}");put("r",buildJsonObject {put("type",type);put("page",1);put("size",10)}.toString())}
        val pages=PddCapturedResponse.decodePages(JsonArray(listOf(entry("all"),entry("unreceived"),entry("search"),entry("refresh"))).toString())
        assertEquals(listOf(true,false,false,false),pages.map {it.matchesPhase(false)})
        assertEquals(listOf(false,true,false,false),pages.map {it.matchesPhase(true)})
        assertTrue(pages.all {it.validOrders})
    }
    @Test fun missingOrderArrayOrUnknownRequestIsNotAnEmptyPage() {
        val pages=PddCapturedResponse.decodePages("""[{"u":"/proxy/api/order_list_v4","b":"{\"error_code\":400}","r":"{\"type\":\"all\"}"},{"u":"/proxy/api/order_list_v4","b":"{\"orders\":[]}"}]""")
        assertFalse(pages[0].validOrders)
        assertFalse(pages[1].matchesPhase(false))
    }
    @Test fun nestedBodiesKeepQuotesNewlinesAndEscapedUrlsExactly() {
        val body=buildJsonObject {put("order_sn","SYNTHETIC");put("goods_name","带\"引号\"\n和\\斜线");put("url","https://example.invalid/image")}.toString()
        val array=JsonArray(listOf(buildJsonObject {put("u","https://mobile.yangkeduo.com/proxy/api/order_list_v4");put("b",body)}))
        val raw=JsonPrimitive(array.toString()).toString()
        val decoded=PddCapturedResponse.decode(raw)
        assertEquals(body,decoded.single().second)
        assertEquals("SYNTHETIC",Json.parseToJsonElement(decoded.single().second).jsonObject.getValue("order_sn").jsonPrimitive.content)
    }
    @Test fun nullEmptyOrMalformedCapturesNeverBecomeCompletedPages() {
        assertTrue(PddCapturedResponse.decode("null").isEmpty())
        assertTrue(PddCapturedResponse.decode("\"\"").isEmpty())
        assertTrue(PddCapturedResponse.decode("invalid").isEmpty())
    }
}
