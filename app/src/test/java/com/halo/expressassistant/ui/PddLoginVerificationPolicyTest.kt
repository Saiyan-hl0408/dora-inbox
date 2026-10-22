package com.halo.expressassistant.ui

import org.junit.Assert.*
import org.junit.Test

class PddLoginVerificationPolicyTest {
    @Test fun completedRecipientVerificationCanBeObservedInTheSameBinding() {
        assertTrue(PddLoginVerificationPolicy.canObserve("https://mobile.yangkeduo.com/verification.html"))
        assertTrue(PddLoginVerificationPolicy.canObserve("https://mobile.yangkeduo.com/psnl_verification.html?from=orders"))
    }
    @Test fun externalPickupAndUntrustedOriginsCannotProveMallAuthorization() {
        assertFalse(PddLoginVerificationPolicy.canObserve("https://m.pinduoduo.net/mdkd/package"))
        assertFalse(PddLoginVerificationPolicy.canObserve("https://mobile.yangkeduo.com.evil.test/verification.html"))
        assertFalse(PddLoginVerificationPolicy.canObserve("http://mobile.yangkeduo.com/login.html"))
    }
    @Test fun aSlowSafetyChallengeDoesNotExhaustAutomaticVerificationForever() {
        assertEquals(15000L,PddLoginVerificationPolicy.retryDelay(1))
        assertEquals(60000L,PddLoginVerificationPolicy.retryDelay(20))
    }
    @Test fun rejectedTicketBeforeExtraVerificationCannotPermanentlyBlockAValidLiveSession() {
        assertTrue(PddLoginVerificationPolicy.shouldVerify(true,"rejected",3000,false,false,true,false))
        assertTrue(PddLoginVerificationPolicy.shouldVerify(true,"network_error",4000,true,false,false,false))
        assertFalse(PddLoginVerificationPolicy.shouldVerify(false,"returned",10000,true,true,true,false))
        assertFalse(PddLoginVerificationPolicy.shouldVerify(true,"exchanging",10000,true,true,true,false))
    }
}
