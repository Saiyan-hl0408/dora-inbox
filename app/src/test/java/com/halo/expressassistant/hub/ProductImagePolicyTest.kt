package com.halo.expressassistant.hub

import com.halo.expressassistant.data.JdGoods
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProductImagePolicyTest {
    @Test fun recognizedJdThumbnailUsesSameImageKeyAtOriginalPath() {
        assertEquals("https://img12.360buyimg.com/img/jfs/t1/example.jpg",ProductImagePolicy.original("https://img12.360buyimg.com/n5/s80x80_jfs/t1/example.jpg"))
        assertEquals("https://img10.360buyimg.com/img/jfs/t1/example.jpg",ProductImagePolicy.original("http://img10.360buyimg.com/N5/jfs/t1/example.jpg"))
        assertEquals("https://img10.360buyimg.com/img/jfs/t1/example.jpg",ProductImagePolicy.original("//img10.360buyimg.com/n2/jfs/t1/example.jpg"))
    }
    @Test fun unrelatedSignedAndUnknownImageFormatsStayUnchanged() {
        for(url in listOf("https://other.example/n5/jfs/example.jpg","https://img12.360buyimg.com/n5/jfs/example.jpg?token=test","https://img12.360buyimg.com/imgzone/example.jpg"))assertEquals(url,ProductImagePolicy.original(url))
    }
    @Test fun imageMetadataSurvivesDetailWithNoImageAndCacheMerge() {
        val detail=Json.parseToJsonElement("""{"cargoInfos":[{"cargoName":"测试商品"}]}""").jsonObject
        val summary=Json.parseToJsonElement("""{"goodsInfos":[{"goodsName":"测试商品","extendProps":{"imgPath":"https://img.example.test/original.jpg"}}]}""").jsonObject
        assertEquals("https://img.example.test/original.jpg",JdlPayload.goods(detail,summary)!!.imageUrl)
        assertEquals("https://img.example.test/original.jpg",GoodsCache.merge(JdGoods(imageUrl="https://img.example.test/original.jpg"),JdGoods(name="新的描述")).imageUrl)
    }
    @Test fun missingImageRetriesAreBoundedAndExistingImagesNeedNoMetadataLookup() {
        assertTrue(ProductImagePolicy.canFetch(false,1000,0))
        assertFalse(ProductImagePolicy.canFetch(false,1000,2000))
        assertTrue(ProductImagePolicy.canFetch(false,2001,2000))
        assertFalse(ProductImagePolicy.canFetch(true,2001,0))
    }
    @Test fun tinySourcesAreNotUsedAsAnOriginalFailureFallback() {
        assertTrue(ProductImagePolicy.isTinyThumbnail("https://img10.360buyimg.com/N5/jfs/a.jpg"))
        assertTrue(ProductImagePolicy.isTinyThumbnail("https://img10.360buyimg.com/n5/s80x80_jfs/a.jpg"))
        assertFalse(ProductImagePolicy.isTinyThumbnail("https://img10.360buyimg.com/img/jfs/a.jpg"))
    }
}
