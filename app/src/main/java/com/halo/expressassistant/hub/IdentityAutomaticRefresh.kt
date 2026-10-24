package com.halo.expressassistant.hub

import android.webkit.WebView

/** Bootstrap the official read action once after each navigation, with bounded DOM readiness checks. */
internal class IdentityAutomaticRefresh(
    private val page: WebView,
    private val eligible: (String) -> Boolean,
    private val onRefreshing: () -> Unit,
    private val onReady: () -> Unit
) {
    private var pending: Runnable? = null
    private var generation=0
    private var closed=false
    private var started=false

    fun onNavigation() {
        pending?.let { page.removeCallbacks(it) }
        pending = null
        generation++;started=false
    }

    /** Returns true while the caller should retain its loading UI. */
    fun afterLoad(url: String): Boolean {
        if(closed || !eligible(url))return false
        if(started)return pending!=null
        started=true
        onRefreshing()
        val version=generation
        var checks=0
        val retry=object:Runnable {
            override fun run() {
                if(closed || version!=generation)return
                val current=page.url.orEmpty()
                if(current!=url || !page.isAttachedToWindow || !eligible(current)){pending=null;return}
                page.evaluateJavascript(CainiaoIdentityBootstrap.script) {raw->
                    if(closed || version!=generation)return@evaluateJavascript
                    val state=raw.trim('"')
                    checks++
                    if(state=="ready" || state=="unavailable" || checks>=32){pending=null;onReady()}
                    else page.postDelayed(this,250)
                }
            }
        }
        pending = retry
        page.post(retry)
        return true
    }

    fun close() {
        pending?.let { page.removeCallbacks(it) }
        pending = null
        closed=true;generation++
    }
}
