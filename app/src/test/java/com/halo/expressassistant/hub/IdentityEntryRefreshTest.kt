package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class IdentityEntryRefreshTest {
    @Test fun firstAuthorizedPageRefreshesExactlyOnce() {
        val gate = IdentityEntryRefresh()
        assertTrue(gate.schedule("official-code", true))
        assertTrue(gate.isPending("official-code"))
        assertFalse(gate.schedule("official-code", true))
        assertTrue(gate.take("official-code", true))
        assertFalse(gate.take("official-code", true))
        assertFalse(gate.schedule("official-code", true))
    }

    @Test fun loginCannotScheduleButAuthorizedLandingCan() {
        val gate = IdentityEntryRefresh()
        assertFalse(gate.schedule("official-login", false))
        assertTrue(gate.schedule("official-code", true))
    }

    @Test fun changedOrRemovedOwnerCannotConsumeQueuedRetry() {
        val gate = IdentityEntryRefresh()
        assertTrue(gate.schedule("official-code", true))
        assertFalse(gate.take("official-code", false))
        gate.close()
        assertFalse(gate.schedule("official-code", true))
    }

    @Test fun leavingOrDestroyingPageDiscardsRetry() {
        val gate = IdentityEntryRefresh()
        assertTrue(gate.schedule("official-code", true))
        gate.close()
        assertFalse(gate.take("official-code", true))
        assertFalse(gate.isPending("official-code"))
    }

    @Test fun navigationCancelsOldRetryWithoutConsumingEntryBudget() {
        val gate = IdentityEntryRefresh()
        assertTrue(gate.schedule("old-official-page", true))
        gate.cancelPending()
        assertTrue(gate.schedule("authorized-code-page", true))
        assertFalse(gate.take("old-official-page", true))
        assertTrue(gate.take("authorized-code-page", true))
    }
}
