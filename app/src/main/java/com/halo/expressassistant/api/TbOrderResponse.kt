package com.halo.expressassistant.api

import com.halo.expressassistant.hub.TrackingFields
import kotlinx.serialization.json.*

/** A valid empty list is different from missing authorization or an unsupported response. */
object TbOrderResponse {
    data class Page(val orders:List<TbOrders.TbOrder>,val hasMore:Boolean?)
    class Failure(val reason:String):IllegalStateException(when {
        reason=="SDK_PREPARE_TIMEOUT" || reason=="SDK_LOAD_FAILED"->"淘宝订单脚本未加载，请检查网络后重试（$reason）"
        reason=="PAGE_LOAD_TIMEOUT" || reason=="PAGE_LOAD_FAILED"->"淘宝订单页面加载失败，请稍后重试（$reason）"
        reason=="ORDER_REQUEST_TIMEOUT"->"淘宝订单请求超时，请重试或在商城检查登录（$reason）"
        reason.contains("SESSION") || reason.contains("LOGIN") || reason.contains("TOKEN")->"淘宝订单会话需要重新验证（$reason）"
        reason.contains("RGV") || reason.contains("VALIDATE")->"淘宝订单需要在官方页面完成验证（$reason）"
        else->"淘宝订单读取失败（$reason）"
    })
    private fun JsonObject.text(key:String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun objectValue(value:JsonElement?):JsonObject?=when(value) {
        is JsonObject->value
        is JsonPrimitive->runCatching {Json.parseToJsonElement(value.content).jsonObject}.getOrNull()
        else->null
    }
    fun read(body:String):Page {
        val trimmed=body.trim()
        val json=if(trimmed.startsWith("{"))trimmed else trimmed.substringAfter("(","").substringBeforeLast(")","")
        val root=runCatching {Json.parseToJsonElement(json).jsonObject}.getOrNull() ?: throw Failure("INVALID_RESPONSE")
        val ret=(root["ret"] as? JsonArray)?.mapNotNull {(it as? JsonPrimitive)?.contentOrNull}.orEmpty()
        if(ret.isEmpty() || ret.any {!it.startsWith("SUCCESS")}) {
            val code=ret.firstOrNull {!it.startsWith("SUCCESS")}?.substringBefore("::")?.takeIf {it.matches(Regex("[A-Za-z0-9_]{1,80}"))} ?: "INVALID_RESPONSE"
            throw Failure(code)
        }
        val data=root["data"] as? JsonObject ?: throw Failure("MISSING_ORDER_DATA")
        val envelope=objectValue(data["result"]) ?: data
        val inner=objectValue(envelope["data"]) ?: envelope
        val array=inner["mainOrders"] as? JsonArray
        val pageControl=(envelope["global"] as? JsonObject)?.get("pageControl") as? JsonObject
        val hasMore=(pageControl?.get("hasMore") as? JsonPrimitive)?.booleanOrNull ?: listOf("hasMore","hasNext","hasNextPage").firstNotNullOfOrNull {key->(inner[key] as? JsonPrimitive)?.booleanOrNull}
        if(array!=null) {
            val orders=array.map {value->
                val order=value as? JsonObject ?: throw Failure("INVALID_ORDER")
                val id=order.text("id").ifBlank {order.text("orderId")}
                if(id.isBlank())throw Failure("MISSING_ORDER_ID")
                val item=((order["subOrders"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("itemInfo") as? JsonObject
                TbOrders.TbOrder(id,(order["extra"] as? JsonObject)?.text("tradeStatus").orEmpty(),
                    (order["statusInfo"] as? JsonObject)?.text("text").orEmpty(),item?.text("title").orEmpty(),item?.text("pic").orEmpty(),
                    TbOrderTime.created(order),
                    TrackingFields.time(order.toString(),setOf("confirmTime","endTime","finishTime","receiveTime")))
            }
            return Page(orders,hasMore)
        }
        val legacy=inner.filterKeys {it.startsWith("Main_")}
        val hierarchy=(envelope["hierarchy"] as? JsonObject)?.get("structure") as? JsonObject
        val validUltron=(envelope["container"] as? JsonObject)?.get("data") is JsonArray && hierarchy!=null
        if(legacy.isEmpty()) {
            if(validUltron && hasMore==false)return Page(emptyList(),false)
            throw Failure("UNSUPPORTED_ORDER_DATA")
        }
        return Page(legacy.map {(key,value)->
            val node=value as? JsonObject ?: throw Failure("INVALID_ORDER")
            val fields=node["fields"] as? JsonObject ?: throw Failure("INVALID_ORDER")
            val id=fields.text("orderId").ifBlank {fields.text("mainOrderId")}.ifBlank {node.text("id")}.ifBlank {key.removePrefix("Main_").takeIf {it.matches(Regex("[0-9]{8,}"))}.orEmpty()}
            if(id.isBlank())throw Failure("MISSING_ORDER_ID")
            val children=mutableListOf<JsonObject>()
            val visited=mutableSetOf<String>()
            fun descend(name:String,depth:Int) {
                if(depth>12 || !visited.add(name))return
                (inner[name] as? JsonObject)?.get("fields")?.let {if(it is JsonObject)children.add(it)}
                (hierarchy?.get(name) as? JsonArray)?.forEach {v->(v as? JsonPrimitive)?.contentOrNull?.let {descend(it,depth+1)}}
            }
            descend(key,0)
            val item=children.firstNotNullOfOrNull {it["item"] as? JsonObject}
            val status=fields.text("orderStatus").ifBlank {children.firstNotNullOfOrNull {child->((child["statusInfo"] as? JsonObject)?.text("text") ?: (child["status"] as? JsonObject)?.text("text"))?.takeIf {it.isNotBlank()}}.orEmpty()}
            TbOrders.TbOrder(id,fields.text("tradeStatus"),status,item?.text("title").orEmpty(),item?.text("pic").orEmpty(),
                createdAt=TbOrderTime.created(fields,*children.toTypedArray()))
        },hasMore)
    }
}
