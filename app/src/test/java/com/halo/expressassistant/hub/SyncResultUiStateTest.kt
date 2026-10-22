package com.halo.expressassistant.hub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncResultUiStateTest {
    @Test fun zeroWithoutFreshBoundaryMustNotClaimEmptyRange() {
        assertFalse(SyncResultUiState(0,null,7,100,false,true).emptyVerified)
    }
    @Test fun completedReadWithCachedRecordsMustNotClaimEmptyRange() {
        assertFalse(SyncResultUiState(0,null,7,100,true,false).emptyVerified)
    }
    @Test fun partialFailureMustNotClaimEmptyRangeEvenWithOldCoverage() {
        assertFalse(SyncResultUiState(0,"request failed",7,100,true,true).emptyVerified)
    }
    @Test fun onlyFreshSuccessfulZeroWithEmptyBoundedCacheMayShowEmptyState() {
        assertTrue(SyncResultUiState(0,null,7,100,true,true).emptyVerified)
        assertFalse(SyncResultUiState(1,null,7,100,true,true).emptyVerified)
    }
}
