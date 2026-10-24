package com.halo.expressassistant.hub

import com.halo.expressassistant.data.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLDecoder

@Serializable data class DouyinProduct(val name:String,val spec:String="",val image:String="",val count:String="",val gift:Boolean=false,val priceDisplay:String="[]")
@Serializable data class DouyinWaybill(val number:String,val carrierCode:String="",val officialUrl:String,val nativeSchema:String)
@Serializable data class DouyinOrder(val id:String,val createdAt:Long=0,val status:String="",val shop:String="",val logisticsCode:String="",val paidFenRaw:String="",val products:List<DouyinProduct> = emptyList(),val waybills:List<DouyinWaybill> = emptyList(),val classification:String="unknown")

/** Only observed list fields and validated, same-order official logistics routes are accepted. */
object DouyinOrderPayload {
    data class Page(val orders:List<DouyinOrder>,val hasMore:Boolean,val yata:JsonObject)
    private fun JsonObject.text(k:String)=(this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.obj(k:String)=this[k] as? JsonObject
    fun display(value:JsonElement?):String=when(value){is JsonArray->value.joinToString(""){(it as? JsonObject)?.text("content").orEmpty()};is JsonPrimitive->value.contentOrNull.orEmpty();else->""}
    private fun exposure(node:JsonObject):JsonObject=(node.obj("events")?.get("AutoExposure") as? JsonArray)?.firstOrNull()?.jsonObject?.obj("fields")?.obj("params") ?: buildJsonObject {}
    fun query(url:String):Map<String,String> = runCatching {URI(url).rawQuery.orEmpty().split('&').filter {it.isNotEmpty()}.associate {part->val kv=part.split('=',limit=2);URLDecoder.decode(kv[0],"UTF-8") to URLDecoder.decode(kv.getOrElse(1){""},"UTF-8")}}.getOrDefault(emptyMap())
    fun waybill(schema:String,orderId:String):DouyinWaybill?=runCatching {
        val outer=URI(schema);if(outer.scheme !in setOf("sslocal","snssdk1128") || outer.rawAuthority!="webcast_webview")return null
        val inner=query(schema)["url"] ?: return null;val uri=URI(inner)
        if(uri.scheme!="https" || uri.host!="ffh.jinritemai.com" || uri.path!="/forest/render/fe/template/channel/ecommerce_express_h5_pia/html/detail.html")return null
        val params=query(inner);val number=params["track_no"].orEmpty()
        if(params["shop_order_id"]!=orderId || !number.matches(Regex("[A-Za-z0-9]{8,40}")) || number.startsWith("ORDER-",true))return null
        DouyinWaybill(number,params["company_code"].orEmpty(),inner,schema)
    }.getOrNull()
    private fun schemas(value:JsonElement?):List<String> = when(value) {
        is JsonObject->value.flatMap {(k,v)->if(k=="schema" && v is JsonPrimitive)listOf(v.content) else schemas(v)}
        is JsonArray->value.flatMap(::schemas)
        else->emptyList()
    }
    fun read(body:String):Page {
        val root=runCatching {Json.parseToJsonElement(body).jsonObject}.getOrNull() ?: error("抖音订单响应无法解析")
        check(root.text("status_code")=="0") {if(root.text("status_code")=="203010001")"抖音登录已失效，请重新登录" else "抖音订单读取未通过，请在官方页面检查登录"}
        val yata=root.obj("yata_data") ?: error("抖音订单数据结构缺失")
        val data=yata.obj("data") ?: error("抖音订单节点缺失")
        val hasMore=(yata.obj("global")?.get("has_more") as? JsonPrimitive)?.booleanOrNull ?: error("抖音分页状态缺失，未确认读取完成")
        val rows=linkedMapOf<String,DouyinOrder>();val gifts=mutableListOf<Pair<String,DouyinProduct>>()
        for((key,value) in data) {
            if(!key.startsWith("product_line_"))continue
            val node=value as? JsonObject ?: continue;val f=node.obj("fields") ?: continue;val id=f.text("shop_order_id");if(id.isBlank())continue
            val product=DouyinProduct(f.text("product_name"),f.text("sku_spec"),f.text("img"),f.text("count_desc"),(f["is_given_product"] as? JsonPrimitive)?.booleanOrNull==true,f["price_desc"]?.toString() ?: "[]")
            if(product.gift){gifts+=id to product;continue}
            val ep=exposure(node);val seconds=ep.text("create_time").toLongOrNull() ?: 0
            val created=if(seconds in 946684800L..4102444800L)seconds*1000 else 0
            val old=rows[id] ?: DouyinOrder(id)
            rows[id]=old.copy(createdAt=old.createdAt.takeIf {it>0} ?: created,status=old.status.ifBlank {ep.text("order_status")},logisticsCode=ep.text("logistics_status"),paidFenRaw=ep.text("order_pay_price"),products=old.products+product,
                classification=if(ep.text("business_identity").contains("topup") || old.classification=="excluded_virtual")"excluded_virtual" else "unknown")
        }
        gifts.forEach {(id,gift)->rows[id]?.let {rows[id]=it.copy(products=it.products+gift)}}
        for((key,value) in data) {
            val node=value as? JsonObject ?: continue;val f=node.obj("fields") ?: continue;val id=f.text("shop_order_id");var row=rows[id] ?: continue
            if(key.startsWith("store_line_"))row=row.copy(shop=f.text("name"),status=row.status.ifBlank {display(f["store_order_status"])})
            node.obj("events")?.forEach {(event,eventValue)->
                if(event.contains("LookVoucher"))row=row.copy(classification="excluded_virtual")
                if(event.contains("LookLogistics"))row=row.copy(waybills=(row.waybills+schemas(eventValue).mapNotNull {waybill(it,id)}).distinctBy {it.carrierCode to it.number})
            }
            if(row.classification!="excluded_virtual" && row.waybills.isNotEmpty())row=row.copy(classification="physical_with_waybill")
            rows[id]=row
        }
        return Page(rows.values.toList(),hasMore,yata)
    }
    fun items(order:DouyinOrder,account:BoundAccount,now:Long):List<ExpressItem> {
        if(order.classification!="physical_with_waybill")return emptyList()
        return order.waybills.map {w->ExpressItem("DY:${order.id}:${w.number}",w.carrierCode,"",w.number,source="douyin",provider="Douyin",queryChannel=order.id,
            accountId=account.id,accountLabel=account.label,originalName=order.products.filterNot {it.gift}.joinToString(" · "){it.name}.take(200),
            orderStatus=order.status,orderCreatedAt=order.createdAt,state=-1,stateName="物流状态待核实",jumpLinks=w.nativeSchema,observedAt=now)}
    }
    fun goods(order:DouyinOrder):JdGoods {
        val products=order.products.filterNot {it.gift};return JdGoods(products.joinToString(" · "){it.name}.take(200),products.firstOrNull()?.image.orEmpty(),products.singleOrNull()?.count.orEmpty())
    }
}
