package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import org.junit.Assert.*
import org.junit.Test

class PddLocationPhoneFeedbackTest {
    @Test fun phoneSchoolHeaderDoesNotNeedStationSuffix() {
        assertEquals("巴南城市科技学院食堂菜鸟城市科技学院食堂",PddPickupLocation.header("取件码\n700-2-5007\n复制\n巴南城市科技学院食堂菜鸟城市科技学院食堂\n展开\n拨打电话"))
    }
    @Test fun phoneCabinetHeaderDoesNotNeedGenericCabinetSuffix() {
        assertEquals("丰巢快递柜金龙花苑北门岗亭旁2号丰巢柜",PddPickupLocation.header("取件码\n123456\n复制\n丰巢快递柜金龙花苑北门岗亭旁2号丰巢柜"))
    }
    @Test fun signedDropOffAndCabinetAreRecoveredFromSavedTraces() {
        fun history(s:String)=PddPickupLocation.history(listOf(DetailPoint(s,"2026-09-10 13:22:27")))
        assertEquals("巴南城市科技学院食堂菜鸟",history("您的快件已取出签收，签收代收点：代收点-巴南城市科技学院食堂菜鸟，联系代收点电话：<phone-redacted>"))
        assertEquals("金龙花苑北门岗亭旁2号丰巢柜",history("您的快件已领取，收件人在[丰巢](金龙花苑北门岗亭旁2号丰巢柜)取件"))
    }
    @Test fun shippingWarehouseAndRecipientAddressAreNotPickupLocations() {
        assertEquals("",PddPickupLocation.header("收货地址：某学校菜鸟驿站\n联系电话：123"))
        assertEquals("",PddPickupLocation.history(listOf(DetailPoint("您的包裹已送达[拼多多中转仓]，将由中转仓换单发往目的地","2026-10-24 13:34:03"))))
    }
    @Test fun verifiedSavedHistoryRepairsLocationWithoutAnotherPageRead() {
        val item=com.halo.expressassistant.data.ExpressItem("order","","中通快递","TEST123456",source="pdd",pddReadIntegrityVersion=1)
        val points=listOf(DetailPoint("已取出签收，签收代收点：代收点-巴南城市科技学院食堂菜鸟，联系代收点","2026-09-10 13:22:27"))
        assertEquals("巴南城市科技学院食堂菜鸟",PddLocationRecovery.repair(item,points).pickupStation)
        assertEquals("",PddLocationRecovery.repair(item.copy(pddReadIntegrityVersion=0),points).pickupStation)
        assertEquals("原地点",PddLocationRecovery.repair(item.copy(pickupStation="原地点"),points).pickupStation)
    }
    @Test fun statusBannerIsNotAStationEvenWhenItMentionsDropOff() {
        val text="包裹已入代收点，请及时取件\n\n代收点便民驿站-重外学苑超市菜鸟驿站\n京东配送\n复制"
        assertEquals("便民驿站-重外学苑超市菜鸟驿站",PddPickupLocation.header(text))
        assertEquals("",PddPickupLocation.header("包裹已入代收点，请及时取件"))
        assertEquals("",PddPickupLocation.header("请到菜鸟驿站领取包裹"))
    }
    @Test fun oldWrongBannerCacheIsRepairedFromVerifiedHistoricalArrival() {
        val item=com.halo.expressassistant.data.ExpressItem("order","","京东配送","JDAP00000000",source="pdd",pddReadIntegrityVersion=1,
            pickupStation="包裹已入代收点，请及时取件",pddPickupStation="包裹已入代收点，请及时取件")
        val points=listOf(DetailPoint("已签收 您的快件已送达","2026-06-15 12:22:30"),
            DetailPoint("待取件 您的快件已到达【便民驿站-重外学苑超市菜鸟驿站】，请凭取件码及时取件。","2026-06-15 11:30:28"))
        assertEquals("便民驿站-重外学苑超市菜鸟驿站",PddLocationRecovery.repair(item,points).pickupStation)
    }
}
