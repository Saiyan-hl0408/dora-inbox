package com.halo.expressassistant.hub

import android.content.Context
import com.halo.expressassistant.data.*

/** Reuse already saved, verified traces. This does not create another network request. */
object PddLocationRecovery {
    fun repair(item:ExpressItem,points:List<DetailPoint>):ExpressItem {
        if(item.source!="pdd")return item
        val station=PddPickupLocation.sanitized(item.pickupStation)
        val pddStation=PddPickupLocation.sanitized(item.pddPickupStation)
        if(station.isNotBlank() && pddStation==item.pddPickupStation)return item
        val location=if(item.pddReadIntegrityVersion>0 && station.isBlank() && pddStation.isBlank())PddPickupLocation.history(points) else ""
        return item.copy(pickupStation=station.ifBlank {pddStation.ifBlank {location}},pddPickupStation=pddStation.ifBlank {location},
            pickupLocationVersion=if(location.isNotBlank())1 else item.pickupLocationVersion)
    }
    fun prepare(context:Context) {
        synchronized(Store) {
            val before=Store.items(context)
            val after=before.map {item->if(item.source=="pdd" && (item.pickupStation.isBlank() || PddPickupLocation.isInstruction(item.pickupStation) || PddPickupLocation.isInstruction(item.pddPickupStation)))repair(item,Store.pddTraces(context,item.mailNo).orEmpty()) else item}
            if(before!=after)Store.saveItems(context,after)
        }
    }
}
