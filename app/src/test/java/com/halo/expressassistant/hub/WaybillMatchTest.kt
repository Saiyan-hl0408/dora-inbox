package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class WaybillMatchTest {
    private val order=ExpressItem("synthetic-order","","","ORDER-JD-synthetic-order",isOrderNumber=true)
    @Test fun unrelatedSearchHitCannotSupplyPickupCode() {
        assertFalse(WaybillMatch.matches("JDTEST123456","JDOTHER123456",order))
        assertFalse(WaybillMatch.matches(order.mailNo,order.mailNo,order))
        assertTrue(WaybillMatch.matches("JDTEST123456","JDTEST123456",order))
    }
}
