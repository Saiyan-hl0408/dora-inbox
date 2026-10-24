package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import java.net.URLDecoder
import kotlinx.serialization.json.*

/** Only explicit official business-route evidence excludes an order from parcel sync. */
object PddOrderBusiness {
    fun nonParcelReason(order:JsonObject):String? {
        val link=listOf("order_link_url","orderLinkUrl").firstNotNullOfOrNull {
            (order[it] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        } ?: return null
        val query=link.substringAfter('?',"").substringBefore('#').split('&').mapNotNull {
            val pair=it.split('=',limit=2)
            if(pair.size!=2)null else runCatching {URLDecoder.decode(pair[0],"UTF-8") to URLDecoder.decode(pair[1],"UTF-8")}.getOrNull()
        }.toMap()
        return if(query["_p_page"]=="vgt_order_detail" &&
            (query["_t_timestamp"]=="mobile-vegetables" || query["_x_cid"]=="ddmc_order_list"))
            "多多买菜业务订单，不属于普通快递查询" else null
    }
    fun key(accountId:String,orderId:String)="${accountId.length}:$accountId$orderId"
    fun keep(item:ExpressItem,excluded:Set<String>)=
        item.source!="pdd" || key(item.accountId,item.id) !in excluded
    fun pageFullyExcluded(serverCount:Int,excludedCount:Int)=serverCount>0 && excludedCount==serverCount
}
