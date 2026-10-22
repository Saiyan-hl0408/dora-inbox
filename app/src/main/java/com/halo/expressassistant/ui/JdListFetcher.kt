package com.halo.expressassistant.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.JdGoods
import com.halo.expressassistant.data.Store
import org.json.JSONObject

/**
 * 京东包裹列表（无需小米登录）：隐藏 WebView 加载京东订单中心，
 * 网络层拦截 order_list_m 响应（页面 SDK 自动签名，绕开 h5st），
 * 滚动触发翻页，把订单转成 ExpressItem（source=jd）。
 */
object JdListFetcher {

    private const val TAG = "JdListFetcher"
    private const val ORDER_CENTER = "https://trade.m.jd.com/order/orderlist_jdm.shtml?orderType=all&source=m_outer_jx_order"

    private const val HOOK_JS = """
        (function(){
          function pushCap(s){
            try {
              var arr = [];
              if (window.name && window.name.charAt(0) === '[') { try { arr = JSON.parse(window.name); } catch(e){ arr = []; } }
              arr.push(s);
              window.name = JSON.stringify(arr);
            } catch(e) {}
          }
          function wrap(fn){
            if(typeof fn!=='function'||fn.__parcelhubCapture)return fn;
            var captured=function(data){try{pushCap(JSON.stringify(data));}catch(e){} return fn.apply(this,arguments);};
            captured.__parcelhubCapture=true;return captured;
          }
          function rewrite(v){
            // Observe JSONP without replacing the SDK callback: the page still needs to render and paginate.
            var m=String(v||'').match(/[?&]callback=([a-zA-Z0-9_$]+)/);
            if(m){
              var name=m[1],desc=Object.getOwnPropertyDescriptor(window,name);
              try{
                if(!desc||desc.configurable){
                  var fn=wrap(window[name]);
                  Object.defineProperty(window,name,{configurable:true,enumerable:true,get:function(){return fn;},set:function(f){fn=wrap(f);}});
                }else if(desc.writable)window[name]=wrap(window[name]);
              }catch(e){}
            }
            return v;
          }
          var origCreate = document.createElement.bind(document);
          document.createElement = function(tag){
            var el = origCreate(tag);
            if (String(tag).toLowerCase() === 'script') {
              var origSet = el.setAttribute.bind(el);
              el.setAttribute = function(k, v){ if (k === 'src') { v = rewrite(v); } return origSet(k, v); };
            }
            return el;
          };
          try {
            var desc = Object.getOwnPropertyDescriptor(HTMLScriptElement.prototype, 'src');
            Object.defineProperty(HTMLScriptElement.prototype, 'src', {
              get: function(){ return desc.get.call(this); },
              set: function(v){ return desc.set.call(this, rewrite(v)); },
              configurable: true
            });
          } catch(e) {}
          var oo = XMLHttpRequest.prototype.open; var os = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function(m,u){ this.__u=u; return oo.apply(this,arguments); };
          XMLHttpRequest.prototype.send = function(){ var self=this; this.addEventListener('load', function(){ try{ pushCap(self.responseText); }catch(e){} }); return os.apply(this,arguments); };
          var of = window.fetch; if (of) { window.fetch = function(input,init){ var p=of.apply(this,arguments); if(p&&p.then){p.then(function(r){try{ r.clone().text().then(function(t){ pushCap(t); }); }catch(e){}});} return p; }; }
        })();
    """

    private val handler = Handler(Looper.getMainLooper())
    private val interceptClient = okhttp3.OkHttpClient.Builder().build()
    private var scrollStarted = false
    private var challengeShown = false

