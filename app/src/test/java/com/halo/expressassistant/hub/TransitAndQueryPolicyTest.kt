package com.halo.expressassistant.hub

import com.halo.expressassistant.data.*
import org.junit.Assert.*
import org.junit.Test

class TransitAndQueryPolicyTest {
    private fun order(status:String)=ExpressItem("test","","","ORDER-PDD-test",source="pdd",isOrderNumber=true,orderStatus=status,stateName=status,originalName="实体商品")
    @Test fun paidPackingAliasesAppearInTransitButNeverUnpaidVirtualOrCancelledOrders() {
        for(alias in listOf("打包中","备货中","未出库","待出库","待发货")) {
            assertTrue(alias,TransitGroup.includes(order(alias)))
            assertFalse(alias,PddPagingPolicy.refreshInScope(order(alias),0,false,true))
        }
        for(status in listOf("待付款","待成团","退款成功","订单取消成功"))assertFalse(status,TransitGroup.includes(order(status)))
        assertFalse(TransitGroup.includes(order("待发货").copy(originalName="省钱月卡")))
    }
    @Test fun deliveryPrecedesTransitAndPackingRegardlessOfPurchaseDate() {
        val packing=order("打包中").copy(id="packing",orderCreatedAt=999)
        val moving=order("运输中").copy(id="moving",isOrderNumber=false,logisticsStatus="运输中",stateName="运输中",latestText="运输中",orderCreatedAt=500)
        val delivery=moving.copy(id="delivery",logisticsStatus="配送中",stateName="配送中",state=5,orderCreatedAt=100)
        assertEquals(listOf("delivery","moving","packing"),TransitGroup.ordered(listOf(packing,moving,delivery)).map {it.id})
        assertEquals("派送中",TransitGroup.label(delivery))
        assertEquals("打包中",TransitGroup.label(packing))
    }
    @Test fun freshOfficialAliasesMoveOneOrderThroughItsActualLifecycle() {
        var item=order("打包中")
        assertTrue(TransitGroup.includes(item))
        item=item.copy(orderStatus="待收货")
        item=PddLogistics.apply(item,listOf(DetailPoint("等待领取 请及时取件","2026-10-12 09:00:00")))
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(item))
        assertFalse(TransitGroup.includes(item))
        item=PddLogistics.apply(item,listOf(DetailPoint("已领取 凭取件码领取","2026-10-12 19:00:00")))
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(item))
        val cancelled=SyncCacheMerge.merge(item,item.copy(orderStatus="订单取消成功",logisticsVersion=0,logisticsStatus=""))
        assertEquals(ParcelPresentation.Stage.AFTERSALE,ParcelPresentation.stage(cancelled))
        assertFalse(TransitGroup.includes(cancelled))
    }
    @Test fun unknownSignatureIsQueriedOnceButVerifiedSignedParcelDoesNotRequery() {
        val item=order("已签收，待确认收货").copy(isOrderNumber=false,logisticsStatus="已签收")
        assertTrue(PddPagingPolicy.refreshInScope(item,0,false,true))
        val done=item.copy(completedAt=100,logisticsVersion=1,companyName="韵达快递",pickupStation="测试驿站")
        assertFalse(PddPagingPolicy.refreshInScope(done,0,false,true))
    }
    @Test fun detailBudgetAndRetryCooldownPreventRepeatedLongRefreshes() {
        assertEquals(25000L,PddDetailQueryPolicy.remainingTimeout(0,0,false))
        assertEquals(10000L,PddDetailQueryPolicy.remainingTimeout(0,30000,false))
        assertEquals(0L,PddDetailQueryPolicy.remainingTimeout(0,45000,false))
        assertEquals(25000L,PddDetailQueryPolicy.remainingTimeout(0,45000,true))
        assertFalse(PddDetailQueryPolicy.mayAttempt(100,200,true))
        assertTrue(PddDetailQueryPolicy.mayAttempt(100,200,false))
        assertTrue(PddDetailQueryPolicy.mayAttempt(200,200,true))
    }
    @Test fun emptyOrUndatedFallbackCannotBeMarkedAsSuccessfulLogisticsRefresh() {
        assertFalse(PddDetailQueryPolicy.hasLogistics(emptyList()))
        assertFalse(PddDetailQueryPolicy.hasLogistics(listOf(DetailPoint("已签收",""))))
        assertTrue(PddDetailQueryPolicy.hasLogistics(listOf(DetailPoint("已签收","2026-10-12 19:00:00"))))
    }
}
