package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import kotlinx.serialization.json.*

/** School-channel result is supplemental data, never a shopping-order inventory. */
object CainiaoPickup {
    const val ORIGIN = "https://pages-fast.m.taobao.com"
    const val ROOT = "$ORIGIN/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform"
    const val SEARCH_URL = "$ROOT/school-search-ssr?openFrom=searchEnter&from=XY"
    const val PHONE_URL = "$ROOT/phone-manage-xy-ssr?from=XY"
    // Public snapshot: no personal station. Configure an official station link in the app.
    const val DEFAULT_STATION_CODE = ""
    fun pageUrl(stationCode:String,manage:Boolean=false):String = (if(manage)PHONE_URL else SEARCH_URL)+
        stationCode.takeIf {it.matches(Regex("[0-9]{1,20}"))}?.let {"&stationCode=$it"}.orEmpty()
    const val API = "mtop.cainiao.pickup.xiaoyuan.queryMultisiteWaybill"
    const val RETRY_MS = 15 * 60 * 1000L
    const val MAX_BATCH = 4
    enum class Outcome { FOUND, VERIFY, LOGIN, COLLECTED, NO_CODE, NO_MATCH, FAILED }
    data class Result(val outcome:Outcome, val waybill:String="", val code:String="", val station:String="",
        val address:String="", val signedAt:String="", val status:String="",val arrivedAt:String="",val companyCode:String="",val historicalCode:String="",val pending:Boolean=false,val accountId:String="")
    fun jdCarrier(item:ExpressItem?)=item!=null && (item.companyName.contains("京东") ||
        item.companyCode.uppercase() in setOf("JD","JDL","JDEXPRESS","JDKD") || item.mailNo.startsWith("JD",true))
    fun canApply(item:ExpressItem, collected:Boolean=false):Boolean =
        item.source in setOf("pdd","taobao") && !collected && !item.isOrderNumber &&
            item.mailNo.matches(Regex("[A-Za-z0-9]{8,32}")) && !item.mailNo.startsWith("ORDER-") &&
            (item.pickupCode.isBlank() || (item.source=="pdd" && (item.pickupCodeSource==PddPickupCodes.SOURCE || item.pickupCodeSource.startsWith("taobao-cainiao")))) && ParcelPresentation.stage(item)==ParcelPresentation.Stage.PICKUP
    fun candidate(item:ExpressItem,collected:Boolean=false)=canApply(item,collected) && !jdCarrier(item) &&
        !(item.pickupCode.isNotBlank() && item.pickupCodeSource.startsWith("taobao-cainiao"))
    fun mayAttempt(now:Long, retryAt:Long, sameStatus:Boolean)=!sameStatus || now>=retryAt
    fun fingerprint(item:ExpressItem)="${item.mailNo}|${ParcelPresentation.stage(item)}|${item.logisticsStatus}|${item.statusUpdatedAt}"
    fun read(raw:String, number:String):Result {
        val root=runCatching {Json.parseToJsonElement(raw).jsonObject}.getOrNull() ?: return Result(Outcome.FAILED)
        fun JsonObject.text(key:String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val ret=(root["ret"] as? JsonArray)?.mapNotNull {(it as? JsonPrimitive)?.contentOrNull}.orEmpty()
        if(ret.any {it.contains("SESSION",true) || it.contains("LOGIN",true) || it.contains("TOKEN",true)})return Result(Outcome.LOGIN)
        if(ret.isEmpty() || ret.none {it.startsWith("SUCCESS")})return Result(Outcome.FAILED)
        val data=root["data"] as? JsonObject ?: return Result(Outcome.FAILED)
        val result=(data["result"] ?: data["data"]) as? JsonObject ?: return Result(Outcome.FAILED)
        val pop=result["securityPop"]
        // Honor verification even when the response happens to contain a pickupCode.
        if(pop!=null && pop!=JsonNull && pop!=JsonPrimitive(false) && pop!=JsonPrimitive("") && pop!=JsonPrimitive("false"))return Result(Outcome.VERIFY,number)
        val groups=result["stationMessageAndPackages"] as? JsonArray ?: return Result(Outcome.NO_MATCH,number)
        for(group in groups) {
            val g=group as? JsonObject ?: continue
            val station=g["stationMessage"] as? JsonObject ?: JsonObject(emptyMap())
            val parcels=(g["waybillPickUpResp"] as? JsonObject)?.get("certainPickupWaybills") as? JsonArray ?: continue
            for(value in parcels) {
                val parcel=value as? JsonObject ?: continue
                if(!parcel.text("waybillNo").equals(number,true))continue
                val signed=parcel.text("signTime")
                val status=parcel.text("status")
                val label=parcel.text("packageStatus")
                val collected=status in setOf("4","-6","3500") || (status=="2300" && parcel.text("outboundStatus")=="1") ||
                    listOf("已签收","已取件","已取货","已退回","退件").any(label::contains)
                val pending=status=="3" && (label=="取件码" || label.contains("待取"))
                val rawCode=parcel.text("pickupCode").trim()
                val code=rawCode.takeIf {PickupText.parse("取件码：$it").codes.singleOrNull()==it && !it.equals(number,true)}.orEmpty()
                return Result(when {collected->Outcome.COLLECTED; pending && code.isNotBlank()->Outcome.FOUND;else->Outcome.NO_CODE},
                    number,if(pending && !collected)code else "",station.text("stationName"),
                    station.text("stationAddress").ifBlank {station.text("address")},signed,label,
                    parcel.text("collectionDay"),parcel.text("upCpCode"),if(collected)code else "",pending && !collected)
            }
        }
        return Result(Outcome.NO_MATCH,number)
    }
    fun apply(item:ExpressItem, result:Result, accountId:String, now:Long):ExpressItem {
        if(!item.mailNo.equals(result.waybill,true) || !canApply(item))return item
        val fallback=PddPickupCodes.fallback(item)
        val checked=item.copy(pickupQueryOutcome=result.outcome.name,pickupCodeCheckedAt=now,pickupQueryAccountId=accountId,
            pddPickupCode=if(item.source=="pdd")fallback else item.pddPickupCode,
            pddPickupStation=if(item.pickupCodeSource==PddPickupCodes.SOURCE)item.pickupStation.ifBlank {item.pddPickupStation} else item.pddPickupStation,
            pickupVerificationNeeded=result.outcome==Outcome.VERIFY)
        if(result.outcome==Outcome.VERIFY)return checked.copy(pickupHint="菜鸟需要确认收件号码，请打开官方页面核验")
        if(result.outcome==Outcome.COLLECTED && item.source=="pdd")return checked.copy(pickupCode="",pickupCodeSource="",
            historicalPickupCode=item.pickupCode.ifBlank {item.historicalPickupCode},pickupHint="菜鸟显示已取件，请核对官方包裹状态")
        if(result.outcome in setOf(Outcome.NO_CODE,Outcome.NO_MATCH) && item.source=="pdd")return checked.copy(
            pickupCode=fallback,pickupCodeSource=if(fallback.isNotBlank())PddPickupCodes.SOURCE else "",pickupHint="",
            pickupStation=if(item.pickupCodeSource.startsWith("taobao-cainiao"))item.pddPickupStation else item.pickupStation,
            pickupAddress=if(item.pickupCodeSource.startsWith("taobao-cainiao"))"" else item.pickupAddress)
        if(result.outcome!=Outcome.FOUND)return checked.copy(pickupHint="")
        return checked.copy(pickupCode=result.code,pickupStation=result.station.ifBlank {item.pickupStation},
            pickupAddress=result.address.ifBlank {item.pickupAddress},pickupLocationVersion=1,
            pickupCodeSource="taobao-cainiao-school",pickupQueryAccountId=accountId,pickupCodeCheckedAt=now,
            pickupVerificationNeeded=false,pickupQueryOutcome=Outcome.FOUND.name,pickupHint="")
    }
    fun message(result:Result)=when(result.outcome) {
        Outcome.FOUND->"菜鸟频道已返回取件码"
        Outcome.VERIFY->"菜鸟需要确认收件号码，请打开官方页面核验"
        Outcome.LOGIN->"淘宝菜鸟会话需要验证，请登录或恢复淘宝账号"
        Outcome.COLLECTED->"菜鸟显示这票已取或已结束，未将历史码作为当前取件码"
        Outcome.NO_CODE->"菜鸟未提供可用的当前取件码，保留购物平台分类"
        Outcome.NO_MATCH->"菜鸟频道未匹配这票运单，保留购物平台记录"
        Outcome.FAILED->"菜鸟频道暂未返回有效结果，请稍后重试"
    }
}
