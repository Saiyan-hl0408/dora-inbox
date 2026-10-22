package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class BindingSyncQueueTest {
    @Test fun latestRangeReplacesIntermediateChoicesWhileOldReadCleansUp() {
        val queue=BindingSyncQueue()
        queue.requestRange(30)
        assertNull(queue.takeNext(working=true,foreground=true))
        queue.requestRange(90)
        queue.requestRange(180)
        assertEquals(180,queue.takeNext(working=false,foreground=true)?.historyDays)
        assertNull(queue.takeNext(false,true))
    }
    @Test fun backgroundReturnKeepsRequestUntilForeground() {
        val queue=BindingSyncQueue()
        queue.requestManual("pdd")
        assertNull(queue.takeNext(false,false))
        val next=queue.takeNext(false,true)
        assertEquals(setOf("pdd"),next?.channels)
        assertTrue(next?.manual==true)
    }
    @Test fun explicitStopClearsRangeAndBindingRequestsTogether() {
        val queue=BindingSyncQueue()
        queue.request("taobao")
        queue.requestManual("jd")
        queue.requestRange(90)
        queue.clear()
        assertTrue(queue.pending().isEmpty())
        assertTrue(queue.pendingManual().isEmpty())
        assertNull(queue.pendingRange())
        assertNull(queue.takeNext(false,true))
    }
    @Test fun wholeRangeReadCoalescesAllPendingPlatformRequests() {
        val queue=BindingSyncQueue()
        queue.request("taobao")
        queue.requestManual("pdd")
        queue.requestRange(30)
        assertEquals(BindingSyncQueue.Request(historyDays=30),queue.takeNext(false,true))
        assertNull(queue.takeNext(false,true))
    }
    @Test fun cachedRangeSkipKeepsQueuedManualPlatformRead() {
        val queue=BindingSyncQueue()
        queue.requestManual("pdd")
        queue.requestRange(90)
        // The coordinator has coverage, so no full-range read replaces the PDD request.
        queue.discardRange()
        assertNull(queue.pendingRange())
        assertEquals(setOf("pdd"),queue.pendingManual())
        assertEquals(BindingSyncQueue.Request(channels=setOf("pdd"),manual=true),queue.takeNext(false,true))
        assertNull(queue.takeNext(false,true))
    }
}
