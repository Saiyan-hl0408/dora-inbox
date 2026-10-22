package com.halo.expressassistant.hub

object SyncStatusText {
    fun describe(channel:String,count:Int,error:String?):String=when {
        error==null -> "${count}件"
        channel=="pdd" && count>0 -> "部分完成（已读${count}条；$error）"
        else -> "失败（$error）"
    }
}
