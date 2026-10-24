package com.halo.expressassistant.hub

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Uses only the selected bound account. Credentials and identity values are never logged or saved. */
object PddIdentityService {
    private val client=OkHttpClient.Builder().callTimeout(12,TimeUnit.SECONDS).followRedirects(false).build()
    suspend fun query(cookies:String):PddIdentityPayload.Result {
        if(cookies.isBlank())return PddIdentityPayload.Result(PddIdentityPayload.Outcome.LOGIN)
        val request=Request.Builder().url("https://api.pinduoduo.com/api/express_trackbox/generate_identification_code")
            .header("Cookie",cookies).header("Referer","https://m.pinduoduo.net/mdkd/identificationCode")
            .header("User-Agent","Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/110.0.0.0 Mobile Safari/537.36")
            .post("""{"entry_source":"APP"}""".toRequestBody("application/json;charset=UTF-8".toMediaType()))
        CookiePairs.parse(cookies)["PDDAccessToken"]?.takeIf(String::isNotBlank)?.let {request.header("AccessToken",it)}
        return suspendCancellableCoroutine {continuation->
            val call=client.newCall(request.build());continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(IOException("身份码请求未完成"))}
                override fun onResponse(call:Call,response:Response){response.use {
                    val parsed=runCatching {PddIdentityPayload.read(it.body?.string().orEmpty())}.getOrDefault(PddIdentityPayload.Result(PddIdentityPayload.Outcome.FAILED))
                    val result=when {it.code==401 || it.code==403->PddIdentityPayload.Result(PddIdentityPayload.Outcome.LOGIN)
                        it.isSuccessful || parsed.outcome==PddIdentityPayload.Outcome.LOGIN->parsed
                        else->PddIdentityPayload.Result(PddIdentityPayload.Outcome.FAILED)}
                    if(continuation.isActive)continuation.resume(result)
                }}
            })
        }
    }
}
