package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class SyncLatencyRegressionTest {
    private fun parcel()=ExpressItem("order","PDD","圆通快递","YT0000000123456",source="pdd",orderStatus="待收货")
    @Test fun rejectedListDoesNotSpendTwentyFiveSecondsOnEachCachedParcel() {
        assertFalse(PddDetailQueryPolicy.mayReadDetails(true,0,"拼多多会话需重新验证，请通过官方扫码登录恢复会话。"))
        assertFalse(PddDetailQueryPolicy.mayReadDetails(false,0,null))
        assertTrue(PddDetailQueryPolicy.mayReadDetails(true,0,null))
        assertTrue(PddDetailQueryPolicy.mayReadDetails(true,20,"分页未完成"))
    }
    @Test fun detailsWarningDoesNotLabelSuccessfullyReadOrdersAsTotalFailure() {
        assertEquals("部分更新",SyncStatusText.brief(20,"2 条物流详情未更新，保留已有信息"))
        assertEquals("读取失败",SyncStatusText.brief(0,"会话需重新验证"))
        assertEquals("已更新",SyncStatusText.brief(0,null))
    }
    @Test fun freshlySignedAwaitingReceiptGetsDetailsBeforeHistoricalClosedOrders() {
        val awaiting=parcel().copy(orderStatus="已签收，待确认收货",logisticsStatus="已签收",completedAt=0)
        val old=parcel().copy(orderStatus="交易成功",logisticsStatus="已签收",completedAt=1)
        assertTrue(PddDetailQueryPolicy.priority(awaiting)<PddDetailQueryPolicy.priority(old))
        assertTrue(PddDetailQueryPolicy.normalSelection(List(4){old}+awaiting).contains(awaiting))
    }
}
