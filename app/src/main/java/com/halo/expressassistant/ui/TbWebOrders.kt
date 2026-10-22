package com.halo.expressassistant.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.*
import com.halo.expressassistant.api.TbOrderResponse
import com.halo.expressassistant.data.*
import com.halo.expressassistant.hub.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.coroutines.resume

/** Fallback to the first-party MTOP client when a direct order request needs browser context. */
object TbWebOrders {
    const val PAGE="https://h5.m.taobao.com/mlapp/olist.html"
    // Only the official SDK runs in this document. Loading the full order app first can leave
    // its first MTOP processor waiting for interactive login, blocking every later request.
    private const val PROBE_DOCUMENT="""<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><script src="https://g.alicdn.com/mtb/lib-mtop/2.5.1/mtop.js" onerror="window.__parcelTbBootstrapError='SDK_LOAD_FAILED'"></script></body></html>"""
    private suspend fun <T> bounded(millis:Long,reason:String,block:suspend CoroutineScope.()->T):T=try {
        withTimeout(millis,block)
    } catch(e:TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw TbOrderResponse.Failure(reason)
    }
    private suspend fun eval(web:WebView,script:String):String=suspendCancellableCoroutine {c->
        if(WebFetchRegistry.closed(web)){c.resume("null");return@suspendCancellableCoroutine}
        web.evaluateJavascript(script){if(c.isActive)c.resume(it)}
    }
    fun official(url:String):Boolean=Uri.parse(url).let {it.scheme=="https" && (it.host=="taobao.com" || it.host.orEmpty().endsWith(".taobao.com")) && !it.host.orEmpty().startsWith("login.")}
    suspend fun read(web:WebView,page:Int=1):TbOrderResponse.Page=withContext(Dispatchers.Main) {
        val data=buildJsonObject {
            put("tabCode","all");put("page",page);put("OrderType","OrderList");put("templateConfigVersion","0")
            put("appName","tborder");put("appVersion","3.0");put("condition","{\"version\":\"1.0.0\",\"appChannel\":\"\"}")
            put("ttid","201200@taobao_h5_9.18.0");put("requestIdentity","#t#ip#h5")
        }
        bounded(12000,"ORDER_REQUEST_TIMEOUT") {
            if(!official(web.url.orEmpty()))throw TbOrderResponse.Failure("SESSION_NEEDS_LOGIN")
            if(eval(web,"!!(window.lib && window.lib.mtop && window.lib.mtop.request)")!="true")throw TbOrderResponse.Failure("SDK_LOAD_FAILED")
            eval(web,"""(function(){window.__parcelTbOrderResult=null;try {window.lib.mtop.request({api:'mtop.taobao.order.queryboughtlistV2',v:'1.0',data:$data,type:'post',dataType:'json',H5Request:true,ecode:'1',isSec:'0',LoginRequest:false,needLogin:false,timeout:10000}).then(function(r){window.__parcelTbOrderResult=JSON.stringify(r)},function(e){window.__parcelTbOrderResult=JSON.stringify({ret:e&&e.ret||['REQUEST_FAILED'],data:{}})})}catch(e){window.__parcelTbOrderResult=JSON.stringify({ret:['SCRIPT_REQUEST_FAILED'],data:{}})}return true})()""")
            while(true) {
                delay(250)
                if(!official(web.url.orEmpty()))throw TbOrderResponse.Failure("SESSION_NEEDS_LOGIN")
                val raw=eval(web,"window.__parcelTbOrderResult")
                val body=runCatching {(Json.parseToJsonElement(raw) as? JsonPrimitive)?.contentOrNull}.getOrNull()
                if(!body.isNullOrBlank())return@bounded TbOrderResponse.read(body)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetch(context:Context,account:BoundAccount,page:Int):TbOrderResponse.Page=CainiaoWebFetcher.sessionMutex.withLock {
        withContext(Dispatchers.Main){SessionCookies.restore(context,"taobao",account)}
        probeCurrentSession(context,page) {CainiaoWebFetcher.accountMatches(account)}.also {
            withContext(Dispatchers.Main) {
                SessionCookies.persistBrowser(context,"taobao",account.id)
                Store.accountById(context,"taobao",account.id)?.let {Store.updateAccount(context,"taobao",it.copy(payload=Store.cookiePayload(SessionCookies.current("taobao"))))}
            }
        }
    }
    /** Caller already owns sessionMutex, e.g. the visible login activity. Never re-lock here. */
    @SuppressLint("SetJavaScriptEnabled")
    internal suspend fun probeCurrentSession(context:Context,page:Int=1,accountMatches:()->Boolean):TbOrderResponse.Page=
        withContext(Dispatchers.Main) {
            val web=WebView(context)
            WebFetchRegistry.register(context,web)
            try {
                web.settings.apply {javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false}
                CookieManager.getInstance().setAcceptThirdPartyCookies(web,true)
                var pageLoaded=false
                var pageError:String?=null
                web.webViewClient=object:WebViewClient() {
                    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                        if(!request.isForMainFrame)return false
                        if(!official(request.url.toString())){pageError="SESSION_NEEDS_LOGIN";return true}
                        return false
                    }
                    override fun onPageFinished(view:WebView,url:String){pageLoaded=true}
                    override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame)pageError="PAGE_LOAD_FAILED"}
                    override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse){if(request.isForMainFrame)pageError="PAGE_LOAD_FAILED"}
                }
                web.loadDataWithBaseURL(PAGE,PROBE_DOCUMENT,"text/html","UTF-8",null)
                bounded(16000,"SDK_PREPARE_TIMEOUT") {
                    while(true) {
                        delay(400)
                        check(!WebFetchRegistry.closed(web)) {"淘宝订单读取已停止"}
                        pageError?.let {throw TbOrderResponse.Failure(it)}
                        if(pageLoaded && !official(web.url.orEmpty()))throw TbOrderResponse.Failure("SESSION_NEEDS_LOGIN")
                        if(!official(web.url.orEmpty()))continue
                        if(eval(web,"window.__parcelTbBootstrapError").contains("SDK_LOAD_FAILED"))throw TbOrderResponse.Failure("SDK_LOAD_FAILED")
                        if(eval(web,"!!(window.lib && window.lib.mtop && window.lib.mtop.request)")!="true")continue
                        if(!accountMatches())throw TbOrderResponse.Failure("ACCOUNT_CHANGED")
                        break
                    }
                }
                val response=read(web,page)
                if(!accountMatches())throw TbOrderResponse.Failure("ACCOUNT_CHANGED")
                response
            } finally {
                if(!WebFetchRegistry.closed(web)){WebFetchRegistry.release(web);web.stopLoading();web.destroy()}
            }
        }
}
