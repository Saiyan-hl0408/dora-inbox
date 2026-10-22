package com.halo.expressassistant.hub

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class JdlFailureTest {
    @Test fun listFailureAndTimeoutNoLongerCollapseIntoSessionAdvice() {
        val negative=JdlFailure.message(buildJsonObject {put("stage","list");put("reason","LIST_FAILED");put("detail","TOTAL_NEGATIVE")})
        val timeout=JdlFailure.message(buildJsonObject {put("stage","accounts");put("reason","REQUEST_TIMEOUT")})
        assertTrue(negative.contains("收件列表"));assertTrue(negative.contains("TOTAL_NEGATIVE"))
        assertTrue(timeout.contains("账号列表"));assertTrue(timeout.contains("请求超时"))
        assertNotEquals(negative,timeout)
    }
    @Test fun untrustedErrorContentNeverAppearsInDiagnostics() {
        val privateText="https://example.invalid/?token=SECRET&phone=12345678901"
        val message=JdlFailure.message(buildJsonObject {for(key in listOf("stage","reason","detail","kind","message"))put(key,privateText)})
        assertFalse(message.contains("SECRET"));assertFalse(message.contains("12345678901"));assertFalse(message.contains("https"))
        assertTrue(message.contains("JD/UNKNOWN/UNKNOWN"))
    }
    @Test fun transportMetadataIsRestrictedToStatusAndKnownResult() {
        val message=JdlFailure.message(buildJsonObject {put("stage","list");put("reason","LIST_FAILED");put("http",403);put("transport","returned")})
        assertTrue(message.contains("HTTP403/returned"))
        val unsafe=JdlFailure.message(buildJsonObject {put("stage","list");put("reason","LIST_FAILED");put("http",987654321);put("transport","SECRET")})
        assertFalse(unsafe.contains("987654321"));assertFalse(unsafe.contains("SECRET"))
    }
}
