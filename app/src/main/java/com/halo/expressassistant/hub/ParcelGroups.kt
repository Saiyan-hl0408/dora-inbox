package com.halo.expressassistant.hub

import com.halo.expressassistant.data.*
import java.util.Locale

/** Physical package presentation; storage and logistics requests retain each original order. */
object ParcelGroups {
    private fun key(item:ExpressItem)=if(item.source=="pdd")
        "pdd|${item.accountId}|${item.mailNo.uppercase(Locale.ROOT)}" else "parcel|${item.mailNo.uppercase(Locale.ROOT)}"
    fun items(stored:List<ExpressItem>):List<ExpressItem> = stored.groupBy(::key).values.map {rows->
        if(rows.first().source!="pdd")rows.last() else {
            val orders=rows.distinctBy {it.id}
            val primary=orders.maxBy {maxOf(it.statusUpdatedAt,ParcelTime.parse(it.latestTime))}
            if(orders.size==1)primary else primary.copy(relatedGoods=orders.map {
                ParcelOrderGoods(it.id,it.originalName.takeIf(GoodsNamePolicy::usable) ?: "商品信息未提供",it.orderImageUrl,it.orderGoodsCount,it.orderWaybill)
            })
        }
    }
    fun title(item:ExpressItem,goods:JdGoods?):String=if(item.relatedGoods.size>1)
        item.relatedGoods.joinToString("、") {it.name} else if(item.source=="pdd")
        item.originalName.takeIf(GoodsNamePolicy::usable) ?: GoodsCache.title(goods,item.originalName) else GoodsCache.title(goods,item.originalName)
    fun image(item:ExpressItem,goods:JdGoods?)=item.orderImageUrl.ifBlank {goods?.imageUrl.orEmpty()}
    fun current(stored:List<ExpressItem>,item:ExpressItem)=items(stored).firstOrNull {
        it.source==item.source && it.accountId==item.accountId && it.mailNo.equals(item.mailNo,true)
    } ?: item
}
