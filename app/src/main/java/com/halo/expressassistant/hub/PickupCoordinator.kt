package com.halo.expressassistant.hub

import android.app.Activity
import com.halo.expressassistant.data.*
import com.halo.expressassistant.ui.*
import kotlinx.coroutines.*

object PickupCoordinator {
    class LoginRequired(val channel:String,val number:String):IllegalStateException("请先登录${if(channel=="jd")"京东" else "淘宝菜鸟"}后继续查询")
    data class Result(val item:ExpressItem,val updated:Boolean,val source:String)
    suspend fun fetch(activity:Activity,item:ExpressItem):String=lookup(activity,item.mailNo)
    suspend fun lookup(activity:Activity,number:String):String=lookupParcel(activity,number).item.pickupCode
    suspend fun lookupParcel(activity:Activity,number:String):Result=withTimeout(45000) {
        require(number.matches(Regex("[A-Za-z0-9]{8,32}"))) {"请填写完整快递单号（8–32位字母或数字）"}
        val existing=HubData.items(activity).firstOrNull {it.mailNo.equals(number,true)}
        require(existing?.isOrderNumber!=true){"平台尚未返回真实快递运单号"}
        val jd=CainiaoPickup.jdCarrier(existing) || number.startsWith("JD",true)
        if(jd) {
            val accounts=Store.accounts(activity,"jd").filter {it.enabled}
            if(accounts.isEmpty())throw LoginRequired("jd",number)
            var error="京东未匹配到这票运单，请检查号码与关联手机号"
            for(account in accounts.take(2)) {
                val response=JdlWebFetcher.fetch(activity,account,number)
                val item=response.items.firstOrNull {it.mailNo.equals(number,true)}
                if(item!=null && ParcelPresentation.stage(item)!=ParcelPresentation.Stage.UNVERIFIED) {
                    synchronized(Store) {
                        val items=Store.items(activity)
                        val found=items.firstOrNull {it.mailNo.equals(number,true)}
                        Store.saveItems(activity,if(found==null)items+item else items.map {old->if(old!==found)old else if(old.source=="jd")SyncCacheMerge.merge(old,item) else old.copy(
                            pickupCode=if(ParcelPresentation.stage(old)==ParcelPresentation.Stage.PICKUP && ParcelPresentation.stage(item)==ParcelPresentation.Stage.PICKUP)item.pickupCode else old.pickupCode,
                            pickupStation=item.pickupStation.ifBlank {old.pickupStation},pickupAddress=item.pickupAddress.ifBlank {old.pickupAddress})})
                        if(response.goods.isNotEmpty())Store.saveJdGoods(activity,GoodsCache.mergeAll(Store.jdGoods(activity),response.goods))
                    }
                    return@withTimeout Result(HubData.items(activity).first {it.mailNo.equals(number,true)},existing!=null,"京东物流")
                }
                response.error?.let {error=it}
            }
            throw IllegalStateException(error)
        }
        fun pddFallback():Result?=HubData.items(activity).firstOrNull {it.mailNo.equals(number,true) && it.source=="pdd" &&
            ParcelPresentation.stage(it)==ParcelPresentation.Stage.PICKUP && it.pickupCodeSource==PddPickupCodes.SOURCE && it.pickupCode.isNotBlank()}
            ?.let {Result(it,existing!=null,"拼多多官方物流")}
        if(CainiaoPickupService.accounts(activity).isEmpty())return@withTimeout pddFallback() ?: throw LoginRequired("taobao",number)
        val response=try {CainiaoPickupService.query(activity,number,existing)}catch(e:CancellationException){throw e}
            catch(e:Exception){return@withTimeout pddFallback() ?: throw e}
        if(response.outcome!=CainiaoPickup.Outcome.FOUND && response.outcome!=CainiaoPickup.Outcome.COLLECTED)pddFallback()?.let {return@withTimeout it}
        if(response.outcome==CainiaoPickup.Outcome.LOGIN)throw LoginRequired("taobao",number)
        check(PickupImport.stage(response)!=null){CainiaoPickup.message(response)}
        synchronized(Store) {Store.saveItems(activity,PickupImport.reconcile(Store.items(activity),response))}
        Result(HubData.items(activity).first {it.mailNo.equals(number,true)},existing!=null,"淘宝菜鸟")
    }
}
