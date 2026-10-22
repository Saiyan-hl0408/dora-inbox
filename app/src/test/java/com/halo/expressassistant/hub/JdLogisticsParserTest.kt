package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class JdLogisticsParserTest {
    private val item=ExpressItem("synthetic-order","","京东快递","JDTEST123456",source="jd",orderStatus="待收货")
    @Test fun pickupCardOutsideTimestampedTimelineIsKept() {
        val result=JdLogisticsParser.parse("取件码：2-3-4567\n包裹已到达自提点\n2026-10-08 12:30:00",item)
        assertTrue(result.data.any {PickupText.parse(it.context).codes.contains("2-3-4567")})
    }
    @Test fun finalTraceWithoutFollowingTimeIsNotDropped() {
        val result=JdLogisticsParser.parse("2026-10-08 12:30:00\n包裹已到达自提点，取件码：2-3-4567",item)
        assertTrue(result.data.any {PickupText.parse(it.context).codes.contains("2-3-4567")})
    }
    @Test fun sameLineTimeAndDescriptionIsNotDropped() {
        val result=JdLogisticsParser.parse("2026-10-08 12:30:00 包裹已到达自提点，取件码：2-3-4567",item)
        assertTrue(result.data.any {it.time=="2026-10-08 12:30:00" && PickupText.parse(it.context).codes.contains("2-3-4567")})
    }
}
