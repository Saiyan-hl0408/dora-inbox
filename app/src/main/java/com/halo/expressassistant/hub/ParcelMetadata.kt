package com.halo.expressassistant.hub

import android.content.Context
import com.halo.expressassistant.data.*
import com.halo.expressassistant.ui.*

/** Resolve logistics using the order ID, then store the actual courier waybill separately. */
object ParcelMetadata {
    suspend fun refresh(act:Context,item:ExpressItem,deferLocation:((PddDetailPayload.Snapshot)->Unit)?=null):ExpressItem {
        val account=Store.accountForItem(act,item) ?: error("请先登录对应平台")
        val cookies=Store.cookieOf(account.payload)
        return when(item.source) {
            "jd"->{val result=JdTrackFetcher.fetchWith(act,item,cookies);check(result.data.isNotEmpty()){"京东网页未返回有效物流；请在官方订单页核对会话或服务异常"};apply(act,item,result.mailNo,result.companyName,result.data)}
            "pdd"->{
                val result=PddTraceFetcher.fetchMetadata(act,item,cookies,allowLocationLookup=deferLocation==null) ?: error("拼多多未返回物流详情，请核对网页会话后重试")
                if(result.locationPending)deferLocation?.invoke(PddDetailPayload.Snapshot(result.points,result.waybill,result.company,result.station,
                    result.deliveryAddress,result.phoneTail,result.pickupHint,pickupCode=result.pickupCode,orderStatus=result.orderStatus))
                val path=if(result.locationReadAttempted) {
                    if(result.station.isNotBlank())"订单页直读；物流页补回取件地点" else "订单页状态已更新；物流页未返回取件地点"
                } else if(result.locationPending)"订单页状态已更新；取件地点稍后补充" else if(result.summaryOnly)"订单页直读" else "完整物流页；状态与地点同次读取"
                HubData.prefs(act).edit().putString("pdd_detail_path_${item.accountId}_${item.id}",path).apply()
                if(!PddDetailQueryPolicy.hasLogistics(result.points)) {
                    check(result.orderStatus.isNotBlank() && (ParcelPresentation.stage(item) in setOf(ParcelPresentation.Stage.NOT_SHIPPED,ParcelPresentation.Stage.UNVERIFIED) || listOf("取消","退款","关闭").any(result.orderStatus::contains))){"拼多多未返回有效物流节点，保留已有信息"}
                    synchronized(Store) {
                        var saved=item
                        Store.saveItems(act,Store.items(act).map {current->
                            if(current.source==item.source && current.accountId==item.accountId && current.id==item.id)PddOrderProgress.apply(current,result.orderStatus).copy(pddReadIntegrityVersion=1,pddReadRepairRequired=false).also {saved=it} else current
                        });saved
                    }
                } else {
                    val points=if(result.summaryOnly)PddOrderPagePayload.withHistory(Store.pddTraces(act,item.mailNo).orEmpty(),result.points) else result.points
                    apply(act,item,result.waybill,result.company,points,result)
                }
            }
            else->item
        }
    }
    fun apply(act:Context,item:ExpressItem,number:String,company:String,points:List<DetailPoint>,pdd:PddTraceFetcher.TraceResult?=null):ExpressItem {
        val actual=number.takeIf {it.matches(Regex("[A-Za-z0-9]{8,32}")) && (!item.isOrderNumber || it!=item.mailNo)}
        val latest=points.maxByOrNull {ParcelTime.parse(it.time)}
        val text=latest?.context ?: item.latestText
        val time=latest?.time ?: item.latestTime
        val ts=ParcelTime.parse(time)
        val code=pdd?.pickupCode.orEmpty().ifBlank {points.asReversed().firstNotNullOfOrNull {PickupText.parse(it.context).codes.takeIf {it.isNotEmpty()}?.joinToString("、")}.orEmpty()}
        val candidate=item.copy(mailNo=actual ?: item.mailNo,companyName=company.ifBlank {item.companyName},isOrderNumber=if(actual!=null)false else item.isOrderNumber,latestText=text,latestTime=time,statusUpdatedAt=ts.takeIf {it>0} ?: item.statusUpdatedAt,pickupCode=if(item.source=="pdd")item.pickupCode else code.ifBlank {item.pickupCode})
        val stage=ParcelPresentation.stage(candidate)
        val location=pdd?.station.orEmpty().ifBlank {PddPickupLocation.history(points)}
        val updated=if(item.source=="pdd")PddPickupPayload.reconcile(PddPickupCodes.apply(PddLogistics.apply(candidate,points),pdd?.pickupCode.orEmpty()).copy(
            pddReadIntegrityVersion=if(pdd?.orderId==item.queryChannel.ifBlank {item.id})1 else item.pddReadIntegrityVersion,
            pddReadRepairRequired=if(pdd?.orderId==item.queryChannel.ifBlank {item.id})false else item.pddReadRepairRequired,
            pickupStation=location.ifBlank {PddPickupLocation.sanitized(item.pickupStation).ifBlank {PddPickupLocation.sanitized(item.pddPickupStation)}},
            pddPickupStation=location.ifBlank {PddPickupLocation.sanitized(item.pddPickupStation)},
            deliveryAddress=pdd?.deliveryAddress?.ifBlank {item.deliveryAddress} ?: item.deliveryAddress,
            recipientPhoneTail=pdd?.phoneTail?.ifBlank {item.recipientPhoneTail} ?: item.recipientPhoneTail,
            pickupHint=pdd?.pickupHint?.ifBlank {item.pickupHint} ?: item.pickupHint
        )) else candidate.copy(completedAt=if(stage==ParcelPresentation.Stage.CLOSED && ts>0)ts else item.completedAt,arrivedAt=if(stage==ParcelPresentation.Stage.PICKUP && ts>0 && item.arrivedAt==0L)ts else item.arrivedAt)
        val items=Store.items(act).filterNot {it.source==item.source && it.accountId==item.accountId && it.id==item.id}.toMutableList();items.add(updated);Store.saveItems(act,items)
        if(updated.mailNo!=item.mailNo && !item.pddReadRepairRequired && items.none {it.mailNo==item.mailNo}) {
            val goods=Store.jdGoods(act).toMutableMap();goods.remove(item.mailNo)?.let {goods.putIfAbsent(updated.mailNo,it)};Store.saveJdGoods(act,goods)
            val times=HubData.collectedTimes(act).toMutableMap();times.remove(item.mailNo)?.let {times[updated.mailNo]=it};HubData.saveCollectedTimes(act,times)
        }
        if(updated.source=="pdd")Store.savePddTrace(act,updated.mailNo,points,done=ParcelPresentation.stage(updated)==ParcelPresentation.Stage.CLOSED)
        return updated
    }
}
