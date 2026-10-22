package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

/** Explicit PDD code evidence only. A code match never establishes an identity-code service. */
object PddPickupCodes {
    const val SOURCE="pdd-official"
    private val label=Regex("取件(?:验证)?码|自提码|驿站码|取货码|提货码")
    private val suffix=Regex("(?:后|末|尾)[0-9一二三四五六七八九十两]{1,2}位|尾号")
    fun fromText(text:String,waybill:String):String {
        val lines=text.split(Regex("[\\n。；;]")).map(String::trim).filter(String::isNotBlank)
        return lines.indices.firstNotNullOfOrNull {index->
            val line=lines[index]
            if(!label.containsMatchIn(line) || suffix.containsMatchIn(line) || line.contains("手机号"))return@firstNotNullOfOrNull null
            val next=lines.getOrNull(index+1).orEmpty()
            val value=line+if(next.matches(Regex("[A-Za-z0-9*＊\\s—–－_-]{2,24}")))" $next" else ""
            PickupText.parse(value).codes.filterNot {it.equals(waybill,true)}.takeIf {it.isNotEmpty()}?.joinToString("、")
        }.orEmpty()
    }
    fun validValue(value:String,waybill:String):String {
        val parts=value.split('、').map(String::trim)
        return value.takeIf {parts.isNotEmpty() && parts.all {it.isNotBlank() && fromText("取件码：$it",waybill)==it}}.orEmpty()
    }
    fun apply(item:ExpressItem,code:String,at:Long=System.currentTimeMillis()):ExpressItem {
        val explicit=validValue(code,item.mailNo)
        if(item.source!="pdd" || explicit.isBlank())return item
        val observed=item.copy(pddPickupCode=explicit,pddPickupCodeAt=at)
        if(item.pickupCode.isNotBlank() && (item.pickupCodeSource.startsWith("taobao-cainiao") || item.pickupCodeSource=="local"))return observed
        if(item.pickupQueryOutcome=="COLLECTED" && item.statusUpdatedAt<=item.pickupCodeCheckedAt)return observed
        return observed.copy(pickupCode=explicit,pickupCodeSource=SOURCE)
    }
    fun fallback(item:ExpressItem):String=validValue(item.pddPickupCode.ifBlank {if(item.pickupCodeSource==SOURCE)item.pickupCode else ""},item.mailNo)
}
