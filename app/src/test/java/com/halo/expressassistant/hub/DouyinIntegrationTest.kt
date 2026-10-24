package com.halo.expressassistant.hub

import com.halo.expressassistant.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class DouyinIntegrationTest {
    private val oid="MOCK_ORDER_01"
    private val waybill="MOCK12345678901"
    private val now=ParcelTime.parse("2026-10-22 20:00:00")
    private fun route(id:String=oid,host:String="ffh.jinritemai.com",number:String=waybill):String {
        val inner="https://$host/forest/render/fe/template/channel/ecommerce_express_h5_pia/html/detail.html?shop_order_id=$id&track_no=$number&company_code=MOCK"
        return "sslocal://webcast_webview?url="+URLEncoder.encode(inner,"UTF-8")
    }
    private fun node(gift:Boolean=false,identity:String="xiaodian..",time:Long=1781115786)=buildJsonObject {
        put("fields",buildJsonObject {put("shop_order_id",oid);put("product_name",if(gift)"示例赠品" else "示例实物");put("sku_spec","标准款");put("count_desc","x1");put("img","https://example.invalid/mock.png");put("is_given_product",gift);put("price_desc",buildJsonArray {add(buildJsonObject {put("content","19.90")})})})
        put("events",buildJsonObject {put("AutoExposure",buildJsonArray {add(buildJsonObject {put("fields",buildJsonObject {put("params",buildJsonObject {put("create_time",time.toString());put("order_status","交易完成");put("business_identity",identity);put("logistics_status","3")})})})})})
    }
    private fun response(schema:String?=route(),voucher:Boolean=false,gift:Boolean=false,identity:String="xiaodian..",time:Long=1781115786,more:Boolean=false)=buildJsonObject {
        put("status_code",0);put("yata_data",buildJsonObject {
            put("global",buildJsonObject {put("has_more",more)})
            put("data",buildJsonObject {
                put("product_line_MAIN",node(identity=identity,time=time));if(gift)put("product_line_GIFT",node(gift=true,time=time))
                put("store_line_MAIN",buildJsonObject {put("fields",buildJsonObject {put("shop_order_id",oid);put("name","示例商家");put("store_order_status",buildJsonArray {add(buildJsonObject {put("content","交易完成")})})})})
                put("button_line_MAIN",buildJsonObject {put("fields",buildJsonObject {put("shop_order_id",oid)});put("events",buildJsonObject {
                    if(schema!=null)put("xiaodian_2_LookLogistics_40000_1",buildJsonArray {add(buildJsonObject {put("next",buildJsonObject {put("success",buildJsonArray {add(buildJsonObject {put("fields",buildJsonObject {put("schema",schema)})})})})})})
                    if(voucher)put("xiaodian_2_LookVoucher_10105_2",buildJsonArray {})
                })})
            })
        })
    }.toString()
    private fun page(orders:List<DouyinOrder>,more:Boolean)=DouyinOrderPayload.Page(orders,more,buildJsonObject {})
    @Test fun `observed fields and rich price are preserved`() {
        val row=DouyinOrderPayload.read(response(gift=true)).orders.single()
        assertEquals("交易完成",row.status);assertEquals("示例商家",row.shop);assertEquals(1781115786000L,row.createdAt)
        assertEquals(2,row.products.size);assertFalse(row.products[0].gift);assertTrue(row.products[1].gift)
        assertTrue(row.products.first().priceDisplay.startsWith("["));assertEquals(waybill,row.waybills.single().number)
    }
    @Test fun `voucher wins over a conflicting logistics route`() {
        val row=DouyinOrderPayload.read(response(voucher=true)).orders.single()
        assertEquals("excluded_virtual",row.classification);assertTrue(DouyinOrderPayload.items(row,BoundAccount("test"),now).isEmpty())
    }
    @Test fun `topup is excluded even with a route`() {assertEquals("excluded_virtual",DouyinOrderPayload.read(response(identity="xiaodian.topup.")).orders.single().classification)}
    @Test fun `missing waybill is unknown not virtual`() {assertEquals("unknown",DouyinOrderPayload.read(response(schema=null)).orders.single().classification)}
    @Test fun `foreign domain cannot supply a waybill`() {assertNull(DouyinOrderPayload.waybill(route(host="ffh.jinritemai.com.attacker.invalid"),oid))}
    @Test fun `other order cannot supply this waybill`() {assertNull(DouyinOrderPayload.waybill(route(id="MOCK_OTHER_ORDER"),oid))}
    @Test fun `order placeholder is never a real waybill`() {assertNull(DouyinOrderPayload.waybill(route(number="ORDER-MOCK12345678"),oid))}
    @Test fun `unknown create time stays unknown`() {assertEquals(0L,DouyinOrderPayload.read(response(time=0)).orders.single().createdAt)}
    @Test(expected=IllegalStateException::class) fun `empty http response is not zero orders`() {DouyinOrderPayload.read("")}
    @Test(expected=IllegalStateException::class) fun `missing paging state is not end of list`() {DouyinOrderPayload.read("""{"status_code":0,"yata_data":{"data":{},"global":{}}}""")}
    @Test fun `commerce completion does not fabricate signing or pickup`() {
        val item=DouyinOrderPayload.items(DouyinOrderPayload.read(response()).orders.single(),BoundAccount("test"),now).single()
        assertEquals(-1,item.state);assertEquals(0L,item.completedAt);assertEquals(0L,item.statusUpdatedAt);assertTrue(item.pickupCode.isEmpty());assertTrue(item.latestTime.isEmpty())
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(item));assertEquals(0L,ParcelPresentation.eventTime(item))
        assertTrue(ParcelPresentation.recent(listOf(item),emptyMap(),days=180,now=now).isEmpty())
        assertEquals(1,ParcelPresentation.recent(listOf(item),emptyMap(),limit=null,days=180,now=now).size)
    }
    @Test fun `fresh metadata cannot erase cached logistics facts`() {
        val fresh=DouyinOrderPayload.items(DouyinOrderPayload.read(response()).orders.single(),BoundAccount("test"),now).single()
        val old=fresh.copy(logisticsStatus="待取件",pickupCode="MOCK-CODE",latestText="示例驿站等待取件",latestTime="2026-10-21 10:00",statusUpdatedAt=now-1000,tracked=true)
        val merged=SyncCacheMerge.merge(old,fresh);assertEquals(old.pickupCode,merged.pickupCode);assertEquals(old.latestText,merged.latestText);assertEquals(old.statusUpdatedAt,merged.statusUpdatedAt);assertTrue(merged.tracked)
    }
    @Test fun `inclusive Beijing natural day boundary`() {
        val since=ParcelTime.cutoff(7,now);assertEquals(ParcelTime.parse("2026-10-16 00:00:00"),since)
        val policy=DouyinPagingPolicy(since,now);assertTrue(policy.include(DouyinOrder("a",since)));assertFalse(policy.include(DouyinOrder("b",since-1)));assertFalse(policy.include(DouyinOrder("c",now+1)))
    }
    @Test fun `old cached active ids continue beyond date stop`() {
        val since=ParcelTime.cutoff(7,now);val policy=DouyinPagingPolicy(since,now,setOf("cached"))
        assertNull(policy.accept(page(listOf(DouyinOrder("old",since-10)),true)))
        assertEquals("server_end",policy.accept(page(listOf(DouyinOrder("cached",since-20)),false)))
        assertTrue(policy.include(DouyinOrder("cached",since-20)));assertFalse(policy.include(DouyinOrder("old",since-10)));assertTrue(policy.unresolvedActive.isEmpty())
    }
    @Test fun `unknown date blocks boundary proof`() {val p=DouyinPagingPolicy(100,200);assertNull(p.accept(page(listOf(DouyinOrder("a",0)),true)));assertTrue(p.missingDates)}
    @Test fun `mixed page is not time boundary`() {assertNull(DouyinPagingPolicy(100,200).accept(page(listOf(DouyinOrder("old",50),DouyinOrder("new",150)),true)))}
    @Test fun `all old dated page reaches boundary`() {assertEquals("order_time_boundary",DouyinPagingPolicy(100,200).accept(page(listOf(DouyinOrder("old",50)),true)))}
    @Test(expected=IllegalStateException::class) fun `repeated page is not success even when marked last`() {val p=DouyinPagingPolicy(0,200);p.accept(page(listOf(DouyinOrder("a",150)),true));p.accept(page(listOf(DouyinOrder("a",150)),false))}
    @Test(expected=IllegalStateException::class) fun `unchanged anchor stops pagination`() {val p=DouyinPagingPolicy(0,200);p.acceptAnchor("MOCK_ANCHOR");p.acceptAnchor("MOCK_ANCHOR")}
    @Test fun `owner cookies permit renewal but reject account switch`() {
        assertTrue(DouyinSessionPolicy.sameOwner("uid_tt=A; sessionid=old","uid_tt=A; sessionid=new"));assertFalse(DouyinSessionPolicy.sameOwner("uid_tt=A; sessionid=old","uid_tt=B; sessionid=old"));assertFalse(DouyinSessionPolicy.sameOwner("",""));assertFalse(DouyinSessionPolicy.hasCredentials("uid_tt=A"));assertTrue(DouyinSessionPolicy.hasCredentials("sessionid=test"))
    }
    @Test fun `gift-only nodes do not create standalone orders`() {
        val root=Json.parseToJsonElement(response(gift=true)).jsonObject;val y=root["yata_data"]!!.jsonObject;val data=y["data"]!!.jsonObject.filterKeys {it!="product_line_MAIN"}
        val body=JsonObject(root.toMutableMap().apply {put("yata_data",JsonObject(y.toMutableMap().apply {put("data",JsonObject(data))}))})
        assertTrue(DouyinOrderPayload.read(body.toString()).orders.isEmpty())
    }
}
