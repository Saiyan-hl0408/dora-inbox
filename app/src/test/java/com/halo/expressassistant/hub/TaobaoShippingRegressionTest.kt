package com.halo.expressassistant.hub

import com.halo.expressassistant.api.TbOrderResponse
import com.halo.expressassistant.api.TbOrderShipping
import com.halo.expressassistant.api.TbOrders
import org.junit.Assert.*
import org.junit.Test

class TaobaoShippingRegressionTest {
    @Test fun officialPddUnshippedBannerIsAValidDetailWithoutTracking() {
        val detail=PddDetailPayload.read("""{"data":{"statusPrompt":"","topBanner":{"orderStatusPrompt":"待发货","orderStatusSubPrompt":"预计拼单成功后2天内发货"},"chatStatusPrompt":"拼单成功，待发货","shippingStatus":0,"orderButtons":[]},"text":"待发货\n预计拼单成功后2天内发货"}""")
        assertEquals("待发货",detail.orderStatus)
        assertTrue(detail.points.isEmpty())
        assertTrue(detail.nextUrl.isEmpty())
    }
    @Test fun instantDeliveryIsExcludedByOfficialBusinessType() {
        val response="""{"ret":["SUCCESS::调用成功"],"data":{"mainOrders":[
            {"id":"1","extra":{"bizType":5000},"statusInfo":{"text":"交易成功"}},
            {"id":"2","extra":{"bizType":"200"},"statusInfo":{"text":"交易成功"},"operations":[]},
            {"id":"3","statusInfo":{"text":"待发货"},"operations":[]},
            {"id":"4","extra":{"bizType":"unknown"},"statusInfo":{"text":"交易成功"}}
        ]}}"""
        val orders=TbOrderResponse.read(response).orders
        assertEquals("5000",orders[0].businessType)
        assertFalse(TbOrderShipping.requiresDetail(orders[0],false))
        assertTrue(TbOrderShipping.requiresDetail(orders[0],true))
        assertTrue(TbOrderShipping.requiresDetail(orders[1],false))
        assertTrue(TbOrderShipping.requiresDetail(orders[2],false))
        assertTrue(TbOrderShipping.requiresDetail(orders[3],false))
    }
    @Test fun detailFailureDoesNotInvalidateAuthorization() {
        assertFalse(SyncStatusText.requiresLogin("4 条淘宝物流详情未更新，保留已有信息"))
        assertTrue(SyncStatusText.requiresLogin("淘宝订单会话需要重新验证（FAIL_SYS_SESSION_EXPIRED）"))
        assertTrue(SyncStatusText.requiresLogin("拼多多会话需重新验证，请通过官方扫码登录恢复会话。"))
        assertTrue(SyncStatusText.describe("taobao",3,"物流未更新").startsWith("部分完成"))
    }
}
