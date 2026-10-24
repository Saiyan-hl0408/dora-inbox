package com.halo.expressassistant.hub

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Read the same owned order page without booting a browser when SSR already has its status. */
object PddOrderPageFetcher {
    private val client=OkHttpClient.Builder().callTimeout(6,TimeUnit.SECONDS).followRedirects(false).build()
    suspend fun fetch(orderId:String,cookies:String,observe:((Int,String)->Unit)?=null):PddDetailPayload.Snapshot? {
        if(orderId.isBlank() || cookies.isBlank())return null
        val url=HttpUrl.Builder().scheme("https").host("mobile.yangkeduo.com").addPathSegment("order.html").addQueryParameter("order_sn",orderId).build()
        val request=Request.Builder().url(url).header("Cookie",cookies)
            .header("Referer","https://mobile.yangkeduo.com/orders.html?type=0")
            .header("User-Agent","Mozilla/5.0 (Linux; Android 16; 25102RKBEC Build/BP2A.250605.031.A3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36").build()
        return suspendCancellableCoroutine {continuation->
            val call=client.newCall(request);continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException){observe?.invoke(0,"");if(continuation.isActive)continuation.resume(null)}
                override fun onResponse(call:Call,response:Response) {
                    val snapshot=response.use {r->runCatching {
                        if(!r.isSuccessful || (r.body?.contentLength() ?: 0)>1_500_000){observe?.invoke(r.code,"");null}
                        else {
                            val html=r.body?.string().orEmpty()
                            observe?.invoke(r.code,PddOrderPagePayload.rawData(html).orEmpty())
                            PddOrderPagePayload.read(html,orderId)
                        }
                    }.getOrNull()}
                    if(continuation.isActive)continuation.resume(snapshot)
                }
            })
        }
    }
}
