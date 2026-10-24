package com.halo.expressassistant.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.halo.expressassistant.data.*
import com.halo.expressassistant.hub.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.coroutines.resume

/** Official mobile module inside the official origin. The module owns auth/signing. */
object JdlWebFetcher {
    data class Result(val items:List<ExpressItem>,val goods:Map<String,JdGoods>,val error:String?,val complete:Boolean,val metadataRead:Set<String> = emptySet())
    suspend fun fetch(context:Context,account:BoundAccount,onlyNumber:String?=null):Result = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val session=Session(context,account,onlyNumber) {if(continuation.isActive)continuation.resume(it)}
            continuation.invokeOnCancellation {Handler(Looper.getMainLooper()).post {session.close()}}
            session.start()
        }
    }
    suspend fun fetchMetadata(context:Context,account:BoundAccount,numbers:List<String>):Result = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation->
            val session=Session(context,account,null) {if(continuation.isActive)continuation.resume(it)}
            session.metadataNumbers=numbers.distinct().take(6)
            continuation.invokeOnCancellation {Handler(Looper.getMainLooper()).post {session.close()}}
            session.start()
        }
    }
    private class Session(val context:Context,val account:BoundAccount,val onlyNumber:String?,val callback:(Result)->Unit) {
        var metadataNumbers:List<String> = emptyList()
        private val metadataRead=linkedSetOf<String>()
        private val handler=Handler(Looper.getMainLooper())
        private lateinit var web:WebView
        private val items=linkedMapOf<String,ExpressItem>()
        private val goods=linkedMapOf<String,JdGoods>()
        private var finished=false
        private var launched=false
        private var authPageAt=0L
        private var activeAt=System.currentTimeMillis()
        private var verifiedCount=0
        private val listDiagnostics=ArrayDeque<String>()
        private val window=SyncLedger.window(context,"jd",account.id)
        private val script=context.assets.open("jdl-mobile.js").bufferedReader().use {it.readText()}
        private val tick=object:Runnable {override fun run(){poll()}}
        @SuppressLint("SetJavaScriptEnabled")
        fun start() {
            web=WebView(context);WebFetchRegistry.register(context,web)
            web.alpha=0f;web.importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            web.settings.apply {
                javaScriptEnabled=true;domStorageEnabled=true;useWideViewPort=true;loadWithOverviewMode=true
                allowFileAccess=false;allowContentAccess=false
                userAgentString="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            }
            SessionCookies.restore(context,"jd",account)
            CookieManager.getInstance().setAcceptThirdPartyCookies(web,true)
            if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))WebViewCompat.addDocumentStartJavaScript(
                web,context.assets.open("jdl-transport.js").bufferedReader().use {it.readText()},setOf("https://kd.jd.com")
            )
            web.webViewClient=object:WebViewClient() {
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    if(!request.isForMainFrame)return false
                    val uri=request.url
                    val host=uri.host.orEmpty()
                    // The official client can silently renew SSO on passport/plogin.
                    return uri.scheme!="https" || !(host=="jd.com" || host.endsWith(".jd.com"))
                }
            }
            val decor=(context as? Activity)?.window?.decorView as? ViewGroup
            if(decor!=null)decor.addView(web,0,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
            else {
                val metrics=context.resources.displayMetrics
                web.measure(android.view.View.MeasureSpec.makeMeasureSpec(metrics.widthPixels,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(metrics.heightPixels,android.view.View.MeasureSpec.EXACTLY))
                web.layout(0,0,metrics.widthPixels,metrics.heightPixels)
            }
            web.loadUrl(JdLogisticsSource.LIST_URL)
            handler.postDelayed(tick,500)
        }
        private fun poll() {
            if(finished || WebFetchRegistry.closed(web))return
            if(System.currentTimeMillis()-activeAt>45000){finish("京东物流读取超时；已保存成功读取的数据，请检查会话后重试",false);return}
            val host=Uri.parse(web.url.orEmpty()).host
            if(host!=null && host!="kd.jd.com") {
                if(host=="jd.com" || host.endsWith(".jd.com")) {
                    if(authPageAt==0L)authPageAt=System.currentTimeMillis()
                    // Let the official SSO page finish its own immediate ticket redirect.
                    if(System.currentTimeMillis()-authPageAt<4000){handler.postDelayed(tick,500);return}
                    web.evaluateJavascript("(function(){return !!document.querySelector('input[type=password],input[type=tel]') && /登录|验证码/.test(document.body?document.body.innerText:'');})()") {loginUi->
                        if(finished)return@evaluateJavascript
                        if(loginUi=="true")finish("京东官网要求登录验证，请在绑定页完成验证 [JD-LOGIN-REQUIRED]",false)
                        else handler.postDelayed(tick,500)
                    }
                } else finish("京东物流跳转到非官网页面，未继续读取 [JD-UNEXPECTED-PAGE]",false)
                return
            }
            authPageAt=0L
            if(!launched) {
                web.evaluateJavascript("location.hostname==='kd.jd.com' && performance.getEntriesByType('resource').some(x=>/\\/actions-[\\w-]+\\.js/.test(x.name))") {ready->
                    if(finished)return@evaluateJavascript
                    if(ready=="true") {
                        launched=true
                        web.evaluateJavascript(script,null)
                        val refresh=if(onlyNumber!=null)listOf(onlyNumber) else Store.items(context).filter {
                            it.source=="jd" && it.accountId==account.id && !it.isOrderNumber && it.queryChannel.startsWith("https://kd.jd.com/express/orderDetail") && ParcelPresentation.stage(it) in setOf(ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.TRANSIT,ParcelPresentation.Stage.NOT_SHIPPED,ParcelPresentation.Stage.UNVERIFIED)
                        }.map {it.mailNo}.distinct()
                        val config=buildJsonObject {
                            put("metadataNumbers",JsonArray(metadataNumbers.map {JsonPrimitive(it)}))
                            put("now",window.now);put("since",window.since);put("onlyNumber",onlyNumber?.let {JsonPrimitive(it)} ?: JsonNull)
                            put("refresh",JsonArray(refresh.map {JsonPrimitive(it)}))
                            val prefs=HubData.prefs(context)
                            put("cached",JsonArray(Store.items(context).filter {it.source=="jd" && it.accountId==account.id && !ActiveParcelRefresh.includes(it) && HubData.prefs(context).getInt("jd_detail_${account.id}_${it.mailNo}_schema",0)>=21}.map {item->
                                val prefix="jd_detail_${account.id}_${item.mailNo}"
                                buildJsonObject {
                                    put("number",item.mailNo);put("status",prefs.getString(prefix+"_status","").orEmpty())
                                    put("version",prefs.getLong(prefix+"_version",0));put("checkedAt",prefs.getLong(prefix+"_at",0))
                                    put("at",item.completedAt)
                                    put("complete",when(ParcelPresentation.stage(item)) {
                                        ParcelPresentation.Stage.PICKUP->item.pickupCode.isNotBlank() && item.pickupStation.isNotBlank()
                                        ParcelPresentation.Stage.CLOSED->item.completedAt>0
                                        ParcelPresentation.Stage.TRANSIT,ParcelPresentation.Stage.NOT_SHIPPED->true
                                        else->false
                                    })
                                }
                            }))
                        }
                        web.evaluateJavascript("window.__parcelJdl.run($config);void 0",null)
                    }
                    if(!finished)handler.postDelayed(tick,500)
                }
            } else web.evaluateJavascript("JSON.stringify(window.__parcelJdl.events.splice(0,20))") {raw->
                if(finished)return@evaluateJavascript
                runCatching {
                    val decoded=(Json.parseToJsonElement(raw) as JsonPrimitive).content
                    (Json.parseToJsonElement(decoded) as JsonArray).forEach {handle(it.jsonObject)}
                }.onFailure {finish("京东物流页面数据格式发生变化，未推进同步范围",false)}
                if(!finished)handler.postDelayed(tick,250)
            }
        }
        private fun handle(event:JsonObject) {
            activeAt=System.currentTimeMillis()
            when(event["type"]?.jsonPrimitive?.content) {
                "accounts"->{verifiedCount=event["count"]?.jsonPrimitive?.intOrNull ?: 0;if(metadataNumbers.isEmpty())HubData.prefs(context).edit().putInt("jd_verified_count_${account.id}",verifiedCount).apply()}
                "cached"->{
                    val number=event["number"]?.jsonPrimitive?.content.orEmpty()
                    val summary=event["summary"] as? JsonObject
                    summary?.let {JdlPayload.goods(it)}?.let {SyncLedger.cacheBatch(context,emptyList(),mapOf(number to it))}
                    Store.items(context).firstOrNull {it.source=="jd" && it.accountId==account.id && it.mailNo==number}?.let {items[number]=it}
                }
                "metadata"->{
                    val order=event.getValue("order").jsonObject
                    val number=order["baseOrderInfo"]?.jsonObject?.get("customOrderNo")?.jsonPrimitive?.content.orEmpty()
                    if(number in metadataNumbers) {
                        metadataRead.add(number)
                        JdlPayload.goods(order,event["summary"] as? JsonObject)?.let {fresh->
                            val merged=GoodsCache.merge(Store.jdGoods(context)[number],fresh)
                            goods[number]=merged;SyncLedger.cacheBatch(context,emptyList(),mapOf(number to merged))
                        }
                    }
                }
                "item"->{
                    val order=event.getValue("order").jsonObject
                    val item=JdlPayload.parse(order,account.id,account.label,event["tail"]?.jsonPrimitive?.content.orEmpty(),event["trace"] as? JsonArray ?: JsonArray(emptyList()))
                    items[item.mailNo]=item;JdlPayload.goods(order,event["summary"] as? JsonObject)?.let {goods[item.mailNo]=GoodsCache.merge(Store.jdGoods(context)[item.mailNo],it)}
                    SyncLedger.cacheBatch(context,listOf(item),goods.filterKeys {it==item.mailNo})
                    val prefix="jd_detail_${account.id}_${item.mailNo}"
                    HubData.prefs(context).edit().putLong(prefix+"_at",System.currentTimeMillis())
                        .putInt(prefix+"_schema",21).putString(prefix+"_status",event["listStatus"]?.jsonPrimitive?.content.orEmpty())
                        .putLong(prefix+"_version",event["listVersion"]?.jsonPrimitive?.longOrNull ?: 0).apply()
                }
                "list_diagnostic"->{
                    // Whitelist scalars only. Never persist responses, account IDs or waybills.
                    fun n(key:String)=event[key]?.jsonPrimitive?.intOrNull ?: 0
                    val pass=event["pass"]?.jsonPrimitive?.content?.takeIf {it in setOf("history","pickup","transit","official")}.orEmpty()
                    val filters=event["statuses"]?.jsonPrimitive?.content?.takeIf {it.matches(Regex("[0-9,]{0,20}"))}.orEmpty()
                    val since=event["since"]?.jsonPrimitive?.longOrNull ?: window.since
                    listDiagnostics.addLast("账号序号 ${n("account")}；分类 $pass；我收=1；状态 [${filters}]；第 ${n("page")} 页；原始 ${n("rows")} / 总计 ${n("total")}；本地边界 ${ParcelTime.format(since)}")
                    if(listDiagnostics.size>40)listDiagnostics.removeFirst()
                }
                "progress"->SyncLedger.progress("jd",event["page"]?.jsonPrimitive?.intOrNull ?: 0,items.values.toList())
                "done"->{
                    val complete=event["complete"]?.jsonPrimitive?.booleanOrNull==true
                    val since=event["since"]?.jsonPrimitive?.longOrNull ?: window.since
                    if(complete && onlyNumber==null && metadataNumbers.isEmpty())SyncLedger.stage("jd",account.id,items.values.toList(),window.copy(sinceOverride=since))
                    val limited=event["limited"]?.jsonPrimitive?.booleanOrNull==true
                    finish(if(!complete)"部分京东物流详情未返回；已保存成功项，未推进同步范围" else null,complete,if(limited)"京东按近半年查询；更早历史保留本机缓存" else null)
                }
                "error"->finish(JdlFailure.message(event),false)
            }
        }
        private fun finish(error:String?,complete:Boolean,notice:String?=null) {
            if(finished)return
            val summary=error ?: "京东同步完成：${items.size} 票，已查询本人及关联账号共 $verifiedCount 个"+(notice?.let {"；$it"} ?: "")
            HubData.prefs(context).edit().putString(if(metadataNumbers.isEmpty())"jd_sync_diagnostic" else "jd_image_diagnostic",summary+if(listDiagnostics.isEmpty())"" else "\n"+listDiagnostics.joinToString("\n")).apply()
            SessionCookies.persistBrowser(context,"jd",account.id)
            if(complete && onlyNumber==null && metadataNumbers.isEmpty())HubData.prefs(context).edit().putLong("jd_sync_success_${account.id}",System.currentTimeMillis()).apply()
            if(complete)Store.updateAccount(context,"jd",account.copy(payload=Store.cookiePayload(SessionCookies.current("jd"))))
            close();callback(Result(items.values.toList(),goods,error,complete,metadataRead))
        }
        fun close() {
            if(finished)return
            finished=true;handler.removeCallbacksAndMessages(null)
            if(::web.isInitialized && !WebFetchRegistry.closed(web)) {
                WebFetchRegistry.release(web);web.stopLoading();(web.parent as? ViewGroup)?.removeView(web);web.destroy()
            }
        }
    }
}
