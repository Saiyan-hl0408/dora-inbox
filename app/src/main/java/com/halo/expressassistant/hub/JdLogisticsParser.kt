package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressDetail
import com.halo.expressassistant.data.ExpressItem
import kotlinx.serialization.json.*

/** Pure decoder for the text returned by the official JD logistics page. */
object JdLogisticsParser {
    private fun decode(raw:String):String? = runCatching {(Json.parseToJsonElement(raw) as? JsonPrimitive)?.takeIf {it.isString}?.content}.getOrNull()
    fun parse(raw: String, item: ExpressItem): ExpressDetail {
        var text = decode(raw) ?: raw.trim()
        text=decode(text) ?: text
        if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length - 1).replace("\\n", "\n")
        }
        val points = ArrayList<DetailPoint>()
        val timeRe = Regex("""^(\d{4}[-/]\d{2}[-/]\d{2}\s+\d{2}:\d{2}(?::\d{2})?)""")
        val noise = setOf("订单跟踪", "联系客服", "复制", "展开详细信息", "仓库", "签", "派", "等待收货", "已发货")
        val skipRe = Regex("""(订单编号|运单号|国内承运人|联系电话|^\d+件$|¥|评论|已售)""")
        val kw = listOf("快件", "包裹", "派件", "揽收", "签收", "发往", "到达", "转运", "投递", "取件", "出库", "配送", "妥投", "物流", "运输", "自提", "提货")
        fun looksLikeDesc(line:String) = line.contains("【") || kw.any(line::contains) || PickupText.parse(line).codes.isNotEmpty()
        val lines=text.lines().map(String::trim).filter(String::isNotBlank)
        val firstTime=lines.indexOfFirst {timeRe.containsMatchIn(it)}
        val firstDesc=lines.indexOfFirst {it !in noise && !skipRe.containsMatchIn(it) && looksLikeDesc(it)}
        val descriptionFirst=firstDesc>=0 && (firstTime<0 || firstDesc<firstTime)
        var pending=StringBuilder()
        var pendingTime=""
        fun flush(time:String) {
            val description=pending.toString().trim()
            if(description.isNotBlank())points.add(DetailPoint(context=description,time=time,formattedTime=time))
            pending=StringBuilder()
        }
        for(line in lines) {
            val match=timeRe.find(line)
            if(match!=null) {
                val time=match.value.replace('/','-')
                val rest=line.substring(match.range.last+1).trim()
                if(rest.isNotBlank()) {
                    flush(pendingTime)
                    points.add(DetailPoint(context=rest,time=time,formattedTime=time))
                    pendingTime=""
                } else if(descriptionFirst) {
                    flush(time)
                } else {
                    flush(pendingTime)
                    pendingTime=time
                }
                continue
            }
            if(line in noise || skipRe.containsMatchIn(line))continue
            if(pending.isNotEmpty() || looksLikeDesc(line)) {
                if(pending.isNotEmpty())pending.append(' ')
                pending.append(line)
            }
        }
        // A standalone pickup card or final timeline node must survive even without a timestamp.
        flush(pendingTime)
        val cardCodes=PickupText.parse(text).codes
        if(cardCodes.isNotEmpty() && points.none {PickupText.parse(it.context).codes.containsAll(cardCodes)}) {
            points.add(DetailPoint(context="取件码："+cardCodes.joinToString("、"),time="",formattedTime=""))
        }
        val decoded=decode(raw)
        val body=decoded?.let {decode(it) ?: it} ?: text
        val wb=com.halo.expressassistant.hub.PickupText.parse(body).waybill
        val carrier=Regex("(京东物流|京东快递|顺丰速运|顺丰快递|极兔速递|极兔快递|中通快递|圆通速递|申通快递|韵达快递|邮政快递|EMS)").find(body)?.value.orEmpty()
        return ExpressDetail(
            mailNo = wb.ifBlank {item.mailNo},
            companyName = carrier.ifBlank {item.companyName},
            state = item.state,
            isReceived = item.state == 3,
            data = points,
            eta = item.eta
        )
    }
}
