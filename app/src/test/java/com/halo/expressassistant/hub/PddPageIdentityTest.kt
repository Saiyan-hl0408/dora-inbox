package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class PddPageIdentityTest {
    private val target="https://mobile.yangkeduo.com/goods_express.html?order_sn=ORDER-B"
    private fun accept(page:String=target,returned:String="ORDER-B",token:String="new-document",url:String=target)=
        PddPageIdentity.accepts("ORDER-B",url,page,returned,"new-document",token)
    @Test fun delayedPreviousPageCallbackIsRejectedEvenWithCompleteTrace() {
        assertFalse(accept(target.replace("ORDER-B","ORDER-A"),"ORDER-A","old-document"))
    }
    @Test fun sameDocumentTokenCannotBlessAnotherOrdersPayload() {
        assertFalse(accept(returned="ORDER-A"))
        assertFalse(accept(page=target.replace("ORDER-B","ORDER-A")))
    }
    @Test fun currentDocumentAndCurrentOrderAreAccepted() {
        assertTrue(accept());assertTrue(accept(returned=""))
    }
    @Test fun emptyOrForeignDocumentCannotSupplyAnyMetadata() {
        assertFalse(accept(token=""));assertFalse(accept(page="about:blank"))
        assertFalse(accept(page=target.replace("mobile.yangkeduo.com","other.invalid")))
    }
    @Test fun logisticsWithoutOrderQueryMustMatchTheOwnedExactLink() {
        val owned="https://mobile.yangkeduo.com/goods_express.html?tracking_number=POST00000001"
        assertTrue(accept(owned,"",url=owned))
        assertFalse(accept(owned.replace("POST00000001","POST00000002"),"",url=owned))
    }
}
