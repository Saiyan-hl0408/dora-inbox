package com.halo.expressassistant.hub

import android.content.Context
import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.Store
import com.halo.expressassistant.ui.PddTraceFetcher
import kotlinx.coroutines.*

/** Optional station enrichment runs only after all selected status reads have completed. */
object PddLocationSupplement {
    data class Request(val item:ExpressItem,val summary:PddDetailPayload.Snapshot)
    data class Report(val attempted:Int=0,val restored:Int=0,val deferred:Int=0)
    suspend fun run(context:Context,requests:List<Request>,automatic:Boolean,onProgress:(Int,Int)->Unit):Report {
        val unique=requests.distinctBy {"${it.item.accountId}|${it.item.id}"}
        val prefs=HubData.prefs(context)
        var attempted=0;var restored=0;var noLongerNeeded=0
        withTimeoutOrNull(40000L) {
            for(request in unique) {
                ensureActive()
                val current=Store.items(context).firstOrNull {it.id==request.item.id && it.accountId==request.item.accountId && it.source=="pdd"}
                if(current==null || !PddDetailQueryPolicy.missingLocation(current)){noLongerNeeded++;continue}
                val key="${current.accountId}_${current.id}"
                val same=prefs.getString("pdd_location_status_$key","")==PddDetailQueryPolicy.fingerprint(current)
                if(automatic && same && System.currentTimeMillis()<prefs.getLong("pdd_location_retry_$key",0))continue
                val account=Store.accountForItem(context,current) ?: continue
                attempted++;onProgress(attempted,unique.size)
                prefs.edit().putString("pdd_location_status_$key",PddDetailQueryPolicy.fingerprint(current))
                    .putLong("pdd_location_retry_$key",System.currentTimeMillis()+PddDetailQueryPolicy.RETRY_DELAY_MS).apply()
                val seed=request.summary.copy(points=PddOrderPagePayload.withHistory(Store.pddTraces(context,current.mailNo).orEmpty(),request.summary.points))
                val result=try {PddTraceFetcher.fetchMetadata(context,current,Store.cookieOf(account.payload),seedSnapshot=seed)}
                    catch(cancelled:CancellationException){throw cancelled}
                    catch(_:Exception){null}
                val ok=result!=null && result.station.isNotBlank()
                if(ok) {
                    // Re-read after suspension so an already-updated status is not replaced
                    // by the older parcel object captured when this optional read was queued.
                    val latest=Store.items(context).firstOrNull {it.id==current.id && it.accountId==current.accountId && it.source=="pdd"}
                    if(latest!=null) {
                        val points=PddOrderPagePayload.withHistory(result!!.points,Store.pddTraces(context,latest.mailNo).orEmpty())
                        ParcelMetadata.apply(context,latest,result.waybill,result.company,points,result)
                        restored++;prefs.edit().putLong("pdd_location_retry_$key",0).apply()
                    }
                }
                prefs.edit().putString("pdd_location_path_$key",if(ok)"物流页已补回地点" else "状态已更新；本次未补回地点").apply()
            }
        }
        return Report(attempted,restored,(unique.size-restored-noLongerNeeded).coerceAtLeast(0))
    }
}
