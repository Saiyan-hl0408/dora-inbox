package com.halo.expressassistant.hub

import java.net.URI

object IdentityWebPolicy {
    fun allowed(url:String, channel:String="taobao")=runCatching {
        val uri=URI(url)
        val root=when(channel){"jd"->"jd.com";"taobao","cainiao"->"taobao.com";else->return@runCatching false}
        val host=uri.host.orEmpty().lowercase()
        uri.scheme=="https" && (host==root || host.endsWith(".$root"))
    }.getOrDefault(false)
    fun loginPage(url:String, channel:String="taobao")=runCatching {
        URI(url).let {allowed(url,channel) && (it.host.orEmpty().startsWith("login.") ||
            it.host.orEmpty().startsWith("passport.") || it.host.orEmpty().startsWith("plogin.") ||
            it.path.orEmpty().contains("/login",true))}
    }.getOrDefault(false)
}
