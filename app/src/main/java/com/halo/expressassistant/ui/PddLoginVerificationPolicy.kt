package com.halo.expressassistant.ui

import java.net.URI

/** Which live official page may be checked without navigating or reloading it. */
object PddLoginVerificationPolicy {
    fun canObserve(url:String):Boolean = runCatching {
        val uri=URI(url)
        uri.scheme=="https" && uri.host=="mobile.yangkeduo.com" && uri.path!="/orders.html"
    }.getOrDefault(false)
    fun retryDelay(attempt:Int):Long=when {attempt<=1->15000L;attempt==2->30000L;else->60000L}
    fun shouldVerify(hasCredentials:Boolean,stage:String,stableFor:Long,scanned:Boolean,returnedFromApp:Boolean,extraVerification:Boolean,manual:Boolean):Boolean {
        if(!hasCredentials)return false
        if(manual)return true
        if(stage=="exchanging")return false
        return stage=="returned" || (stableFor>=3000 && (scanned || returnedFromApp || extraVerification))
    }
}
