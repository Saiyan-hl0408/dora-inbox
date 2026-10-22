package com.halo.expressassistant.hub

import android.app.Activity
import com.halo.expressassistant.data.*
import com.halo.expressassistant.ui.*

/** Resolve logistics using the order ID, then store the actual courier waybill separately. */
object ParcelMetadata {
    suspend fun refresh(act:Activity,item:ExpressItem):ExpressItem {
        val account=Store.accountForItem(act,item) ?: error("请先登录对应平台")
        val cookies=Store.cookieOf(account.payload)
        return when(item.source) {
            "jd"->{val result=JdTrackFetcher.fetchWith(act,item,cookies);check(result.data.isNotEmpty()){"京东网页未返回有效物流；请在官方订单页核对会话或服务异常"};apply(act,item,result.mailNo,result.companyName,result.data)}
            "pdd"->{val result=PddTraceFetcher.fetchMetadata(act,item,cookies) ?: error("拼多多未返回物流详情，请核对网页会话后重试");check(PddDetailQueryPolicy.hasLogistics(result.points)){"拼多多未返回有效物流节点，保留已有信息"};apply(act,item,result.waybill,result.company,result.points,result)}
            else->item
        }
    }
    fun apply(act:Activity,item:ExpressItem,number:String,company:String,points:List<DetailPoint>,pdd:PddTraceFetcher.TraceResult?=null):ExpressItem {
        val actual=number.takeIf {it.matches(Regex("[A-Za-z0-9]{8,32}")) && (!item.isOrderNumber || it!=item.mailNo)}
        val latest=points.maxByOrNull {ParcelTime.parse(it.time)}
        val text=latest?.context ?: item.latestText
        val time=latest?.time ?: item.latestTime
        val ts=ParcelTime.parse(time)
        val code=pdd?.pickupCode.orEmpty().ifBlank {points.asReversed().firstNotNullOfOrNull {PickupText.parse(it.context).codes.takeIf {it.isNotEmpty()}?.joinToString("、")}.orEmpty()}
        val candidate=item.copy(mailNo=actual ?: item.mailNo,companyName=company.ifBlank {item.companyName},isOrderNumber=if(actual!=null)false else item.isOrderNumber,latestText=text,latestTime=time,statusUpdatedAt=ts.takeIf {it>0} ?: item.statusUpdatedAt,pickupCode=if(item.source=="pdd")item.pickupCode else code.ifBlank {item.pickupCode})
        val stage=ParcelPresentation.stage(candidate)
        val updated=if(item.source=="pdd")PddPickupCodes.apply(PddLogistics.apply(candidate,points),pdd?.pickupCode.orEmpty()).copy(
            pickupStation=if(item.pickupCodeSource.startsWith("taobao-cainiao"))item.pickupStation else pdd?.station?.ifBlank {item.pickupStation} ?: item.pickupStation,
            pddPickupStation=pdd?.station?.ifBlank {item.pddPickupStation} ?: item.pddPickupStation,
            deliveryAddress=pdd?.deliveryAddress?.ifBlank {item.deliveryAddress} ?: item.deliveryAddress,
            recipientPhoneTail=pdd?.phoneTail?.ifBlank {item.recipientPhoneTail} ?: item.recipientPhoneTail,
            pickupHint=pdd?.pickupHint?.ifBlank {item.pickupHint} ?: item.pickupHint
        ) else candidate.copy(completedAt=if(stage==ParcelPresentation.Stage.CLOSED && ts>0)ts else item.completedAt,arrivedAt=if(stage==ParcelPresentation.Stage.PICKUP && ts>0 && item.arrivedAt==0L)ts else item.arrivedAt)
        val items=Store.items(act).filterNot {it.source==item.source && it.accountId==item.accountId && it.id==item.id}.toMutableList();items.add(updated);Store.saveItems(act,items)
        if(updated.mailNo!=item.mailNo) {
            val goods=Store.jdGoods(act).toMutableMap();goods.remove(item.mailNo)?.let {goods.putIfAbsent(updated.mailNo,it)};Store.saveJdGoods(act,goods)
            val times=HubData.collectedTimes(act).toMutableMap();times.remove(item.mailNo)?.let {times[updated.mailNo]=it};HubData.saveCollectedTimes(act,times)
        }
        if(updated.source=="pdd")Store.savePddTrace(act,updated.mailNo,points,done=ParcelPresentation.stage(updated)==ParcelPresentation.Stage.CLOSED)
        return updated
    }
}
