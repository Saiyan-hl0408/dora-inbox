package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

/** An order is not automatically a parcel. A resolved physical waybill takes precedence. */
object ParcelEligibility {
    private val shipping=Regex("已发货|已出库|派送|派件|运输|转运|揽收|请上门提货|等待取件|已取件|已取货|已签收")
    fun hasShippingText(text:String)=shipping.containsMatchIn(text)
    fun isVirtual(item:ExpressItem,goodsName:String=""):Boolean {
        if(!item.isOrderNumber && item.mailNo.isNotBlank() && !item.mailNo.startsWith("ORDER-"))return false
        if(hasShippingText(item.latestText))return false
        return Regex("优惠券|支付券|电子券|券包|省钱月卡|会员权益|电子会员|买药权益|开方服务|问诊服务|虚拟商品").containsMatchIn(goodsName+" "+item.originalName+" "+item.latestText)
    }
}
