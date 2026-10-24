package com.halo.expressassistant.hub

object SyncStatusText {
    fun brief(count:Int,error:String?)=when {error==null->"已更新";count>0->"部分更新";else->"读取失败"}
    fun needsAttention(brief:String?)=brief in setOf("读取失败","部分更新")
    fun requiresLogin(error:String)=Regex("未登录|请先登录|重新登录|登录失效|登录已过期|登录状态需要恢复|会话.{0,4}(失效|过期|验证|恢复)|恢复登录|重新授权|SESSION|LOGIN|TOKEN",RegexOption.IGNORE_CASE).containsMatchIn(error)
    fun detailsError(previous:String?,failed:Int,retrySkipped:Int):String? =
        listOfNotNull(previous,if(failed>0)"$failed 条物流详情未更新，保留已有信息" else null,
            if(retrySkipped>0)"$retrySkipped 条物流等待自动重试，可手动刷新" else null).takeIf {it.isNotEmpty()}?.joinToString("；")
    fun describe(channel:String,count:Int,error:String?):String=when {
        error==null -> "${count}件"
        count>0 -> "部分完成（已读${count}条；$error）"
        else -> "失败（$error）"
    }
}
