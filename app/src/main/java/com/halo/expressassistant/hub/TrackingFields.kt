package com.halo.expressassistant.hub

import kotlinx.serialization.json.*

/** Small pure seam used by the platform order adapters. */
object TrackingFields {
    data class Metadata(val waybill:String,val company:String)
    val numberKeys=setOf("tracking_no","tracking_number","trackingNo","mailNo","mail_no","express_no","expressNo","logistics_no","waybill_no","waybillCode","waybillNo","trackingNumber","logistics_sn","delivery_no")
    val companyKeys=setOf("logistics_company_short_name","logisticsCompanyName","logistics_company_name","shipping_company","express_company_name","company_name","express_company")
    fun read(raw:String):Metadata {
        val obj=runCatching {Json.parseToJsonElement(raw)}.getOrNull() ?: return Metadata("","")
        fun find(value:JsonElement,keys:Set<String>,depth:Int):String {
            if(depth>7)return ""
            when(value) {
                is JsonObject->{
                    for(key in keys) {
                        val candidate=(value[key] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
                        if(candidate.isNotBlank() && candidate!="null")return candidate
                    }
                    for((key,child) in value)if(!key.contains("recommend",true) && key !in setOf("goods_list","goods","items"))find(child,keys,depth+1).takeIf {it.isNotBlank()}?.let {return it}
                }
                is JsonArray->for(child in value)find(child,keys,depth+1).takeIf {it.isNotBlank()}?.let {return it}
                else->Unit
            }
            return ""
        }
        val number=find(obj,numberKeys,0).takeIf {it.matches(Regex("[A-Za-z0-9]{8,32}"))}.orEmpty()
        val company=find(obj,companyKeys,0).takeIf {it.any(Char::isLetter) && it!="拼多多快递"}.orEmpty()
        return Metadata(number,company)
    }
    fun time(raw:String,keys:Set<String>):Long {
        val obj=runCatching {Json.parseToJsonElement(raw).jsonObject}.getOrNull() ?: return 0
        for(key in keys) {
            val value=(obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            ParcelTime.parse(value).takeIf {it>0}?.let {return it}
        }
        return 0
    }
}
