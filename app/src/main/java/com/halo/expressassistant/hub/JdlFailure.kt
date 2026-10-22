package com.halo.expressassistant.hub

import kotlinx.serialization.json.*

/** Fixed labels only. Server text, URLs, order identifiers and credentials are excluded. */
object JdlFailure {
    fun message(event:JsonObject):String {
        fun text(key:String)=(event[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val stage=text("stage").takeIf {it in setOf("sdk","accounts","list","detail")} ?: "unknown"
        val reason=text("reason").takeIf {it in setOf("SESSION_INVALID","MODULE_CHANGED","NOT_READY","LIST_FAILED","PAGING_STALLED","PAGING_INCOMPLETE","REQUEST_TIMEOUT","OFFICIAL_REQUEST_FAILED","DETAIL_FAILED","HISTORY_DATE_MISSING")} ?: "UNKNOWN"
        val detail=text("detail").takeIf {it in setOf("RECEIVE_DATA_MISSING","ORDERS_MISSING","TOTAL_INVALID","TOTAL_NEGATIVE")}.orEmpty()
        val kind=text("kind").takeIf {it in setOf("TypeError","SyntaxError","ReferenceError","SecurityError","AbortError")}.orEmpty()
        val http=(event["http"] as? JsonPrimitive)?.intOrNull?.takeIf {it in 100..599}?.let {"HTTP$it"}.orEmpty()
        val transport=text("transport").takeIf {it in setOf("pending","returned","network_error")}.orEmpty()
        val label=when(stage){"sdk"->"官网模块";"accounts"->"账号列表";"list"->"收件列表";"detail"->"物流详情";else->"官网读取"}
        val explanation=when(reason) {
            "SESSION_INVALID"->"未返回已验证账号，不能据此认定会话过期"
            "MODULE_CHANGED","NOT_READY"->"官网模块未就绪或版本变化"
            "REQUEST_TIMEOUT"->"请求超时"
            "LIST_FAILED"->when(detail){
                "TOTAL_NEGATIVE"->"官网模块返回查询失败标记，未取得有效列表"
                "TOTAL_INVALID"->"列表数量格式不符合预期"
                "ORDERS_MISSING"->"响应缺少运单列表"
                else->"响应缺少收件数据"
            }
            "PAGING_STALLED","PAGING_INCOMPLETE"->"分页未完整返回"
            "DETAIL_FAILED"->"部分物流详情未返回"
            "HISTORY_DATE_MISSING"->"部分已送达记录没有可靠签收日期"
            else->"调用异常"
        }
        val code=(listOf("JD",stage.uppercase(),reason)+listOf(detail,kind,http,transport).filter {it.isNotBlank()}).joinToString("/")
        val total=(event["accounts"] as? JsonPrimitive)?.intOrNull?.takeIf {it in 1..100}
        val completed=(event["accountsCompleted"] as? JsonPrimitive)?.intOrNull?.takeIf {total!=null && it in 0..total}
        val coverage=if(total!=null && completed!=null)"；完整查询 $completed/$total 个已验证账号" else ""
        return "京东$label：$explanation [$code]$coverage；已保存成功项，未推进同步范围"
    }
}
