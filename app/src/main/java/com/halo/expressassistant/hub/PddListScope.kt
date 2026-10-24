package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

/** The boundary page may contain old orders; do not import them as fresh history. */
class PddListScope(val since:Long,cached:List<ExpressItem>,val oldestCandidate:Long=since) {
    private val knownDates=cached.filter {it.orderCreatedAt>0}.associate {it.id to it.orderCreatedAt}
    fun dated(item:ExpressItem)=if(item.orderCreatedAt>0)item else item.copy(orderCreatedAt=knownDates[item.id] ?: 0L)
    private val activeIds=cached.filter {it.pddReadRepairRequired || ParcelPresentation.stage(it) in setOf(ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.TRANSIT)}.mapTo(HashSet()){it.id}
    private val recentSignedIds=cached.filter {it.completedAt>=since}.mapTo(HashSet()){it.id}
    fun retain(item:ExpressItem)=item.orderCreatedAt>=since || item.completedAt>=since || item.orderCompletedAt>=since || item.id in activeIds || item.id in recentSignedIds ||
        (item.orderCreatedAt>=oldestCandidate && item.orderStatus.contains("待确认收货") && ParcelPresentation.stage(item)==ParcelPresentation.Stage.CLOSED && item.completedAt==0L)
    fun select(items:List<ExpressItem>)=items.map(::dated).filter {it.orderCreatedAt==0L || retain(it)}
}
