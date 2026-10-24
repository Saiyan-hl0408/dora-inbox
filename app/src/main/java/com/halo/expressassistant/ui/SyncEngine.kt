package com.halo.expressassistant.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import com.halo.expressassistant.api.EtaParser
import com.halo.expressassistant.api.TbOrders
import com.halo.expressassistant.api.XiaomiSync
import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.JdGoods
import com.halo.expressassistant.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 四源融合同步：小米（优先级最高）/ 京东 / 淘宝（菜鸟）/ 拼多多。
 * 各渠道按登录态独立拉取，失败互不影响；按 mailNo 去重合并，
 * 小米同件覆盖京东/淘宝/拼多多；未登录渠道的旧数据在本次同步中移除。
 */
object SyncEngine {

    private const val TAG = "SyncEngine"

    data class ChannelStatus(val channel: String, val count: Int, val error: String?)

    data class Progress(val label:String,val current:Int=0,val total:Int?=null)

    data class Report(val statuses: List<ChannelStatus>, val total: Int)

    private val mutex = kotlinx.coroutines.sync.Mutex()
    fun isBusy():Boolean=mutex.isLocked

    suspend fun sync(act: Context, skip: Set<String> = emptySet(), historyImport:Boolean=false, automatic:Boolean=false, supplementPickup:Boolean=true, onProgress: ((Progress)->Unit)? = null, onPddProgress: ((List<ExpressItem>, Map<String, JdGoods>) -> Unit)? = null): Report =
        mutex.withLock {
            com.halo.expressassistant.hub.SyncLedger.begin {onProgress?.invoke(Progress(it))}
            com.halo.expressassistant.hub.HubData.prefs(act).edit().putBoolean("history_import_running",historyImport).apply()
            try {syncInternal(act, skip, automatic, historyImport, onPddProgress, onProgress,supplementPickup)}
            finally {com.halo.expressassistant.hub.HubData.prefs(act).edit().putBoolean("history_import_running",false).apply()}
        }

    private suspend fun syncInternal(act: Context, skip: Set<String>, automatic:Boolean, historyImport:Boolean, onPddProgress: ((List<ExpressItem>, Map<String, JdGoods>) -> Unit)? = null, onProgress: ((Progress)->Unit)? = null, supplementPickup:Boolean=true): Report = withContext(Dispatchers.IO) {
        val xiaomiOn = Store.accounts(act, Store.CH_XIAOMI).any { it.enabled } && "xiaomi" !in skip
        val jdOn = Store.accounts(act, Store.CH_JD).any { it.enabled } && "jd" !in skip
        val tbOn = Store.accounts(act, Store.CH_TAOBAO).any { it.enabled && it.taobaoOrdersEnabled } && "taobao" !in skip
        val pddOn = Store.accounts(act, Store.CH_PDD).any { it.enabled } && "pdd" !in skip
        val douyinOn=com.halo.expressassistant.BuildConfig.ENABLE_DOUYIN && Store.accounts(act,Store.CH_DOUYIN).any {it.enabled} && "douyin" !in skip
        Log.i(TAG, "channels: xiaomi=$xiaomiOn jd=$jdOn tb=$tbOn pdd=$pddOn")

        val window=com.halo.expressassistant.hub.SyncWindow(com.halo.expressassistant.hub.HistoryRangePolicy.clamp(com.halo.expressassistant.hub.HubData.prefs(act).getInt("sync_days",com.halo.expressassistant.hub.HistoryRangePolicy.DEFAULT_DAYS)))
        if(pddOn) {
            com.halo.expressassistant.hub.PddReadRecovery.prepare(act)
            com.halo.expressassistant.hub.PddLocationRecovery.prepare(act)
            com.halo.expressassistant.hub.PddDiagnostics.beginSync(act,if(historyImport)"历史范围读取" else if(automatic)"自动同步" else "手动刷新",window.days)
        }
        onProgress?.invoke(Progress("读取近 ${window.days} 天记录与未完成包裹"))
        val oldSnapshot=Store.items(act)
        val oldItems = oldSnapshot.associateBy { it.mailNo }
        val statuses = ArrayList<ChannelStatus>()

        // ── 小米：逐账号顺序同步（内部合并到 items，先跑保优先级）──
        var xiaomiItems: List<ExpressItem>? = null
        if (xiaomiOn) {
            var err: String? = null
            for (account in Store.accounts(act, Store.CH_XIAOMI).filter { it.enabled }) {
                try {
                    XiaomiSync.syncAccount(act, account)
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "xiaomi sync fail: $e")
                    err = e.message ?: "小米同步失败"
                }
            }
            xiaomiItems = Store.items(act).filter { it.source == "xiaomi" }
            statuses.add(ChannelStatus("xiaomi", xiaomiItems.size, err))
        }

