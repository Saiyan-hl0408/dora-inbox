package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class PddNonParcelOrderTest {
    // Shape captured from the phone report: a special-channel parent with seven child orders.
    private val vegetables="""{"type":2,"order_sn":"PO-parent","parent_order_sn":"PO-parent","order_status_prompt":"待评价","order_link_url":"ywgnpxpt.html?_t_timestamp=mobile-vegetables&_p_page=vgt_order_detail&from_pdd=1&_x_cid=ddmc_order_list&parent_order_sn=PO-parent","orders":[{"order_sn":"child-a","order_goods":[{"goods_name":"蔬菜"}]},{"order_sn":"child-b","order_goods":[{"goods_name":"水果"}]}]}"""
    private val regular="""{"order_sn":"parcel","order_time":1792825139,"order_status_prompt":"待收货","order_goods":[{"goods_name":"普通商品"}]}"""
    private fun read(body:String)=PddOrderPayload.read(listOf("/proxy/api/order_list_v4" to body),"account")

    @Test fun vegetablesParentAndChildrenAreNotOrdinaryParcels() {
        val (items,goods)=read("""{"orders":[$vegetables]}""")
        assertTrue("Special-channel parent must not become a parcel/detail task",items.isEmpty())
        assertTrue(goods.isEmpty())
    }
    @Test fun mixedPageKeepsOrdinaryParcelWithoutInventingAParentDate() {
        val (items,_)=read("""{"orders":[$vegetables,$regular]}""")
        assertEquals(listOf("parcel"),items.map {it.id})
        assertTrue(items.single().orderCreatedAt>0)
    }
    @Test fun missingWaybillAndPickupCodeDoNotExcludeRegularOrder() {
        val item=read("""{"orders":[$regular]}""").first.single()
        assertEquals("parcel",item.id)
        assertTrue(item.isOrderNumber)
        assertEquals("",item.pickupCode)
    }
    @Test fun prefixTypeAndGoodsNameAloneAreNotBusinessTypeEvidence() {
        val raw="""{"orders":[{"type":2,"order_sn":"PO-ordinary","parent_order_sn":"PO-ordinary","order_goods":[{"goods_name":"多多买菜购物袋"}],"orders":[{"order_sn":"not-a-separate-parcel"}]}]}"""
        assertEquals(listOf("PO-ordinary"),read(raw).first.map {it.id})
    }
    @Test fun excludedParentIsReportedForAccountScopedCacheCleanup() {
        val excluded=linkedMapOf<String,String>()
        PddOrderPayload.read(listOf("ordersStore/order_list_v4" to """{"orders":[$vegetables]}"""),"account","") {id,reason->excluded[id]=reason}
        assertEquals(setOf("PO-parent"),excluded.keys)
        val old=com.halo.expressassistant.data.ExpressItem("PO-parent","","","ORDER-PDD-PO-parent",source="pdd",accountId="account")
        val keys=excluded.keys.map {PddOrderBusiness.key("account",it)}.toSet()
        assertFalse(PddOrderBusiness.keep(old,keys))
        assertTrue(PddOrderBusiness.keep(old.copy(accountId="other-account"),keys))
        assertTrue(PddOrderBusiness.keep(old.copy(source="jd"),keys))
        assertTrue(PddOrderBusiness.keep(old.copy(id="ordinary-order"),keys))
    }
    @Test fun exclusionsAreVisibleInBusinessDiagnostics() {
        val rows=PddDiagnosticBusiness.inputs(kotlinx.serialization.json.Json.parseToJsonElement("""{"orders":[$vegetables,$regular]}"""))
        assertTrue(rows.toString().contains("excludedFromParcelSync"))
        assertTrue(rows.toString().contains("多多买菜"))
        assertEquals(2,rows.size)
    }
    @Test fun excludedOnlyPageIsRecognizedButMixedUnknownPageIsNotHidden() {
        assertTrue(PddOrderBusiness.pageFullyExcluded(1,1))
        assertFalse(PddOrderBusiness.pageFullyExcluded(2,1))
        assertFalse(PddOrderBusiness.pageFullyExcluded(0,0))
    }
}
