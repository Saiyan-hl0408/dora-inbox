package com.halo.expressassistant.data

import org.junit.Assert.*
import org.junit.Test

class PddAccountBindingTest {
    @Test fun renamedAccountStillReceivesItsRenewedCredentials() {
        val owner=BoundAccount("stable-id","我的拼多多",payload="pdduid=1001234; PDDAccessToken=old")
        assertEquals(owner,PddAccountBinding.findExisting(listOf(owner),"pdduid=1001234; PDDAccessToken=new"){it})
    }
    @Test fun sameTailOrSameDisplayNameNeverMergesDifferentAccounts() {
        val owner=BoundAccount("old","拼多多账号 · 1234",payload="pdduid=1001234; PDDAccessToken=old")
        assertNull(PddAccountBinding.findExisting(listOf(owner),"pdduid=2001234; PDDAccessToken=new"){it})
    }
}
