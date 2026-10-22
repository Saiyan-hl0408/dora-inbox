package com.halo.expressassistant.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.JdGoods
import com.halo.expressassistant.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * 拼多多物流轨迹 + 商品解析：隐藏 WebView 加载拼多多 H5 订单详情页，
 * JS 钩子捕获页面 SDK 自动签名后的物流接口响应（绕开 anti_content），
 * 容错解析出轨迹时间线（DetailPoint）与商品（JdGoods）。
 *
 * 轨迹/商品字段随拼多多版本有差异，解析器按「候选键 + 递归扫描」实现，
 * 真机验证阶段以 docs/API.md 记录的现场字段为准。
 */
object PddTraceFetcher {

    private const val TAG = "PddTraceFetcher"
    // 拼多多 H5 订单详情页（order_link_url 实测为 order.html?order_sn=…；物流/运单号/轨迹在该页接口里）
    const val TRACE_PAGE = "https://mobile.yangkeduo.com/order.html?order_sn="

    private val TIME_KEYS = setOf(
        "time", "create_time", "update_time", "logistics_time", "tracking_time",
        "subtitle", "format_time", "formatTime", "gmt_create"
    )
    private val DESC_KEYS = setOf(
        "desc", "description", "context", "status_desc", "statusDesc", "text",
        "content", "track_desc", "logistics_desc", "logistics_status_desc",
        "sub_desc", "title"
    )

    private val GOODS_NAME_KEYS = setOf(
        "goods_name", "goodsName", "goods_title", "goods_brief", "product_name",
        "item_name", "sku_name", "goods_name_text"
    )
    private val GOODS_PIC_KEYS = setOf(
        "goods_image_url", "goodsImageUrl", "image_url", "thumbnail", "pic", "image",
        "goods_img", "goods_thumb_url"
    )
    private val GOODS_NUM_KEYS = setOf("goods_num", "goodsCount", "quantity", "num", "count")

    private val handler = Handler(Looper.getMainLooper())

    data class TraceResult(val points: List<DetailPoint>, val goods: JdGoods?, val waybill:String="",val company:String="",
        val station:String="",val deliveryAddress:String="",val phoneTail:String="",val pickupHint:String="",val pickupCode:String="")

    suspend fun fetchMetadata(act:Activity,item:ExpressItem,cookies:String):TraceResult? = run(act,orderIdOf(item),cookies,true)

    /** 详情页时间线（source=pdd）——旧接口：第一个启用拼多多账号 */
    suspend fun fetch(act: Activity, item: ExpressItem): List<DetailPoint>? =
        fetchWith(act, item, Store.pddCookies(act))

    /** 多源绑定：按指定拼多多账号凭证；silent=true 用于后台补抓（永不显示 WebView） */
    suspend fun fetchWith(act: Activity, item: ExpressItem, pddCookies: String, silent: Boolean = false): List<DetailPoint>? =
        run(act, orderIdOf(item), pddCookies, silent)?.points

    /** 完整结果（轨迹 + 商品，供详情页统一更新） */
    suspend fun fetchResult(act: Activity, item: ExpressItem): TraceResult? =
        run(act, orderIdOf(item), Store.pddCookies(act), false)

    /** 商品扫码解析（详情页「获取商品信息」按钮）——旧接口 */
    suspend fun resolveGoods(act: Activity, item: ExpressItem): JdGoods? =
        resolveGoodsWith(act, item, Store.pddCookies(act))

    /** 多源绑定：按指定拼多多账号凭证解析商品 */
    suspend fun resolveGoodsWith(act: Activity, item: ExpressItem, pddCookies: String): JdGoods? =
        run(act, orderIdOf(item), pddCookies, false)?.goods

    private fun orderIdOf(item: ExpressItem): String = item.queryChannel.ifBlank { item.mailNo }

    /**
     * 轨迹「双写」：① 存入 pdd_traces 缓存；② 回写卡片外显字段
     * latestText/latestTime/pickupCode（列表接口只有状态提示，真实最后轨迹在物流页）。
     */
    fun applyTraceToItem(act: Activity, item: ExpressItem, points: List<DetailPoint>, done: Boolean = item.state == 3) {
        if (points.isEmpty()) return
        val resolved=com.halo.expressassistant.hub.PddLogistics.apply(item,points)
        Store.savePddTrace(act, item.mailNo, points, com.halo.expressassistant.hub.ParcelPresentation.stage(resolved)==com.halo.expressassistant.hub.ParcelPresentation.Stage.CLOSED)
        val last = com.halo.expressassistant.hub.LatestLogistics.point(points) ?: return
        val code = runCatching { GoodsPresentation.pickupCodeFrom(last.context) }.getOrNull()
        val items = Store.items(act).map {
            if (it.mailNo == item.mailNo && it.source == "pdd" && it.accountId==item.accountId) {
                com.halo.expressassistant.hub.PddLogistics.apply(it,points)
            } else {
                it
            }
        }
        Store.saveItems(act, items)
    }

