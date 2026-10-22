package com.halo.expressassistant.hub

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView

/** Hidden fetch pages belong to an operation and must stop when it is cancelled. */
object WebFetchRegistry {
    private val pages=java.util.IdentityHashMap<WebView,Context>()
    private const val CLOSED="parcelhub-fetch-closed"
    fun register(owner:Context,web:WebView){pages[web]=owner}
    fun closed(web:WebView)=web.tag==CLOSED
    fun release(web:WebView){pages.remove(web);web.tag=CLOSED}
    fun cancel(owner:Context) {
        val action=Runnable {pages.filterValues {it===owner}.keys.toList().forEach {web->
            release(web);web.stopLoading();(web.parent as? ViewGroup)?.removeView(web);web.destroy()
        }}
        if(Looper.myLooper()==Looper.getMainLooper())action.run() else Handler(Looper.getMainLooper()).post(action)
    }
}
