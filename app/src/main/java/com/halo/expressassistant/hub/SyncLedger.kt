package com.halo.expressassistant.hub

import android.content.Context
import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.Store

/** Commit a coverage boundary only after paging reaches the requested boundary or the end. */
object SyncLedger {
    private data class Proof(val channel:String,val accountId:String,val items:List<ExpressItem>,val since:Long,val startedAt:Long)
    private val proofs=SyncCoverageProofs<Proof>()
    private var onProgress:((String)->Unit)?=null
    private val signatureRanges=java.util.concurrent.ConcurrentHashMap<String,Long>()
    private fun key(channel:String,accountId:String)="sync_${when(channel){"jd"->"v10_jdl";"pdd"->"v15_order_window";"taobao"->"v19_order_window";else->"v5"}}_${channel}_$accountId"
    fun begin(progress:((String)->Unit)?=null){proofs.clear();signatureRanges.clear();onProgress=progress}
    /** Cancel only uncommitted evidence; successfully saved parcels and coverage remain intact. */
    fun cancel(){proofs.clear();signatureRanges.clear();onProgress=null}
    fun progress(channel:String,page:Int,items:List<ExpressItem>) {
        val oldest=items.map {it.orderCreatedAt}.filter {it>0}.minOrNull()
        onProgress?.invoke("${HubData.channels[channel]}：第 $page 次分页，已保存 ${items.size} 条"+(oldest?.let {"，已到 ${ParcelTime.format(it)}"} ?: ""))
    }
    fun cacheBatch(context:Context,items:List<ExpressItem>,goods:Map<String,com.halo.expressassistant.data.JdGoods>) {
        if(items.isEmpty() && goods.isEmpty())return
        synchronized(Store) {
            val cached=Store.items(context).toMutableList()
            for(fresh in items) {
                val index=cached.indexOfFirst {it.source==fresh.source && it.accountId==fresh.accountId && it.id==fresh.id}
                if(index>=0)cached[index]=SyncCacheMerge.merge(cached[index],fresh) else cached.add(fresh)
            }
            val hidden=Store.xiaomiHidden(context).mapTo(HashSet()){it.mailNo}
            Store.saveItems(context,cached.filterNot {it.mailNo in hidden})
            if(goods.isNotEmpty())Store.saveJdGoods(context,GoodsCache.mergeAll(Store.jdGoods(context),goods))
        }
    }
    fun hasCheckpoint(context:Context,channel:String,accountId:String)=HubData.prefs(context).getLong(key(channel,accountId)+"_at",0)>0
    /** Read-only proof used by UI; an older successful checkpoint cannot bless this attempt. */
    fun completedRange(context:Context,channel:String,accountId:String,days:Int,startedAt:Long):Boolean {
        val prefs=HubData.prefs(context);val prefix=key(channel,accountId)
        val at=prefs.getLong(prefix+"_at",0);val since=prefs.getLong(prefix+"_since",0)
        return startedAt>0 && at>=startedAt && since>0 && since<=targetSince(channel,days)
    }
    fun isExpanding(context:Context,channel:String,accountId:String):Boolean {
        val prefs=HubData.prefs(context)
        // An explicit range read must actually query that whole range, including
        // previously covered dates. Automatic refresh still uses its checkpoint.
        return prefs.getBoolean("history_import_running",false)
    }
    fun window(context:Context,channel:String,accountId:String):SyncWindow {
        val prefs=HubData.prefs(context)
        val days=HistoryRangePolicy.clamp(prefs.getInt("sync_days",HistoryRangePolicy.DEFAULT_DAYS))
        val last=prefs.getLong(key(channel,accountId)+"_at",0)
        val import=isExpanding(context,channel,accountId)
        val now=System.currentTimeMillis()
        if(channel=="taobao")return TbPagingPolicy.window(days,now)
        if(channel=="pdd")return SyncWindow(days,now,PddPagingPolicy.requestedSince(days,now,last,import),exhaustive=import)
        val deltaDays=if(last>0)((now-last).coerceAtLeast(0)/86400000L+2).coerceAtMost(HistoryRangePolicy.MAX_DAYS.toLong()).toInt() else days
        return SyncWindow(if(import)days else maxOf(HistoryRangePolicy.DEFAULT_DAYS,deltaDays),now,if(!import && last>0){maxOf(if(channel=="jd")ParcelTime.cutoff(1,last) else last,ParcelTime.cutoff(HistoryRangePolicy.MAX_DAYS,now))} else null,exhaustive=import)
    }
    fun checkpoint(context:Context,channel:String,accountId:String,disabled:Boolean=false):SyncCheckpoint {
        val prefs=HubData.prefs(context)
        val known=prefs.getStringSet(key(channel,accountId)+"_ids",emptySet()).orEmpty()
        val cached=Store.items(context).filter {it.source==channel && it.accountId==accountId && it.id in known}
        return SyncCheckpoint(cached,disabled || isExpanding(context,channel,accountId))
    }
    fun stage(channel:String,accountId:String,items:List<ExpressItem>,window:SyncWindow) {
        proofs.stage(key(channel,accountId),Proof(channel,accountId,items,window.since,window.now))
        if(channel=="pdd")signatureRanges[accountId]=window.since
    }
    /** Called after Store.saveItems: a checkpoint must never get ahead of its cached records. */
    fun commit(context:Context) {
        proofs.commit {proof->complete(context,proof)}
        onProgress=null
    }
    fun awaitingPddDetails(context:Context)=Store.items(context).any {item->
        val since=signatureRanges[item.accountId] ?: return@any false
        item.source=="pdd" && ParcelPresentation.stage(item)==ParcelPresentation.Stage.CLOSED && item.completedAt==0L &&
            maxOf(item.orderCreatedAt,item.orderCompletedAt)>=since
    }
    private fun complete(context:Context,proof:Proof):Boolean {
        // This proof covers the bounded order list, not completeness of supplemental trace data.
        val prefs=HubData.prefs(context)
        val prefix=key(proof.channel,proof.accountId)
        val oldSince=prefs.getLong(prefix+"_since",0)
        val ids=prefs.getStringSet(prefix+"_ids",emptySet()).orEmpty()+proof.items.map {it.id}
        prefs.edit().putLong(prefix+"_at",proof.startedAt)
            .putLong(prefix+"_since",if(oldSince>0)minOf(oldSince,proof.since) else proof.since)
            .putStringSet(prefix+"_ids",ids).apply()
        return true
    }
    fun needsExpansion(context:Context,days:Int):Boolean {
        return Store.CHANNELS.filter {it in HubData.channels}.any {channel->
            Store.accounts(context,channel).filter {it.enabled && (channel!="taobao" || it.taobaoOrdersEnabled)}.any {account->
                val since=HubData.prefs(context).getLong(key(channel,account.id)+"_since",0)
                since==0L || since>targetSince(channel,days)
            }
        }
    }
    private fun targetSince(channel:String,days:Int)=if(channel=="jd")maxOf(ParcelTime.cutoff(days),JdLogisticsSource.oldestAvailable()) else ParcelTime.cutoff(days)
}