        // ── 京东（WebView）/ 淘宝（HTTP）/ 拼多多（WebView）三路并行；各自内部逐账号 ──
        val platformTotal=listOf(jdOn,tbOn,pddOn,douyinOn).count {it}
        val platformDone=java.util.concurrent.atomic.AtomicInteger()
        fun progress(label:String){onProgress?.invoke(Progress("$label · 已完成 ${platformDone.get()}/$platformTotal 平台"))}
        fun completed(label:String){platformDone.incrementAndGet();progress("$label 读取结束")}
        val jdDeferred = if (jdOn) async {progress("正在同步京东");fetchJdAll(act,automatic,historyImport).also {completed("京东")}} else null
        val tbDeferred = if (tbOn) async {fetchTbAll(act,window){p->onProgress?.invoke(p.copy(label="${p.label} · 已完成 ${platformDone.get()}/$platformTotal 平台"))}.also {completed("淘宝")}} else null
        val douyinDeferred=if(douyinOn)async {progress("正在同步抖音商城");fetchDouyinAll(act){progress(it)}.also {completed("抖音商城")}} else null
        val pddDeferred = if (pddOn) async {
            progress("正在同步拼多多")
            fetchPddAll(act,onPddProgress).also {
                // List batches are already saved with their goods. Classify pending parcels
                // now instead of waiting for another platform's historical detail requests.
                val pickupStarted=System.currentTimeMillis()
                com.halo.expressassistant.hub.PddPickupService.supplement(act) {label->onProgress?.invoke(Progress(label))}
                com.halo.expressassistant.hub.PddDiagnostics.note(act,"多多取快递阶段结束",mapOf("elapsedMs" to (System.currentTimeMillis()-pickupStarted).toString(),"result" to com.halo.expressassistant.hub.HubData.prefs(act).getString("pdd_pickup_diagnostic","").orEmpty()))
                completed("拼多多")
            }
        } else null

        // Apply every returned status even if completion falls outside the visible history.
        // Filtering before merging would leave an old cached pending item pending forever.
        val jdResult = jdDeferred?.await()
        val tbResult = tbDeferred?.await()
        val pddResult = pddDeferred?.await()
        val douyinResult=douyinDeferred?.await()
        if (jdOn) statuses.add(ChannelStatus("jd", jdResult?.first?.size ?: 0, jdResult?.third))
        if (tbOn) {
            statuses.add(ChannelStatus("taobao", tbResult?.first?.size ?: 0, tbResult?.second))
        }
        if (pddOn) statuses.add(ChannelStatus("pdd", pddResult?.first?.size ?: 0, pddResult?.third))
        if(douyinOn)statuses.add(ChannelStatus("douyin",douyinResult?.first?.size ?: 0,douyinResult?.third))
        douyinResult?.second?.takeIf {it.isNotEmpty()}?.let {saveGoods(act,it)}
        // 预缓存京东/拼多多订单商品（保留已优化 shortName）
        jdResult?.second?.takeIf { it.isNotEmpty() }?.let { saveGoods(act, it) }
        pddResult?.second?.takeIf { it.isNotEmpty() }?.let { saveGoods(act, it) }

