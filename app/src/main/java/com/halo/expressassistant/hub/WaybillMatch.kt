package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

object WaybillMatch {
    fun matches(requested:String,returned:String,order:ExpressItem):Boolean =
        returned.equals(requested,true) && (!order.isOrderNumber || returned!=order.mailNo)
}
