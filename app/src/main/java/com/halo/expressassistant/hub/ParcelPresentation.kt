package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

/** Platform states are authoritative; a location name alone never means pending pickup. */
object ParcelPresentation {
    enum class Stage { PICKUP, TRANSIT, CLOSED, AFTERSALE, NOT_SHIPPED, UNVERIFIED }
    fun stage(item: ExpressItem): Stage {
        if(item.source=="taobao") {
            if(listOf("退款","售后","退货","取消","交易关闭","订单关闭").any(item.orderStatus::contains))return Stage.AFTERSALE
            ParcelStatusAliases.stage(item.logisticsStatus)?.let {return it}
        }
        if(item.source=="jd" && item.stateName=="旧版状态待核实")return Stage.UNVERIFIED
        if(item.source=="pdd") {
            if(listOf("退款","售后","退货","取消","交易关闭","订单关闭").any(item.orderStatus::contains))return Stage.AFTERSALE
            val explicit=item.logisticsStatus.ifBlank {if(item.stateName=="已签收" || item.orderStatus.startsWith("已签收"))"已签收" else ""}
            PddLogistics.stage(explicit)?.let {return it}
        }
        if(item.source=="jd" && item.queryChannel.startsWith("https://kd.jd.com/express/orderDetail")) {
            return when(item.stateNum) {
                107->Stage.CLOSED
                106->Stage.PICKUP
                108->Stage.AFTERSALE
                101->Stage.NOT_SHIPPED
                104,105->Stage.TRANSIT
                else->Stage.UNVERIFIED
            }
        }
        val status=item.orderStatus+" "+item.stateLabel()
        val text=status+" "+item.latestText
        if(ParcelEligibility.isVirtual(item))return Stage.NOT_SHIPPED
        if (item.state==4 || listOf("退款","退回","退货","拒收","拒签","交易关闭","订单关闭","已取消","已撤销").any(text::contains)) return Stage.AFTERSALE
        if(listOf("待付款","待支付","待成团").any(status::contains) || ParcelStatusAliases.packing.any(status::contains))return Stage.NOT_SHIPPED
        val received=listOf("已取货","已取件","本人签收","收件人已取","用户已取","客户已取","消费者取件成功","交易成功","已完成","已评价","待评价").any(status::contains)
        val recipientCollected=Regex("(?<!快递员)(?<!配送员)(?<!揽件员)(?<!揽收员)(?:已取货|已取件)").containsMatchIn(item.latestText)
        if(received || recipientCollected || listOf("本人签收","收件人已取","用户已取","客户已取","消费者取件成功").any(item.latestText::contains))return Stage.CLOSED
        // Explicit recipient-pickup status can override courier 'station signed' status.
        val awaiting=listOf("待取件","待取货","待自提","等待取件","等待自提","请取件","请上门提货","凭取件码","可取件").any(text::contains)
        if(awaiting)return Stage.PICKUP
        if(item.state==3 || item.stateNum==107 || text.contains("已签收"))return Stage.CLOSED
        if(item.pickupCode.isNotBlank() || listOf("已到站","已入库","到达驿站","存入快递柜","到达自提点").any(text::contains))return Stage.PICKUP
        // Unknown shopping states are not evidence that a physical parcel is in transit.
        if(item.source in setOf("jd","pdd") && !ParcelEligibility.hasShippingText(item.latestText) &&
            (item.isOrderNumber || item.stateName.matches(Regex("\\d*")) || item.latestText in setOf("待收货","等待收货")))return Stage.UNVERIFIED
        return Stage.TRANSIT
    }
    fun eventTime(item:ExpressItem):Long=when(stage(item)) {
        Stage.CLOSED->if(item.source in setOf("pdd","taobao","cainiao") || (item.source=="jd" && item.queryChannel.startsWith("https://kd.jd.com/express/orderDetail")))item.completedAt else item.completedAt.takeIf {it>0} ?: ParcelTime.parse(item.latestTime).takeIf {it>0} ?: item.statusUpdatedAt.takeIf {it>0} ?: item.orderCreatedAt
        Stage.PICKUP->item.arrivedAt.takeIf {it>0} ?: item.statusUpdatedAt.takeIf {it>0} ?: ParcelTime.parse(item.latestTime).takeIf {it>0} ?: item.orderCreatedAt
        Stage.TRANSIT,Stage.NOT_SHIPPED->item.orderCreatedAt.takeIf {it>0} ?: item.statusUpdatedAt.takeIf {it>0} ?: ParcelTime.parse(item.latestTime)
        else->item.statusUpdatedAt.takeIf {it>0} ?: ParcelTime.parse(item.latestTime).takeIf {it>0} ?: item.orderCreatedAt
    }
    fun recent(items: List<ExpressItem>, collectedAt: Map<String, Long>, limit: Int? = 3, days:Int?=null,now:Long=System.currentTimeMillis()): List<ExpressItem> {
        val since=days?.let {ParcelTime.cutoff(it,now)} ?: 0
        val result = items.filter { (collectedAt.containsKey(it.mailNo) || stage(it)==Stage.CLOSED) && stage(it)!=Stage.AFTERSALE && (days==null || (collectedAt[it.mailNo] ?: eventTime(it))>=since) }.sortedWith(
            compareByDescending<ExpressItem> { collectedAt[it.mailNo] ?: eventTime(it) }.thenBy { it.mailNo })
        return if (limit == null) result else result.take(limit)
    }
    fun arrivalTime(item:ExpressItem):Long=item.arrivedAt.takeIf {it>0} ?: if(stage(item)==Stage.PICKUP)ParcelTime.parse(item.latestTime) else 0
    fun historySubtitle(item:ExpressItem,collectedAt:Long?):String {
        if(collectedAt!=null)return "${ParcelTime.format(collectedAt)} · 本机标记已取"
        if(item.source=="pdd" && item.orderStatus.contains("待确认收货"))return if(item.completedAt>0)
            "${ParcelTime.format(item.completedAt)} · 已签收 · 待确认收货" else "已签收 · 待确认收货 · 签收时间未提供"
        if((item.source=="pdd" || (item.source=="jd" && item.queryChannel.startsWith("https://kd.jd.com/express/orderDetail"))) && item.completedAt==0L)return "${item.stateLabel()} · 签收时间未提供"
        val label=if(stage(item)==Stage.AFTERSALE)item.orderStatus.ifBlank {item.stateLabel()} else "平台已签收 / 已完成"
        return "${ParcelTime.format(eventTime(item))} · $label"
    }
}
