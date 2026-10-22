package com.halo.expressassistant.data

import org.junit.Assert.*
import org.junit.Test

class AccountSelectionPolicyTest {
    private val first = BoundAccount("first", enabled = true)
    private val paused = BoundAccount("paused", enabled = false)
    private val second = BoundAccount("second", enabled = true)

    @Test fun ownedParcelNeverFallsBackWhenItsAccountIsRemovedOrPaused() {
        val accounts = listOf(first, paused, second)
        assertNull(AccountSelectionPolicy.forItem(accounts, "removed"))
        assertNull(AccountSelectionPolicy.forItem(accounts, "paused"))
        assertEquals(second, AccountSelectionPolicy.forItem(accounts, "second"))
    }

    @Test fun unownedLegacyParcelCanStillUseTheFirstEnabledAccount() {
        assertEquals(first, AccountSelectionPolicy.forItem(listOf(paused, first, second), ""))
        assertNull(AccountSelectionPolicy.forItem(listOf(paused), ""))
    }
}