    private fun tapAt(w: WebView, cssX: Float, cssY: Float) {
        val density = w.resources.displayMetrics.density
        val x = cssX * density
        val y = cssY * density
        val downTime = android.os.SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 60, MotionEvent.ACTION_UP, x, y, 0)
        try {
            w.dispatchTouchEvent(down)
            w.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    fun fetch(
        act: Context,
        onDone: (List<ExpressItem>?, Map<String, JdGoods>, String?) -> Unit
    ) = fetchWith(act, Store.firstEnabledAccount(act, Store.CH_JD), onDone)

    /** 多源绑定：按指定京东账号凭证抓列表 */
    fun fetchWith(
        act: Context,
        account: com.halo.expressassistant.data.BoundAccount?,
        onDone: (List<ExpressItem>?, Map<String, JdGoods>, String?) -> Unit
    ) {
        handler.post { start(act, account, onDone) }
    }

    /** Search only the user's authorized shopping orders; never treat the entered waybill as an order ID. */
    suspend fun searchWaybill(act:Context,account:com.halo.expressassistant.data.BoundAccount,number:String):List<ExpressItem> =
        kotlinx.coroutines.suspendCancellableCoroutine {cont ->
            cont.invokeOnCancellation {com.halo.expressassistant.hub.WebFetchRegistry.cancel(act)}
            handler.post {
                if(cont.isActive)start(act,account,{items,_,_->if(cont.isActive)cont.resumeWith(Result.success(items.orEmpty()))},number)
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun start(
        act: Context,
        account: com.halo.expressassistant.data.BoundAccount?,
        onDone: (List<ExpressItem>?, Map<String, JdGoods>, String?) -> Unit,
        waybillQuery:String?=null
    ) {
        val captures = ArrayList<String>()
        var finished = false
        scrollStarted = false
        val w = WebView(act)
        com.halo.expressassistant.hub.WebFetchRegistry.register(act,w)
        WebView.setWebContentsDebuggingEnabled(false)
        w.setBackgroundColor(0x00000000)
        w.alpha = 0.01f
        w.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
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
        val jdCookies = account?.let { Store.cookieOf(it.payload) } ?: Store.jdCookies(act)
        for (part in jdCookies.split(";")) {
            val kv = part.trim().split("=", limit = 2)
            if (kv.size == 2 && kv[0].isNotBlank()) {
                cm.setCookie("https://www.jd.com", "${kv[0]}=${kv[1]}")
                cm.setCookie("https://wqs.jd.com", "${kv[0]}=${kv[1]}")
                cm.setCookie("https://trade.m.jd.com", "${kv[0]}=${kv[1]}")
                cm.setCookie("https://api.m.jd.com", "${kv[0]}=${kv[1]}")
                cm.setCookie("https://jingfen.jd.com", "${kv[0]}=${kv[1]}")
            }
        }
        cm.flush()
        try {
            val decor = (act as? Activity)?.window?.decorView as? ViewGroup
            decor?.addView(w, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } catch (e: Throwable) {
            Log.w(TAG, "attach fail: $e")
        }

        fun finish(result: List<ExpressItem>?, err: String?) {
            if (finished || com.halo.expressassistant.hub.WebFetchRegistry.closed(w)) return
            finished = true
            scrollStarted = false
            challengeShown = false
            handler.postDelayed({
                try {
                    (w.parent as? ViewGroup)?.removeView(w)
                    com.halo.expressassistant.hub.WebFetchRegistry.release(w)
                    w.destroy()
                } catch (e: Throwable) {
                    Log.w(TAG, "cleanup fail: $e")
                }
                val (items, goods) = convert(captures, account)
                onDone(result ?: items.takeIf {it.isNotEmpty() || err==null}, goods, err)
            }, 200)
        }

        /** 汇总京东主要域 Cookie（用于验证完成后回存，防每次刷新都脱线） */
        fun collectJdCookies(): String {
            val sb = StringBuilder()
            for (host in listOf(
                "https://www.jd.com", "https://wqs.jd.com", "https://trade.m.jd.com",
                "https://api.m.jd.com", "https://jingfen.jd.com", "https://plogin.m.jd.com"
            )) {
                val c = CookieManager.getInstance().getCookie(host) ?: continue
                if (sb.isNotEmpty()) sb.append("; ")
                sb.append(c)
            }
            return sb.toString()
        }

        fun saveCookies(cookies: String) {
            try {
                // 防降级覆盖：账号 payload 里有登录态（pt_key）而本次收集到的没有（匿名会话），
                // 不能用匿名集把登录 Cookie 冲掉（曾把 11.5KB 登录集缩水成 2.5KB 导致抓取失效）
                if (account != null) {
                    val old = Store.cookieOf(account.payload)
                    if (old.contains("pt_key=") && !cookies.contains("pt_key=")) {
                        Log.i(TAG, "skip anon overwrite: keep login payload (${old.length})")
                        return
                    }
                    Store.updateAccount(act, Store.CH_JD, account.copy(payload = Store.cookiePayload(cookies)))
                } else {
                    Store.saveJdCookies(act, cookies)
                }
                Log.i(TAG, "jd cookies refreshed (len=${cookies.length})")
            } catch (e: Throwable) {
                Log.w(TAG, "save cookies fail: $e")
            }
        }

        w.webViewClient = object : WebViewClient() {            override fun onPageFinished(view: WebView, url: String) {
                if(com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return
                Log.i(TAG, "official order page loaded")
                if (url.contains("plogin") || url.contains("nopasswordcmcc")) {
                    // 京东安全验证页：保持隐藏 WebView 静默等待（不弹窗打扰）；
                    // 页面内登录/一键登录完成后会继续跳回 orderlist_jdm，验证后回存新 Cookie 防再次脱线
                    Log.i(TAG, "login challenge (silent) -> wait page continue")
                    handler.postDelayed({
                        if (!finished) {
                            val cookies = collectJdCookies()
                            if (cookies.isNotBlank()) saveCookies(cookies)
                        }
                    }, 2500)
                    // 兜底：静默等待较久仍无数据 → 一次性提示（持久化 6 小时节流 + 仅当本地确有京东旧数据；
                    // 局部变量节流跨同步无效，会话真失效时每次刷新/后台轮询都弹）
                    handler.postDelayed({
                        if (!finished && captures.isEmpty()) {
                            val now = System.currentTimeMillis()
                            if (now - Store.jdLoginHintAt(act) > 6 * 60 * 60 * 1000L &&
                                Store.items(act).any { it.source == "jd" }
                            ) {
                                Store.setJdLoginHintAt(act, now)
                                android.widget.Toast.makeText(
                                    act,
                                    "京东 H5 登录态已失效：本次保留旧数据；如需更新，请到 设置→京东登录 重新绑定",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }, 8000)
                    return
                }
                if (url.contains("orderlist_jdm.shtml")) {
                    // 回到订单页（验证完成/直接进入）：回存最新 Cookie
                    val cookies = collectJdCookies()
                    if (cookies.isNotBlank()) saveCookies(cookies)
                    handler.postDelayed({
                        scrollAndCollect(w, captures, account, ::finish)
                    }, 4000)
                }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url.toString()
                if (u.contains("api.m.jd.com") && u.contains("order_list_m")) {
                    Log.i(TAG, "REQ order_list_m (passthrough)")
                    return null
                }
                return null
            }
        }
        // JS 级捕获钩子：改写 JSONP 回调，不动网络层
        androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
            w, HOOK_JS,
            setOf("https://trade.m.jd.com", "https://wqs.jd.com")
        )
        w.loadUrl(if(waybillQuery==null)ORDER_CENTER else "https://trade.m.jd.com/order/orderlist_jdm.shtml?orderType=search&searchKey=${android.net.Uri.encode(waybillQuery)}&sceneval=")
        // 兜底超时
        handler.postDelayed({
            if (!finished && (!com.halo.expressassistant.hub.HubData.prefs(act).getBoolean("history_import_running",false) || !scrollStarted)) {
                Log.w(TAG, "timeout, finalize with ${captures.size} captures")
                finish(null, "京东列表超时（已用部分数据）")
            }
        }, 60000)
    }

    private fun scrollAndCollect(
        w: WebView,
        captures: ArrayList<String>,
        account: com.halo.expressassistant.data.BoundAccount?,
        finish: (List<ExpressItem>?, String?) -> Unit
    ) {
        if (scrollStarted) return
        scrollStarted = true
        var rounds = 0
        var activePhase=false
        val isSearch=w.url.orEmpty().contains("orderType=search")
        val window=com.halo.expressassistant.hub.SyncLedger.window(w.context,"jd",account?.id.orEmpty())
        val checkpoint=com.halo.expressassistant.hub.SyncLedger.checkpoint(w.context,"jd",account?.id.orEmpty(),isSearch)
        val scanActive=!isSearch && (!com.halo.expressassistant.hub.SyncLedger.hasCheckpoint(w.context,"jd",account?.id.orEmpty()) || com.halo.expressassistant.hub.SyncLedger.isExpanding(w.context,"jd",account?.id.orEmpty()))
        var listComplete=false
        val paging=com.halo.expressassistant.hub.PagingProgress()
        fun done(orders:List<ExpressItem>,warning:String?=null) {
            if(listComplete && !isSearch)com.halo.expressassistant.hub.SyncLedger.stage("jd",account?.id.orEmpty(),orders,window)
            finish(orders,warning ?: if(listComplete || isSearch)null else "已保存部分京东订单，尚未到查询边界；未推进同步进度")
        }
        val runnable = object : Runnable {
            override fun run() {
                if(com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return
                rounds++
                // 从 window.name（跨导航存活）读取捕获的响应
                w.evaluateJavascript(
                    "(function(){var s=window.name||'[]';window.name='';return s;})()"
                ) { v ->
                    try {
                        val arr = org.json.JSONArray(
                            v.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                        )
                        synchronized(captures) {
                            for (i in 0 until arr.length()) {
                                val s = arr.getString(i)
                                if (s.contains("orderList") && captures.none { it == s }) {
                                    captures.add(s)
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "js capture poll fail: $e")
                    }
                    if(com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return@evaluateJavascript
                val (orders, goods) = convert(captures, account)
                if(!isSearch) {
                    com.halo.expressassistant.hub.SyncLedger.cacheBatch(w.context,orders,goods)
                    com.halo.expressassistant.hub.SyncLedger.progress("jd",rounds,orders)
                }
                val total = totalNum(captures)
                Log.i(TAG, "round=$rounds orders=${orders.size} total=$total captures=${captures.size}")
                val lastPage=convert(captures.takeLast(1),account).first
                val emptyEnd=captures.lastOrNull()?.let {runCatching {val b=JSONObject(it).getJSONObject("body");b.has("orderList") && b.optJSONArray("orderList")?.length()==0 && b.optInt("totalNum",-1)==0}.getOrDefault(false)} ?: false
                val reached=if(activePhase)emptyEnd else checkpoint.reached(lastPage) || window.reachedOrderBoundary(lastPage) || emptyEnd || (total>0 && orders.size>=total)
                if(!reached && paging.stalled(captures.size)) {
                    if(activePhase && listComplete)done(orders,"京东历史已到查询边界；待收货列表分页停滞，旧包裹需继续更新")
                    else finish(orders,"京东分页停滞，历史未查完整；已保留 ${orders.size} 条，未推进未完成边界")
                    return@evaluateJavascript
                }
                if(!activePhase && reached)listComplete=true
                if (reached || rounds >= window.pageBudget) {
                    if(!activePhase && scanActive) {
                        activePhase=true
                        w.evaluateJavascript("""(function(){var e=Array.from(document.querySelectorAll('a,span,div,li')).find(function(n){var r=n.getBoundingClientRect();return ['待收货','等待收货'].includes(n.textContent.trim())&&r.width>0&&r.height>0;});if(!e)return false;e.click();return true;})()""") {clicked->
                            if(com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return@evaluateJavascript
                            if(clicked=="true"){rounds=0;paging.reset();handler.postDelayed(this,3000)}else done(orders)
                        }
                    } else done(orders)
                    return@evaluateJavascript
                }
                swipeUp(w)
                handler.postDelayed(this, 3000)
                }
            }
        }
        handler.post(runnable)
    }

    private fun swipeUp(w: WebView) {
        val width = w.width.toFloat()
        val height = w.height.toFloat()
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
        val up = MotionEvent.obtain(downTime, t + 30, MotionEvent.ACTION_UP,
            x, height * 0.08f, 0)
        w.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun totalNum(captures: List<String>): Int {
        for (body in captures) {
            runCatching {
                val total = JSONObject(body).getJSONObject("body").optInt("totalNum", 0)
                if (total > 0) return total
            }
        }
        return 0
    }

    private fun convert(
        captures: List<String>,
        account: com.halo.expressassistant.data.BoundAccount?
    ): Pair<List<ExpressItem>, Map<String, JdGoods>> {
        val byId = LinkedHashMap<String, ExpressItem>()
        val goodsMap = HashMap<String, JdGoods>()
        synchronized(captures) {
            for (body in captures) {
                runCatching {
                    val ol = JSONObject(body).getJSONObject("body").optJSONArray("orderList") ?: return@runCatching
                    for (i in 0 until ol.length()) {
                        val o = ol.getJSONObject(i)
                        val orderId = o.optString("orderId")
                        if (orderId.isBlank()) continue
                        val status = o.optJSONObject("orderStatusInfo")
                        val statusName = status?.optString("orderStatusName") ?: ""
                        val prog = o.optJSONObject("progressInfo")
                        val latestText = (prog?.optString("content") ?: "")
                            .ifBlank { o.optJSONObject("orderStatusInfo")?.optString("stateTip") ?: "" }
                            .ifBlank { statusName }
                        // 物流页完整参数（无 progressLink 的订单用订单字段拼装）
                        val trackLink = (prog?.optString("progressLink") ?: "").ifBlank {
                            val skuId = o.optJSONArray("wareInfoList")?.optJSONObject(0)?.optString("skuId") ?: ""
                            val shopId = o.optJSONObject("shopInfo")?.optString("shopId") ?: ""
                            val dealState = o.optJSONObject("orderStatusInfo")?.optString("originOrderStatus") ?: ""
                            "https://trade.m.jd.com/order/deal_wuliu_jdm.shtml?from=orderdetail" +
                                    "&dealState=$dealState&dealId=$orderId&orderType=${o.optString("orderType")}" +
                                    "&skuid=$skuId&shopid=$shopId&source=m_inner_orderList.track_orderTrack"
                        }
                        val latestTime = prog?.optString("tip") ?: ""
                        val tracking=com.halo.expressassistant.hub.TrackingFields.read(o.toString())
                        val created=com.halo.expressassistant.hub.TrackingFields.time(o.toString(),setOf("orderTime","createTime","createdTime","orderDate"))
                        val completed=com.halo.expressassistant.hub.TrackingFields.time(o.toString(),setOf("finishTime","completeTime","receiveTime","confirmTime"))
                        val state = when {
                            listOf("退款","取消","关闭","退回","拒收").any(statusName::contains) -> 4
                            statusName.contains("完成") || statusName.contains("签收") -> 3
                            statusName.contains("待收货") || statusName.contains("等待收货") -> 5
                            statusName.contains("待发货") || statusName.contains("出库") -> 1
                            else -> 0
                        }
                        val stateNum = when (state) {
                            3 -> 107
                            5 -> 105
                            1 -> 103
                            else -> 105
                        }
                        val item = ExpressItem(
                            id = orderId,
                            companyCode = "JDKD",
                            companyName = tracking.company,
                            mailNo = tracking.waybill.ifBlank {"ORDER-JD-$orderId"},
                            latestText = latestText,
                            latestTime = latestTime,
                            state = state,
                            stateName = statusName,
                            provider = "JingDong",
                            stateNum = stateNum,
                            queryChannel = trackLink,
                            source = "jd",
                            accountId = account?.id ?: "",
                            accountLabel = account?.label ?: "",
                            isOrderNumber=tracking.waybill.isBlank(),orderStatus=statusName,orderCreatedAt=created,
                            completedAt=completed,statusUpdatedAt=com.halo.expressassistant.hub.ParcelTime.parse(latestTime)
                        )
                        val title=o.optJSONArray("wareInfoList")?.optJSONObject(0)?.optString("wareName").orEmpty()
                        if(com.halo.expressassistant.hub.ParcelEligibility.isVirtual(item,title))continue
                        byId[orderId] = item
                        val wares = o.optJSONArray("wareInfoList")
                        if (wares != null && wares.length() > 0) {
                            val w0 = wares.getJSONObject(0)
                            goodsMap[item.mailNo] = JdGoods(
                                name = w0.optString("wareName"),
                                imageUrl = w0.optString("imageUrl"),
                                count = "x" + w0.optInt("num", 1)
                            )
                        }
                    }
                }
            }
        }
        return byId.values.toList() to goodsMap
    }
}
