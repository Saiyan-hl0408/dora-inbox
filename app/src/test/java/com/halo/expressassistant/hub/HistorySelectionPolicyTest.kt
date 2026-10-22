package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class HistorySelectionPolicyTest {
    @Test fun explicitHistoryChoiceWinsOverFormerIndependentSyncSetting() {
        assertEquals(90,HistorySelectionPolicy.resolve(90,7))
        assertEquals(30,HistorySelectionPolicy.resolve(30,180))
    }
    @Test fun installationsWithoutHistoryChoiceKeepTheirSyncChoice() {
        assertEquals(180,HistorySelectionPolicy.resolve(null,180))
        assertEquals(7,HistorySelectionPolicy.resolve(null,null))
    }
    @Test fun legacyUnlimitedMeansSupportedHalfYear() {
        assertEquals(180,HistorySelectionPolicy.resolve(0,30))
        assertEquals(180,HistorySelectionPolicy.resolve(null,999))
    }
    @Test fun cachedRangeDoesNotTriggerFullReadButMissingCoverageDoes() {
        assertFalse(HistorySelectionPolicy.select(30,false).readSelectedRange)
        assertTrue(HistorySelectionPolicy.select(90,true).readSelectedRange)
        assertEquals(180,HistorySelectionPolicy.select(999,true).days)
    }
}
