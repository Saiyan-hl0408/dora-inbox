package com.halo.expressassistant.api

import kotlinx.coroutines.CancellationException

/** Preserve both transport stages, without retaining server bodies or credential-bearing URLs. */
object TbOrderRead {
    class Failure(val directCode:String,val browserCode:String,val loginRequired:Boolean,
        val directMillis:Long,val browserMillis:Long):
        IllegalStateException("淘宝订单未读取（接口：$directCode，${directMillis}ms；网页：$browserCode，${browserMillis}ms）")

    fun code(error:Exception):String=when(error) {
        is TbOrderResponse.Failure->error.reason.takeIf {it.matches(Regex("[A-Za-z0-9_]{1,80}"))} ?: "INVALID_RESPONSE"
        else->error.javaClass.simpleName.takeIf {it.matches(Regex("[A-Za-z0-9_]{1,80}"))} ?: "REQUEST_FAILED"
    }
    fun needsLogin(error:Throwable):Boolean=when(error) {
        is Failure->error.loginRequired
        is TbOrderResponse.Failure->listOf("SESSION_EXPIRED","SID_INVALID","AUTH_REJECT","NEED_LOGIN","NEEDS_LOGIN").any(error.reason::contains)
        else->false
    }
    suspend fun page(direct:suspend ()->TbOrderResponse.Page,browser:suspend ()->TbOrderResponse.Page):TbOrderResponse.Page {
        val started=System.nanoTime()
        val first=try{return direct()}catch(e:CancellationException){throw e}catch(e:Exception){e}
        val browserStarted=System.nanoTime()
        try{return browser()}catch(e:CancellationException){throw e}catch(e:Exception){
            throw Failure(code(first),code(e),needsLogin(e),(browserStarted-started)/1_000_000,
                (System.nanoTime()-browserStarted)/1_000_000)
        }
    }
}
