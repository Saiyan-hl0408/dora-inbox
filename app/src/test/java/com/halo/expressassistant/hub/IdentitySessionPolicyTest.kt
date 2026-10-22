package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class IdentitySessionPolicyTest {
    @Test fun strictCookieOwnershipRejectsEmptyAndForeignPddAccounts() {
        assertFalse(IdentitySessionPolicy.matchesOwner("pdd", "", "pdd_user_id=one"))
        assertFalse(IdentitySessionPolicy.matchesOwner("pdd", "pdd_user_id=one", "pdd_user_id=two"))
        assertTrue(IdentitySessionPolicy.matchesOwner("pdd", "pdd_user_id=one; PDDAccessToken=old", "pdd_user_id=one; PDDAccessToken=new"))
        assertFalse(IdentitySessionPolicy.matchesOwner("jd", "", "pt_pin=one"))
    }
    @Test fun changedOrUnknownSessionHidesTheCodeAndCannotBePersisted() {
        val session = IdentitySessionPolicy("jd", "pt_pin=owner; pt_key=old")
        assertFalse(session.mayPersist)
        assertEquals(IdentitySessionPolicy.State.ACTIVE, session.check(true, "pt_pin=owner; pt_key=renewed"))
        assertTrue(session.mayPersist)
        assertEquals(IdentitySessionPolicy.State.ACCOUNT_CHANGED, session.check(true, "pt_pin=another"))
        assertFalse(session.mayPersist)
        // A late callback cannot resurrect a window invalidated by an account change.
        assertEquals(IdentitySessionPolicy.State.ACCOUNT_CHANGED, session.check(true, "pt_pin=owner"))
    }

    @Test fun pausedOrRemovedAccountInvalidatesAnOtherwiseMatchingSession() {
        val session = IdentitySessionPolicy("taobao", "unb=owner; cookie1=token")
        assertEquals(IdentitySessionPolicy.State.ACCOUNT_UNAVAILABLE, session.check(false, "unb=owner"))
        assertFalse(session.mayPersist)
    }

    @Test fun unrelatedLoginTokenDoesNotProveIdentity() {
        val session = IdentitySessionPolicy("jd", "pt_key=token")
        assertEquals(IdentitySessionPolicy.State.ACCOUNT_CHANGED, session.check(true, "pt_key=token"))
        assertFalse(session.mayPersist)
    }

    @Test fun officialTaobaoTokenRenewalKeepsTheSameOwner() {
        val session = IdentitySessionPolicy("taobao", "unb=owner; cookie1=old")
        assertEquals(IdentitySessionPolicy.State.ACTIVE, session.check(true, "unb=owner; cookie1=new"))
        assertTrue(session.mayPersist)
    }

    @Test fun pddIdentityNeverCreatesAnEmbeddedWebFallback() {
        assertFalse(IdentitySessionPolicy.allowsEmbeddedPage("pdd"))
        assertTrue(IdentitySessionPolicy.allowsEmbeddedPage("jd"))
        assertTrue(IdentitySessionPolicy.allowsEmbeddedPage("taobao"))
        assertFalse(IdentitySessionPolicy.allowsEmbeddedPage("unknown"))
    }
}
