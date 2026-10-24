package com.halo.expressassistant.hub

import kotlinx.serialization.json.*

/** Small copyable report; raw field-shape samples remain available in the shared TXT. */
object PddDiagnosticSummary {
    fun render(raw:String):String {
        val root=runCatching {Json.parseToJsonElement(raw).jsonObject}.getOrNull() ?: return "暂无绑定诊断"
        fun JsonObject.text(key:String)=(get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
        fun facts(event:JsonObject)=(event["facts"] as? JsonObject).orEmpty()
        fun line(event:JsonObject):String=event.text("stage")+"："+facts(event).entries.take(8).joinToString("；"){"${it.key}=${(it.value as? JsonPrimitive)?.content.orEmpty().take(100)}"}
        val login=(root["login"] as? JsonArray).orEmpty().mapNotNull {it as? JsonObject}
        val events=(root["sync"] as? JsonArray).orEmpty().mapNotNull {it as? JsonObject}
        val environment=root["environment"] as? JsonObject
        val important=setOf("分类与边界检查","范围过滤说明","物流任务选择","同步前本地订单","同步后本地订单","同步结束","物流归属拒绝")
        return buildString {
            appendLine("诊断精简版 v4 · 完整业务数据可通过分享TXT发送")
            environment?.let {appendLine("${it.text("appVersion")} / Android ${it.text("androidApi")} / ${it.text("webView")}")}
            if(login.isNotEmpty())appendLine("授权："+login.map {it.text("stage")}.distinct().joinToString(" → "))
            val pages=events.filter {it.text("stage")=="物流页面启动"}
            if(pages.isNotEmpty())appendLine("页面复用 ${pages.count {facts(it)["pageReused"]==JsonPrimitive("true")}}/${pages.size}；物流链接直达 ${pages.count {facts(it)["directLink"]==JsonPrimitive("true")}}/${pages.size}")
            events.filter {it.text("stage") in important}.takeLast(12).forEach {appendLine(line(it))}
            events.filter {event->
                val f=facts(event);val kind=(f["event"] as? JsonPrimitive)?.content.orEmpty();val http=(f["http"] as? JsonPrimitive)?.intOrNull ?: 0
                event.text("stage")=="订单响应识别" && (http>=400 || kind in setOf("error","timeout","abort","fetch-error","body-error") || (kind!="start" && f["recognized"]==JsonPrimitive("false")))
            }.takeLast(8).forEach {appendLine(line(it))}
            val snapshots=root["itemSnapshots"] as? JsonObject
            val samples=(snapshots?.get("时间范围筛选后") as? JsonArray).orEmpty().mapNotNull {it as? JsonObject}
            val missing=samples.filter {(it["createdAt"] as? JsonPrimitive)?.longOrNull==0L}
            if(missing.isNotEmpty())appendLine("缺下单时间（匿名）："+missing.joinToString("；"){"${it.text("orderRef")}/${it.text("orderStatus")}/${it.text("stage")}"})
            append("[精简诊断结束]")
        }
    }
}
