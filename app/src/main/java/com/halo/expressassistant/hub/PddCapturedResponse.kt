package com.halo.expressassistant.hub

import kotlinx.serialization.json.*

/** Decode the WebView JSON string once, then decode the capture array once.
 * Replacing escape sequences by hand corrupts nested JSON/escaped URLs. */
object PddCapturedResponse {
    data class Page(val url:String,val body:String,val type:String,val page:Int,val validOrders:Boolean,val orderCount:Int,
        val http:Int=0,val elapsedMs:Long=0,val event:String="response",val requestFormat:String="unknown",val requestBody:String="") {
        fun matchesPhase(active:Boolean)=validOrders && type==if(active)"unreceived" else "all"
    }
    fun decode(raw:String):List<Pair<String,String>> = decodePages(raw).map {it.url to it.body}
    fun decodePages(raw:String):List<Page> = runCatching {
        val outer=Json.parseToJsonElement(raw)
        val array=(if(outer is JsonPrimitive && outer.isString)Json.parseToJsonElement(outer.content) else outer) as? JsonArray ?: return@runCatching emptyList()
        array.mapNotNull {value->
            val obj=value as? JsonObject ?: return@mapNotNull null
            val url=(obj["u"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val body=(obj["b"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if(body.isBlank())return@mapNotNull null
            val request=runCatching {Json.parseToJsonElement((obj["r"] as? JsonPrimitive)?.contentOrNull.orEmpty()).jsonObject}.getOrNull()
            val type=(request?.get("type") as? JsonPrimitive)?.contentOrNull.orEmpty()
            val data=runCatching {Json.parseToJsonElement(body).jsonObject}.getOrNull()
            val orders=(data?.get("orders") as? JsonArray) ?: listOf("data","result").firstNotNullOfOrNull {key->(data?.get(key) as? JsonObject)?.get("orders") as? JsonArray}
            Page(url,body,type,(request?.get("page") as? JsonPrimitive)?.intOrNull ?: 0,orders!=null,orders?.size ?: 0,
                (obj["http"] as? JsonPrimitive)?.intOrNull ?: 0,(obj["elapsed"] as? JsonPrimitive)?.longOrNull ?: 0,
                (obj["event"] as? JsonPrimitive)?.contentOrNull ?: "response",(obj["format"] as? JsonPrimitive)?.contentOrNull ?: "unknown",(obj["r"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }
    }.getOrDefault(emptyList())
}
