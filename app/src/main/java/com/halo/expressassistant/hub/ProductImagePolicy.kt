package com.halo.expressassistant.hub

import java.net.URI

object ProductImagePolicy {
    fun canFetch(hasImage:Boolean,now:Long,retryAt:Long)=!hasImage && (retryAt<=0 || now>=retryAt)
    fun isTinyThumbnail(raw:String):Boolean {
        val path=runCatching {URI(if(raw.startsWith("//"))"https:$raw" else raw).path.orEmpty()}.getOrDefault("")
        if(Regex("""^/n(?:2|5)/""",RegexOption.IGNORE_CASE).containsMatchIn(path))return true
        val size=Regex("""/s([0-9]+)x([0-9]+)_""").find(path) ?: return false
        return size.groupValues.drop(1).all {(it.toIntOrNull() ?: 999)>0 && (it.toIntOrNull() ?: 999)<=160}
    }
    fun original(raw:String):String {
        val source=when {raw.startsWith("//")->"https:$raw";raw.startsWith("http://")->"https:"+raw.removePrefix("http:");else->raw}
        val uri=runCatching {URI(source)}.getOrNull() ?: return source
        if(uri.scheme!="https" || !uri.host.orEmpty().matches(Regex("img[0-9]+\\.360buyimg\\.com")) || uri.rawQuery!=null || uri.rawFragment!=null)return source
        val path=Regex("""^/(?:n[0-9]+|img)/(?:s[0-9]+x[0-9]+_)?(jfs/.+)$""",RegexOption.IGNORE_CASE).matchEntire(uri.rawPath.orEmpty()) ?: return source
        return "https://${uri.rawAuthority}/img/${path.groupValues[1]}"
    }
}
