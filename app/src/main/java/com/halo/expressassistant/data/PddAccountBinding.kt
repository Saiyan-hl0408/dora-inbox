package com.halo.expressassistant.data

import com.halo.expressassistant.hub.CookiePairs

internal object PddAccountBinding {
    fun findExisting(accounts:List<BoundAccount>,cookies:String,cookieOf:(String)->String):BoundAccount? {
        val incoming=CookiePairs.parse(cookies)
        return accounts.firstOrNull {CookiePairs.samePddAccount(incoming,CookiePairs.parse(cookieOf(it.payload)))}
    }
}
