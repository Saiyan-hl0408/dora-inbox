// Synthetic phone-shaped rejection fixtures only; not an account or contact.
package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class PddIdentityPayloadTest {
    private fun raw(ttl:String="603000",value:String="DEMO20261024ACCOUNTATEST000001")="""{"success":true,"result":{"identification_code":"$value","expire_mills":$ttl,"mobile_info":{"mobile_info_desc":"已绑定常用手机号：138****0000","link_url":"https://example.invalid"}}}"""
    @Test fun readsOriginalIdentityValueAndServerRemainingLifetime() {
        val result=PddIdentityPayload.read(raw())
        assertEquals(PddIdentityPayload.Outcome.SUCCESS,result.outcome)
        assertEquals("DEMO20261024ACCOUNTATEST000001",result.value)
        assertEquals(603000L,result.lifetimeMs)
        assertEquals("138****0000",result.mobileLabel)
    }
    @Test fun repeatedRequestsNeverInventANewTenMinuteLifetime() {
        val result=PddIdentityPayload.read(raw("15000"))
        val deadline=PddIdentityPayload.deadline(100000,result.lifetimeMs)
        assertEquals(113000L,deadline)
        assertTrue(PddIdentityPayload.valid(deadline,112999))
        assertFalse(PddIdentityPayload.valid(deadline,113000))
    }
    @Test fun ignoresUnmaskedPhoneAndNeverFollowsAnOfficialResponseLink() {
        val result=PddIdentityPayload.read(raw().replace("138****0000","19900000000"))
        assertEquals("",result.mobileLabel)
    }
    @Test fun rejectsMissingExpiredAndUnreasonableLifetime() {
        for(ttl in listOf("null","0","-1","9999999999"))assertEquals(PddIdentityPayload.Outcome.FAILED,PddIdentityPayload.read(raw(ttl)).outcome)
        for(ttl in listOf("1","1500","2000")) {
            val expiring=PddIdentityPayload.read(raw(ttl))
            assertEquals(PddIdentityPayload.Outcome.EXPIRING,expiring.outcome)
            assertTrue(expiring.value.isBlank())
        }
    }
    @Test fun rejectsEmptyAndUnsupportedBarcodeValues() {
        for(code in listOf("","中文身份码","A".repeat(257)))assertEquals(PddIdentityPayload.Outcome.FAILED,PddIdentityPayload.read(raw(value=code)).outcome)
    }
    @Test fun recognizesLoginFailureAndRejectsMalformedSuccess() {
        assertEquals(PddIdentityPayload.Outcome.LOGIN,PddIdentityPayload.read("""{"success":false,"error_code":40001}""").outcome)
        for(body in listOf("invalid","{}","""{"success":true}""","""{"success":false,"result":{}}"""))assertEquals(PddIdentityPayload.Outcome.FAILED,PddIdentityPayload.read(body).outcome)
    }
}
