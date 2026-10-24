package com.halo.expressassistant.hub

object PddLogisticsReadiness {
    fun terminalWithoutTrace(snapshot:PddDetailPayload.Snapshot)=snapshot.points.isEmpty() && snapshot.nextUrl.isBlank() &&
        snapshot.orderStatus in setOf("待发货","待成团","待付款","已取消","退款成功","交易关闭","订单关闭")
    fun canFinish(snapshot:PddDetailPayload.Snapshot,logisticsPage:Boolean,documentComplete:Boolean,following:Boolean)=
        snapshot.points.isNotEmpty() && (logisticsPage || (documentComplete && !following && snapshot.nextUrl.isBlank()))
}
