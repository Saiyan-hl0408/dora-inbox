package com.halo.expressassistant.hub

import kotlinx.serialization.json.*
import java.net.URI
import java.security.MessageDigest

/** Shareable response shapes, not raw responses, cookies or personal order contents. */
object PddDiagnosticPayload {
    fun ref(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(5).joinToString(""){byte->val n=byte.toInt() and 255;"${('A'.code+n/16).toChar()}${('A'.code+n%16).toChar()}"}
    fun endpoint(value:String)=runCatching {URI(value).path.orEmpty()}.getOrDefault("")
        .replace(Regex("[A-Za-z0-9_-]{24,}"),"<id>").take(120)
    fun cleanText(value:String):String = value
        .replace(Regex("(?i)(?:\"?(?:address|recipient|consignee|receiver|mobile|phone|user_name)\"?\\s*[:=]\\s*)(?:\"[^\"]*\"|[^\\n,;，。]+)"),"<personal-redacted>")
        .replace(Regex("(?:收货地址|收件人|联系电话|手机号)\\s*[:：=]\\s*[^\\n;；]+"),"<personal-redacted>")
        .replace(Regex("(?i)(?:https?://|pinduoduo://|xunmeng://)[^\\s，。]+"),"<link>")
        .replace(Regex("(?i)(?:PDDAccessToken|access_token|authorization|cookie|password|ticket|token|uid|uin)\\s*[:=]\\s*[^\\s,;，。]+"),"<credential-redacted>")
        .replace(Regex("(?i)Bearer\\s+\\S+"),"<credential-redacted>")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),"<email>")
        .replace(Regex("[A-Za-z0-9_-]*\\d{6,}[A-Za-z0-9_-]*"),"<number>")
        .replace(Regex("[A-Za-z0-9_-]{24,}"),"<opaque>")
    private fun normalized(key:String)=key.lowercase().replace("_","").replace("-","")
    private fun privateKey(key:String):Boolean {
        val k=normalized(key)
        if(k in setOf("shippingname","companyname","logisticscompanyname","expresscompanyname"))return false
        return listOf("cookie","token","ticket","session","authorization","password","secret","mobile","phone","address","recipient","consignee","contact","goodsname","username","receiver","barcode","qrcode","anticontent").any(k::contains) ||
            k in setOf("uid","uin","userid","user","openid","name","sign","signature")
    }
    fun safeStatus(value:String):String {
        val known=Regex("待确认收货|已签收|待取件|待取货|待自提|等待取件|待收货|待发货|待成团|待付款|运输中|在途中|派送中|派件中|配送中|已发货|已揽收|已取走|已领取|已取件|交易成功|待评价|退款成功|已取消|交易关闭|SIGN|DELIVERING|TRANSPORT|SUCCESS|FAIL",RegexOption.IGNORE_CASE)
            .findAll(value).map {it.value}.distinct().toList()
        return known.joinToString(" / ").ifBlank {if(value.length<=8 && value.matches(Regex("[A-Za-z0-9_-]+")))value else "unrecognized-text(length=${value.length})"}
    }
    fun schema(raw:String):JsonElement {
        val value=runCatching {Json.parseToJsonElement(raw)}.getOrNull() ?: return buildJsonObject {put("format","not-json");put("length",raw.length)}
        var remaining=800
        fun shape(node:JsonElement,key:String,depth:Int):JsonElement {
            if(--remaining<0)return JsonPrimitive("sample-budget-limit")
            if(privateKey(key))return JsonPrimitive("<redacted:${when(node){is JsonObject->"object";is JsonArray->"array";JsonNull->"null";else->"value"}}>")
            if(depth>=6)return JsonPrimitive(when(node){is JsonObject->"object(depth-limit)";is JsonArray->"array(size=${node.size})";else->"value"})
            return when(node) {
                is JsonObject->buildJsonObject {
                    node.entries.take(48).forEach {(k,v)->put(if(k.length>64 || k.matches(Regex(".*\\d{6,}.*")))"<dynamic-key>" else k,shape(v,k,depth+1))}
                    if(node.size>48)put("_omittedKeys",node.size-48)
                }
                is JsonArray->buildJsonObject {
                    put("_arraySize",node.size)
                    val selected=listOf(0,1,node.size/2,node.size-1).filter {it in node.indices}.distinct()
                    put("samples",JsonArray(selected.map {shape(node[it],key,depth+1)}))
                }
                JsonNull->JsonNull
                is JsonPrimitive->{
                    val k=normalized(key)
                    val safe=listOf("status","time","code","count","page","hasmore","total","success","type").any(k::contains)
                    if(!safe)JsonPrimitive(if(node.isString)"string(length=${node.content.length})" else "number/boolean")
                    else if(node.isString)JsonPrimitive(when {
                        k.contains("status")->safeStatus(node.content)
                        k.contains("type") && node.content.matches(Regex("[A-Za-z_-]{1,24}"))->node.content
                        node.content.matches(Regex("[0-9]{1,13}")) && (k.contains("time") || node.content.length<=5)->node.content
                        else->"string(length=${node.content.length})"
                    })
                    else if(node.booleanOrNull!=null || (node.longOrNull!=null && (k.contains("time") || node.content.length<=5)))node
                    else JsonPrimitive("number")
                }
            }
        }
        return shape(value,"",0)
    }
}
