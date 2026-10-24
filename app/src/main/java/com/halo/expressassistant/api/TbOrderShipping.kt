package com.halo.expressassistant.api

/** bizType=5000 was verified against this account's four food-delivery orders.
 * Unknown types remain eligible; an already resolved courier waybill always wins. */
object TbOrderShipping {
    fun requiresDetail(order:TbOrders.TbOrder,knownWaybill:Boolean)=knownWaybill || order.businessType!="5000"
}
