package com.halo.expressassistant.hub

import com.halo.expressassistant.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ParcelGroupsTest {
    private fun order(n:Int)=ExpressItem("order-$n","","邮政EMS","EMS12345678",source="pdd",accountId="account",originalName="商品$n",
        orderImageUrl="https://example.test/$n.jpg",orderWaybill="FIRST000$n",orderGoodsCount="x1",logisticsStatus="运输中",statusUpdatedAt=n.toLong())
    @Test fun fiveOfficialOrdersSharingTerminalWaybillKeepAllProducts() {
        val rows=(1..5).map(::order)
        val grouped=ParcelGroups.items(rows).single()
        assertEquals(5,grouped.relatedGoods.size)
        assertEquals(rows.map {it.id}.toSet(),grouped.relatedGoods.map {it.orderId}.toSet())
        assertEquals(5,grouped.relatedGoods.map {it.originalWaybill}.toSet().size)
        assertEquals(5,grouped.relatedGoods.map {it.imageUrl}.toSet().size)
        val title=ParcelGroups.title(grouped,JdGoods(name="错误的最后一件商品"))
        for(n in 1..5)assertTrue(title.contains("商品$n"))
        assertEquals("order-5",grouped.id)
    }
    @Test fun accountsAndDifferentWaybillsAreNotCombined() {
        assertEquals(3,ParcelGroups.items(listOf(order(1),order(2).copy(accountId="other"),order(3).copy(mailNo="OTHER123456"))).size)
    }
    @Test fun perOrderNameAndImageBeatSharedWaybillGoodsCache() {
        val item=order(1)
        assertEquals("商品1",ParcelGroups.title(item,JdGoods(name="商品2")))
        assertEquals(item.orderImageUrl,ParcelGroups.image(item,JdGoods(imageUrl="https://example.test/wrong.jpg")))
    }
    @Test fun runtimeGroupingDoesNotRewriteStoredOrderFacts() {
        val grouped=ParcelGroups.items(listOf(order(1),order(2))).single()
        val restored=Json.decodeFromString<ExpressItem>(Json.encodeToString(grouped))
        assertTrue(restored.relatedGoods.isEmpty())
        assertEquals("商品2",restored.originalName)
        assertEquals("FIRST0002",restored.orderWaybill)
        assertEquals("x1",restored.orderGoodsCount)
    }
    @Test fun metadataUpdateKeepsOriginalOrderProductAndFirstLegWaybill() {
        val old=order(1)
        val refreshed=old.copy(orderImageUrl="",orderGoodsCount="",orderWaybill="")
        val merged=SyncCacheMerge.merge(old,refreshed)
        assertEquals(old.orderImageUrl,merged.orderImageUrl)
        assertEquals(old.orderWaybill,merged.orderWaybill)
    }
    @Test fun realListParsingStoresEachOrdersOwnProductBeforePostalRelabel() {
        val raw="""{"orders":[{"order_sn":"A","tracking_number":"FIRST12345","order_goods":[{"goods_name":"转换线","thumb_url":"https://example.test/a.jpg","goods_number":1}]},{"order_sn":"B","tracking_number":"FIRST67890","order_goods":[{"goods_name":"充电器","thumb_url":"https://example.test/b.jpg","goods_number":2}]}]}"""
        val rows=PddOrderPayload.read(listOf("/api/order_list_v4" to raw)).first
        val group=ParcelGroups.items(rows.map {it.copy(mailNo="EMS12345678")}).single()
        assertEquals(setOf("转换线","充电器"),group.relatedGoods.map {it.name}.toSet())
        assertEquals(setOf("FIRST12345","FIRST67890"),group.relatedGoods.map {it.originalWaybill}.toSet())
    }
}
