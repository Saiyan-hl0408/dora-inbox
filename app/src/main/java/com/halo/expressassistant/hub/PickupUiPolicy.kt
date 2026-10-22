package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

/** Shopping source is not the service that issued the current pickup code. */
object PickupUiPolicy {
    fun identity(item:ExpressItem):String = when {
        item.pickupCodeSource.startsWith("taobao-cainiao") -> "taobao"
        item.identityChannel=="cainiao" -> "taobao"
        item.identityChannel in setOf("jd","taobao","pdd") -> item.identityChannel
        CainiaoPickup.jdCarrier(item) -> "jd"
        else -> ""
    }
    fun account(item:ExpressItem,service:String):String = when(service) {
        "taobao" -> item.pickupQueryAccountId.ifBlank {if(item.source=="taobao")item.accountId else ""}
        "jd" -> if(item.source=="jd")item.accountId else item.pickupQueryAccountId
        else -> if(item.source==service)item.accountId else ""
    }
    fun offerPdd(item:ExpressItem)=item.source=="pdd" && item.pickupCode.isBlank() &&
        ParcelPresentation.stage(item)==ParcelPresentation.Stage.PICKUP
    fun missingLabel(item:ExpressItem)=when(item.pickupQueryOutcome) {
        "FAILED" -> "菜鸟查询未完成，可重试"
        "LOGIN" -> "请登录或恢复淘宝菜鸟"
        "VERIFY" -> "需在菜鸟确认收件号码"
        "NO_MATCH","NO_CODE","COLLECTED" -> "菜鸟暂未查到当前取件码"
        else -> "尚未查询取件码"
    }
}
