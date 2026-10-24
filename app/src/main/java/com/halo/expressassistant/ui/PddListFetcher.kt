package com.halo.expressassistant.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.JdGoods
import com.halo.expressassistant.data.Store
import com.halo.expressassistant.hub.PddDiagnostics
import com.halo.expressassistant.hub.PddDiagnosticPayload
import org.json.JSONArray
import org.json.JSONObject

/**
 * 拼多多包裹列表（无需小米/京东/淘宝登录）：隐藏 WebView 加载拼多多 H5 订单中心，
 * JS 钩子捕获页面 SDK 自动签名后的 proxy 接口响应（绕开 anti_content），
 * 滚动 + 点击「加载更多」翻页，把订单/包裹转成 ExpressItem（source=pdd）。
 *
 * 说明：拼多多订单接口字段随版本有差异，解析器按「候选键 + 递归扫描」容错实现，
 * 真实字段样例在真机验证阶段记录（docs/API.md），以现场为准。
 */
object PddListFetcher {

    private const val TAG = "PddListFetcher"
    // 拼多多 H5 买家订单页（油猴脚本同源确认：orders.html，React 无限滚动 + 「没有更多了...」）
    const val ORDER_PAGE = "https://mobile.yangkeduo.com/orders.html?type=0"

    /**
     * JS 程序化滚动：找到滚动容器后逐步 scrollTop（每轮调用推进一次）。
     * 无任何触摸事件，由页面自身响应滚动并发起带签名的翻页请求。
     */
    private val SCROLL_JS = """
(function(){
  try {
    var all = document.querySelectorAll('*');
    var best = null, bestSh = 0;
    for (var i = 0; i < all.length; i++) {
      var n = all[i];
      var sh = n.scrollHeight || 0;
      if (sh > bestSh && sh > n.clientHeight + 100 && n.clientHeight > 100) { best = n; bestSh = sh; }
    }
    if (!best) best = document.scrollingElement || document.documentElement;
    window.__pddScroll = (window.__pddScroll || 0) + Math.floor((best.clientHeight || 900) * 0.85);
    var y = window.__pddScroll;
    try { if (typeof best.scrollTo === 'function') best.scrollTo(0, y); } catch(e){}
    try { best.scrollTop = y; } catch(e){}
    try { document.scrollingElement.scrollTop = y; } catch(e){}
    try { window.scrollTo(0, y); } catch(e){}
    try { best.dispatchEvent(new Event('scroll', {bubbles: true})); } catch(e){}
    var now = best ? (best.scrollTop || 0) : 0;
    return 'tag=' + (best ? best.tagName : '?') + ' sh=' + bestSh + ' ch=' + (best ? best.clientHeight : 0) + ' top=' + now + ' y=' + y;
  } catch(e) { return 'ERR:' + e; }
})();
""".trimIndent()

    /** 点击订单页顶部标签（全部/已完成/待评价…），每个标签页各滚一遍以抓全各分类订单 */
    private val TAP_TAB_JS = """
(function(){
  try {
    var target = '__TAB__';
    var els = document.querySelectorAll('div,span,button,a,li');
    var best = null;
    for (var i = 0; i < els.length; i++) {
      var e = els[i];
      var t = (e.innerText || '').trim();
      if (t === target && e.offsetParent !== null) { best = e; break; }
    }
    if (best) {
      window.__pddScroll = 0;
      best.click();
      return 'CLICKED ' + target;
    }
    return 'NO_TAB ' + target;
  } catch(e) { return 'ERR:' + e; }
})();
""".trimIndent()

    private val handler = Handler(Looper.getMainLooper())
    private var scrolling = false
    private var orderSampled = false

    fun fetch(
        act: Context,
        onDone: (List<ExpressItem>?, Map<String, JdGoods>, String?) -> Unit
    ) = fetchWith(act, Store.firstEnabledAccount(act, Store.CH_PDD), onDone)

    /** 多源绑定：按指定拼多多账号凭证抓列表 */
    fun fetchWith(
        act: Context,
        account: com.halo.expressassistant.data.BoundAccount?,
        onDone: (List<ExpressItem>?, Map<String, JdGoods>, String?) -> Unit
    ) {
        handler.post { start(act, account, onDone) }
    }

