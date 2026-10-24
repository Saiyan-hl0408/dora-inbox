package com.halo.expressassistant.hub

import android.content.Context
import android.webkit.CookieManager
import com.halo.expressassistant.data.Store
import com.halo.expressassistant.ui.SourceWebActivity
import kotlinx.serialization.json.*

object SessionCookies {
    /** Explicit visible-page session. Never recover another account from the global jar. */
    fun restoreOwned(context:Context,channel:String,account:com.halo.expressassistant.data.BoundAccount):Boolean {
        val selected=Store.accountById(context,channel,account.id)?.takeIf {it.enabled} ?: return false
        val saved=Store.cookieOf(selected.payload)
        if(!IdentitySessionPolicy.matchesOwner(channel,Store.cookieOf(account.payload),saved))return false
        val live=current(channel)
        val sameOwner=IdentitySessionPolicy.matchesOwner(channel,saved,live)
        if(channel=="pdd" && sameOwner && com.halo.expressassistant.ui.PddLoginSession.hasCredentials(CookiePairs.parse(live)))return true
        if(!sameOwner)clearChannel(channel)
        if(channel=="pdd") {
            val cm=CookieManager.getInstance();cm.setAcceptCookie(true)
            for(domain in domains(channel))for((key,value) in CookiePairs.parse(saved))cm.setCookie(domain,"$key=$value; Path=/; Secure")
            cm.flush()
        } else restore(context,channel,selected)
        return IdentitySessionPolicy.matchesOwner(channel,saved,current(channel))
    }
    /** Recheck both selected-record ownership and live cookies before writing a named snapshot. */
    fun persistOwned(context:Context,channel:String,account:com.halo.expressassistant.data.BoundAccount):Boolean {
        val selected=Store.accountById(context,channel,account.id)?.takeIf {it.enabled} ?: return false
        val expected=Store.cookieOf(account.payload)
        if(!IdentitySessionPolicy.matchesOwner(channel,expected,Store.cookieOf(selected.payload)) ||
            !IdentitySessionPolicy.matchesOwner(channel,expected,current(channel)))return false
        persistBrowser(context,channel,account.id)
        return true
    }
    fun domains(channel:String)=SourceWebActivity.domainsFor(channel)+when(channel) {
        "taobao"->listOf("https://login.m.taobao.com","https://acs.m.taobao.com","https://h5.m.taobao.com","https://login.taobao.com","https://buyertrade.taobao.com")
        "jd"->listOf("https://my.m.jd.com","https://convenient-h5.jd.com")
        else->emptyList()
    }
    fun restore(context:Context,channel:String,account:com.halo.expressassistant.data.BoundAccount?=Store.firstEnabledAccount(context,channel)) {
        val accountCookies=account?.let {Store.cookieOf(it.payload)}.orEmpty()
        val saved=if(channel=="jd")accountCookies.ifBlank {Store.browserCookieJar(context)[channel].orEmpty()}
            else CookiePairs.merge(Store.browserCookieJar(context)[channel].orEmpty(),CookiePairs.parse(accountCookies))
        val cm=CookieManager.getInstance();cm.setAcceptCookie(true)
        if(channel=="taobao" && account!=null) {
            val live=CookiePairs.parse(current(channel))
            if(CookiePairs.sameTaobaoAccount(live,CookiePairs.parse(accountCookies)))return
            // Never mix the global jar of one Taobao user into a different bound account.
            clearChannel(channel)
            val origins=Store.browserCookieJar(context)["taobao_origins_${account.id}"]?.let {runCatching {Json.parseToJsonElement(it).jsonObject}.getOrNull()}
            for(domain in domains(channel)) {
                val cookies=origins?.get(domain)?.jsonPrimitive?.content ?: accountCookies
                for((key,value) in CookiePairs.parse(cookies))cm.setCookie(domain,"$key=$value; Path=/; Secure")
            }
            cm.flush();return
        }
        if(channel=="pdd") {
            val live=CookiePairs.parse(cm.getCookie("https://mobile.yangkeduo.com").orEmpty())
            if(CookiePairs.samePddAccount(live,CookiePairs.parse(accountCookies)) && com.halo.expressassistant.ui.PddLoginSession.hasCredentials(live))return
        }
        if(channel=="douyin" && account!=null) {
            if(DouyinSessionPolicy.sameOwner(accountCookies,current(channel)) && DouyinSessionPolicy.hasCredentials(current(channel)))return
            clearChannel(channel)
            for(domain in domains(channel))for((key,value) in CookiePairs.parse(accountCookies))cm.setCookie(domain,"$key=$value; Path=/; Secure")
            cm.flush();return
        }
        if(channel=="jd") {
            val live=CookiePairs.parse(cm.getCookie("https://kd.jd.com").orEmpty())
            // A saved snapshot can be older than the native jar after official SSO renewal.
            if(CookiePairs.sameJdAccount(live,CookiePairs.parse(saved)))return
            val snapshot=account?.let {Store.browserCookieJar(context)["jd_origins_${it.id}"]}
            val origins=snapshot?.let {runCatching {Json.parseToJsonElement(it).jsonObject}.getOrNull()}
            if(origins!=null) {
                for(domain in domains(channel))for((key,value) in CookiePairs.parse(origins[domain]?.jsonPrimitive?.content.orEmpty()))cm.setCookie(domain,"$key=$value; Path=/; Secure")
            } else for((key,value) in CookiePairs.parse(saved))cm.setCookie("https://kd.jd.com","$key=$value; Path=/; Secure")
            cm.flush();return
        }
        for(domain in domains(channel))for((key,value) in CookiePairs.parse(saved))cm.setCookie(domain,"$key=$value; Path=/; Max-Age=7776000; Secure")
        cm.flush()
    }
    fun clearChannel(channel:String) {
        val cm=CookieManager.getInstance()
        val root=when(channel){"jd"->".jd.com";"taobao"->".taobao.com";"douyin"->".douyin.com";else->".yangkeduo.com"}
        for(domain in domains(channel))for(key in CookiePairs.parse(cm.getCookie(domain).orEmpty()).keys) {
            cm.setCookie(domain,"$key=; Path=/; Max-Age=0")
            var suffix=android.net.Uri.parse(domain).host.orEmpty()
            while(suffix.endsWith(root.removePrefix("."))) {
                cm.setCookie(domain,"$key=; Domain=.$suffix; Path=/; Max-Age=0")
                if(suffix==root.removePrefix("."))break
                suffix=suffix.substringAfter('.',"")
            }
        }
        cm.flush()
    }
    fun current(channel:String):String {
        val cm=CookieManager.getInstance();val map=linkedMapOf<String,String>()
        for(domain in domains(channel))for((key,value) in CookiePairs.parse(cm.getCookie(domain).orEmpty()))map.putIfAbsent(key,value)
        return CookiePairs.merge("",map)
    }
    fun persistBrowser(context:Context,channel:String,accountId:String="") {
        CookieManager.getInstance().flush()
        val current=current(channel)
        Store.saveBrowserCookies(context,channel,current)
        if(channel=="jd" || channel=="taobao") {
            val owner=accountId.ifBlank {Store.accounts(context,channel).firstOrNull {account->
                if(channel=="jd")CookiePairs.sameJdAccount(CookiePairs.parse(current),CookiePairs.parse(Store.cookieOf(account.payload)))
                else CookiePairs.sameTaobaoAccount(CookiePairs.parse(current),CookiePairs.parse(Store.cookieOf(account.payload)))
            }?.id.orEmpty()}
            if(owner.isNotBlank())Store.saveBrowserCookies(context,"${channel}_origins_$owner",buildJsonObject {
                domains(channel).forEach {put(it,CookieManager.getInstance().getCookie(it).orEmpty())}
            }.toString())
        }
    }
}
