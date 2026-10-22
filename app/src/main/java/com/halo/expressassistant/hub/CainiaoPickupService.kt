package com.halo.expressassistant.hub

import android.content.Context
import com.halo.expressassistant.data.*
import com.halo.expressassistant.ui.CainiaoWebFetcher
import kotlinx.coroutines.*

object CainiaoPickupService {
    fun stationCode(context:Context)=HubData.prefs(context).getString("cainiao_station_code",CainiaoPickup.DEFAULT_STATION_CODE).orEmpty()
    fun pageUrl(context:Context,manage:Boolean=false)=CainiaoPickup.pageUrl(stationCode(context),manage)
    class VerificationRequired(val number:String,val accountId:String):IllegalStateException("菜鸟需要确认收件号码，请在官方页面完成核验")
    fun accounts(context:Context,item:ExpressItem?=null):List<BoundAccount> = Store.accounts(context,"taobao").filter {it.enabled}.sortedBy {
        when(it.id) {item?.pickupQueryAccountId->0; if(item?.source=="taobao")item.accountId else null->1;else->2}
    }
    fun save(context:Context,number:String,result:CainiaoPickup.Result,accountId:String) {
        val now=System.currentTimeMillis()
        synchronized(Store) {
            val items=Store.items(context).map {item->
                if(item.mailNo.equals(number,true) && !HubData.collected(context,item))CainiaoPickup.apply(item,result,accountId,now) else item
            }
            Store.saveItems(context,items)
        }
        HubData.prefs(context).edit().putString("pickup_diagnostic",CainiaoPickup.message(result)).apply()
    }
    suspend fun query(context:Context,number:String,item:ExpressItem?=null,force:Boolean=true):CainiaoPickup.Result {
        check(stationCode(context).matches(Regex("[0-9]{1,20}"))) {"请先在「我的」设置官方菜鸟站点链接"}
        val accounts=accounts(context,item)
        check(accounts.isNotEmpty()) {"请在「我的」绑定淘宝 / 菜鸟账号，用于查询拼多多与淘宝包裹取件码"}
        var last=CainiaoPickup.Result(CainiaoPickup.Outcome.NO_MATCH,number)
        var normal:CainiaoPickup.Result?=null
        val prefs=HubData.prefs(context)
        for(account in accounts.take(2)) {
            val prefix="cainiao_pickup_${stationCode(context)}_${account.id}_${number}"
            val fingerprint=item?.let(CainiaoPickup::fingerprint).orEmpty()
            if(!force && !CainiaoPickup.mayAttempt(System.currentTimeMillis(),prefs.getLong(prefix+"_retry",0),prefs.getString(prefix+"_status","")==fingerprint))continue
            // Record before networking, so cancellation and failures also respect the retry limit.
            prefs.edit().putLong(prefix+"_retry",System.currentTimeMillis()+CainiaoPickup.RETRY_MS).putString(prefix+"_status",fingerprint).apply()
            last=try {CainiaoWebFetcher.fetch(context,account,number).copy(accountId=account.id)}
            catch(e:CancellationException){throw e}
            catch(e:Exception){save(context,number,CainiaoPickup.Result(CainiaoPickup.Outcome.FAILED,number),account.id);throw e}
            save(context,number,last,account.id)
            if(last.outcome==CainiaoPickup.Outcome.VERIFY)throw VerificationRequired(number,account.id)
            if(last.outcome in setOf(CainiaoPickup.Outcome.FOUND,CainiaoPickup.Outcome.COLLECTED))return last
            if(last.outcome in setOf(CainiaoPickup.Outcome.NO_MATCH,CainiaoPickup.Outcome.NO_CODE))normal=last
            // An expired first account must not prevent trying another enabled, authorized account.
        }
        return (normal ?: last).also {if(normal!=null)save(context,number,it,it.accountId)}
    }
    /** Called after platform classification. No pending candidates means no WebView or network call. */
    suspend fun supplement(context:Context,channels:Set<String>?=null,onCandidate:((String?)->Unit)?=null,onProgress:((Int,Int)->Unit)?=null) {
        if(stationCode(context).isBlank() || accounts(context).isEmpty())return
        val candidates=Store.items(context).filter {(channels==null || it.source in channels) && CainiaoPickup.candidate(it,HubData.collected(context,it))}.filter {item->
            accounts(context,item).take(2).any {account->
                val prefix="cainiao_pickup_${stationCode(context)}_${account.id}_${item.mailNo}"
                val prefs=HubData.prefs(context)
                CainiaoPickup.mayAttempt(System.currentTimeMillis(),prefs.getLong(prefix+"_retry",0),prefs.getString(prefix+"_status","")==CainiaoPickup.fingerprint(item))
            }
        }.take(CainiaoPickup.MAX_BATCH)
        val start=System.currentTimeMillis()
        for((index,item) in candidates.withIndex()) {
            val remaining=20000-(System.currentTimeMillis()-start)
            if(remaining<2000)break
            onCandidate?.invoke(item.mailNo)
            onProgress?.invoke(index+1,candidates.size)
            try {withTimeout(remaining){query(context,item.mailNo,item,force=false)}}
            catch(_:TimeoutCancellationException){currentCoroutineContext().ensureActive();break}
            catch(_:VerificationRequired){} // Show the pending verification on this order; never submit a phone.
            catch(e:CancellationException){throw e}
            catch(e:Exception){HubData.prefs(context).edit().putString("pickup_diagnostic",e.message ?: "菜鸟取件码查询暂不可用").apply()}
            finally {onCandidate?.invoke(null)}
        }
    }
}
