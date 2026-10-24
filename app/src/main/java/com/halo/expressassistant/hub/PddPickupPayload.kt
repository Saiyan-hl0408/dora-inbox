package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import kotlinx.serialization.json.*

/** Official station/package response. Pending groups and recent out records are separate facts. */
object PddPickupPayload {
    enum class Outcome { SUCCESS, LOGIN, FAILED }
    data class Parcel(val number:String,val state:String,val code:String="",val station:String="",val completedAt:Long=0)
    data class Snapshot(val outcome:Outcome,val parcels:List<Parcel> = emptyList(),val pendingCount:Int=0)
    private fun JsonObject.text(key:String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun read(raw:String):Snapshot {
        val root=runCatching {Json.parseToJsonElement(raw) as? JsonObject}.getOrNull() ?: return Snapshot(Outcome.FAILED)
        if(root.text("error_code")=="40001")return Snapshot(Outcome.LOGIN)
        if((root["success"] as? JsonPrimitive)?.booleanOrNull!=true)return Snapshot(Outcome.FAILED)
        val result=root["result"] as? JsonObject ?: return Snapshot(Outcome.FAILED)
        val parcels=linkedMapOf<String,Parcel>()
        fun number(obj:JsonObject)=obj.text("tracking_number").trim().uppercase().takeIf {it.matches(Regex("[A-Z0-9]{8,32}")) && !it.startsWith("ORDER-")}.orEmpty()
        for(group in (result["combine_list"] as? JsonArray).orEmpty()) {
            val station=group as? JsonObject ?: continue
            // station_delivery_track_list describes home delivery, not a current pickup-code list.
            for(value in (station["combine_track_list"] as? JsonArray).orEmpty()) {
                val parcel=value as? JsonObject ?: continue
                if(parcel.text("pickup_status") in setOf("1","2"))continue
                val no=number(parcel);if(no.isBlank())continue
                val code=parcel.text("pick_code").trim().removePrefix("取件码").trim().trimStart(':','：').trim()
                parcels[no]=Parcel(no,"PENDING",PddPickupCodes.validValue(code,no),station.text("station_name").ifBlank {station.text("address")})
            }
        }
        for(value in (result["package_out_list"] as? JsonArray).orEmpty()) {
            val parcel=value as? JsonObject ?: continue
            val no=number(parcel);if(no.isBlank())continue
            // A current pending record wins a conflicting recent-out row for a re-entered parcel.
            parcels.putIfAbsent(no,Parcel(no,"COLLECTED",station=parcel.text("address"),completedAt=ParcelTime.parse(parcel.text("operate_time"))))
        }
        return Snapshot(Outcome.SUCCESS,parcels.values.toList(),(result["count"] as? JsonPrimitive)?.intOrNull ?: 0)
    }
    /** Never infer collection from absence, an empty list, or a failed request. */
    fun apply(item:ExpressItem,snapshot:Snapshot,accountId:String,observedAt:Long):ExpressItem {
        if(snapshot.outcome!=Outcome.SUCCESS || item.source!="pdd" || item.accountId!=accountId || item.isOrderNumber || observedAt<item.pddPickupServiceAt)return item
        val parcel=snapshot.parcels.firstOrNull {it.number.equals(item.mailNo,true)} ?: return if(item.pddPickupServiceState=="PENDING") {
            // Missing is not collected. Retire the old pending override so fresh logistics can advance it.
            item.copy(pddPickupServiceState="",pddPickupServiceAt=observedAt)
        } else item
        return reconcile(item.copy(pddPickupServiceState=parcel.state,pddPickupServiceAt=observedAt,
            pddPickupServiceCompletedAt=parcel.completedAt,identityChannel="pdd",
            pddPickupServiceCode=parcel.code,pddPickupServiceStation=parcel.station,
            pddPickupCode=if(parcel.state=="PENDING")parcel.code else "",pddPickupCodeAt=observedAt,
            pddPickupStation=parcel.station.ifBlank {item.pddPickupStation},
            pickupStation=parcel.station.ifBlank {item.pickupStation},
            historicalPickupCode=if(parcel.state=="COLLECTED")item.pickupCode.ifBlank {item.historicalPickupCode} else item.historicalPickupCode))
    }
    /** Preserve direct service evidence while commerce rows and cached logistics are refreshed. */
    fun reconcile(item:ExpressItem):ExpressItem {
        if(item.source!="pdd" || item.pddPickupServiceAt<=0)return item
        return when(item.pddPickupServiceState) {
            "COLLECTED"->item.copy(logisticsStatus="已取件",stateName="已取件",state=3,stateNum=107,logisticsVersion=maxOf(1,item.logisticsVersion),
                completedAt=item.pddPickupServiceCompletedAt,statusUpdatedAt=item.pddPickupServiceCompletedAt,
                latestText="多多取快递：已取件",latestTime=if(item.pddPickupServiceCompletedAt>0)ParcelTime.format(item.pddPickupServiceCompletedAt) else "",
                pickupCode="",pickupCodeSource="",pddPickupCode="",identityChannel="pdd",pickupVerificationNeeded=false,
                pickupStation=item.pddPickupServiceStation.ifBlank {item.pickupStation},pddPickupStation=item.pddPickupServiceStation.ifBlank {item.pddPickupStation})
            "PENDING"->item.copy(logisticsStatus="待取件",stateName="待取件",state=0,stateNum=106,logisticsVersion=maxOf(1,item.logisticsVersion),completedAt=0,
                pickupCode=if(item.pickupCodeSource=="local")item.pickupCode else item.pddPickupServiceCode.ifBlank {if(item.pickupCodeSource==PddPickupCodes.SOURCE)"" else item.pickupCode},
                pickupCodeSource=if(item.pickupCodeSource=="local")"local" else if(item.pddPickupServiceCode.isNotBlank())PddPickupCodes.SOURCE else if(item.pickupCodeSource==PddPickupCodes.SOURCE)"" else item.pickupCodeSource,
                pddPickupCode=item.pddPickupServiceCode,pddPickupCodeAt=item.pddPickupServiceAt,
                pickupStation=item.pddPickupServiceStation.ifBlank {item.pickupStation},pddPickupStation=item.pddPickupServiceStation.ifBlank {item.pddPickupStation},
                identityChannel="pdd",pickupVerificationNeeded=if(item.pddPickupServiceCode.isNotBlank())false else item.pickupVerificationNeeded)
            else->item
        }
    }
}
