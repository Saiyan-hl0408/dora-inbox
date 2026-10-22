package com.halo.expressassistant.hub

import kotlinx.serialization.json.*

/** Defensive reader for explicitly named pickup fields; never infer a code from order IDs. */
object PickupPayload {
    private val codeNames=setOf("pickupcode","pickcode","takecode","selfpickupcode")
    fun code(raw:String,waybill:String):String? {
        val element=runCatching {Json.parseToJsonElement(raw)}.getOrNull() ?: return null
        fun walk(value:JsonElement,depth:Int):String? {
            if(depth>12)return null
            when(value) {
                is JsonObject -> {
                    val ownMail=(value["mailNo"] as? JsonPrimitive)?.contentOrNull ?: (value["waybillNo"] as? JsonPrimitive)?.contentOrNull
                    if(!ownMail.isNullOrBlank() && !ownMail.equals(waybill,true))return null
                    // Verified in Cainiao's official express-detail.js: depot/locker card types 2/3.
                    val provider=value["serviceProvider"] as? JsonObject
                    if(provider!=null && (provider["type"] as? JsonPrimitive)?.content in setOf("2","3")) {
                        val candidate=(provider["deliveryCode"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                        val parsed=PickupText.parse("取件码：$candidate").codes.singleOrNull()
                        if(candidate.isNotBlank() && parsed==candidate && !candidate.equals(waybill,true))return candidate
                    }
                    for((key,child) in value)if(key.lowercase() in codeNames) {
                        val candidate=(child as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                        if(candidate.isNotBlank()) {
                            val parsed=PickupText.parse("取件码：$candidate").codes.singleOrNull()
                            if(parsed==candidate && !candidate.equals(waybill,true))return candidate
                        }
                    }
                    // Product descriptions can contain example codes; only parse actual trace text.
                    val traces=value["fullTraceDetail"] as? JsonArray
                    if(traces!=null)for(trace in traces) {
                        val text=((trace as? JsonObject)?.get("desc") as? JsonPrimitive)?.contentOrNull.orEmpty()
                        PickupText.parse(text).codes.takeIf {it.isNotEmpty()}?.let {return it.joinToString("、")}
                    }
                    for((key,child) in value)if(key !in setOf("packageItems","goods","items"))walk(child,depth+1)?.let {return it}
                }
                is JsonArray -> for(child in value)walk(child,depth+1)?.let {return it}
                else -> Unit
            }
            return null
        }
        return walk(element,0)
    }
}
