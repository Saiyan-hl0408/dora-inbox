package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class JdlQueryPolicyTest {
    private val now=2000000L
    @Test fun recentAutomaticSyncReadsLocalOnly() {
        assertTrue(JdlQueryPolicy.localOnly(true,false,false,now,now-1000,0))
        assertTrue(JdlQueryPolicy.localOnly(true,false,false,now,0,now-1000))
        assertFalse(JdlQueryPolicy.localOnly(true,false,false,now,now-6*60000,now-6*60000))
    }
    @Test fun manualRefreshBypassesCooldown() {
        assertFalse(JdlQueryPolicy.localOnly(false,false,false,now,now-1000,now-1000))
    }
    @Test fun explicitRangeAlwaysRereadsEvenPreviouslyCoveredDates() {
        assertFalse(JdlQueryPolicy.localOnly(false,true,false,now,now-1000,now-1000))
        assertFalse(JdlQueryPolicy.localOnly(true,true,true,now,now-1000,now-1000))
    }
    @Test fun firstRunOrBackwardClockDoesNotSuppressSync() {
        assertFalse(JdlQueryPolicy.localOnly(true,false,false,now,0,0))
        assertFalse(JdlQueryPolicy.localOnly(true,false,false,now,now+1000,now+1000))
    }
}
