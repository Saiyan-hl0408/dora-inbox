package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLDecoder

/** Reads current-order JSON and the official logistics page. No recommendation scraping. */
object PddDetailPayload {
    data class Snapshot(val points:List<DetailPoint> = emptyList(),val waybill:String="",val company:String="",
        val station:String="",val deliveryAddress:String="",val phoneTail:String="",val hint:String="",val nextUrl:String="",val pickupCode:String="",val orderStatus:String="",val orderId:String="",val stationSource:String="")
    private val date=Regex("""20\d{2}-\d{1,2}-\d{1,2}\s+\d{1,2}:\d{2}(?::\d{2})?""")
    private val courier=Regex("""(韵达快递|极兔速递|极兔快递|邮政电商标快|邮政标准快递|邮政快递包裹|邮政快递|邮政EMS|中通快递|圆通快递|圆通速递|申通快递|顺丰速运|顺丰快递|京东快递|德邦快递)\s*[:：]\s*([A-Za-z0-9]{8,32})""")
    private fun JsonObject.text(vararg keys:String)=keys.firstNotNullOfOrNull {(this[it] as? JsonPrimitive)?.contentOrNull?.takeIf {s->s.isNotBlank() && s!="null"}}.orEmpty()
    fun read(raw:String):Snapshot {
        val root=runCatching {Json.parseToJsonElement(raw).jsonObject}.getOrNull() ?: return Snapshot()
        val data=root["data"] as? JsonObject ?: JsonObject(emptyMap())
        val shipping=root["shipping"] as? JsonObject
            ?: (((root["store"] as? JsonObject)?.get("traceData") as? JsonObject)?.get("shipping") as? JsonObject)
            ?: data["shipping"] as? JsonObject
        val fields=shipping?.let {TrackingFields.read(it.toString())}
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
        val number=fields?.waybill.orEmpty().ifBlank {match?.groupValues?.get(2).orEmpty()}.ifBlank {
            runCatching {URI(next).rawQuery.orEmpty().split('&').firstOrNull {it.startsWith("tracking_number=")}?.substringAfter('=')?.let {URLDecoder.decode(it,"UTF-8")}.orEmpty()}.getOrDefault("")
        }.takeIf {it.matches(Regex("[A-Za-z0-9]{8,32}"))}.orEmpty()
        val station=PddPickupLocation.header(header)
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
        val shippingPoints=(shipping?.get("traces") as? JsonArray)?.mapNotNull {node->
            val trace=node as? JsonObject ?: return@mapNotNull null
            val at=trace.text("time","timestamp")
            val info=trace.text("info")
            if(ParcelTime.parse(at)==0L || info.isBlank())return@mapNotNull null
            val heading=PddLogistics.status(trace.text("traceStatusTip")).ifBlank {
                if(trace.text("realStatus","status")=="SIGN")"已签收" else PddLogistics.status(info)
            }
            DetailPoint(if(heading.isBlank() || info.startsWith(heading))info else "$heading $info",at,at)
        }.orEmpty()
        val points=shippingPoints.ifEmpty {parsePoints(lines)}.ifEmpty {
            val express=data["express"] as? JsonObject
            if(express==null)emptyList() else {
                val rawStatus=express.text("statusPrompt","status_prompt")
                val explicitCollection=Regex("^您的(?:快件|包裹)已(?:取走|领取|签收)(?:$|[，,。.【\\[])").containsMatchIn(rawStatus)
                val status=if(explicitCollection)"已取件 $rawStatus" else rawStatus
                val at=express.text("timestamp","timeLabel")
                if(PddLogistics.stage(status)==null || ParcelTime.parse(at)==0L)emptyList()
                else listOf(DetailPoint(listOf(status,express.text("lastTrace","last_trace").takeIf {it!=rawStatus}.orEmpty()).filter(String::isNotBlank).joinToString(" "),at))
            }
        }
        val orderStatus=listOf(data.text("orderStatusPrompt","order_status_prompt","statusPrompt"),
            (data["topBanner"] as? JsonObject)?.text("orderStatusPrompt").orEmpty(),data.text("chatStatusPrompt"))
            .firstNotNullOfOrNull {PddOrderProgress.recognized(it).takeIf(String::isNotBlank)}.orEmpty()
        val historyStation=if(station.isBlank())PddPickupLocation.history(points) else ""
        return Snapshot(points,number,fields?.company.orEmpty().ifBlank {match?.groupValues?.get(1).orEmpty()},station.ifBlank {historyStation},address,phone,hint,next,code,
            orderStatus,root.text("orderId").ifBlank {data.text("orderSn","order_sn").ifBlank {(data["queryParams"] as? JsonObject)?.text("order_sn").orEmpty()}},
            if(station.isNotBlank())"网页站点名称行" else if(historyStation.isNotBlank())"物流轨迹中的明确站点" else "未取得明确站点")
    }
    private fun parsePoints(lines:List<String>):List<DetailPoint> {
        val firstDate=lines.indexOfFirst {date.containsMatchIn(it)}
        // Some official pages show description -> time without per-event headings.
        // Keep this separate from the existing heading -> time -> description layout.
        if(firstDate>0 && date.matches(lines[firstDate]) &&
            PddLogistics.stage(lines[firstDate-1])==null && isEventDescription(lines[firstDate-1])) {
            val points=mutableListOf<DetailPoint>()
            val pageHeading=lines.take(firstDate).firstNotNullOfOrNull {PddLogistics.status(it).takeIf(String::isNotBlank)}.orEmpty()
            var description=mutableListOf<String>()
            for(line in lines) {
                if(line in setOf("展开","收起","推荐商品"))break
                if(date.matches(line)) {
                    val body=description.joinToString(" ")
                    if(body.isNotBlank()) {
                        val heading=PddLogistics.status(body).ifBlank {if(points.isEmpty())pageHeading else ""}.ifBlank {if(isShippingEvent(body))"运输中" else ""}
                        if(heading.isNotBlank())points.add(DetailPoint("$heading $body".trim().take(600),line,line))
                    }
                    description=mutableListOf()
                } else if(description.isNotEmpty() || isEventDescription(line))description.add(line)
            }
            return points
        }
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
    private fun isEventDescription(text:String)=text.startsWith("您的快件") || text.startsWith("您的包裹") ||
        text.startsWith("商家已发货") || text.startsWith("您的订单") || text.startsWith("商家已打印") ||
        text.startsWith("订单确认") || text.startsWith("您已提交订单")
    private fun isShippingEvent(text:String)=ParcelEligibility.hasShippingText(text) ||
        Regex("(?:快件|包裹).*(?:离开|到达|发往|投递|揽件)").containsMatchIn(text)
}
