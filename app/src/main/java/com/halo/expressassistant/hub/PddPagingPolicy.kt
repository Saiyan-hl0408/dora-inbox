package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import kotlinx.serialization.json.*

object PddPagingPolicy {
    /** List order is purchase-time order. Signature-time filtering belongs to history display. */
    fun listPhaseBoundary(page:List<ExpressItem>,since:Long,activePhase:Boolean,checkpointReached:Boolean):Boolean=
        (page.isNotEmpty() && page.all {it.orderCreatedAt in 1 until since}) || (!activePhase && checkpointReached)
    fun requestedSince(days:Int,now:Long,last:Long=0,expanding:Boolean=false):Long {
        val cutoff=ParcelTime.cutoff(HistoryRangePolicy.clamp(days),now)
        return if(last>0 && !expanding)maxOf(cutoff,last) else cutoff
    }
    fun hasMore(raw:String):Boolean? {
        val root=runCatching {Json.parseToJsonElement(raw) as? JsonObject}.getOrNull() ?: return null
        fun read(obj:JsonObject,depth:Int):Boolean? {
            if(depth>3)return null
            for(key in listOf("has_more","hasMore")) {
                val value=(obj[key] as? JsonPrimitive)?.contentOrNull
                if(value in setOf("false","0"))return false
                if(value in setOf("true","1"))return true
            }
            for(key in listOf("data","result","result_data"))(obj[key] as? JsonObject)?.let {read(it,depth+1)}?.let {return it}
            return null
        }
        return read(root,0)
    }
    fun refreshInScope(item:ExpressItem,since:Long,verifiedActiveTrace:Boolean,freshAwaitingReceipt:Boolean=false):Boolean {
        if(item.source!="pdd")return false
        if(item.pddReadRepairRequired)return true
        val stage=ParcelPresentation.stage(item)
        if(ActiveParcelRefresh.includes(item))return true
        val recent=maxOf(item.orderCreatedAt,ParcelTime.parse(item.latestTime))>=since
        // A list checkpoint covers purchases, not later movement of an existing parcel.
        if(stage in setOf(ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.TRANSIT) && item.logisticsStatus.isNotBlank())return true
        if(freshAwaitingReceipt && (stage==ParcelPresentation.Stage.UNVERIFIED || (stage==ParcelPresentation.Stage.CLOSED && item.completedAt==0L)))return true
        if(item.logisticsStatus=="待取件" || item.logisticsStatus=="等待取件")return true
        if(stage==ParcelPresentation.Stage.CLOSED && (recent || item.completedAt>=since || item.orderCompletedAt>=since))
            return item.logisticsVersion==0 || item.completedAt==0L || item.companyName.isBlank() || item.pickupStation.isBlank()
        return (stage in setOf(ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.TRANSIT) && (recent || verifiedActiveTrace)) ||
            (stage==ParcelPresentation.Stage.UNVERIFIED && recent)
    }
    /** Order creation is not a valid cutoff for parcels signed later. */
    fun historyBoundary(page:List<ExpressItem>,since:Long):Boolean=page.isNotEmpty() && page.all {
        val stage=ParcelPresentation.stage(it)
        when(stage) {
            ParcelPresentation.Stage.CLOSED -> (it.completedAt.takeIf {at->at>0} ?: it.orderCompletedAt) in 1 until since
            ParcelPresentation.Stage.AFTERSALE,ParcelPresentation.Stage.NOT_SHIPPED -> it.orderCreatedAt in 1 until since
            else -> false
        }
    }
}
