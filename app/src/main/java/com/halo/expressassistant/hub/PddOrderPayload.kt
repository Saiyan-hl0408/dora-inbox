package com.halo.expressassistant.hub

import com.halo.expressassistant.data.*
import kotlinx.serialization.json.*

/** Observed official order-list schema (SSR camelCase and AJAX snake_case). */
object PddOrderPayload {
    val ORDER_KEYS=arrayOf("order_sn","orderSn","order_id","orderId")
    val CREATED_KEYS=arrayOf("order_time","orderTime","order_create_time","createTime","create_time")
    val STATUS_KEYS=arrayOf("order_status_prompt","orderStatusPrompt","status_prompt","statusPrompt","order_status_text")
    val COMPLETED_KEYS=arrayOf("receive_time","receiveTime","confirm_time","confirmTime","finish_time")
    private fun JsonObject.text(vararg keys:String)=keys.firstNotNullOfOrNull {(this[it] as? JsonPrimitive)?.contentOrNull?.takeIf {s->s.isNotBlank() && s!="null"}}.orEmpty()
    private fun JsonObject.at(vararg keys:String)=ParcelTime.parse(text(*keys))
    fun read(captures:List<Pair<String,String>>,accountId:String="",accountLabel:String="",onExcluded:(String,String)->Unit={_,_->}):Pair<List<ExpressItem>,Map<String,JdGoods>> {
        val orders=linkedMapOf<String,ExpressItem>();val goods=linkedMapOf<String,JdGoods>()
        fun order(obj:JsonObject) {
            val id=obj.text(*ORDER_KEYS)
            if(id.isBlank())return
            val tracking=TrackingFields.read(obj.toString())
            val mail=tracking.waybill.ifBlank {"ORDER-PDD-$id"}
            val commerce=obj.text(*STATUS_KEYS)
            val state=when {
                listOf("退款","售后","取消","关闭","退回","拒收").any(commerce::contains)->4
                listOf("交易成功","待评价","已评价","已完成").any(commerce::contains)->3
                ParcelStatusAliases.packing.any(commerce::contains) || commerce.contains("待成团")->1
                else->0
            }
            val logistics=PddLogistics.status(commerce).takeIf {PddLogistics.stage(it) in setOf(ParcelPresentation.Stage.CLOSED,ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.TRANSIT)}.orEmpty()
            val firstGoods=((obj["order_goods"] ?: obj["orderGoods"]) as? JsonArray)?.firstOrNull() as? JsonObject
            var item=ExpressItem(id,"PDD",tracking.company,mail,source="pdd",provider="Pinduoduo",queryChannel=id,
                accountId=accountId,accountLabel=accountLabel,isOrderNumber=tracking.waybill.isBlank(),
                orderStatus=commerce,state=if(PddLogistics.stage(logistics)==ParcelPresentation.Stage.CLOSED)3 else state,stateName=logistics.ifBlank {commerce},
                logisticsStatus=logistics,latestText=commerce,originalName=firstGoods?.text("goods_name","goodsName")?.takeIf(GoodsNamePolicy::usable).orEmpty(),observedAt=System.currentTimeMillis(),
                orderCreatedAt=obj.at(*CREATED_KEYS),
                orderCompletedAt=obj.at(*COMPLETED_KEYS))
            val express=(obj["express"] as? JsonObject)
            if(express!=null) {
                val title=express.text("statusPrompt","status_prompt")
                val time=express.text("timestamp","timeLabel","time_label")
                val desc=express.text("lastTrace","last_trace")
                if(PddLogistics.stage(title)!=null)item=PddLogistics.apply(item,listOf(DetailPoint("$title $desc",time)),title)
            }
            val g=firstGoods
            if(g!=null) {
                val name=g.text("goods_name","goodsName")
                val image=g.text("thumb_url","thumbUrl","goods_image_url").let {if(it.startsWith("//"))"https:$it" else it}
                val count=g.text("goods_number","goodsNumber","goods_num")
                item=item.copy(orderImageUrl=image,orderGoodsCount=if(count.isBlank())"" else "x$count")
                if(name.isNotBlank() || image.isNotBlank() || count.isNotBlank())goods[mail]=GoodsCache.merge(goods[mail],JdGoods(name=name.take(200),imageUrl=image,count=if(count.isBlank())"" else "x$count"))
            }
            item=item.copy(orderWaybill=tracking.waybill)
            orders[id]=orders[id]?.let {SyncCacheMerge.merge(it,item)} ?: item
        }
        fun walk(value:JsonElement) {
            when(value) {
                is JsonObject->{
                    PddOrderBusiness.nonParcelReason(value)?.let {reason->
                        value.text(*ORDER_KEYS).takeIf(String::isNotBlank)?.let {onExcluded(it,reason)}
                        return // Do not flatten the special-channel parent's child orders into parcels.
                    }
                    if(value.text("order_sn","orderSn","order_id","orderId").isNotBlank()){order(value);return}
                    value.forEach {(key,child)->if(!key.contains("recommend",true) && key !in setOf("goods_list","rec_list","orderGoods","order_goods"))walk(child)}
                }
                is JsonArray->value.forEach(::walk)
                else->Unit
            }
        }
        captures.forEach {(url,body)->if(url.contains("order_list") || url.contains("ordersStore"))runCatching {walk(Json.parseToJsonElement(body))}}
        // Track/status codes are not invented meanings. Only explicit textual latest nodes merge.
        captures.filter {(url,_)->url.contains("track/latest")}.forEach {(_,body)->
            fun merge(value:JsonElement) {
                when(value) {
                    is JsonObject->{
                        val id=value.text("order_sn","orderSn");val old=orders[id]
                        val title=value.text("status_prompt","statusPrompt","status_text","statusText")
                        val at=value.text("timestamp","time","timeLabel","logistics_update_time")
                        if(old!=null && PddLogistics.stage(title)!=null)orders[id]=PddLogistics.apply(old,listOf(DetailPoint("$title ${value.text("lastTrace","last_trace","desc","context")}",at)),title)
                        else value.values.forEach(::merge)
                    }
                    is JsonArray->value.forEach(::merge)
                    else->Unit
                }
            }
            runCatching {merge(Json.parseToJsonElement(body))}
        }
        return orders.values.toList() to goods
    }
}
