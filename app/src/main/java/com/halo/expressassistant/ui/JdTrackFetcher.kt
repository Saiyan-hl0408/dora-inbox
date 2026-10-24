package com.halo.expressassistant.ui

import android.app.Activity
import android.content.Context
import com.halo.expressassistant.data.*
import com.halo.expressassistant.hub.*

object JdTrackFetcher {
    suspend fun fetch(act:Activity,item:ExpressItem):ExpressDetail=fetchWith(act,item,"")
    suspend fun fetchWith(act:Context,item:ExpressItem,jdCookies:String):ExpressDetail {
        require(!item.isOrderNumber){"旧版订单号无法作为京东物流运单查询，请同步京东物流账号"}
        val account=Store.accountForItem(act,item) ?: error("请先绑定京东物流账号")
        val result=JdlWebFetcher.fetch(act,account,item.mailNo)
        val fresh=result.items.firstOrNull {it.mailNo==item.mailNo} ?: error(result.error ?: "京东物流未返回此运单")
        val text=listOf(fresh.latestText,fresh.pickupStation,fresh.pickupAddress,fresh.pickupCode.takeIf {it.isNotBlank()}?.let {"取件码：$it"}.orEmpty()).filter {it.isNotBlank()}.joinToString("\n")
        return ExpressDetail(fresh.mailNo,fresh.companyName,fresh.state,fresh.state==3,listOf(DetailPoint(context=text,time=fresh.latestTime,formattedTime=fresh.latestTime)),fresh.eta)
    }
}
