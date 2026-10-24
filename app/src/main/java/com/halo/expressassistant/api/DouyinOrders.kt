package com.halo.expressassistant.api

import android.content.Context
import com.halo.expressassistant.data.*
import com.halo.expressassistant.hub.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.concurrent.TimeUnit

/** App-owned official web login. No native Douyin private storage or private-session import. */
object DouyinOrders {
    const val HOME="https://www.douyin.com/jingxuan"
    const val UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    private const val LIST="https://ec.ecombdapi.com/order/yata/orderListQuery?aid=1128&version_code=360400&version_name=36.4.0&device_platform=android"
    private val http=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).followRedirects(false).build()
    private val json=Json {ignoreUnknownKeys=true}
    data class Identity(val id:String,val nickname:String)
    data class Result(val items:List<ExpressItem>,val goods:Map<String,JdGoods>,val warning:String?)
    fun userId(payload:String)=runCatching {Json.parseToJsonElement(payload).jsonObject["user_id"]?.jsonPrimitive?.content.orEmpty()}.getOrDefault("")
    private fun boundedBytes(stream:java.io.InputStream):ByteArray {
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=stream.read(buffer);if(n<0)break;out.write(buffer,0,n);check(out.size()<=2_000_000){"抖音响应过大，读取未完成"}}
        return out.toByteArray()
    }
    private fun request(cookies:String,url:String,body:JsonObject?=null):String {
        check(DouyinSessionPolicy.hasCredentials(cookies)){"请先登录抖音并验证订单权限"}
        val r=Request.Builder().url(url).header("User-Agent",UA).header("Referer",HOME).header("Cookie",cookies)
        if(body!=null)r.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        return http.newCall(r.build()).execute().use {response->
            check(response.isSuccessful){"抖音请求暂不可用，请稍后重试"}
            val stream=response.body?.byteStream() ?: error("抖音返回空响应，已有数据保留")
            val bytes=boundedBytes(stream);check(bytes.size<=2_000_000){"抖音订单响应过大，读取未完成"}
            String(bytes,Charsets.UTF_8).also {check(it.isNotBlank()){ "抖音返回空响应，已有数据保留" }}
        }
    }
    suspend fun identity(cookies:String):Identity=withContext(Dispatchers.IO) {
        val root=runCatching {Json.parseToJsonElement(request(cookies,"https://www.douyin.com/aweme/v1/web/user/profile/self/?aid=6383&device_platform=webapp")).jsonObject}.getOrNull() ?: error("抖音登录验证失败")
        check(root["status_code"]?.jsonPrimitive?.intOrNull==0){"抖音登录已失效，请重新登录"}
        val user=root["user"] as? JsonObject ?: error("抖音登录尚未完成")
        val id=user["uid"]?.jsonPrimitive?.content.orEmpty();check(id.isNotBlank() && id!="0"){"抖音账号未验证"}
        Identity(id,user["nickname"]?.jsonPrimitive?.content.orEmpty())
    }
    private fun version()=buildJsonObject {put("version_code",360400);put("app_id","1128");put("yata_version","1.25.0");put("slice_version","2.32.0");put("h5_orders_version",1791861819396L)}
    private fun firstParams()=buildJsonObject {
        put("technology","h5");put("enter_from","mall_homepage_request");put("page_type","YATA_H5");put("query_type",1)
        put("order_list_params",buildJsonObject {put("tab",0);put("is_first_enter",true)})
        put("status_bar_height",24);put("safe_area_bottom_height",8);put("system_font_scale",1);put("hide_guess_you_like",true)
        put("yata_session_id",UUID.randomUUID().toString());put("client_version",version().toString())
    }
    private fun page(cookies:String,ex:JsonObject,state:String)=DouyinOrderPayload.read(request(cookies,LIST,buildJsonObject {put("ex_params",ex);put("query_params",state);put("yata_version","1.25.0")}))
    suspend fun verify(cookies:String):Identity=withContext(Dispatchers.IO) {val owner=identity(cookies);page(cookies,firstParams(),"");owner}
    private fun decode(state:String):JsonObject {
        val bytes=Base64.getDecoder().decode(state)
        val decoded=GZIPInputStream(bytes.inputStream()).use {boundedBytes(it)}
        check(decoded.size<=2_000_000){"抖音分页状态过大"}
        return Json.parseToJsonElement(String(decoded,Charsets.UTF_8)).jsonObject
    }
    private fun saved(context:Context,account:BoundAccount):List<DouyinOrder> = runCatching {
        val value=HubData.prefs(context).getString("douyin_orders_${account.id}",null) ?: return emptyList()
        json.decodeFromString<List<DouyinOrder>>(Secrets.decrypt(value))
    }.getOrDefault(emptyList())
    private fun save(context:Context,account:BoundAccount,orders:List<DouyinOrder>) {
        val merged=(saved(context,account)+orders).associateBy {it.id}.values.toList().takeLast(2000)
        HubData.prefs(context).edit().putString("douyin_orders_${account.id}",Secrets.encrypt(json.encodeToString(merged))).apply()
    }
    suspend fun sync(context:Context,account:BoundAccount,onProgress:(String)->Unit={}):Result=withContext(Dispatchers.IO) {
        val cookies=Store.cookieOf(account.payload);val owner=identity(cookies)
        check(userId(account.payload).isNotBlank() && owner.id==userId(account.payload)){"抖音账号与所选绑定不一致，请重新登录"}
        val window=SyncLedger.window(context,"douyin",account.id)
        val active=Store.items(context).filter {it.source=="douyin" && it.accountId==account.id && ParcelPresentation.stage(it) in setOf(ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.TRANSIT,ParcelPresentation.Stage.NOT_SHIPPED)}
        val policy=DouyinPagingPolicy(window.since,window.now,active.mapTo(hashSetOf()){it.queryChannel})
        val collected=linkedMapOf<String,DouyinOrder>();var ex=firstParams();var state="";var stop="page_cap_reached";var pages=0
        while(pages<80) {
            currentCoroutineContext().ensureActive()
            check(Store.accountById(context,"douyin",account.id)?.enabled==true){"抖音账号已停用，已有数据保留"}
            onProgress("正在读取抖音第 ${pages+1} 页")
            val result=page(cookies,ex,state);pages++;result.orders.forEach {order->collected[order.id]=order}
            val reason=policy.accept(result);if(reason!=null){stop=reason;break}
            state=result.yata["linkage"]?.jsonObject?.get("common")?.jsonObject?.get("query_params")?.jsonPrimitive?.content ?: error("抖音缺少续页状态，读取未完成")
            val meta=decode(state)["page_meta"]?.jsonObject ?: error("抖音分页结构未识别")
            val list=meta["order_list_params"]?.jsonObject ?: error("抖音缺少列表游标")
            policy.acceptAnchor(list["page_anchor"]?.jsonPrimitive?.content.orEmpty())
            ex=JsonObject(meta.toMutableMap().apply {put("query_type",JsonPrimitive(1));put("page_type",JsonPrimitive("YATA_H5"));put("client_version",JsonPrimitive(version().toString()));put("hide_guess_you_like",JsonPrimitive(true));put("order_list_params",JsonObject(list.toMutableMap().apply {put("is_first_enter",JsonPrimitive(false))}))})
        }
        check(Store.accountById(context,"douyin",account.id)?.enabled==true){"抖音账号已停用，未写入新数据"}
        val selected=collected.values.filter(policy::include);val items=selected.flatMap {DouyinOrderPayload.items(it,account,window.now)}
        val goods=selected.flatMap {order->order.waybills.map {it.number to DouyinOrderPayload.goods(order)}}.toMap()
        val excluded=selected.count {it.classification=="excluded_virtual"};val unknown=selected.count {it.classification=="unknown"}
        val scopeComplete=stop!="page_cap_reached" && !policy.missingDates
        save(context,account,selected)
        val diagnostic="读取 $pages 页 / ${collected.size} 笔订单；范围内 ${selected.size} 笔，运单 ${items.size} 个；排除虚拟 $excluded 笔，待判断 $unknown 笔。完整轨迹暂不可用。"+
            if(policy.unresolvedActive.isNotEmpty())" ${policy.unresolvedActive.size} 笔旧未完成单本次未返回，保留缓存。" else ""
        HubData.prefs(context).edit().putString("douyin_list_diagnostic",diagnostic).putString("douyin_list_diagnostic_${account.id}",diagnostic).putInt("douyin_pages_${account.id}",pages).putInt("douyin_range_orders_${account.id}",selected.size).putInt("douyin_waybills_${account.id}",items.size).putBoolean("douyin_scope_complete_${account.id}",scopeComplete).putLong("douyin_last_read_${account.id}",window.now).apply()
        if(scopeComplete)SyncLedger.stage("douyin",account.id,items,window)
        val warnings=listOfNotNull(if(!scopeComplete)"抖音范围尚未读完，已保留缓存" else null,if(unknown>0)"$unknown 笔订单履约待确认，未导入" else null,if(policy.unresolvedActive.isNotEmpty())"部分旧未完成单未返回，缓存保留" else null)
        Result(items,goods,warnings.takeIf {it.isNotEmpty()}?.joinToString("；"))
    }
}
