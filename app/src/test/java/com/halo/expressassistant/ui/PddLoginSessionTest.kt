package com.halo.expressassistant.ui

import org.junit.Assert.*
import org.junit.Test

class PddLoginSessionTest {
    @Test fun qrIdentityPairIsAcceptedWithoutSmsToken() {
        assertTrue(PddLoginSession.hasCredentials(mapOf("pdd_user_id" to "test-user","pdd_user_uin" to "test-uin")))
    }
    @Test fun identityAloneDoesNotAuthorizeAnAccount() {
        assertFalse(PddLoginSession.hasCredentials(mapOf("pdd_user_id" to "test-user")))
        assertFalse(PddLoginSession.hasCredentials(mapOf("pdd_user_uin" to "test-uin")))
        assertFalse(PddLoginSession.hasCredentials(mapOf("pdd_user_id" to "test-user","pdd_user_uin" to "")))
    }
    @Test fun smsTokenStillRequiresAnIdentity() {
        assertFalse(PddLoginSession.hasCredentials(mapOf("PDDAccessToken" to "test-token")))
        assertTrue(PddLoginSession.hasCredentials(mapOf("PDDAccessToken" to "test-token","pdduid" to "test-user")))
    }
}
