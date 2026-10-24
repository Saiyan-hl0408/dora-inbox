package com.halo.expressassistant.hub
import org.junit.Assert.*
import org.junit.Test
class BackgroundSyncPolicyTest {
    @Test fun recentBackgroundSuccessPreventsForegroundRepeat(){assertFalse(BackgroundSyncPolicy.due(1_000_000,990_000,980_000,15))}
    @Test fun freshColdStartDoesNotBypassInterval(){assertFalse(BackgroundSyncPolicy.due(1_000_000,990_000,980_000,15))}
    @Test fun selectedIntervalsReallyChangeDueTime(){for(m in listOf(15,30,60)){val last=1_000_000L;assertFalse(BackgroundSyncPolicy.due(last+m*60_000L-1,last,last,m));assertTrue(BackgroundSyncPolicy.due(last+m*60_000L,last,last,m))}}
    @Test fun failedRunHasBoundedRetryWithoutClaimingSuccess(){assertFalse(BackgroundSyncPolicy.due(1_000_000,1,999_000,15));assertTrue(BackgroundSyncPolicy.due(1_600_000,1,999_000,15))}
    @Test fun firstRunIsDueAndInvalidOptionsUse15(){assertTrue(BackgroundSyncPolicy.due(1_000_000,0,0,15));assertEquals(15,BackgroundSyncPolicy.interval(120));assertEquals(60,BackgroundSyncPolicy.interval(60))}
}
