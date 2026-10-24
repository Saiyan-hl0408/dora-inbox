package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class PddPickupHistoryPriorityTest {
    private fun point(text:String,day:Int)=DetailPoint(text,"2026-10-${day} 12:00:00")
    private val pickup=point("待取件 您的快件已到达【校园东门菜鸟驿站】，请及时取件。",23)
    private val signed=point("已签收 签收代收点：校园西门菜鸟驿站，感谢使用。",24)

    @Test fun pickupStationWinsOverNewerSignedStationForCollectedParcel() {
        val points=listOf(signed,pickup)
        assertEquals("校园东门菜鸟驿站",PddPickupLocation.history(points))
        val item=ExpressItem("order","","申通快递","TEST123456",source="pdd")
        val updated=PddLogistics.apply(item,points)
        assertEquals("已签收",updated.logisticsStatus)
        assertEquals(ParcelTime.parse(signed.time),updated.completedAt)
    }

    @Test fun newestPickupStationWinsRegardlessOfInputOrderOrCurrentStatus() {
        val newer=point("待取件 您的快件已到达【校园北门菜鸟驿站】，请及时取件。",24)
        val olderSigned=point("已签收 签收代收点：校园西门菜鸟驿站，感谢使用。",22)
        for(points in listOf(listOf(pickup,olderSigned,newer),listOf(newer,pickup,olderSigned))) {
            assertEquals("校园北门菜鸟驿站",PddPickupLocation.history(points))
            val item=ExpressItem("order","","申通快递","TEST123456",source="pdd")
            assertEquals("待取件",PddLogistics.apply(item,points).logisticsStatus)
        }
    }

    @Test fun pickupWithoutUsableStationFallsBackToSignedStation() {
        assertEquals("校园西门菜鸟驿站",PddPickupLocation.history(listOf(signed,
            point("待取件 请凭取件码领取包裹。",23))))
    }

    @Test fun otherHistoricalLocationsRemainFallbackWhenPickupIsAbsent() {
        assertEquals("校园西门菜鸟驿站",PddPickupLocation.history(listOf(signed,
            point("运输中 快件已到达【校园东门菜鸟驿站】。",23))))
    }

    @Test fun forwardingWarehouseWithPickupHeadingCannotWinStationSelection() {
        val warehouse=point("待取件 包裹已到达【中转仓菜鸟服务点】，将由中转仓换单发往目的地。",25)
        assertEquals("运输中",PddLogistics.status(warehouse.context))
        assertEquals("校园东门菜鸟驿站",PddPickupLocation.history(listOf(warehouse,signed,pickup)))
    }
}