        // ── 合并（小米 > 京东 > 淘宝 > 拼多多；同平台多账号时先绑定账号优先）──
        // 防误清：通道「成功但 0 件」或抓取失败时保留该通道旧数据（WebView 抓取偶发空页会清空列表）；
        // 新账号首次同步（无旧数据）才允许写入空结果。
        fun preserveOld(channel: String, fresh: List<ExpressItem>?): List<ExpressItem>? {
            if (fresh != null && fresh.isNotEmpty()) return fresh
            val old = oldItems.values.filter { it.source == channel }
            return if (old.isNotEmpty()) old else fresh
        }
        val oldPddCount = oldItems.values.count { it.source == "pdd" }
        if ((pddResult?.first ?: emptyList()).isEmpty() && pddOn && oldPddCount > 0) {
            Log.w(TAG, "pdd fetch empty/missing -> keep old $oldPddCount")
        }
        val merged=com.halo.expressassistant.hub.SyncOrderMerge.combine(oldSnapshot,Store.items(act),
            xiaomiItems.orEmpty()+jdResult?.first.orEmpty()+tbResult?.first.orEmpty()+pddResult?.first.orEmpty()+douyinResult?.first.orEmpty()) {old,fresh->
                val goods=Store.jdGoods(act).toMutableMap();goods[old.mailNo]?.let {goods.putIfAbsent(fresh.mailNo,it)};Store.saveJdGoods(act,goods)
            }
        val finalList = merged.map { item ->
            val old = (if(item.source=="pdd")oldSnapshot.firstOrNull {it.source==item.source && it.accountId==item.accountId && it.id==item.id} else oldItems[item.mailNo]) ?: return@map item
            if(item.source=="pdd" || item.source=="taobao" || item.source=="douyin")return@map com.halo.expressassistant.hub.SyncCacheMerge.merge(old,item)
            // 拼多多：列表最新文案只是状态提示（交易成功/待评价…），
            // 已被物流页回写的真实轨迹（latestText/latestTime）要在同步合并时保留，防止覆盖丢失
            val pddPromptOnly = item.source == "pdd" &&
                item.latestText in setOf("交易成功", "待评价", "已评价", "待发货", "待收货", "待成团", "待付款", "已取消", "退款成功", "交易关闭")
            val latestText = if (pddPromptOnly) old.latestText.ifBlank { item.latestText } else item.latestText
            val latestTime = if (pddPromptOnly) old.latestTime.ifBlank { item.latestTime } else item.latestTime
            // 拼多多状态自愈：旧存件状态名已是「完成语义」（已评价/交易成功…）但 state 未标完成
            // → 纠正为完成，防远古单滞留在途（列表抓取深度波动后可能不再回吐该单来刷新状态）
            val pddDoneHeal = item.source == "pdd" && item.state != 3 &&
                listOf("已评价", "交易成功", "待评价", "已完成", "已签收", "签收").any { item.stateName.contains(it) }
            item.copy(
                state = if (pddDoneHeal) 3 else item.state,
                stateNum = if (pddDoneHeal) 107 else item.stateNum,
                tracked = old.tracked,
                notifiedText = old.notifiedText,
                notifiedTime = old.notifiedTime,
                stateOverride = old.stateOverride,
                partitionOverride = old.partitionOverride,
                aiProgress = old.aiProgress,
                aiEta = old.aiEta,
                aiProgressAt = old.aiProgressAt,
                latestText = latestText,
                latestTime = latestTime,
                addressId = old.addressId,
                pickupCode = item.pickupCode.ifBlank { old.pickupCode }
            )
        }
        // 聚合取件码：从最新轨迹文本解析
        val withPickup = finalList.map { item ->
            if(item.source=="pdd")com.halo.expressassistant.hub.PddPickupCodes.apply(item,com.halo.expressassistant.hub.PddPickupCodes.fromText(item.latestText,item.mailNo))
            else if (item.pickupCode.isNotBlank()) item
            else {
                val code = GoodsPresentation.pickupCodeFrom(item.latestText)
                if (code.isNullOrBlank()) item else item.copy(pickupCode = code)
            }
        }
        // 同步合并后立即回填「已抓取」的 PDD 轨迹到卡片（列表接口只有状态提示，
        // 避免每次同步把物流页回写的 latestText 覆盖回「交易成功」）
        val pddPrompts = setOf("交易成功", "待评价", "已评价", "待发货", "待收货", "待成团", "待付款", "已取消", "退款成功", "交易关闭")
        val withTrace = withPickup.map { item ->
            if (item.source != "pdd") return@map item
            val pts = Store.pddTraces(act, item.mailNo) ?: return@map item
            if (pts.isEmpty()) return@map item
            val last = com.halo.expressassistant.hub.LatestLogistics.point(pts) ?: return@map item
            com.halo.expressassistant.hub.PddLogistics.apply(item,pts)
        }
        // 已移除的快递不再复活：合并结果统一过滤用户手动移除过的单号
        // （PDD 并集/各渠道重抓会把服务端仍存在的单再次带回，此前从不查隐藏名单）
        val hiddenNos = Store.xiaomiHidden(act).mapTo(HashSet()) { it.mailNo }
        val finalSaved = if (hiddenNos.isEmpty()) withTrace else withTrace.filterNot { it.mailNo in hiddenNos }
        synchronized(Store) {
            val latestHidden=Store.xiaomiHidden(act).mapTo(HashSet()){it.mailNo}
            Store.saveItems(act,com.halo.expressassistant.hub.SyncCacheMerge.withLatest(Store.items(act),finalSaved).filterNot {it.mailNo in latestHidden})
        }
        if(supplementPickup)com.halo.expressassistant.hub.CainiaoPickupService.supplement(act) {current,total->
            onProgress?.invoke(Progress("菜鸟：查询待取包裹取件码",current,total))
        }
        com.halo.expressassistant.hub.SyncLedger.commit(act)
        Log.i(TAG, "merged total=${finalSaved.size} statuses=$statuses")
        Report(statuses, Store.items(act).size)
    }

    private fun oldItemsForOrder(act:Context,orderId:String,accountId:String)=Store.items(act).firstOrNull {it.source=="taobao" && it.accountId==accountId && it.queryChannel==orderId}

    /** 后台任务：商品短名批量优化（不阻塞同步）；每小批完成即落库并回调 onBatch（渐进式外显，建议 reload） */
    suspend fun optimizeShortNames(act: Context, onBatch: ((Map<String, JdGoods>) -> Unit)? = null) {
        val v2 = Store.shortOptV2(act)
        val goodsMap = Store.jdGoods(act)
        val need = goodsMap.filter { (_, g) ->
            g.name.length > 12 && (g.shortName.isBlank() || !v2)
        }
        if (need.isEmpty()) return
        GoodsPresentation.batchShorten(act, need.mapValues { it.value.name }) { chunk ->
            val merged = Store.jdGoods(act).toMutableMap()
            chunk.forEach { (k, v) ->
                merged[k] = (merged[k] ?: JdGoods(name = need[k]?.name ?: "")).copy(shortName = v)
            }
            Store.saveJdGoods(act, merged)
            onBatch?.invoke(merged)
        }
        Store.setShortOptV2(act, true)
        Log.i(TAG, "shortName optimized need=${need.size}")
    }

    /** 京东：逐账号抓取（一个失败不影响其它账号） */
    private suspend fun fetchJdAll(act: Context,automatic:Boolean,historyImport:Boolean): Triple<List<ExpressItem>?, Map<String, JdGoods>, String?> {
        val accounts = Store.accounts(act, Store.CH_JD).filter { it.enabled }
        val allItems = ArrayList<ExpressItem>()
        val goodsAcc = HashMap<String, JdGoods>()
        var err: String? = null
        for (account in accounts) {
            try {
                val prefs=com.halo.expressassistant.hub.HubData.prefs(act)
                val now=System.currentTimeMillis()
                if(com.halo.expressassistant.hub.JdlQueryPolicy.localOnly(automatic,historyImport,
                    com.halo.expressassistant.hub.SyncLedger.isExpanding(act,"jd",account.id),now,
                    prefs.getLong("jd_sync_success_${account.id}",0),prefs.getLong("jd_sync_attempt_${account.id}",0),
                    Store.items(act).any {it.source=="jd" && it.accountId==account.id && com.halo.expressassistant.hub.ActiveParcelRefresh.includes(it)})) {
                    allItems.addAll(Store.items(act).filter {it.source=="jd" && it.accountId==account.id})
                    continue
                }
                prefs.edit().putLong("jd_sync_attempt_${account.id}",now).apply()
                val (items, goodsMap, e) = fetchJd(act, account)
                if(e!=null)err=e
                if (items == null) err = e ?: err
                else {
                    allItems.addAll(items)
                    goodsAcc.putAll(goodsMap)
                }
            } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "jd account ${account.label} fail: $e")
                err = e.message ?: err
            }
        }
        return Triple(if (allItems.isEmpty() && err != null) null else allItems, goodsAcc, err)
    }

    private suspend fun fetchJd(act: Context, account: com.halo.expressassistant.data.BoundAccount?): Triple<List<ExpressItem>?, Map<String, JdGoods>, String?> {
        if(account==null)return Triple(null,emptyMap(),"请先绑定京东物流账号")
        val result=JdlWebFetcher.fetch(act,account)
        return Triple(result.items,result.goods,result.error)
    }

    /** 淘宝：逐账号抓取（订单列表 → 过滤 → SSR 详情 → 商品预缓存） */
    private suspend fun fetchTbAll(act: Context, window: com.halo.expressassistant.hub.SyncWindow, onProgress:((Progress)->Unit)?): Pair<List<ExpressItem>?, String?> {
        val accounts = Store.accounts(act, Store.CH_TAOBAO).filter { it.enabled && it.taobaoOrdersEnabled }
        val allItems = ArrayList<ExpressItem>()
        var err: String? = null
        try {
            for (account in accounts) {
                var cookies = Store.cookieOf(account.payload)
                if (cookies.isBlank()) continue
                val orders = LinkedHashMap<String, TbOrders.TbOrder>()
                val accountWindow=com.halo.expressassistant.hub.SyncLedger.window(act,"taobao",account.id)
                val activeCache=Store.items(act).filter {it.source=="taobao" && it.accountId==account.id && com.halo.expressassistant.hub.ActiveParcelRefresh.includes(it)}
                val activeIds=activeCache.mapTo(HashSet()) {it.queryChannel}
                var reachedEnd=false
                var unknownDatePages=0
                var missingDates=false
                val resolvedDetails=HashMap<String,TbOrders.TbParcel>()
                var dateLookups=0
                for(page in 1..com.halo.expressassistant.hub.TbPagingPolicy.pageLimit(accountWindow.days)) {
                    onProgress?.invoke(Progress("淘宝：近 ${accountWindow.days} 天，第 $page 页"))
                    val response=try {
                        com.halo.expressassistant.api.TbOrderRead.page(
                            {TbOrders.fetchBoughtPageWith(cookies,page)},
                            {TbWebOrders.fetch(act,Store.accountById(act,"taobao",account.id) ?: account,page)})
                    }
                    catch(e:kotlinx.coroutines.CancellationException){throw e}
                    catch(e:Exception){
                        err=e.message ?: "淘宝订单读取失败，已保留本地数据"
                        if(com.halo.expressassistant.api.TbOrderRead.needsLogin(e))Store.accountById(act,"taobao",account.id)?.let {Store.updateAccount(act,"taobao",it.copy(taobaoOrdersVerified=false))}
                        break
                    }
                    Store.accountById(act,"taobao",account.id)?.let {Store.updateAccount(act,"taobao",it.copy(taobaoOrdersVerified=true))}
                    cookies=Store.accountById(act,"taobao",account.id)?.let {Store.cookieOf(it.payload)} ?: cookies
                    val batch=response.orders.map {order->
                        if(order.createdAt>0)return@map order
                        val cached=oldItemsForOrder(act,order.orderId,account.id)?.orderCreatedAt ?: 0L
                        if(cached>0)return@map order.copy(createdAt=cached)
                        // Bounded, same-order official detail fallback; never use payment/signature time.
                        val detail=if(dateLookups<4){dateLookups++;TbOrders.fetchSsrDetailWith(cookies,order.orderId)} else null
                        if(detail!=null)resolvedDetails[order.orderId]=detail
                        order.copy(createdAt=detail?.createdAt ?: 0L)
                    }
                    val unknown=batch.count {it.createdAt==0L}
                    missingDates=missingDates || unknown>0
                    unknownDatePages=if(batch.isNotEmpty() && unknown==batch.size)unknownDatePages+1 else 0
                    // Unknown dates remain explicitly unknown; don't discard their packages or
                    // abort known in-range rows just because one list row lacks a timestamp.
                    batch.filter {it.createdAt==0L || it.orderId in activeIds || com.halo.expressassistant.hub.TbPagingPolicy.inRange(it.createdAt,accountWindow.since,accountWindow.now)}.forEach {orders.putIfAbsent(it.orderId,it)}
                    com.halo.expressassistant.hub.HubData.prefs(act).edit().putString("tb_list_diagnostic","近 ${accountWindow.days} 天；第 $page 页；原始 ${batch.size} 条；本页缺少创建时间 $unknown 条；已保留 ${orders.size} 条；详情时间回退 $dateLookups 次").apply()
                    if(batch.isEmpty() || response.hasMore==false || com.halo.expressassistant.hub.TbPagingPolicy.reachedBoundary(batch.map {it.createdAt},accountWindow.since)) {
                        reachedEnd=true
                        if(!missingDates)com.halo.expressassistant.hub.SyncLedger.stage("taobao",account.id,orders.values.map {ExpressItem(it.orderId,"","",it.orderId,orderCreatedAt=it.createdAt)},accountWindow)
                        break
                    }
                    if(unknownDatePages>=3)break
                }
                if(missingDates)err="部分淘宝订单仍缺少可识别的创建时间；已保留订单并读取物流，未确认的日期不计入范围覆盖 [TB-DATE-PARTIAL]"
                if(!reachedEnd && err==null)err="已达到淘宝分页预算，保留部分结果；未证明近 ${accountWindow.days} 天已读完"
                Store.items(act).filter {it.source=="taobao" && it.accountId==account.id && com.halo.expressassistant.hub.ActiveParcelRefresh.includes(it)}.forEach {old->
                    if(old.queryChannel.isNotBlank())orders.putIfAbsent(old.queryChannel,TbOrders.TbOrder(old.queryChannel,"",old.orderStatus,createdAt=old.orderCreatedAt,completedAt=old.completedAt))
                }
                // 2) 过滤有物流的订单（跳过交易关闭/待付款）
                val withParcels = orders.values.filter {o->
                    val done=listOf("成功","签收","完成","退款","取消","关闭").any(o.statusText::contains)
                    val at=o.completedAt.takeIf {it>0} ?: o.createdAt
                    val old=oldItemsForOrder(act,o.orderId,account.id)
                    com.halo.expressassistant.api.TbOrderShipping.requiresDetail(o,old!=null && !old.isOrderNumber && !old.mailNo.startsWith("ORDER-")) &&
                        (!done || at==0L || at>=accountWindow.since)
                }
                orders.values.filter {it !in withParcels}.forEach {o->
                    oldItemsForOrder(act,o.orderId,account.id)?.let {old->
                        if(!com.halo.expressassistant.api.TbOrderShipping.requiresDetail(o,!old.isOrderNumber && !old.mailNo.startsWith("ORDER-")))return@let
                        val returned=listOf("退款","取消","关闭","退回","拒收").any(o.statusText::contains)
                        allItems.add(old.copy(orderStatus=o.statusText,state=if(returned)4 else 3,stateName=o.statusText,
                            completedAt=o.completedAt.takeIf {it>0} ?: old.completedAt,
                            orderCreatedAt=o.createdAt.takeIf {it>0} ?: old.orderCreatedAt))
                    }
                }
                val instantSkipped=orders.values.count {it.businessType=="5000" && it !in withParcels}
                if(instantSkipped>0) {
                    val diagnostic=com.halo.expressassistant.hub.HubData.prefs(act)
                    diagnostic.edit().putString("tb_list_diagnostic",diagnostic.getString("tb_list_diagnostic","").orEmpty()+"；快递订单 ${withParcels.size} 条；已排除 $instantSkipped 条闪购即时配送").apply()
                }
                Log.i(TAG, "tb(${account.label}) orders=${orders.size} withParcels=${withParcels.size}")
                // 3) 并发拉 SSR 详情
                var completedDetails=0
                val detailFailures=java.util.concurrent.atomic.AtomicInteger()
                val parcels = coroutineScope {
                    withParcels.chunked(4).flatMap { chunk ->
                        chunk.map {o->async {
                            val fetched=try {resolvedDetails[o.orderId] ?: TbOrders.fetchSsrDetailWith(cookies,o.orderId)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){null}
                            if(fetched==null)detailFailures.incrementAndGet()
                            val detail=fetched?.let {it.copy(orderStatus=o.statusText,createdAt=o.createdAt.takeIf {at->at>0} ?: it.createdAt,completedAt=o.completedAt)}
                                ?: oldItemsForOrder(act,o.orderId,account.id)?.let {old->TbOrders.TbParcel(old.mailNo,old.companyName,old.stateName,old.latestText,old.latestTime,o.orderId,o.statusText,o.createdAt,o.completedAt)}
                                ?: TbOrders.TbParcel("ORDER-TB-${o.orderId}","",o.statusText,o.statusText,"",o.orderId,o.statusText,o.createdAt,o.completedAt)
                            synchronized(orders){completedDetails++;onProgress?.invoke(Progress("淘宝：读取物流详情",completedDetails,withParcels.size))}
                            detail
                        }}.awaitAll().filterNotNull()
                    }
                }
                if(detailFailures.get()>0)err=listOfNotNull(err,"${detailFailures.get()} 条淘宝物流详情未更新，保留已有信息").joinToString("；")
                // 4) 商品预缓存：订单号 -> mailNo 重新映射（保留旧 shortName）
                val goodsByOrder = withParcels.associateBy({ it.orderId }, { o ->
                    JdGoods(
                        name = o.goodsName,
                        imageUrl = if (o.goodsPic.startsWith("//")) "https:${o.goodsPic}" else o.goodsPic,
                        count = ""
                    )
                })
                val goodsByMail = HashMap<String, JdGoods>()
                parcels.forEach { p ->
                    goodsByOrder[p.orderId]?.let { g ->
                        if (g.name.isNotBlank()) goodsByMail[p.mailNo] = g
                    }
                }
                if (goodsByMail.isNotEmpty()) {
                    val merged = Store.jdGoods(act).toMutableMap()
                    goodsByMail.forEach { (k, v) ->
                        val old = merged[k]
                        merged[k] = if (old != null && old.shortName.isNotBlank()) {
                            v.copy(shortName = old.shortName)
                        } else {
                            v
                        }
                    }
                    Store.saveJdGoods(act, merged)
                }
                allItems.addAll(parcels.mapNotNull { p -> toItem(p, account) })
            }
            return allItems to err
        } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "tb sync fail: $e")
            return allItems to (e.message ?: "淘宝同步失败")
        }
    }

    private suspend fun fetchDouyinAll(act:Context,onProgress:(String)->Unit):Triple<List<ExpressItem>?,Map<String,JdGoods>,String?> {
        val items=mutableListOf<ExpressItem>();val goods=linkedMapOf<String,JdGoods>();val errors=mutableListOf<String>()
        for(account in Store.accounts(act,Store.CH_DOUYIN).filter {it.enabled})try {
            val result=com.halo.expressassistant.api.DouyinOrders.sync(act,account,onProgress)
            items+=result.items;goods.putAll(result.goods);result.warning?.let(errors::add)
        }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){errors+=e.message?.takeIf {it.startsWith("抖音") || it.startsWith("请先登录抖音") }?.take(180) ?: "抖音商城读取失败，已有数据保留"}
        return Triple(items,goods,errors.takeIf {it.isNotEmpty()}?.distinct()?.joinToString("；"))
    }

    /** 拼多多：逐账号**流式**抓取——每增量一批（订单+商品图）先上报（实时上涨），最终返回全量 */
    private suspend fun fetchPddAll(act: Context, onPddProgress: ((List<ExpressItem>, Map<String, JdGoods>) -> Unit)? = null): Triple<List<ExpressItem>?, Map<String, JdGoods>, String?> {
        val accounts = Store.accounts(act, Store.CH_PDD).filter { it.enabled }
        val allItems = LinkedHashMap<String, ExpressItem>()
        val goodsAcc = HashMap<String, JdGoods>()
        var err: String? = null
        for (account in accounts) {
            try {
                val (items, goodsMap, e) = fetchPddStream(act, account) { batch, goods ->
                    if (batch.isNotEmpty()) {
                        goodsAcc.putAll(goods)
                        batch.forEach { allItems.put("${it.accountId}|${it.id}", it) }
                        // 每轮批次都上报（不只新单）：成熟设备已无新增单，若只报 fresh
                        // 首页「首批即停圈」永远不触发，转圈会拖到全量抓完
                        onPddProgress?.invoke(batch, goods)
                    }
                }
                if(e!=null)err=e
                if (items == null) err = e ?: err
                else allItems.putAll(items.associateBy { "${it.accountId}|${it.id}" })
            } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "pdd account ${account.label} fail: $e")
                err = e.message ?: err
            }
        }
        return Triple(if (allItems.isEmpty() && err != null) null else allItems.values.toList(), goodsAcc, err)
    }

    private suspend fun fetchPdd(act: Context, account: com.halo.expressassistant.data.BoundAccount?): Triple<List<ExpressItem>?, Map<String, JdGoods>, String?> =
        suspendCancellableCoroutine { cont ->
            PddListFetcher.fetchWith(act, account) { items, goods, err ->
                if (cont.isActive) cont.resume(Triple(items, goods, err))
            }
        }

    private suspend fun fetchPddStream(
        act: Context,
        account: com.halo.expressassistant.data.BoundAccount?,
        onBatch: (List<ExpressItem>, Map<String, JdGoods>) -> Unit
    ): Triple<List<ExpressItem>?, Map<String, JdGoods>, String?> =
        suspendCancellableCoroutine { cont ->
            PddListFetcher.fetchStream(act, account, onBatch) { items,error ->
                if (cont.isActive) cont.resume(Triple(items, emptyMap(), error ?: if (items == null) "流式抓取失败" else null))
            }
        }

    /** 合并商品预缓存（保留旧 shortName） */
    private fun saveGoods(act: Context, goodsMap: Map<String, JdGoods>) {
        Store.saveJdGoods(act,com.halo.expressassistant.hub.GoodsCache.mergeAll(Store.jdGoods(act),goodsMap))
    }

    private fun toItem(p: TbOrders.TbParcel, account: com.halo.expressassistant.data.BoundAccount?): ExpressItem? {
        if (p.mailNo.isBlank()) return null
        // 订单列表状态（交易成功 = 已签收等）优先，其次物流节点标题
        val label = p.orderStatus+" "+p.stateLabel
        val state = when {
            listOf("退款","取消","关闭","退回","拒收").any(label::contains)->4
            com.halo.expressassistant.hub.ParcelStatusAliases.stage(p.stateLabel)==com.halo.expressassistant.hub.ParcelPresentation.Stage.CLOSED -> 3
            label.contains("派送") -> 5
            // 揽收 = 快递已取件发出 → 已发货/运输中（原误归「未发货」组）
            label.contains("揽收") || (label.contains("发货") && !label.contains("待发货")) -> 2
            label.contains("下单") || label.contains("待发货") -> 1
            else -> 0
        }
        val stateNum = when (state) {
            3 -> 107
            5 -> 105
            2 -> 104
            1 -> 103
            else -> 105
        }
        val eta = EtaParser.extract(p.latestText, p.stateLabel)
        return ExpressItem(
            id = p.orderId,
            companyCode = "",
            companyName = p.cpName.ifBlank { "淘宝快递" },
            mailNo = p.mailNo,
            latestText = p.latestText,
            latestTime = p.latestTime,
            state = state,
            stateName = p.stateLabel,orderStatus=p.orderStatus,orderCreatedAt=p.createdAt,
            orderCompletedAt=p.completedAt,completedAt=if(com.halo.expressassistant.hub.ParcelStatusAliases.stage(p.stateLabel)==com.halo.expressassistant.hub.ParcelPresentation.Stage.CLOSED)com.halo.expressassistant.hub.ParcelTime.parse(p.latestTime) else 0,
            statusUpdatedAt=com.halo.expressassistant.hub.ParcelTime.parse(p.latestTime),logisticsStatus=if(p.mailNo.startsWith("ORDER-"))"" else p.stateLabel,
            isOrderNumber=p.mailNo.startsWith("ORDER-"),
            provider = "CaiNiao",
            stateNum = stateNum,
            eta = eta,
            queryChannel = p.orderId,
            source = "taobao",
            accountId = account?.id ?: "",
            accountLabel = account?.label ?: ""
        )
    }
}
