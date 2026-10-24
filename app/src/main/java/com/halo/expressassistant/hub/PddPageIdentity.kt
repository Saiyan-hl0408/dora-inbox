package com.halo.expressassistant.hub

import java.net.URI
import java.net.URLDecoder

/** Validate the document that produced the snapshot, independently of WebView callbacks. */
object PddPageIdentity {
    private fun uri(value:String)=runCatching {URI(value)}.getOrNull()
    private fun order(uri:URI)=uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull {
        val pair=it.split('=',limit=2)
        if(pair[0] in setOf("order_sn","orderSn"))runCatching {URLDecoder.decode(pair.getOrElse(1){""},"UTF-8")}.getOrNull() else null
    }.orEmpty()
    fun orderOf(url:String)=uri(url)?.let(::order).orEmpty()
    fun accepts(expectedOrder:String,targetUrl:String,pageUrl:String,returnedOrder:String,expectedToken:String,documentToken:String):Boolean {
        if(expectedOrder.isBlank() || expectedToken.isBlank() || expectedToken!=documentToken)return false
        val page=uri(pageUrl) ?: return false
        if(page.scheme!="https" || page.host!="mobile.yangkeduo.com" || page.userInfo!=null || page.port !in setOf(-1,443) || page.path !in setOf("/order.html","/goods_express.html"))return false
        val pageOrder=order(page)
        if(returnedOrder.isNotBlank() && returnedOrder!=expectedOrder)return false
        if(pageOrder.isNotBlank())return pageOrder==expectedOrder
        if(returnedOrder==expectedOrder)return true
        val target=uri(targetUrl) ?: return false
        return page.path=="/goods_express.html" && page.scheme==target.scheme && page.host==target.host && page.path==target.path && page.rawQuery==target.rawQuery
    }
}
