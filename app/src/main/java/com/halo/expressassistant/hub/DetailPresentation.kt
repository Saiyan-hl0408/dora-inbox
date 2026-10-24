package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.JdGoods
import com.halo.expressassistant.api.TbOrders
import com.halo.expressassistant.ui.PddTraceFetcher
import com.halo.expressassistant.ui.PddListFetcher
import com.halo.expressassistant.ui.TbWebOrders
import java.net.URI
import java.net.URLEncoder

/** Facts for the native summary. Missing official fields are never inferred from read time. */
object DetailPresentation {
    data class Summary(
        val title:String, val image:String, val quantity:String, val status:String,
        val waybill:String?, val currentCode:String, val historicalCode:String,
        val station:String, val address:String, val phoneTail:String, val eventTime:String,
        val orderCreated:String, val locallyCollected:String
    )
    fun hasWaybill(item:ExpressItem)=!item.isOrderNumber && item.mailNo.matches(Regex("[A-Za-z0-9]{8,32}")) &&
        !item.mailNo.startsWith("LOCAL-",true) && !item.mailNo.startsWith("ORDER-",true)

    fun summary(item:ExpressItem,goods:JdGoods?=null,collectedAt:Long?=null):Summary {
        val stage=ParcelPresentation.stage(item)
        val active=stage==ParcelPresentation.Stage.PICKUP && collectedAt==null
        val historical=item.historicalPickupCode.ifBlank {
            if(stage==ParcelPresentation.Stage.CLOSED || collectedAt!=null)item.pickupCode else ""
        }
        val status=when {
            collectedAt!=null->"本机标记已取"
            stage==ParcelPresentation.Stage.CLOSED->if(item.logisticsStatus.contains("签收") || item.state==3 || item.completedAt>0)"平台显示已签收" else "订单已完成 · 物流签收待核实"
            stage==ParcelPresentation.Stage.PICKUP->"待取件"
            stage==ParcelPresentation.Stage.AFTERSALE->item.orderStatus.ifBlank {"订单已结束"}
            stage==ParcelPresentation.Stage.TRANSIT->"运输中"
            else->item.logisticsStatus.ifBlank {item.orderStatus.ifBlank {item.stateLabel()}}
        }
        val event=ParcelTime.parse(item.latestTime).takeIf {it>0}?.let(ParcelTime::format).orEmpty()
        return Summary(ParcelGroups.title(item,goods),ParcelGroups.image(item,goods),item.orderGoodsCount.ifBlank {goods?.count.orEmpty()},status,
            item.mailNo.takeIf {hasWaybill(item)},if(active)item.pickupCode else "",historical,
            PickupCardInfo.station(item),PickupCardInfo.address(item),item.recipientPhoneTail.takeIf {it.matches(Regex("\\d{4}"))}.orEmpty(),
            event,item.orderCreatedAt.takeIf {it>0}?.let(ParcelTime::format).orEmpty(),
            collectedAt?.takeIf {it>0}?.let(ParcelTime::format).orEmpty())
    }
    fun points(points:List<DetailPoint>)=points.filter {it.context.isNotBlank()}.distinctBy {it.time to it.context}
        .sortedByDescending {ParcelTime.parse(it.time)}
    fun localSummary(item:ExpressItem)=item.latestText.takeIf {it.isNotBlank()}?.let {
        listOf(DetailPoint(it,item.latestTime))
    }.orEmpty()

    fun officialUrl(item:ExpressItem):String? {
        val order=URLEncoder.encode(item.queryChannel,"UTF-8")
        return when(item.source) {
            "jd"->if(hasWaybill(item))JdLogisticsSource.detailUrl(item.mailNo) else JdLogisticsSource.LIST_URL
            "pdd"->if(item.queryChannel.matches(Regex("[A-Za-z0-9_-]{8,80}")))PddTraceFetcher.TRACE_PAGE+order else PddListFetcher.ORDER_PAGE
            "douyin"->DouyinOrderPayload.waybill(item.jumpLinks,item.queryChannel)?.officialUrl
            "taobao"->if(item.queryChannel.matches(Regex("[0-9]{8,32}")))TbOrders.SSR_DETAIL_PAGE+order else TbWebOrders.PAGE
            else->null
        }
    }
    /** Domain boundary is label-aware; taobao.com.attacker.test must never receive this session. */
    fun allowedOfficialUrl(source:String,url:String):Boolean=runCatching {
        val uri=URI(url);val host=uri.host?.lowercase().orEmpty()
        uri.scheme=="https" && when(source) {
            "jd"->host=="jd.com" || host.endsWith(".jd.com")
            "taobao"->host=="taobao.com" || host.endsWith(".taobao.com") || host=="cainiao.com" || host.endsWith(".cainiao.com")
            "pdd"->host=="yangkeduo.com" || host.endsWith(".yangkeduo.com") || host=="pinduoduo.com" || host.endsWith(".pinduoduo.com") || host=="pinduoduo.net" || host.endsWith(".pinduoduo.net")
            else->false
        }
    }.getOrDefault(false)
}
