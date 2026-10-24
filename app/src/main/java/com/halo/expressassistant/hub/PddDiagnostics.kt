package com.halo.expressassistant.hub

import android.content.Context
import android.os.Build
import android.webkit.WebView
import com.halo.expressassistant.BuildConfig
import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.Store
import kotlinx.serialization.json.*

/** Bounded local journal retained across the official login Activity and its first sync. */
object PddDiagnostics {
    private const val KEY="pdd_binding_sync_diagnostic_v1"
    private val pretty=Json {prettyPrint=true}
    private fun root(context:Context)=runCatching {Json.parseToJsonElement(HubData.prefs(context).getString(KEY,"{}").orEmpty()).jsonObject}.getOrDefault(JsonObject(emptyMap())).toMutableMap()
    private fun save(context:Context,data:Map<String,JsonElement>){HubData.prefs(context).edit().putString(KEY,JsonObject(data).toString()).apply()}
    private fun environment()=buildJsonObject {
        put("appVersion",BuildConfig.VERSION_NAME);put("versionCode",BuildConfig.VERSION_CODE);put("diagnosticRevision",6);put("buildRevision","0.9.9")
        put("androidApi",Build.VERSION.SDK_INT);put("device",PddDiagnosticPayload.cleanText("${Build.MANUFACTURER} ${Build.MODEL}").take(80))
        put("webView",runCatching {WebView.getCurrentWebViewPackage()?.let {"${it.packageName} ${it.versionName}"}}.getOrNull() ?: "unavailable")
        put("source","拼多多官方H5；不依赖本地拼多多App版本")
    }
    @Synchronized fun beginLogin(context:Context) {
        val data=mutableMapOf<String,JsonElement>("environment" to environment(),"sessionStartedAt" to JsonPrimitive(System.currentTimeMillis()),"login" to JsonArray(emptyList()),"sync" to JsonArray(emptyList()),"responses" to JsonArray(emptyList()))
        save(context,data);login(context,"打开官方登录",mapOf("entry" to "扫码绑定或恢复"))
    }
    @Synchronized fun beginSync(context:Context,trigger:String,days:Int) {
        PddBusinessJournal.begin(context)
        val data=root(context);data["environment"]=environment();data.putIfAbsent("sessionStartedAt",JsonPrimitive(System.currentTimeMillis()));data["itemSnapshots"]=JsonObject(emptyMap())
        data["syncStartedAt"]=JsonPrimitive(System.currentTimeMillis());data["sync"]=JsonArray(emptyList());data["responses"]=JsonArray(emptyList());data["detailResponses"]=JsonArray(emptyList())
        save(context,data)
        note(context,"同步开始",mapOf("trigger" to trigger,"days" to days.toString(),"boundAccounts" to Store.accounts(context,"pdd").count {it.enabled}.toString()))
        items(context,"同步前本地订单",Store.items(context).filter {it.source=="pdd"})
    }
    fun login(context:Context,stage:String,facts:Map<String,String> = emptyMap())=event(context,"login",stage,facts,40)
    fun note(context:Context,stage:String,facts:Map<String,String> = emptyMap())=event(context,"sync",stage,facts,180)
    @Synchronized private fun event(context:Context,section:String,stage:String,facts:Map<String,String>,limit:Int) {
        val data=root(context);val now=System.currentTimeMillis()
        val at=(data[if(section=="login")"sessionStartedAt" else "syncStartedAt"] as? JsonPrimitive)?.longOrNull ?: now
        val detail=buildJsonObject {facts.entries.take(18).forEach {(k,v)->put(k,if((k.endsWith("Ms") || k in setOf("since","createdAt","completedAt")) && v.matches(Regex("[0-9]{1,13}")))v else PddDiagnosticPayload.cleanText(v).take(240))}}
        val events=(data[section] as? JsonArray).orEmpty().toMutableList()
        val previous=events.lastOrNull() as? JsonObject
        if(previous?.get("stage")==JsonPrimitive(stage) && previous["facts"]==detail)return
        events.add(buildJsonObject {put("at",now);put("elapsedMs",(now-at).coerceAtLeast(0));put("stage",stage);put("facts",detail)})
        data[section]=JsonArray(events.takeLast(limit));save(context,data)
    }
    fun items(context:Context,stage:String,items:List<ExpressItem>) {
        note(context,stage,mapOf("total" to items.size.toString(),"stages" to items.groupingBy {ParcelPresentation.stage(it).name}.eachCount().toString(),
            "uniqueParcels" to items.map {it.mailNo}.toSet().size.toString(),"repairPending" to items.count {it.pddReadRepairRequired}.toString(),
            "missingOrderDate" to items.count {it.orderCreatedAt==0L}.toString(),"missingWaybill" to items.count {it.isOrderNumber}.toString(),
            "unrecognizedStatus" to items.count {it.logisticsStatus.isBlank() && ParcelPresentation.stage(it)==ParcelPresentation.Stage.UNVERIFIED}.toString()))
        PddBusinessJournal.append(context,stage,emptyMap(),JsonArray(items.map {item->buildJsonObject {
            put("orderId",item.id);put("queryOrderId",item.queryChannel);put("trackingNumber",item.mailNo);put("companyName",item.companyName);put("goodsName",item.originalName)
            put("originalTrackingNumber",item.orderWaybill);put("orderImageUrl",item.orderImageUrl);put("orderGoodsCount",item.orderGoodsCount)
            put("stage",ParcelPresentation.stage(item).name);put("orderStatus",item.orderStatus);put("logisticsStatus",item.logisticsStatus)
            put("state",item.state);put("stateNum",item.stateNum);put("stateName",item.stateName);put("latestText",item.latestText);put("latestTime",item.latestTime)
            put("createdAt",item.orderCreatedAt);put("completedAt",item.completedAt);put("orderCompletedAt",item.orderCompletedAt)
            put("isOrderNumber",item.isOrderNumber);put("stationName",item.pickupStation);put("hasPickupCode",item.pickupCode.isNotBlank());put("pickupCodeSource",item.pickupCodeSource)
            put("readIntegrityVersion",item.pddReadIntegrityVersion);put("repairRequired",item.pddReadRepairRequired)
        }}).toString())
        val samples=JsonArray(items.take(40).map {item->buildJsonObject {
            put("accountRef",PddDiagnosticPayload.ref(item.accountId));put("orderRef",PddDiagnosticPayload.ref(item.id));put("parcelRef",PddDiagnosticPayload.ref(item.mailNo))
            put("stage",ParcelPresentation.stage(item).name);put("orderStatus",PddDiagnosticPayload.safeStatus(item.orderStatus));put("logisticsStatus",PddDiagnosticPayload.safeStatus(item.logisticsStatus))
            put("createdAt",item.orderCreatedAt);put("completedAt",item.completedAt);put("hasDatedTrace",ParcelTime.parse(item.latestTime)>0);put("isOrderNumber",item.isOrderNumber);put("hasPickupLocation",item.pickupStation.isNotBlank())
            put("readIntegrityVersion",item.pddReadIntegrityVersion);put("repairRequired",item.pddReadRepairRequired)
        }})
        itemSamples(context,stage,samples)
    }
    @Synchronized private fun itemSamples(context:Context,stage:String,samples:JsonArray) {
        val data=root(context);val snapshots=(data["itemSnapshots"] as? JsonObject).orEmpty().toMutableMap()
        snapshots[stage]=samples;data["itemSnapshots"]=JsonObject(snapshots);save(context,data)
    }
    @Synchronized fun response(context:Context,url:String,body:String,type:String,page:Int,valid:Boolean,expected:String,requestBody:String="") {
        PddBusinessJournal.append(context,"官方业务响应",mapOf("endpoint" to PddDiagnosticPayload.endpoint(url),"requestType" to type,"page" to page.toString(),"expectedType" to expected,"orderArrayRecognized" to valid.toString()),body,requestBody)
        val shape=PddDiagnosticPayload.schema(body)
        val data=root(context);val section=if(expected in setOf("all","unreceived"))"responses" else "detailResponses"
        val responses=(data[section] as? JsonArray).orEmpty().toMutableList()
        val sample=buildJsonObject {put("endpoint",PddDiagnosticPayload.endpoint(url));put("requestType",PddDiagnosticPayload.cleanText(type).take(30));put("page",page);put("expectedType",expected);put("orderArrayRecognized",valid);put("schema",shape)}
        val signature=PddDiagnosticPayload.ref(sample.toString())
        if(responses.any {(it as? JsonObject)?.get("signature")==JsonPrimitive(signature)})return
        responses.add(buildJsonObject {put("signature",signature);sample.forEach {(k,v)->put(k,v)}})
        // Response bodies are never persisted; only selected, redacted shape samples.
        data[section]=JsonArray(responses.takeLast(if(section=="responses")12 else 4));save(context,data)
    }
    fun finish(context:Context,outcome:String) {
        note(context,"同步结束",mapOf("outcome" to outcome));items(context,"同步后本地订单",Store.items(context).filter {it.source=="pdd"})
    }
    fun selection(context:Context,items:List<ExpressItem>,retained:Set<String>,since:Long) {
        PddBusinessJournal.append(context,"逐单范围判断",mapOf("since" to since.toString()),JsonArray(items.map {item->buildJsonObject {
            put("orderId",item.id);put("trackingNumber",item.mailNo);put("orderStatus",item.orderStatus);put("stage",ParcelPresentation.stage(item).name)
            put("createdAt",item.orderCreatedAt);put("completedAt",item.completedAt);put("orderCompletedAt",item.orderCompletedAt)
            put("retained",item.id in retained);put("reason",if(item.id !in retained)"不满足购买时间/完成时间/缓存未完成订单保留条件" else if(item.orderCreatedAt==0L)"缺下单时间，保留订单" else if(item.orderCreatedAt>=since)"购买时间在范围内" else "完成时间或缓存订单保留")
        }}).toString())
    }
    @Synchronized fun report(context:Context):String {
        val data=root(context)
        if(data.isEmpty())return "尚无绑定与同步诊断；请从拼多多登录授权开始重试一次。"
        return "拼多多绑定与同步诊断 v4\n简报使用匿名关联；业务日志保留订单号、运单号、商品及物流原文。登录凭证与收件隐私已移除。\n"+
            pretty.encodeToString(JsonElement.serializer(),JsonObject(data))+"\n\n"+PddBusinessJournal.report(context)+"\n[完整诊断结束]"
    }
    @Synchronized fun summary(context:Context)=PddDiagnosticSummary.render(JsonObject(root(context)).toString())
}
