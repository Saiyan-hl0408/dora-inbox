package com.halo.expressassistant.hub

import kotlinx.serialization.json.*

/** The API returns a value and remaining lifetime, not an image or an absolute wall-clock time. */
object PddIdentityPayload {
    enum class Outcome { SUCCESS, EXPIRING, LOGIN, FAILED }
    data class Result(val outcome:Outcome,val value:String="",val lifetimeMs:Long=0,val mobileLabel:String="")
    fun read(raw:String):Result {
        val root=runCatching {Json.parseToJsonElement(raw) as? JsonObject}.getOrNull() ?: return Result(Outcome.FAILED)
        fun JsonObject.text(key:String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val error=root.text("error_code")
        if(error in setOf("40001","40002","401","10002") || Regex("未登录|登录失效|请.{0,4}登录").containsMatchIn(root.text("error_msg")))return Result(Outcome.LOGIN)
        if(root["success"]?.jsonPrimitive?.booleanOrNull!=true)return Result(Outcome.FAILED)
        val result=root["result"] as? JsonObject ?: return Result(Outcome.FAILED)
        val value=result.text("identification_code")
        val ttl=(result["expire_mills"] as? JsonPrimitive)?.longOrNull ?: 0
        if(value.isBlank() || value.length>256 || value.any {it.code !in 32..126} || ttl !in 1..3_600_000)return Result(Outcome.FAILED)
        if(ttl<=2000)return Result(Outcome.EXPIRING,lifetimeMs=ttl)
        // Never copy an arbitrary official link into our navigation or expose an unmasked number.
        val mobile=(result["mobile_info"] as? JsonObject)?.text("mobile_info_desc").orEmpty()
        val masked=Regex("1\\d{2}\\*{4,}\\d{4}").find(mobile)?.value.orEmpty()
        return Result(Outcome.SUCCESS,value,ttl,masked)
    }
    fun deadline(startedElapsed:Long,lifetimeMs:Long)=startedElapsed+lifetimeMs-2000L
    fun valid(deadlineElapsed:Long,nowElapsed:Long)=deadlineElapsed>nowElapsed
}
