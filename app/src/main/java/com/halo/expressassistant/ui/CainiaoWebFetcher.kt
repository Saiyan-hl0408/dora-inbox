package com.halo.expressassistant.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.*
import com.halo.expressassistant.data.*
import com.halo.expressassistant.hub.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.coroutines.resume

/** Run the verified first-party MTOP client in its own official origin; no guessed HTTP signing. */
object CainiaoWebFetcher {
    val sessionMutex=Mutex()
    fun official(url:String):Boolean = Uri.parse(url).let {it.scheme=="https" && it.host=="pages-fast.m.taobao.com" && it.path.orEmpty().startsWith("/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/")}
    fun script(context:Context)=context.assets.open("cainiao-school.js").bufferedReader().use {it.readText()}
    fun accountMatches(account:BoundAccount)=CookiePairs.sameTaobaoAccount(CookiePairs.parse(SessionCookies.current("taobao")),CookiePairs.parse(Store.cookieOf(account.payload)))
    suspend fun fetch(context:Context,account:BoundAccount,number:String):CainiaoPickup.Result = sessionMutex.withLock {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val session=Session(context,account,number) {if(continuation.isActive)continuation.resume(it)}
                continuation.invokeOnCancellation {Handler(Looper.getMainLooper()).post {session.close()}}
                session.start()
            }
        }
    }
    private class Session(val context:Context,val account:BoundAccount,val number:String,val complete:(CainiaoPickup.Result)->Unit) {
        val handler=Handler(Looper.getMainLooper())
        lateinit var web:WebView
        var finished=false
        var launched=false
        val started=System.currentTimeMillis()
        val tick=object:Runnable {override fun run(){poll()}}
        @SuppressLint("SetJavaScriptEnabled") fun start() {
            web=WebView(context);WebFetchRegistry.register(context,web)
            web.settings.apply {javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false}
            CookieManager.getInstance().setAcceptThirdPartyCookies(web,true)
            SessionCookies.restore(context,"taobao",account)
            web.webViewClient=object:WebViewClient() {
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    if(!request.isForMainFrame)return false
                    if(!official(request.url.toString())){finish(CainiaoPickup.Result(CainiaoPickup.Outcome.LOGIN,number));return true}
                    return false
                }
                override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {
                    if(request.isForMainFrame)finish(CainiaoPickup.Result(CainiaoPickup.Outcome.FAILED,number))
                }
            }
            val width=context.resources.displayMetrics.widthPixels
            web.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(800,android.view.View.MeasureSpec.EXACTLY))
            web.layout(0,0,width,800)
            web.loadUrl(CainiaoPickupService.pageUrl(context))
            handler.postDelayed(tick,400)
        }
        fun poll() {
            if(finished)return
            if(WebFetchRegistry.closed(web)){finish(CainiaoPickup.Result(CainiaoPickup.Outcome.FAILED,number));return}
            if(System.currentTimeMillis()-started>16000){finish(CainiaoPickup.Result(CainiaoPickup.Outcome.FAILED,number));return}
            if(!official(web.url.orEmpty())){handler.postDelayed(tick,400);return}
            if(!launched) {
                web.evaluateJavascript("typeof window.lib!=='undefined' && !!window.lib.mtop") {ready->
                    if(finished)return@evaluateJavascript
                    if(ready=="true") {
                        if(!accountMatches(account)){finish(CainiaoPickup.Result(CainiaoPickup.Outcome.LOGIN,number));return@evaluateJavascript}
                        launched=true
                        web.evaluateJavascript(script(context),null)
                        web.evaluateJavascript("window.__parcelCainiao.query(${JsonPrimitive(number)});void 0",null)
                    }
                    handler.postDelayed(tick,400)
                }
            } else web.evaluateJavascript("JSON.stringify(window.__parcelCainiao.events.splice(0,1))") {raw->
                if(finished)return@evaluateJavascript
                val events=runCatching {Json.parseToJsonElement(Json.parseToJsonElement(raw).jsonPrimitive.content).jsonArray}.getOrNull()
                val event=events?.firstOrNull()
                if(event!=null) {
                    if(!accountMatches(account))finish(CainiaoPickup.Result(CainiaoPickup.Outcome.LOGIN,number))
                    else finish(CainiaoPickup.read(event.toString(),number))
                } else handler.postDelayed(tick,300)
            }
        }
        fun finish(result:CainiaoPickup.Result) {
            if(finished)return
            if(accountMatches(account)) {
                SessionCookies.persistBrowser(context,"taobao",account.id)
                Store.accountById(context,"taobao",account.id)?.let {current->Store.updateAccount(context,"taobao",current.copy(payload=Store.cookiePayload(SessionCookies.current("taobao"))))}
            }
            close();complete(result)
        }
        fun close() {
            if(finished)return
            finished=true;handler.removeCallbacksAndMessages(null)
            if(::web.isInitialized && !WebFetchRegistry.closed(web)){WebFetchRegistry.release(web);web.stopLoading();web.destroy()}
        }
    }
}
