package com.halo.expressassistant.hub

import android.content.Context
import android.widget.ImageView
import coil.ImageLoader
import coil.load
import coil.request.CachePolicy
import coil.size.Size

/** Thumbnail and enlarged view share the original disk cache, not a 50px bitmap. */
object ProductPhotos {
    private var loader:ImageLoader?=null
    private fun loader(context:Context)=loader ?: ImageLoader.Builder(context.applicationContext)
        .respectCacheHeaders(false).build().also {loader=it}
    fun load(view:ImageView,raw:String,pixels:Int?=null) {
        val prefs=HubData.prefs(view.context)
        val sourceRaw=when {raw.startsWith("//")->"https:$raw";raw.startsWith("http:")->"https:"+raw.removePrefix("http:");else->raw}
        val original=ProductImagePolicy.original(raw)
        val retryKey="photo_original_retry_${original.hashCode()}"
        val tiny=original!=sourceRaw && ProductImagePolicy.isTinyThumbnail(sourceRaw)
        if(tiny && prefs.getLong(retryKey,0)>System.currentTimeMillis())return
        val source=if(prefs.getLong(retryKey,0)>System.currentTimeMillis())sourceRaw else original
        fun request(url:String,allowFallback:Boolean) {
            view.load(url,loader(view.context)) {
                if(pixels==null)size(Size.ORIGINAL) else size(pixels)
                memoryCachePolicy(CachePolicy.ENABLED);diskCachePolicy(CachePolicy.ENABLED)
                allowHardware(false)
                if(url.startsWith("https://img") && url.contains(".360buyimg.com/")) {
                    setHeader("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                    setHeader("Referer","https://kd.jd.com/")
                }
                listener(onError={_,_->
                    if(allowFallback && url!=sourceRaw) {
                        prefs.edit().putLong(retryKey,System.currentTimeMillis()+86400000).apply()
                        if(!tiny)request(sourceRaw,false)
                    }
                })
            }
        }
        request(source,true)
    }
}
