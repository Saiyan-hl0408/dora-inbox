package com.halo.expressassistant.hub

import com.halo.expressassistant.api.TbOrderResponse
import com.halo.expressassistant.data.*
import org.junit.Assert.*
import org.junit.Test

class TbReadAndQueryTest {
    private val now=ParcelTime.parse("2026-10-14 12:00:00")
    private val success="""{"ret":["SUCCESS::调用成功"],"data":{"result":{"hasMore":false,"mainOrders":[{"id":"order-a","createTime":"2026-10-13 10:00:00","statusInfo":{"text":"已签收，待确认收货"},"subOrders":[{"itemInfo":{"title":"real title","pic":"//image.example/goods"}}]}]}}}"""
    @Test fun plainAndJsonpHaveSameOrderMetadata(){
        val plain=TbOrderResponse.read(success);val jsonp=TbOrderResponse.read("mtopjsonp1($success);")
        assertEquals(plain,jsonp);assertEquals(false,plain.hasMore);assertEquals("real title",plain.orders.single().goodsName)
        assertEquals(ParcelTime.parse("2026-10-13 10:00:00"),plain.orders.single().createdAt)
    }
    @Test fun errorsCannotBeSuccessfulEmptyOrders(){
        for(body in listOf("""{"ret":["FAIL_SYS_SESSION_EXPIRED::sensitive server text"],"data":{}}""","""{"ret":["SUCCESS::ok"],"data":{"unexpected":1}}""","<html>login</html>")) {
            try{TbOrderResponse.read(body);fail("must fail") }catch(e:TbOrderResponse.Failure){assertFalse(e.message.orEmpty().contains("sensitive"))}
        }
    }
    @Test fun legitimateEmptyOrdersAreSuccessful(){assertTrue(TbOrderResponse.read("""{"ret":["SUCCESS::ok"],"data":{"result":"{\"mainOrders\":[]}"}}""").orders.isEmpty())}
    @Test fun boundsAreCalendarDaysAndNeverExhaustive(){
        val window=TbPagingPolicy.window(7,now)
        assertEquals(ParcelTime.parse("2026-10-08 00:00:00"),window.since)
        assertFalse(TbPagingPolicy.inRange(window.since-1,window.since,now));assertTrue(TbPagingPolicy.inRange(window.since,window.since,now))
        assertEquals(180,TbPagingPolicy.window(365,now).days);assertTrue(TbPagingPolicy.pageLimit(365)<=24)
        assertFalse(TbPagingPolicy.reachedBoundary(listOf(0,window.since-1),window.since))
    }
    @Test fun manualSinglePlatformStaysQueuedWhileBusy(){
        val queue=BindingSyncQueue();queue.request("pdd");queue.requestManual("taobao")
        assertNull(queue.takeNext(true,true));assertEquals(setOf("taobao"),queue.takeNext(false,true)?.channels)
        assertEquals(setOf("pdd"),queue.takeNext(false,true)?.channels)
    }
    @Test fun standaloneQueriesImportAndUpdateWithoutDuplicating(){
        val found=CainiaoPickup.Result(CainiaoPickup.Outcome.FOUND,"JT1234567890","8-1-1005","学校驿站",arrivedAt="2026-10-13 10:00:00")
        val first=PickupImport.reconcile(emptyList(),found,now)
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(first.single()))
        val signed=found.copy(outcome=CainiaoPickup.Outcome.COLLECTED,code="",status="已签收",signedAt="2026-10-14 10:00:00",historicalCode=found.code)
        val second=PickupImport.reconcile(first,signed,now)
        assertEquals(1,second.size);assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(second.single()))
        assertEquals("",second.single().pickupCode);assertEquals(found.code,second.single().historicalPickupCode)
        assertEquals(ParcelTime.parse(signed.signedAt),second.single().completedAt)
    }
    @Test fun failuresAndMissingTimeDoNotInventRecordsOrDates(){
        assertTrue(PickupImport.reconcile(emptyList(),CainiaoPickup.Result(CainiaoPickup.Outcome.NO_MATCH,"JT1234567890"),now).isEmpty())
        val item=PickupImport.reconcile(emptyList(),CainiaoPickup.Result(CainiaoPickup.Outcome.COLLECTED,"JT1234567890",status="已签收",arrivedAt="2026-10-13 10:00:00"),now).single()
        assertEquals(0L,ParcelPresentation.eventTime(item));assertEquals(0L,item.completedAt)
    }
    @Test fun stationHistoryCannotReclassifyShoppingOrders(){
        val old=ExpressItem("1","","韵达","1234567890",source="pdd",logisticsStatus="待取件",stateName="待取件")
        val signed=CainiaoPickup.Result(CainiaoPickup.Outcome.COLLECTED,old.mailNo,status="已签收",historicalCode="old-code")
        assertEquals(old,PickupImport.reconcile(listOf(old),signed,now).single())
    }
    @Test fun newerUltronResponseRetainsGoodsAndPaging(){
        val body="""{"ret":["SUCCESS::ok"],"data":{"container":{"data":[]},"global":{"pageControl":{"hasMore":false}},"hierarchy":{"structure":{"Main_1234567890":["item_a"]}},"data":{"Main_1234567890":{"id":"1234567890","fields":{"orderStatus":"待取件","createTime":"2026-10-13 12:00:00"}},"item_a":{"fields":{"item":{"title":"product from official item node","pic":"//example.test/image"}}}}}}"""
        val page=TbOrderResponse.read(body);assertEquals(false,page.hasMore)
        assertEquals("1234567890",page.orders.single().orderId);assertEquals("product from official item node",page.orders.single().goodsName)
    }
    @Test fun orderSyncDoesNotEraseQueryImportedDuringSync(){
        val item=ExpressItem("query","","快递","JT1234567890",source="cainiao",stateName="待取件")
        assertEquals(listOf(item),SyncCacheMerge.withLatest(listOf(item),emptyList()))
    }
    @Test fun explicitPendingWithoutCodeCanBeImported(){
        val result=CainiaoPickup.Result(CainiaoPickup.Outcome.NO_CODE,"JT1234567890",status="取件码",pending=true)
        val item=PickupImport.reconcile(emptyList(),result,now).single()
        assertEquals(ParcelPresentation.Stage.PICKUP,ParcelPresentation.stage(item));assertEquals("",item.pickupCode)
    }
    @Test fun notificationParcelCanBecomeCollectedAfterRealQuery(){
        val old=ExpressItem("notification","","快递","JT1234567890",source="local",stateName="待取件",pickupCode="8-1-1005")
        val result=CainiaoPickup.Result(CainiaoPickup.Outcome.COLLECTED,old.mailNo,status="已签收",signedAt="2026-10-14 10:00:00")
        val item=PickupImport.reconcile(listOf(old),result,now).single()
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(item));assertTrue(item.pickupCode.isBlank())
    }
    @Test fun newerQueryStatusSurvivesStaleSyncSnapshot(){
        val old=ExpressItem("q","","快递","JT1234567890",source="cainiao",stateName="待取件",observedAt=1)
        val latest=old.copy(stateName="已签收",state=3,observedAt=2,completedAt=now)
        assertEquals(latest,SyncCacheMerge.withLatest(listOf(latest),listOf(old)).single())
    }
    @Test fun legacyCapabilitiesDoNotAdvertiseOrderAccess(){assertFalse(BoundAccount("legacy").taobaoOrdersEnabled);assertFalse(BoundAccount("legacy").taobaoOrdersVerified)}
}
