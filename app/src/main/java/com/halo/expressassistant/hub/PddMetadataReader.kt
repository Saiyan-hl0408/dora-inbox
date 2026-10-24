package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Shared metadata decision, separated from Android WebView lifetime for replay tests. */
object PddMetadataReader {
    data class Result(val snapshot:PddDetailPayload.Snapshot,val summaryOnly:Boolean,val locationAttempted:Boolean=false,val locationPending:Boolean=false)
    suspend fun read(item:ExpressItem,cached:List<DetailPoint>,
        summary:suspend ()->PddDetailPayload.Snapshot?,detail:suspend ()->PddDetailPayload.Snapshot?,
        locationBudgetMs:Long=8000,allowLocationLookup:Boolean=false):Result? {
        val quick=summary() ?: return detail()?.let {Result(it,false)}
        val prior=PddPickupLocation.history(PddOrderPagePayload.withHistory(cached,quick.points))
        val known=quick.station.ifBlank {prior}.ifBlank {item.pickupStation.ifBlank {item.pddPickupStation}}
        val physical=PddLogistics.stage(PddLogistics.status(LatestLogistics.point(quick.points)?.context.orEmpty()))
        if(known.isNotBlank() || item.pickupAddress.isNotBlank() || physical !in setOf(ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.CLOSED))
            return Result(quick.copy(station=known),true)
        if(!allowLocationLookup)return Result(quick,true,locationPending=true)
        // A valid status-only response is not a complete location response. Only this
        // missing-location case opens the exact owned logistics page. Optional failure
        // must neither discard the successful status nor cancel the caller's job.
        val full=try {withTimeoutOrNull(locationBudgetMs){detail()}}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){null}
        if(full==null)return Result(quick,true,locationAttempted=true)
        if(quick.waybill.isNotBlank() && full.waybill.isNotBlank() && !quick.waybill.equals(full.waybill,true))
            return Result(quick,true,locationAttempted=true)
        val points=PddOrderPagePayload.withHistory(full.points,quick.points)
        return Result(quick.copy(points=points,
            waybill=quick.waybill.ifBlank {full.waybill},company=quick.company.ifBlank {full.company},
            station=full.station.ifBlank {PddPickupLocation.history(points)},
            deliveryAddress=full.deliveryAddress.ifBlank {quick.deliveryAddress},phoneTail=full.phoneTail.ifBlank {quick.phoneTail},
            hint=full.hint.ifBlank {quick.hint},pickupCode=full.pickupCode.ifBlank {quick.pickupCode}),false,locationAttempted=true)
    }
}
