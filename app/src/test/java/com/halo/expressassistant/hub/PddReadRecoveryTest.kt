package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class PddReadRecoveryTest {
    private fun item(id:String,number:String)=ExpressItem(id,"","","$number",source="pdd",accountId="account",queryChannel=id,orderStatus="待收货")
    @Test fun twoOrdersCorruptedToOneNumberBothSurviveRestore() {
        val a=item("A","NO00000001");val b=item("B","NO00000001")
        val fresh=listOf(a,b.copy(mailNo="NO00000002"))
        val result=SyncOrderMerge.combine(listOf(a,b),fresh,fresh)
        assertEquals(2,result.size);assertEquals(2,result.map {it.mailNo}.toSet().size)
        assertEquals(setOf("A","B"),result.map {it.id}.toSet())
    }
    @Test fun unreturnedOrderIsNotLostToAnotherOrdersCorrectedWaybill() {
        val a=item("A","NO00000001");val b=item("B","NO00000001")
        val result=SyncOrderMerge.combine(listOf(a,b),listOf(a,b),listOf(a))
        assertEquals(setOf("A","B"),result.map {it.id}.toSet())
    }
    @Test fun commitMergesPhysicalStateByOrderNotCorruptedWaybill() {
        val a=item("A","NO00000001").copy(pickupStation="A-station")
        val b=item("B","NO00000001").copy(pickupStation="B-station")
        val result=SyncCacheMerge.withLatest(listOf(a,b),listOf(a.copy(pickupStation=""),b.copy(mailNo="NO00000002",pickupStation="")))
        assertEquals("A-station",result.first {it.id=="A"}.pickupStation)
        assertEquals("B-station",result.first {it.id=="B"}.pickupStation)
    }
    @Test fun resetDiscardsUnverifiedPhysicalFieldsAndPreservesOrderAndAccount() {
        val previous=item("A","NO00000001").copy(logisticsVersion=1,logisticsStatus="已签收",latestText="wrong trace",completedAt=100,
            pickupStation="wrong station",tracked=true)
        val reset=PddReadRecoveryPolicy.reset(previous)
        assertEquals(previous.id,reset.id);assertEquals(previous.accountId,reset.accountId);assertEquals(previous.mailNo,reset.mailNo)
        assertTrue(reset.tracked);assertTrue(reset.pddReadRepairRequired)
        assertEquals(0L,reset.completedAt);assertEquals("",reset.logisticsStatus);assertEquals("",reset.pickupStation)
        assertTrue(PddPagingPolicy.refreshInScope(reset,Long.MAX_VALUE,false))
        assertTrue(PddDetailQueryPolicy.normalSelection(listOf(reset)).contains(reset))
    }
    @Test fun otherPlatformCachesAreUntouched() {
        val jd=item("A","NO00000001").copy(source="jd",completedAt=123,logisticsStatus="已签收")
        assertSame(jd,PddReadRecoveryPolicy.reset(jd))
    }
    @Test fun successfulVerifiedReadClearsRepairButListAloneDoesNot() {
        val old=item("A","NO00000001").copy(pddReadRepairRequired=true)
        assertTrue(SyncCacheMerge.merge(old,old.copy(pddReadRepairRequired=false)).pddReadRepairRequired)
        assertFalse(SyncCacheMerge.merge(old,old.copy(pddReadRepairRequired=false,pddReadIntegrityVersion=1)).pddReadRepairRequired)
    }
}
