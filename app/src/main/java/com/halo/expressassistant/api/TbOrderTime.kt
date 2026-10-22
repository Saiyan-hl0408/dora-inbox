package com.halo.expressassistant.api

import com.halo.expressassistant.hub.ParcelTime
import kotlinx.serialization.json.*

/** Only creation fields/labels in this order, never a product, payment or delivery date. */
object TbOrderTime {
    private val keys=setOf("createTime","createdTime","gmtCreate","orderTime","orderCreateTime","create_time","order_create_time")
    private val excluded=setOf("subOrders","itemInfo","item","goods","goodsInfo","goodsList","logistics","recommend","recommendations")
    /** A logistics document's generic createTime may be waybill creation, so only
     * its explicitly named order-placement event is a safe order-date fallback. */
    fun placedStage(value:JsonElement):Long {
        val stages=(value as? JsonObject)?.get("multiStage") as? JsonArray ?: return 0
        return stages.firstNotNullOfOrNull {stage->
            val obj=stage as? JsonObject ?: return@firstNotNullOfOrNull null
            if((obj["title"] as? JsonPrimitive)?.contentOrNull!="已下单")return@firstNotNullOfOrNull null
            listOf("subtitle","time","value").firstNotNullOfOrNull {key->ParcelTime.parse((obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()).takeIf {it>0}}
        } ?: 0
    }
    fun created(vararg values:JsonElement):Long {
        fun read(value:JsonElement,depth:Int):Long {
            if(depth>10)return 0
            when(value) {
                is JsonObject->{
                    for(key in keys)ParcelTime.parse((value[key] as? JsonPrimitive)?.contentOrNull.orEmpty()).takeIf {it>0}?.let {return it}
                    val label=listOf("name","title","label","key").mapNotNull {(value[it] as? JsonPrimitive)?.contentOrNull}.firstOrNull {it.trim().trimEnd(':','：') in setOf("创建时间","下单时间","订单创建时间","订单时间","已下单")}
                    if(label!=null)for(key in if(label.trim()=="已下单")listOf("subtitle","time","value") else listOf("value","content","text"))ParcelTime.parse((value[key] as? JsonPrimitive)?.contentOrNull.orEmpty()).takeIf {it>0}?.let {return it}
                    for((key,child) in value)if(key !in excluded && !key.contains("recommend",true))read(child,depth+1).takeIf {it>0}?.let {return it}
                }
                is JsonArray->for(child in value)read(child,depth+1).takeIf {it>0}?.let {return it}
                else->Unit
            }
            return 0
        }
        return values.firstNotNullOfOrNull {read(it,0).takeIf {at->at>0}} ?: 0
    }
}
