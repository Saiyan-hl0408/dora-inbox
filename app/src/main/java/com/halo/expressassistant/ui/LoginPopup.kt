package com.halo.expressassistant.ui

import android.os.Message
import android.webkit.*

/** Keep user-initiated OAuth popups in the same browser session. */
object LoginPopup {
    fun forward(web:WebView,isUserGesture:Boolean,resultMsg:Message,handle:(android.net.Uri)->Boolean):Boolean {
        if(!isUserGesture)return false
        val child=WebView(web.context)
        child.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                if(!request.isForMainFrame)return false
                if(request.url.toString()=="about:blank")return false
                if(!handle(request.url))web.loadUrl(request.url.toString())
                child.post {child.destroy()};return true
            }
        }
        (resultMsg.obj as WebView.WebViewTransport).webView=child;resultMsg.sendToTarget();return true
    }
}
