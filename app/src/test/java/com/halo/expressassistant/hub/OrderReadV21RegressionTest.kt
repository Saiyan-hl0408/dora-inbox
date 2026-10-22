package com.halo.expressassistant.hub

import com.halo.expressassistant.api.TbOrderResponse
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class OrderReadV21RegressionTest {
    @Test fun taobaoNestedCreationTimeAndLabelAreOrderScoped() {
        val nested=TbOrderResponse.read("""{"ret":["SUCCESS::调用成功"],"data":{"mainOrders":[{"id":"123456789","orderInfo":{"createTime":"2026-10-19 10:00:00"},"subOrders":[{"itemInfo":{"createTime":"2025-01-01 00:00:00"}}]}]}}""").orders.single()
        assertEquals(ParcelTime.parse("2026-10-19 10:00:00"),nested.createdAt)
        val labeled=TbOrderResponse.read("""{"ret":["SUCCESS::调用成功"],"data":{"mainOrders":[{"id":"123456789","timeInfo":[{"name":"付款时间","value":"2026-10-20 10:00:00"},{"name":"创建时间","value":"2026-10-19 10:00:00"}]}]}}""").orders.single()
        assertEquals(nested.createdAt,labeled.createdAt)
    }
    @Test fun taobaoProductOrPaymentDateIsNotCreationDate() {
        val item=TbOrderResponse.read("""{"ret":["SUCCESS::调用成功"],"data":{"mainOrders":[{"id":"123456789","payTime":"2026-10-19 10:00:00","subOrders":[{"itemInfo":{"createTime":"2026-10-19 10:00:00"}}]}]}}""").orders.single()
        assertEquals(0L,item.createdAt)
    }
    @Test fun jdAdministrativeUpdatesDoNotInventSignatureOrArrival() {
        fun order(code:Int)=Json.parseToJsonElement("""{"baseOrderInfo":{"customOrderNo":"SYNTHETIC001","lastOperatorTime":"2026-10-19 10:00:00","cusBaseInfo":{"orderStatus":$code}}}""").jsonObject
        assertEquals(0L,JdlPayload.parse(order(4),"root","test").completedAt)
        assertEquals(0L,JdlPayload.parse(order(9),"root","test").arrivedAt)
    }
    @Test fun oldJdInferredTimesAreReplacedButVerifiedTimesSurviveTemporaryTraceLoss() {
        val old=com.halo.expressassistant.data.ExpressItem("id","jd","京东","SYNTHETIC001",source="jd",completedAt=123L,arrivedAt=100L)
        val fresh=old.copy(completedAt=0,arrivedAt=0,logisticsVersion=21)
        assertEquals(0L,SyncCacheMerge.merge(old,fresh).completedAt)
        assertEquals(0L,SyncCacheMerge.merge(old,fresh).arrivedAt)
        assertEquals(123L,SyncCacheMerge.merge(old.copy(logisticsVersion=21),fresh).completedAt)
    }
    @Test fun taobaoUltronChildFieldsAndPlacedStageSupplyCreationOnly() {
        val page=TbOrderResponse.read("""{"ret":["SUCCESS::调用成功"],"data":{"data":{"Main_1":{"fields":{"orderId":"123456789"}},"Time_1":{"fields":{"label":"创建时间","value":"2026-10-19 10:00:00"}}},"hierarchy":{"structure":{"Main_1":["Time_1"]}}}}""")
        assertEquals(ParcelTime.parse("2026-10-19 10:00:00"),page.orders.single().createdAt)
        assertEquals(ParcelTime.parse("2026-10-19 10:00:00"),com.halo.expressassistant.api.TbOrderTime.placedStage(Json.parseToJsonElement("""{"createTime":"2026-10-20 12:00:00","multiStage":[{"title":"已签收","subtitle":"2026-10-20 10:00:00"},{"title":"已下单","subtitle":"2026-10-19 10:00:00"}]}""")))
        assertEquals(0L,com.halo.expressassistant.api.TbOrderTime.placedStage(Json.parseToJsonElement("""{"createTime":"2026-10-20 12:00:00"}""")))
    }
    @Test fun missingPddDatesReuseSameOrderCacheOrRemainUnknownWithoutDroppingTheOrder() {
        val old=com.halo.expressassistant.data.ExpressItem("same","","","ORDER-PDD-same",source="pdd",orderCreatedAt=2000L)
        val scope=PddListScope(1000L,listOf(old))
        val selected=scope.select(listOf(old.copy(orderCreatedAt=0),old.copy(id="new",orderCreatedAt=0)))
        assertEquals(listOf(2000L,0L),selected.map {it.orderCreatedAt})
    }
}