    /** 已有缓存 → 回填卡片（处理历史数据：卡片轨迹外显此前未生效） */
    fun applyCachedTraceToItem(act: Activity, item: ExpressItem) {
        val cached = Store.pddTraces(act, item.mailNo) ?: return
        val latest=com.halo.expressassistant.hub.LatestLogistics.point(cached)
        if (item.logisticsVersion==0 || item.latestText.isBlank() || item.latestTime.isBlank() || (latest!=null && com.halo.expressassistant.hub.ParcelTime.parse(latest.time)>com.halo.expressassistant.hub.ParcelTime.parse(item.latestTime))) {
            applyTraceToItem(act, item, cached, done = item.state == 3)
        }
    }

    private suspend fun run(act: Activity, orderId: String, pddCookies: String, silent: Boolean): TraceResult? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { com.halo.expressassistant.hub.WebFetchRegistry.cancel(act) }
                start(act, orderId, pddCookies, silent) { r ->
                    if (cont.isActive) cont.resume(r)
                }
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun start(act: Activity, orderId: String, pddCookies: String, silent: Boolean = false, onDone: (TraceResult?) -> Unit) {
        val captures = ArrayList<Pair<String, String>>()
        var finished = false
        var challengeShown = false
        var collectStarted = false
        var retriedLogin = false
        val pageHolder = arrayOf("")
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
        PddCapture.setCookiesFor(act, pddCookies)
        PddCapture.attach(act, w)

        fun finish(result: TraceResult?) {
            if (finished || com.halo.expressassistant.hub.WebFetchRegistry.closed(w)) return
            finished = true
            handler.postDelayed({
                com.halo.expressassistant.hub.WebFetchRegistry.release(w)
                PddCapture.detach(w)
                val captured=convert(captures)
                val r = result?.copy(waybill=result.waybill.ifBlank {captured.waybill},company=result.company.ifBlank {captured.company}) ?: captured
                onDone(r)
            }, 200)
        }

        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if(com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return
                pageHolder[0] = url
                Log.i(TAG, "official logistics page loaded")
                // 真正的登录页才算（goods_express.html?…&needs_login=1 只是参数，不是登录页）
                val realLogin = url.contains("login.html") || url.contains("passport") || url.contains("/login") || url.contains("login?")
                if (realLogin) {
                    if (challengeShown) return
                    challengeShown = true
                    if (silent) {
                        // 后台补抓：静默；首次遇登录页重试一次（可能只是会话冷启动），再失败才放弃该单
                        Log.i(TAG, "login page during silent backfill -> retry once")
                        handler.post {
                            if (!retriedLogin) {
                                retriedLogin = true
                                w.reload()
                            } else {
                                finish(null)
                            }
                        }
                    } else {
                        handler.post {
                            w.alpha = 1f
                            w.bringToFront()
                            android.widget.Toast.makeText(
                                act, "拼多多需要登录或安全验证，请在页面中完成", android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    return
                }
                if (url.contains("order") || url.contains("logistic")) {
                    if (collectStarted) return
                    collectStarted = true
                    handler.postDelayed({ collect(w, captures, pageHolder, ::finish) }, 2500)
                }
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
        w.loadUrl(TRACE_PAGE + orderId)
        handler.postDelayed({
            if (!finished) {
                Log.w(TAG, "timeout, finalize with ${captures.size} captures")
                finish(null)
            }
        }, 45_000)
    }

    private fun collect(
        w: WebView,
        captures: ArrayList<Pair<String, String>>,
        pageHolder: Array<String>,
        finish: (TraceResult?) -> Unit
    ) {
        collectOfficialDetail(w,captures,pageHolder,finish)
    }

    /** Read the embedded current-order payload, then its exact official logistics link. */
    private fun collectOfficialDetail(w:WebView,captures:ArrayList<Pair<String,String>>,pageHolder:Array<String>,finish:(TraceResult?)->Unit) {
        var rounds=0
        var followed=false
        var address="";var phoneTail=""
        val poll=object:Runnable {
            override fun run() {
                if(com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return
                rounds++
                PddCapture.readCaptures(w) {batch->
                    if(com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return@readCaptures
                    batch.forEach {if(it !in captures)captures.add(it)}
                    w.evaluateJavascript("""(function(){var d=window.rawData&&window.rawData.data||{};return JSON.stringify({data:{express:d.express,mobile:d.mobile,shippingAddress:d.shippingAddress,address:d.address,orderButtons:(d.orderButtons||[]).filter(function(b){return b.briefPrompt==='查看物流';})},text:document.body?document.body.innerText.slice(0,30000):''});})()""") {raw->
                        if(com.halo.expressassistant.hub.WebFetchRegistry.closed(w))return@evaluateJavascript
                        val json=runCatching {org.json.JSONTokener(raw).nextValue() as? String}.getOrNull().orEmpty()
                        val snapshot=com.halo.expressassistant.hub.PddDetailPayload.read(json)
                        address=snapshot.deliveryAddress.ifBlank {address};phoneTail=snapshot.phoneTail.ifBlank {phoneTail}
                        if(!followed && snapshot.nextUrl.isNotBlank()) {
                            followed=true;w.loadUrl(snapshot.nextUrl);handler.postDelayed(this,800);return@evaluateJavascript
                        }
                        val logisticsLoaded=android.net.Uri.parse(pageHolder[0]).path=="/goods_express.html"
                        if(snapshot.points.isNotEmpty() && (logisticsLoaded || (!followed && snapshot.nextUrl.isBlank()))) {
                            finish(TraceResult(snapshot.points,null,snapshot.waybill,snapshot.company,snapshot.station,address,phoneTail,snapshot.hint,snapshot.pickupCode));return@evaluateJavascript
                        }
                        if(rounds>=16){val fallback=convert(captures);finish(fallback.takeIf {it.points.isNotEmpty()});return@evaluateJavascript}
                        handler.postDelayed(this,700)
                    }
                }
            }
        }
        handler.post(poll)
    }

    private fun blockScheme(u: String): Boolean {
        if (u.startsWith("pinduoduo://") || u.startsWith("xunmeng://")) {
            Log.i(TAG, "blocked app navigation during background query")
            return true
        }
        return false
    }

    /* ─────────────── 容错解析 ─────────────── */

    private fun convert(captures: List<Pair<String, String>>): TraceResult {
        val pointsMap = LinkedHashMap<String, DetailPoint>()
        var goods: JdGoods? = null
        var metadata=com.halo.expressassistant.hub.TrackingFields.Metadata("","")
        for ((url, body) in captures) {
            if (!PddCapture.looksRelevant(url, body)) continue
            val root = runCatching { JSONObject(body) }.getOrNull() ?: continue
            collectPoints(root, pointsMap)
            // A list response can contain unrelated orders; only the current detail/logistics
            // response is allowed to supply this parcel's waybill.
            if(!url.contains("order_list") && listOf("express","logistic","order_detail").any(url::contains)) {
                val found=com.halo.expressassistant.hub.TrackingFields.read(body)
                if(metadata.waybill.isBlank() && found.waybill.isNotBlank())metadata=found
            }
            // 商品只从列表接口取（详情页 order_detail_group.goods_list 是推荐流，不要误采）
            if (goods == null && url.contains("order_list_v4")) goods = extractGoods(root)
        }
        val points = pointsMap.values.toList()

        return TraceResult(points, goods,metadata.waybill,metadata.company)
    }

    private fun collectPoints(node: Any?, out: MutableMap<String, DetailPoint>) {
        when (node) {
            is JSONObject -> {
                // 轨迹点对象：同时有时间与描述
                val time = PddCapture.firstString(node, TIME_KEYS)
                val desc = PddCapture.firstString(node, DESC_KEYS)
                if (time.isNotBlank() && desc.isNotBlank() && desc != time) {
                    val key = "$time|$desc"
                    if (!out.containsKey(key)) {
                        out[key] = DetailPoint(context = desc, time = time, formattedTime = time)
                    }
                    return
                }
                for (k in node.keys()) {
                    val v = node.opt(k)
                    if (v is JSONObject || v is JSONArray) collectPoints(v, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val v = node.opt(i)
                    if (v is JSONObject || v is JSONArray) collectPoints(v, out)
                }
            }
        }
    }

    private fun extractGoods(obj: JSONObject): JdGoods? {
        val name = PddCapture.firstStringDeep(obj, GOODS_NAME_KEYS, 6)
        if (name.isBlank()) return null
        var pic = PddCapture.firstStringDeep(obj, GOODS_PIC_KEYS, 6)
        if (pic.startsWith("//")) pic = "https:$pic"
        val count = PddCapture.firstStringDeep(obj, GOODS_NUM_KEYS, 6)
        return JdGoods(
            name = name.take(200),
            imageUrl = pic,
            count = if (count.isNotBlank()) "x$count" else ""
        )
    }
}
