package com.halo.expressassistant.hub

import com.halo.expressassistant.data.JdGoods

/** A detail without an image must not erase a usable list image or older cached image. */
object GoodsCache {
    fun clean(goods:JdGoods,fallbackName:String="")=goods.copy(
        name=goods.name.takeIf(GoodsNamePolicy::usable) ?: fallbackName.takeIf(GoodsNamePolicy::usable).orEmpty(),
        shortName=goods.shortName.takeIf(GoodsNamePolicy::usable).orEmpty())
    fun title(goods:JdGoods?,originalName:String=""):String=goods?.name?.takeIf(GoodsNamePolicy::usable)
        ?: originalName.takeIf(GoodsNamePolicy::usable) ?: "商品信息未提供"
    fun merge(old:JdGoods?,fresh:JdGoods):JdGoods {
        val safe=clean(fresh)
        val previous=old?.let {clean(it)} ?: return safe
        return safe.copy(name=safe.name.ifBlank {previous.name},imageUrl=safe.imageUrl.ifBlank {previous.imageUrl},
            count=safe.count.ifBlank {previous.count},shortName=previous.shortName.ifBlank {safe.shortName})
    }
    fun mergeAll(old:Map<String,JdGoods>,fresh:Map<String,JdGoods>)=old.toMutableMap().apply {
        fresh.forEach {(number,goods)->this[number]=merge(this[number],goods)}
    }
}
