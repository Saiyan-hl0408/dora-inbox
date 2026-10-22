package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class JdCookieIdentityTest {
    @Test fun aRenewedTokenDoesNotChangeWhoOwnsTheLiveSession() {
        assertTrue(CookiePairs.sameJdAccount(mapOf("pt_pin" to "synthetic","pt_key" to "renewed"),mapOf("pt_pin" to "synthetic","pt_key" to "old")))
    }
    @Test fun differentOrUnknownAccountsAreNotAssumedEquivalent() {
        assertFalse(CookiePairs.sameJdAccount(mapOf("pt_pin" to "one"),mapOf("pt_pin" to "two")))
        assertFalse(CookiePairs.sameJdAccount(mapOf("pt_key" to "token"),mapOf("pt_key" to "token")))
    }
}
