package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLDecoder

/** Reads current-order JSON and the official logistics page. No recommendation scraping. */
object PddDetailPayload {
    data class Snapshot(val points:List<DetailPoint> = emptyList(),val waybill:String="",val company:String="",
        val station:String="",val deliveryAddress:String="",val phoneTail:String="",val hint:String="",val nextUrl:String="",val pickupCode:String="")
    private val date=Regex("""20\d{2}-\d{1,2}-\d{1,2}\s+\d{1,2}:\d{2}(?::\d{2})?""")
    private val courier=Regex("""(韵达快递|极兔速递|极兔快递|邮政快递包裹|邮政快递|邮政EMS|中通快递|圆通快递|圆通速递|申通快递|顺丰速运|顺丰快递|京东快递|德邦快递)\s*[:：]\s*([A-Za-z0-9]{8,32})""")
    private fun JsonObject.text(vararg keys:String)=keys.firstNotNullOfOrNull {(this[it] as? JsonPrimitive)?.contentOrNull?.takeIf {s->s.isNotBlank() && s!="null"}}.orEmpty()
    fun read(raw:String):Snapshot {
        val root=runCatching {Json.parseToJsonElement(raw).jsonObject}.getOrNull() ?: return Snapshot()
        val data=root["data"] as? JsonObject ?: JsonObject(emptyMap())
        val body=root.text("text")
        val lines=body.lines().map(String::trim).filter(String::isNotBlank)
        val header=body.substringBefore(date.find(body)?.value ?: "\u0000")
        val match=courier.find(header)
        val buttons=data["orderButtons"] as? JsonArray
        val link=buttons?.mapNotNull {it as? JsonObject}?.firstNotNullOfOrNull {b->
            if(b.text("briefPrompt")!="查看物流")null else (b["typeValue"] as? JsonObject)?.text("url")?.takeIf(String::isNotBlank)
        }.orEmpty()
        val next=runCatching {URI("https://mobile.yangkeduo.com/").resolve(link).takeIf {
            it.scheme=="https" && it.host=="mobile.yangkeduo.com" && it.path=="/goods_express.html"
        }?.toString().orEmpty()}.getOrDefault("")
        val number=match?.groupValues?.get(2).orEmpty().ifBlank {
            runCatching {URI(next).rawQuery.orEmpty().split('&').firstOrNull {it.startsWith("tracking_number=")}?.substringAfter('=')?.let {URLDecoder.decode(it,"UTF-8")}.orEmpty()}.getOrDefault("")
        }.takeIf {it.matches(Regex("[A-Za-z0-9]{8,32}"))}.orEmpty()
        val station=header.lines().map(String::trim).firstOrNull {
            it.length in 6..100 && (it.endsWith("驿站") || it.endsWith("快递柜")) && !it.contains("收货地址") && !it.contains("【")
        }.orEmpty().removePrefix("菜鸟驿站 ")
        val address=data.text("shippingAddress","address").ifBlank {
            Regex("收货地址\\s*[:：]\\s*([^\\n]+)").find(header)?.groupValues?.get(1).orEmpty()
        }
        val phone=data.text("mobile").takeLast(4).takeIf {it.matches(Regex("\\d{4}"))}.orEmpty()
        val headerLines=header.lines().map(String::trim).filter(String::isNotBlank)
        fun withNextValue(index:Int):String {
            val nextLine=headerLines.getOrNull(index+1).orEmpty()
            return headerLines[index]+if(nextLine.matches(Regex("[A-Za-z0-9*＊\\s—–－_-]{2,24}")))" $nextLine" else ""
        }
        val hintIndex=headerLines.indexOfFirst {it.startsWith("取件出示")}
        val hint=if(hintIndex>=0)withNextValue(hintIndex) else ""
        val code=PddPickupCodes.fromText(header,number)
        val points=parsePoints(lines).ifEmpty {
            val express=data["express"] as? JsonObject
            if(express==null)emptyList() else {
                val status=express.text("statusPrompt","status_prompt")
                val at=express.text("timestamp","timeLabel")
                if(PddLogistics.stage(status)==null || ParcelTime.parse(at)==0L)emptyList()
                else listOf(DetailPoint("$status ${express.text("lastTrace","last_trace")}",at))
            }
        }
        return Snapshot(points,number,match?.groupValues?.get(1).orEmpty(),station,address,phone,hint,next,code)
    }
    private fun parsePoints(lines:List<String>):List<DetailPoint> {
        val out=mutableListOf<DetailPoint>();var time="";var context=StringBuilder();var pendingHeading=""
        fun flush(){if(time.isNotBlank() && context.isNotBlank())out.add(DetailPoint(context.toString().trim().take(600),time,time))}
        for(line in lines) {
            val match=date.find(line)
            if(match!=null) {
                val heading=PddLogistics.status(line.substring(0,match.range.first).trim()).ifBlank {pendingHeading}
                if(heading.isBlank())continue
                flush();time=match.value;context=StringBuilder(heading);pendingHeading=""
                val suffix=line.substring(match.range.last+1).trim()
                if(suffix.isNotBlank())context.append(' ').append(suffix)
            } else if(PddLogistics.stage(line)!=null) {
                pendingHeading=line
            } else if(time.isNotBlank()) {
                if(line in setOf("展开","复制","物流服务","顶部") || line.contains("未发货秒退") || line.startsWith("已包邮"))break
                context.append(' ').append(line)
            }
        }
        flush();return out
    }
}
