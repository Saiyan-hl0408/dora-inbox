package com.halo.expressassistant.hub

import android.content.Context
import android.widget.ImageView
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Size
import java.lang.ref.WeakReference

/** Thumbnail and enlarged view share the original disk cache, not a 50px bitmap. */
object ProductPhotos {
    private class Binding(val raw:String,var disposable:Disposable?=null,var failed:Boolean=false)
    private var loader:ImageLoader?=null
    private fun loader(context:Context)=loader ?: ImageLoader.Builder(context.applicationContext)
        .respectCacheHeaders(false).build().also {loader=it}
    fun load(view:ImageView,raw:String,pixels:Int?=null) {
        val previous=view.tag as? Binding
        if(previous?.raw==raw && !previous.failed)return
        previous?.disposable?.dispose()
        val binding=Binding(raw)
        view.tag=binding
        val reference=WeakReference(view)
        val imageLoader=loader(view.context)
        val prefs=HubData.prefs(view.context)
        val sourceRaw=when {raw.startsWith("//")->"https:$raw";raw.startsWith("http:")->"https:"+raw.removePrefix("http:");else->raw}
        val original=ProductImagePolicy.original(raw)
        val retryKey="photo_original_retry_${original.hashCode()}"
        val tiny=original!=sourceRaw && ProductImagePolicy.isTinyThumbnail(sourceRaw)
        if(tiny && prefs.getLong(retryKey,0)>System.currentTimeMillis()){binding.failed=true;return}
        val source=if(prefs.getLong(retryKey,0)>System.currentTimeMillis())sourceRaw else original
        fun request(url:String,allowFallback:Boolean) {
            val request=ImageRequest.Builder(view.context).apply {
                data(url)
                if(pixels==null)size(Size.ORIGINAL) else size(pixels)
                memoryCachePolicy(CachePolicy.ENABLED);diskCachePolicy(CachePolicy.ENABLED)
                memoryCacheKey(url)
                allowHardware(false)
                if(url.startsWith("https://img") && url.contains(".360buyimg.com/")) {
                    setHeader("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                    setHeader("Referer","https://kd.jd.com/")
                }
                // A generic target deliberately retains the drawable during detach/reattach.
                // ImageView.load's attach listener would restart a finished request when cards move.
                target(onSuccess={drawable->
                    reference.get()?.takeIf {it.tag===binding}?.setImageDrawable(drawable)
                },onError={_->
                    if(reference.get()?.tag!==binding)return@target
                    binding.failed=true
                    if(allowFallback && url!=sourceRaw) {
                        prefs.edit().putLong(retryKey,System.currentTimeMillis()+86400000).apply()
                        if(!tiny){binding.failed=false;request(sourceRaw,false)}
                    }
                })
            }.build()
            binding.disposable=imageLoader.enqueue(request)
        }
        request(source,true)
    }
    fun clear(view:ImageView) {
        (view.tag as? Binding)?.disposable?.dispose()
        view.tag=null
    }
}
