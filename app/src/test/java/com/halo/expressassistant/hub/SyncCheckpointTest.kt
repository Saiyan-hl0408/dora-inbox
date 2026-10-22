package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class SyncCheckpointTest {
    private fun order(id:String,at:Long)=ExpressItem(id,"","",id,orderCreatedAt=at)
    @Test fun knownBoundaryStopsButSameTimeNewOrderDoesNot() {
        val old=order("old",100)
        val checkpoint=SyncCheckpoint(listOf(old))
        assertTrue(checkpoint.reached(listOf(old)))
        assertFalse(checkpoint.reached(listOf(old,order("new",100))))
        assertFalse(checkpoint.reached(emptyList()))
    }
    @Test fun firstImportAndExplicitHistoryDoNotUseAnIncrementalBoundary() {
        val old=order("old",100)
        assertFalse(SyncCheckpoint(emptyList()).reached(listOf(old)))
        assertFalse(SyncCheckpoint(listOf(old),true).reached(listOf(old)))
    }
    @Test fun statusChangesAtTheBoundaryStillGetMerged() {
        val old=order("old",100).copy(state=5)
        val update=old.copy(state=3)
        assertTrue(SyncCheckpoint(listOf(old)).reached(listOf(update)))
        assertEquals(3,update.state)
    }
}