    /** 流式抓取：每增量一批新订单+商品图就回调 onBatch（「获取一点加载一点」），最终列表经 onDone 回传 */
    fun fetchStream(
        act: Context,
        account: com.halo.expressassistant.data.BoundAccount?,
        onBatch: (List<ExpressItem>, Map<String, JdGoods>) -> Unit,
        onDone: (List<ExpressItem>?,String?) -> Unit
    ) {
        handler.post {
            start(act, account, { items, _, error -> onDone(items,error) }, onBatch)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun start(
        act: Context,
        account: com.halo.expressassistant.data.BoundAccount?,
        onDone: (List<ExpressItem>?, Map<String, JdGoods>, String?) -> Unit,
        onBatch: ((List<ExpressItem>, Map<String, JdGoods>) -> Unit)? = null
    ) {
        val captures = ArrayList<Pair<String, String>>()
        val listStartedAt=System.currentTimeMillis()
        val window=com.halo.expressassistant.hub.SyncLedger.window(act,"pdd",account?.id.orEmpty())
        val scope=com.halo.expressassistant.hub.PddListScope(com.halo.expressassistant.hub.ParcelTime.cutoff(window.days,window.now),Store.items(act).filter {it.source=="pdd" && it.accountId==account?.id.orEmpty()},com.halo.expressassistant.hub.ParcelTime.cutoff(com.halo.expressassistant.hub.HistoryRangePolicy.MAX_DAYS,window.now))
        com.halo.expressassistant.hub.HubData.prefs(act).edit().remove("pdd_paging_diagnostic").apply()
        var finished = false
        scrolling = false
        var challengeShown = false
        orderSampled = false
        val w = WebView(act)
        com.halo.expressassistant.hub.WebFetchRegistry.register(act,w)
        WebView.setWebContentsDebuggingEnabled(false)
        w.setBackgroundColor(0x00000000)
        w.alpha = 0.01f
        w.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (Linux; Android 16; 25102RKBEC Build/BP2A.250605.031.A3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(w, true)
        val pddCookies = account?.let { Store.cookieOf(it.payload) } ?: Store.pddCookies(act)
        PddDiagnostics.note(act,"开始读取订单列表",mapOf("accountRef" to PddDiagnosticPayload.ref(account?.id.orEmpty()),"days" to window.days.toString(),"since" to window.since.toString(),"credentialPresent" to PddLoginSession.hasCredentials(com.halo.expressassistant.hub.CookiePairs.parse(pddCookies)).toString(),"uaProfile" to "Android Mobile Chrome126"))
        PddCapture.setCookiesFor(act, pddCookies)
        PddCapture.attach(act, w)

        fun finish(result: List<ExpressItem>?, err: String?) {
            if (finished || com.halo.expressassistant.hub.WebFetchRegistry.closed(w)) return
            finished = true
            scrolling = false
            handler.postDelayed({
                com.halo.expressassistant.hub.WebFetchRegistry.release(w)
                PddCapture.detach(w)
                val excludedIds=HashSet<String>()
                val (rawItems, rawGoods) = convert(act,captures, account) {id,_->excludedIds.add(id)}
                val items=scope.select(rawItems)
                val goods=rawGoods.filterKeys {number->items.any {it.mailNo==number}}
                PddDiagnostics.items(act,"列表原始解析",rawItems)
                PddDiagnostics.items(act,"时间范围筛选后",items)
                val selectedIds=items.mapTo(HashSet()){it.id}
                val omitted=rawItems.map(scope::dated).filter {it.id !in selectedIds}
                PddDiagnostics.selection(act,rawItems.map(scope::dated),selectedIds,scope.since)
                PddDiagnostics.note(act,"范围过滤说明",mapOf("droppedTotal" to omitted.size.toString(),"droppedActive" to omitted.count {com.halo.expressassistant.hub.ParcelPresentation.stage(it) in setOf(com.halo.expressassistant.hub.ParcelPresentation.Stage.TRANSIT,com.halo.expressassistant.hub.ParcelPresentation.Stage.PICKUP)}.toString(),"oldestCandidate" to com.halo.expressassistant.hub.ParcelTime.format(scope.oldestCandidate),"reason" to "按购买日期范围及已缓存未完成订单保留规则"))
                PddDiagnostics.note(act,"列表阶段结束",mapOf("accountRef" to PddDiagnosticPayload.ref(account?.id.orEmpty()),"captured" to captures.size.toString(),"goods" to goods.size.toString(),"elapsedMs" to (System.currentTimeMillis()-listStartedAt).toString(),"result" to (err ?: "列表已完成")))
                com.halo.expressassistant.hub.HubData.prefs(act).edit().putString("pdd_list_diagnostic",
                    "查询：官方商城订单；捕获 ${captures.size} 个响应；原始解析 ${rawItems.size} 条；范围内 ${items.size} 条订单、${goods.size} 份商品。"+
                        (if(excludedIds.isNotEmpty())"\n已排除 ${excludedIds.size} 笔多多买菜业务订单，不计入普通快递读取结果。" else "")+"\n结果：${err ?: "已到查询边界或列表末尾"}\n"+
                        com.halo.expressassistant.hub.HubData.prefs(act).getString("pdd_paging_diagnostic","").orEmpty()).apply()
                Log.i(TAG, "finish items=${items.size} goods=${goods.size} err=$err")
                onDone(result ?: items.takeIf {it.isNotEmpty() || err==null}, goods, err)
            }, 200)
        }

        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if(com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return
                Log.i(TAG, "official order page loaded")
                if (url.contains("login.html") || url.contains("passport") || url.contains("/login") || url.contains("login?")) {
                    PddDiagnostics.note(act,"列表被要求重新登录",mapOf("path" to PddDiagnosticPayload.endpoint(url)))
                    finish(null,"拼多多会话需重新验证，请通过官方扫码登录恢复会话。")
                    return
                }
                if (url.contains("order") && !url.contains("login.html") && !url.contains("proxy")) {
                    PddCapture.seedOrderPage(view)
                    handler.postDelayed({
                        collect(w, captures, account, onBatch, scope, ::finish)
                    }, 4000)
                }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url.toString()
                if (u.contains("proxy/api") && (u.contains("order") || u.contains("logistic") || u.contains("express"))) {
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return blockScheme(request.url.toString())
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                return blockScheme(url.orEmpty())
            }
        }
        WebViewCompat.addDocumentStartJavaScript(w, PddCapture.HOOK_JS, PddCapture.COOKIE_HOSTS.toSet())
        w.loadUrl(ORDER_PAGE)
        handler.postDelayed({
            if (!finished && (!com.halo.expressassistant.hub.HubData.prefs(act).getBoolean("history_import_running",false) || !scrolling)) {
                Log.w(TAG, "timeout, finalize with ${captures.size} captures")
                finish(null, "拼多多列表超时（已用部分数据）")
            }
        }, 75_000)
    }

    private fun collect(w: WebView, captures: ArrayList<Pair<String, String>>, account: com.halo.expressassistant.data.BoundAccount?, onBatch: ((List<ExpressItem>, Map<String, JdGoods>) -> Unit)?, scope:com.halo.expressassistant.hub.PddListScope, finish: (List<ExpressItem>?, String?) -> Unit) {
        if (scrolling) return
        scrolling = true
        var rounds=0
        var activePhase=false
        val window=com.halo.expressassistant.hub.SyncLedger.window(w.context,"pdd",account?.id.orEmpty())
        val checkpoint=com.halo.expressassistant.hub.SyncLedger.checkpoint(w.context,"pdd",account?.id.orEmpty())
        val scanActive=!com.halo.expressassistant.hub.SyncLedger.hasCheckpoint(w.context,"pdd",account?.id.orEmpty()) || com.halo.expressassistant.hub.SyncLedger.isExpanding(w.context,"pdd",account?.id.orEmpty())
        var historyComplete=false
        var activeComplete=!scanActive
        var phaseResponses=0
        var latestOrderResponse:com.halo.expressassistant.hub.PddCapturedResponse.Page?=null
        var ignoredResponses=0
        var unknownDatePages=0
        var unresolvedDates=false
        val paging=com.halo.expressassistant.hub.PagingProgress()
        val emitted=HashSet<String>();val emittedGoods=HashSet<String>()
        val diagnosedResponses=HashSet<String>()
        fun done(orders:List<ExpressItem>,warning:String?=null) {
            scrolling=false
            val complete=historyComplete && activeComplete && !unresolvedDates
            PddDiagnostics.note(w.context,"分类与边界检查",mapOf("historyComplete" to historyComplete.toString(),"activeComplete" to activeComplete.toString(),"scanActive" to scanActive.toString(),"unresolvedDates" to unresolvedDates.toString(),"pollRounds" to rounds.toString(),"phaseResponses" to phaseResponses.toString(),"ignoredResponses" to ignoredResponses.toString(),"checkpointAdvanced" to complete.toString(),"warning" to warning.orEmpty()))
            com.halo.expressassistant.hub.HubData.prefs(w.context).edit().putString("pdd_paging_diagnostic",
                "范围：近 ${window.days} 天；实际边界 ${com.halo.expressassistant.hub.ParcelTime.format(window.since)}；轮询 $rounds 次；当前分类响应 $phaseResponses 个；忽略搜索/其他分类/无效响应 $ignoredResponses 个；历史边界 ${if(historyComplete)"完成" else "未完成"}；待收货分类 ${if(activeComplete)"完成" else "未完成"}。").apply()
            if(complete)com.halo.expressassistant.hub.SyncLedger.stage("pdd",account?.id.orEmpty(),orders,window)
            finish(orders,warning ?: if(unresolvedDates)"部分拼多多订单缺少下单时间，已保留订单；未推进同步范围 [PDD-DATE-PARTIAL]" else if(complete)null else "已保存部分拼多多订单，尚未到查询边界；未推进同步进度")
        }
        val runnable=object:Runnable {
            override fun run() {
                if(!scrolling || com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return
                rounds++
                w.evaluateJavascript("(function(){return Array.from(document.querySelectorAll('div,span,p')).some(function(e){return e.getClientRects().length>0 && /^(没有更多了?[.。…]*|暂无更多[.。…]*|已经到底[.。…]*|暂无订单)$/.test((e.innerText||'').trim());});})()") {pageEnd ->
                    PddCapture.readPages(w) {batch->
                        if(!scrolling || com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return@readPages
                        var receivedOrderPage=false
                        synchronized(captures) {
                            for(capture in batch) {
                                val url=capture.url;val body=capture.body
                                if((url.contains("order_list") || url.startsWith("ordersStore")) && diagnosedResponses.add(PddDiagnosticPayload.ref(url+body+capture.type+capture.event+capture.elapsedMs))) {
                                    PddDiagnostics.response(w.context,url,body,capture.type,capture.page,capture.validOrders,if(activePhase)"unreceived" else "all",capture.requestBody)
                                    PddDiagnostics.note(w.context,"订单响应识别",mapOf("phase" to if(activePhase)"unreceived" else "all","type" to capture.type,"page" to capture.page.toString(),"recognized" to capture.validOrders.toString(),"orderCount" to capture.orderCount.toString(),"matchesPhase" to capture.matchesPhase(activePhase).toString(),"http" to capture.http.toString(),"elapsedMs" to capture.elapsedMs.toString(),"event" to capture.event,"requestFormat" to capture.requestFormat,"requestSchema" to PddDiagnosticPayload.schema(capture.requestBody).toString(),"hasMore" to com.halo.expressassistant.hub.PddPagingPolicy.hasMore(body).toString()))
                                }
                                if(capture.event=="start")continue
                                if(!PddCapture.looksRelevant(url,body))continue
                                if(url.contains("order_list")) {
                                    // Search/refresh use the same endpoint. Only a valid full page
                                    // for the selected category can prove its date/end boundary.
                                    if(!capture.matchesPhase(activePhase)){ignoredResponses++;continue}
                                    latestOrderResponse=capture;phaseResponses++;receivedOrderPage=true
                                }
                                if(captures.none {it.first==url && it.second==body})captures.add(url to body)
                            }
                        }
                        val (rawOrders,rawGoods)=convert(w.context,captures,account)
                        val orders=scope.select(rawOrders)
                        val numbers=orders.mapTo(HashSet()){it.mailNo}
                        val allGoods=rawGoods.filterKeys {it in numbers}
                        com.halo.expressassistant.hub.SyncLedger.cacheBatch(w.context,orders,allGoods)
                        com.halo.expressassistant.hub.SyncLedger.progress("pdd",phaseResponses,orders)
                        if(onBatch!=null) {
                            val fresh=orders.filter {emitted.add(it.mailNo)}
                            val freshGoods=allGoods.filterKeys {emittedGoods.add(it)}
                            if(fresh.isNotEmpty())onBatch(fresh,freshGoods)
                        }
                        val response=latestOrderResponse
                        val excludedOnPage=HashSet<String>()
                        val lastPage=response?.let {convert(w.context,listOf(it.url to it.body),account) {id,_->excludedOnPage.add(id)}.first.map(scope::dated)}.orEmpty()
                        if(receivedOrderPage && response!=null && response.orderCount>0 && lastPage.isEmpty() &&
                            !com.halo.expressassistant.hub.PddOrderBusiness.pageFullyExcluded(response.orderCount,excludedOnPage.size)){done(orders,"拼多多返回订单但格式未识别，未推进同步范围 [PDD-ORDER-FORMAT]");return@readPages}
                        if(receivedOrderPage)unknownDatePages=if(lastPage.isNotEmpty() && lastPage.all {it.orderCreatedAt==0L})unknownDatePages+1 else 0
                        if(receivedOrderPage && lastPage.any {it.orderCreatedAt==0L})unresolvedDates=true
                        if(unknownDatePages>=3){done(orders,"拼多多未提供可识别的订单日期，已停止继续翻旧单；未推进同步范围 [PDD-DATE-MISSING]");return@readPages}
                        val end=response!=null && (pageEnd=="true" || (response.orderCount==0 && response.url!="ordersStore/order_list_v4") || com.halo.expressassistant.hub.PddPagingPolicy.hasMore(response.body)==false)
                        // BOTH lists stop at their purchase-date boundary. Older cached
                        // unfinished orders refresh individually, not by walking all history.
                        val reached=response!=null && (end || com.halo.expressassistant.hub.PddPagingPolicy.listPhaseBoundary(lastPage,window.since,activePhase,checkpoint.reached(lastPage)))
                        PddDiagnostics.note(w.context,"分页轮询",mapOf("phase" to if(activePhase)"unreceived" else "all","round" to rounds.toString(),"newResponse" to receivedOrderPage.toString(),"acceptedResponses" to phaseResponses.toString(),"latestPage" to (response?.page ?: 0).toString(),"visibleEnd" to (pageEnd=="true").toString(),"responseEnd" to (response?.let {com.halo.expressassistant.hub.PddPagingPolicy.hasMore(it.body)}).toString(),"boundaryReached" to reached.toString()))
                        if(!reached && paging.stalled(phaseResponses)){done(orders,"拼多多分页停滞；已保存成功项，未推进同步范围 [PDD-PAGING-STALLED]");return@readPages}
                        if(reached) {
                            if(!activePhase) {
                                historyComplete=reached
                                if(scanActive) {
                                    activePhase=true;latestOrderResponse=null;phaseResponses=0;unknownDatePages=0
                                    w.evaluateJavascript(TAP_TAB_JS.replace("__TAB__","待收货")) {result->
                                        PddDiagnostics.note(w.context,"切换待收货分类",mapOf("clicked" to result.contains("CLICKED").toString()))
                                        if(com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return@evaluateJavascript
                                        if(result.contains("CLICKED")){rounds=0;paging.reset();handler.postDelayed(this,3000)}
                                        else done(orders,"拼多多未找到待收货分类，已保存当前订单，未推进同步范围 [PDD-TAB-MISSING]")
                                    }
                                } else done(orders)
                            } else {activeComplete=reached;done(orders)}
                        } else {
                            // Wait for the selected tab's FIRST response before scrolling it.
                            if(response!=null){PddCapture.tryTapMore(w);swipeUp(w)}
                            handler.postDelayed(this,3000)
                        }
                    }
                }
            }
        }
        handler.post(runnable)
    }

    /** 拦截 pinduoduo:// App deep link（阻止跳 App，留在 H5 页继续捕获） */
    private fun blockScheme(u: String): Boolean {
        if (u.startsWith("pinduoduo://") || u.startsWith("xunmeng://")) {
            Log.i(TAG, "block app scheme: ${u.take(80)}")
            return true
        }
        return false
    }

    private fun swipeUp(w: WebView) {
        val width = w.width.toFloat()
        val height = w.height.toFloat()
        if (width <= 0 || height <= 0) return
        val x = width / 2f
        val downTime = android.os.SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, height * 0.9f, 0)
        w.dispatchTouchEvent(down)
        var t = downTime
        for (i in 1..8) {
            t += 30
            val move = MotionEvent.obtain(downTime, t, MotionEvent.ACTION_MOVE,
                x, height * 0.9f - height * 0.11f * i, 0)
            w.dispatchTouchEvent(move)
            move.recycle()
        }
        val up = MotionEvent.obtain(downTime, t + 30, MotionEvent.ACTION_UP, x, height * 0.08f, 0)
        w.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    /* ─────────────── 容错解析（候选键 + 递归扫描） ─────────────── */

    private fun convert(
        context:Context,
        captures: List<Pair<String, String>>,
        account: com.halo.expressassistant.data.BoundAccount?,
        onExcluded:(String,String)->Unit={_,_->}
    ): Pair<List<ExpressItem>, Map<String, JdGoods>> {
        val excluded=linkedMapOf<String,String>()
        val result=com.halo.expressassistant.hub.PddOrderPayload.read(captures,account?.id.orEmpty(),account?.label.orEmpty()) {id,reason->
            excluded[id]=reason;onExcluded(id,reason)
        }
        if(excluded.isNotEmpty() && account!=null) {
            val added=Store.excludePddNonParcelOrders(context,account.id,excluded.keys)
            if(added>0)PddDiagnostics.note(context,"排除非快递业务订单",mapOf(
                "accountRef" to PddDiagnosticPayload.ref(account.id),"count" to added.toString(),
                "reason" to "官方多多买菜详情链接；不进入普通快递列表、日期检查或物流队列；已归档旧占位项"))
        }
        return result
    }

}
