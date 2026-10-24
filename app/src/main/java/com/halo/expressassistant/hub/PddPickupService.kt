package com.halo.expressassistant.hub

import android.content.Context
import com.halo.expressassistant.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One read-only request per bound account; uses its own saved commerce session, never a global jar. */
object PddPickupService {
    private val client=OkHttpClient.Builder().callTimeout(12,TimeUnit.SECONDS).followRedirects(false).build()
    suspend fun query(cookies:String):PddPickupPayload.Snapshot {
        if(cookies.isBlank())return PddPickupPayload.Snapshot(PddPickupPayload.Outcome.LOGIN)
        val request=Request.Builder().url("https://api.pinduoduo.com/api/express_trackbox/station/package")
            .header("Cookie",cookies).header("Referer","https://m.pinduoduo.net/mdkd/package")
            .header("User-Agent","Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/110.0.0.0 Mobile Safari/537.36")
            .post("""{"entry_source":11,"dynamic_qrcode":false,"dp_source":""}""".toRequestBody("application/json;charset=UTF-8".toMediaType()))
        CookiePairs.parse(cookies)["PDDAccessToken"]?.takeIf(String::isNotBlank)?.let {request.header("AccessToken",it)}
        return suspendCancellableCoroutine {continuation->
            val call=client.newCall(request.build());continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(IOException("多多取快递网络请求未完成"))}
                override fun onResponse(call:Call,response:Response) {
                    response.use {
                        try {
                            val raw=it.body?.string().orEmpty()
                            val parsed=PddPickupPayload.read(raw)
                            val snapshot=if(it.isSuccessful || parsed.outcome==PddPickupPayload.Outcome.LOGIN)parsed else PddPickupPayload.Snapshot(PddPickupPayload.Outcome.FAILED)
                            if(continuation.isActive)continuation.resume(snapshot)
                        } catch(_:Exception){if(continuation.isActive)continuation.resumeWithException(IOException("多多取快递响应读取失败"))}
                    }
                }
            })
        }
    }
    suspend fun supplement(context:Context,onProgress:((String)->Unit)?=null) {
        val notes=mutableListOf<String>()
        for((index,account) in Store.accounts(context,"pdd").filter {it.enabled}.withIndex()) {
            val cookies=Store.cookieOf(account.payload)
            onProgress?.invoke("读取多多取快递：账号 ${index+1}")
            try {
                val snapshot=query(cookies)
                if(snapshot.outcome==PddPickupPayload.Outcome.SUCCESS) {
                    var matched=0
                    synchronized(Store) {
                        val selected=Store.accountById(context,"pdd",account.id)
                        if(selected?.enabled==true && IdentitySessionPolicy.matchesOwner("pdd",cookies,Store.cookieOf(selected.payload))) {
                            val now=System.currentTimeMillis()
                            Store.saveItems(context,Store.items(context).map {item->
                                val updated=PddPickupPayload.apply(item,snapshot,account.id,now)
                                if(updated!=item)matched++
                                updated
                            })
                        }
                    }
                    notes+="账号 ${index+1}：取快递读取成功；当前待取 ${snapshot.pendingCount}，近期已取 ${snapshot.parcels.count {it.state=="COLLECTED"}}；关联 $matched 票。未出现在列表的包裹保留原状态。"
                } else notes+="账号 ${index+1}："+if(snapshot.outcome==PddPickupPayload.Outcome.LOGIN)"取件服务会话需恢复，请在拼多多绑定页重新扫码；已有数据保留。" else "取件服务未返回有效结果；已有数据保留。"
            } catch(e:CancellationException){throw e}
            catch(_:Exception){notes+="账号 ${index+1}：取件服务网络请求未完成；已有数据保留。"}
        }
        if(notes.isNotEmpty())HubData.prefs(context).edit().putString("pdd_pickup_diagnostic",notes.joinToString("\n")).apply()
    }
}
