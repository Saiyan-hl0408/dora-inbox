package com.halo.expressassistant.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import com.halo.expressassistant.hub.PddIdlePage
import com.halo.expressassistant.hub.WebFetchRegistry

/** Reuse only between consecutive reads of the same owner/session; never retain a busy page. */
internal object PddTracePagePool {
    data class Lease(val web:WebView,val reused:Boolean)
    private val idle=PddIdlePage<WebView>()
    private val handler=Handler(Looper.getMainLooper())
    fun obtain(owner:Context,session:String):Lease {
        val cached=idle.take(owner,session,SystemClock.elapsedRealtime(),{!WebFetchRegistry.closed(it)},WebFetchRegistry::cancelPage)
        return Lease(cached ?: WebView(owner),cached!=null)
    }
    fun park(owner:Context,session:String,web:WebView) {
        if(WebFetchRegistry.closed(web))return
        web.stopLoading();web.webViewClient=WebViewClient()
        (web.parent as? ViewGroup)?.removeView(web)
        // Keep it registered so stopping the owning operation also disposes its idle page.
        idle.put(owner,session,web,SystemClock.elapsedRealtime(),WebFetchRegistry::cancelPage)
        handler.postDelayed({idle.expire(SystemClock.elapsedRealtime(),WebFetchRegistry::cancelPage)},5100)
    }
}
