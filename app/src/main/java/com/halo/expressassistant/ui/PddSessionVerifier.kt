package com.halo.expressassistant.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.webkit.*
import com.halo.expressassistant.hub.WebFetchRegistry

/** Check the same live CookieManager in a separate page; never replace the QR page. */
class PddSessionVerifier(private val context:Context,private val ua:String,private val callback:(Boolean)->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private var web:WebView?=null
    private var finished=false
    private val timeout=Runnable {complete(false)}
    private val tick=object:Runnable {
        override fun run() {
            val page=web ?: return
            if(finished)return
            val uri=Uri.parse(page.url.orEmpty())
            if(uri.host=="mobile.yangkeduo.com" && uri.path=="/orders.html") {
                page.evaluateJavascript("(function(){var s=window.rawData&&window.rawData.ordersStore;return !!(s&&s.uid&&String(s.uid)!=='0');})()") {valid->
                    if(!finished){if(valid=="true")complete(true) else handler.postDelayed(this,500)}
                }
            } else handler.postDelayed(this,500)
        }
    }
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    fun start(load:((WebView)->Unit)?=null) {
        if(finished || web!=null)return
        val page=WebView(context);web=page;WebFetchRegistry.register(context,page)
        page.settings.apply {javaScriptEnabled=true;domStorageEnabled=true;userAgentString=ua;allowFileAccess=false;allowContentAccess=false}
        CookieManager.getInstance().setAcceptThirdPartyCookies(page,true)
        page.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                if(!request.isForMainFrame)return false
                val uri=request.url
                return uri.scheme!="https" || uri.host!="mobile.yangkeduo.com"
            }
        }
        // Native WebView must have a viewport even when it is used only for validation.
        val metrics=context.resources.displayMetrics
        page.measure(android.view.View.MeasureSpec.makeMeasureSpec(metrics.widthPixels,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(metrics.heightPixels,android.view.View.MeasureSpec.EXACTLY))
        page.layout(0,0,metrics.widthPixels,metrics.heightPixels)
        handler.postDelayed(timeout,12000)
        if(load==null)page.loadUrl("https://mobile.yangkeduo.com/orders.html") else load(page)
        handler.postDelayed(tick,500)
    }
    private fun complete(valid:Boolean){if(finished)return;close();callback(valid)}
    fun close() {
        if(finished)return
        finished=true;handler.removeCallbacksAndMessages(null)
        web?.let {WebFetchRegistry.release(it);it.stopLoading();it.destroy()};web=null
    }
}
