package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint

/** A location is independent of an expiring pickup code and a terse latest event. */
object PddPickupLocation {
    private val marker=Regex("驿站|快递柜|丰巢柜|代收点|自提点|服务点|菜鸟")
    private fun clean(raw:String):String {
        val value=raw.trim().trim('[',']','【','】','(',')','（','）').removePrefix("代收点-").trim()
        val withoutLabel=value.removePrefix("代收点").trimStart(':','：',' ','-')
        return if(withoutLabel!=value && marker.containsMatchIn(withoutLabel))withoutLabel else value
    }
    fun isInstruction(value:String)=Regex("[，,。！？!？；;]|包裹已|快件已|请|及时|等待|出示|取件码|取件提示|已入|已到|送达|已签收").containsMatchIn(value)
    fun sanitized(value:String)=if(isInstruction(value))"" else value
    private fun usable(raw:String):Boolean=raw.length in 4..120 && marker.containsMatchIn(raw) &&
        raw !in setOf("菜鸟驿站","代收点","自提点","快递柜","服务点","丰巢快递柜") &&
        !isInstruction(raw) && !Regex("收货地址|联系电话|拨打|客服电话|您的|取件地址|中转仓").containsMatchIn(raw)
    fun header(text:String):String = text.lineSequence().map {it.trim()}.firstNotNullOfOrNull {line->
        if(line.length !in 4..120 || line.contains("收货地址") || line.contains("【") || line.contains("联系电话"))null
        else line.split('|','｜').map(::clean).lastOrNull(::usable)
    }.orEmpty().removePrefix("菜鸟驿站 ")
    fun history(points:List<DetailPoint>):String {
        // Pickup events describe the recipient's station even after the parcel is collected.
        // A newer signed event remains status evidence, but is only a location fallback.
        val (pickup,other)=points.sortedByDescending {ParcelTime.parse(it.time)}.partition {
            PddLogistics.stage(PddLogistics.status(it.context))==ParcelPresentation.Stage.PICKUP
        }
        return (pickup+other).firstNotNullOfOrNull {point->
            val text=point.context
            if(PddLogistics.forwarding(text))return@firstNotNullOfOrNull null
            val patterns=listOf(
                Regex("签收代收点\\s*[:：]\\s*([^，。；\\n]{4,100})"),
                Regex("\\[丰巢\\]\\(([^)]+)\\)"),
                Regex("(?:已送达|已到达)[【\\[]((?:代收点-)?[^】\\]]{4,100})[】\\]]"),
                Regex("(?:快件|包裹)(?:已经|已)?由([^，。；\\n]{4,100}?)(?:代收|签收)")
            )
            patterns.firstNotNullOfOrNull {pattern->pattern.find(text)?.groupValues?.get(1)?.let(::clean)?.takeIf(::usable)}
        }.orEmpty()
    }
}
