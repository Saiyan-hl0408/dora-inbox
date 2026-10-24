package com.halo.expressassistant.hub

import kotlinx.serialization.json.*
import java.net.URLDecoder

/** Full business values and structure; authentication and recipient privacy are removed. */
object PddDiagnosticBusiness {
    private fun key(value:String)=value.lowercase().replace("_","").replace("-","")
    private fun hidden(value:String,path:String):Boolean {
        val k=key(value)
        if(listOf("cookie","token","password","authorization","anticontent","session","secret","captcha","qrcode","barcode").any(k::contains))return true
        if(k in setOf("uid","uin","userid","openid","accesskey","sign","signature","ticket","verifycode","smscode","credential"))return true
        if(listOf("mobile","phone","telephone","recipient","consignee","receivername","buyername","nickname","contactname").any(k::contains))return true
        if(k.contains("address"))return !Regex("station|pickup|collectsite|receivesite|posthouse|servicepoint",RegexOption.IGNORE_CASE).containsMatchIn(path+value)
        return false
    }
    fun text(value:String):String=value
        .replace(Regex("(?i)[\"']?(?:cookie|access_token|pddaccesstoken|token|authorization|password|anti_content|ticket)[\"']?\\s*[:=]\\s*(?:[\"'][^\"']*[\"']|[^\\s,;&}]+)"),"<credential-redacted>")
        .replace(Regex("(?i)Bearer\\s+\\S+"),"<credential-redacted>")
        .replace(Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)|(?<!\\d)0\\d{2,3}[- ]?\\d{7,8}(?!\\d)"),"<phone-redacted>")
        .replace(Regex("(?:收货地址|收件人|联系电话|手机号)\\s*[:：=]\\s*[^\\n;；]+"),"<recipient-redacted>")
    private fun identifiers(k:String)=Regex("order(sn|id|number|no)|tracking(number|no)|waybill|mailno|expressno",RegexOption.IGNORE_CASE).containsMatchIn(key(k))
    fun clean(node:JsonElement,name:String="",path:String=""):JsonElement {
        if(hidden(name,path))return JsonPrimitive("<redacted>")
        return when(node) {
            is JsonObject->JsonObject(node.mapValues {(k,v)->clean(v,k,"$path/$name")})
            is JsonArray->JsonArray(node.map {clean(it,"","$path/$name")})
            is JsonPrimitive->if(node.isString && !identifiers(name))JsonPrimitive(text(node.content)) else node
        }
    }
    fun read(raw:String):JsonElement {
        if(raw.isBlank())return JsonNull
        val parsed=if(raw.trimStart().startsWith('{') || raw.trimStart().startsWith('['))runCatching {Json.parseToJsonElement(raw)}.getOrNull() else null
        if(parsed!=null)return clean(parsed)
        if(raw.contains('=') && !raw.contains('<')) {
            val pairs=raw.split('&').map {it.split('=',limit=2)}
            return clean(buildJsonObject {pairs.forEach {pair->
                val k=runCatching {URLDecoder.decode(pair[0],"UTF-8")}.getOrDefault(pair[0])
                val v=runCatching {URLDecoder.decode(pair.getOrElse(1){""},"UTF-8")}.getOrDefault("")
                put(k,v)
            }})
        }
        val visible=raw.replace(Regex("(?is)<script\\b[^>]*>.*?</script>"),"").replace(Regex("<[^>]*>")," ")
        return buildJsonObject {put("format","text");put("characters",raw.length);put("text",text(visible))}
    }
    /** Uses the same candidate order as production, including a non-empty but invalid date. */
    fun inputs(raw:JsonElement):JsonArray {
        val rows=mutableListOf<JsonElement>()
        fun visit(node:JsonElement,path:String) {
            when(node) {
                is JsonObject->{
                    val idKey=PddOrderPayload.ORDER_KEYS.firstOrNull {(node[it] as? JsonPrimitive)?.contentOrNull?.let {v->v.isNotBlank() && v!="null"}==true}
                    if(idKey!=null) {
                        rows+=buildJsonObject {
                            put("orderId",node[idKey]!!);put("objectPath",path)
                            PddOrderBusiness.nonParcelReason(node)?.let {reason->
                                put("excludedFromParcelSync",true);put("exclusionReason",reason)
                            }
                            for((role,keys) in listOf("createdAt" to PddOrderPayload.CREATED_KEYS,"orderStatus" to PddOrderPayload.STATUS_KEYS,"completedAt" to PddOrderPayload.COMPLETED_KEYS)) {
                                val selected=keys.firstOrNull {(node[it] as? JsonPrimitive)?.contentOrNull?.let {v->v.isNotBlank() && v!="null"}==true}
                                put(role,buildJsonObject {
                                    put("selectedPath",selected?.let {"$path.$it"}.orEmpty())
                                    put("candidates",buildJsonObject {keys.forEach {k->node[k]?.let {put(k,it)}}})
                                    if(role.endsWith("At"))put("parsedTime",selected?.let {ParcelTime.parse((node[it] as? JsonPrimitive)?.content.orEmpty())} ?: 0L)
                                })
                            }
                            put("otherTimeStatusFields",JsonObject(node.filterKeys {Regex("time|date|status",RegexOption.IGNORE_CASE).containsMatchIn(it)}))
                        }
                        return
                    }
                    node.forEach {(k,v)->if(!k.contains("recommend",true) && k !in setOf("goods_list","rec_list","orderGoods","order_goods"))visit(v,"$path.$k")}
                }
                is JsonArray->node.forEachIndexed {index,value->visit(value,"$path[$index]")}
                else->Unit
            }
        }
        visit(raw,"$")
        return JsonArray(rows)
    }
}
