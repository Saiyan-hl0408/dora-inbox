package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.assertEquals
import org.junit.Test

class JdLogisticsStateTest {
    @Test fun deliveredDoesNotReopenFromHistoricalPickupTextOrCode() {
        val item=ExpressItem("JDTEST000001","jd","京东快递","JDTEST000001",source="jd",
            queryChannel="https://kd.jd.com/express/orderDetail",state=3,stateNum=107,stateName="已送达",
            latestText="待自提，凭取件码取件",pickupCode="TEST-01")
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(item))
    }
}
