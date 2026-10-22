package com.halo.expressassistant.hub

import com.halo.expressassistant.data.JdGoods
import org.junit.Assert.*
import org.junit.Test

class PddRefreshRegressionTest {
    @Test fun signedAwaitingConfirmationStillNeedsActualTimeWhenOrderedOutsideWindow() {
        val raw="""{"orders":[{"order_sn":"milk","tracking_number":"465740832195331","order_time":"2026-10-04 10:00:00","order_status_prompt":"已签收，待确认收货","order_goods":[{"goods_name":"娃哈哈AD钙奶"}]}]}"""
        val item=PddOrderPayload.read(listOf("/order_list_v4" to raw)).first.single()
        assertTrue(PddPagingPolicy.refreshInScope(item,ParcelTime.parse("2026-10-07"),false,freshAwaitingReceipt=true))
    }
    @Test fun repeatedPartialOrderResponseCannotReplaceProductNameWithQuantity() {
        fun read(name:String)=PddOrderPayload.read(listOf("/order_list_v4" to """{"orders":[{"order_sn":"milk","tracking_number":"465740832195331","order_goods":[{"goods_name":$name,"goods_number":1}]}]}""")).second
        var cache=read("\"娃哈哈AD钙奶220ml整箱\"")
        for(name in listOf("1","\"1\"","\"x1\""))cache=GoodsCache.mergeAll(cache,read(name))
        assertEquals("娃哈哈AD钙奶220ml整箱",cache["465740832195331"]?.name)
        assertEquals("x1",cache["465740832195331"]?.count)
    }
    @Test fun corruptedExistingNameIsRepairedByNextValidOrderResponse() {
        val old=JdGoods(name="1",shortName="1",imageUrl="https://example.test/milk.jpg",count="x1")
        val fresh=JdGoods(name="娃哈哈AD钙奶220ml整箱")
        val merged=GoodsCache.merge(old,fresh)
        assertEquals(fresh.name,merged.name)
        assertEquals("",merged.shortName)
        assertEquals(old.imageUrl,merged.imageUrl)
    }
    @Test fun displayRecoversOriginalNameEvenBeforeAnotherNetworkRefresh() {
        assertEquals("真实牛奶商品",GoodsCache.title(JdGoods(name="1"),"真实牛奶商品"))
    }
    @Test fun userHistoryChoicesAreCappedAtSixMonthsAndDefaultToAWeek() {
        assertEquals(7,SyncWindow().days)
        assertEquals(listOf(3,7,15,30,90,180),HistoryRangePolicy.options)
        assertEquals(180,HistoryRangePolicy.clamp(365))
        assertEquals(180,HistoryRangePolicy.clamp(0))
        assertEquals(15,HistoryRangePolicy.clamp(15))
    }
    @Test fun returnedPartialOrdersAreReportedAsPartialInsteadOfZeroReadFailure() {
        assertTrue(SyncStatusText.describe("pdd",3,"未到查询边界").startsWith("部分完成"))
        assertTrue(SyncStatusText.describe("pdd",0,"需重新验证").startsWith("失败"))
        assertTrue(SyncStatusText.describe("jd",3,"失败").startsWith("失败"))
    }
}
