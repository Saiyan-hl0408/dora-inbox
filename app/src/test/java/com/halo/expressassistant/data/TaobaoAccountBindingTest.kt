package com.halo.expressassistant.data

import org.junit.Assert.*
import org.junit.Test

class TaobaoAccountBindingTest {
    private val first = "cookie2=synthetic; unb=111123456; cookie1=old-session"
    private val other = "cookie2=synthetic; unb=222123456; cookie1=other-session"
    private val label = "淘宝账号 · 尾号123456"
    // Cookie payload decoding is supplied by Store; the helper receives only the header.
    private fun find(accounts: List<BoundAccount>, cookies: String) =
        TaobaoAccountBinding.findExisting(accounts, cookies) { it }

    @Test fun equalDisplaySuffixCannotSelectAnotherOwnerOrInheritTheirCapabilities() {
        val original = BoundAccount("first", label, payload = first, cainiaoVerified = true)
        assertNull(find(listOf(original), other))
        assertEquals(first, original.payload)
    }

    @Test fun sameOwnerIsUpdatedDespiteCustomizedDisplayLabel() {
        val original = BoundAccount("first", "我的购物账号", payload = first)
        val existing = find(listOf(original), "unb=111123456; cookie1=renewed-session")
        assertSame(original, existing)
    }

    @Test fun oldUnknownCredentialsCannotBeSelectedUsingOnlyDisplayName() {
        assertNull(find(listOf(BoundAccount("unknown", label, payload = "cookie2=synthetic")), first))
        assertNull(find(listOf(BoundAccount("first", label, payload = first)), "cookie2=synthetic"))
    }

    @Test fun mergePreservesPausedStateAndCustomLabel() {
        val original = BoundAccount("first", "保留名称", enabled = false, payload = first)
        val merged = TaobaoAccountBinding.merge(original, "renewed-payload", true, false, false)
        assertEquals(original.id, merged.id)
        assertEquals(original.label, merged.label)
        assertFalse(merged.enabled)
        assertEquals("renewed-payload", merged.payload)
    }

    @Test fun cainiaoOnlyMergeRetainsPreviouslyVerifiedOrdersWithoutEnablingNewOrders() {
        val original = BoundAccount("first", payload = first, taobaoOrdersEnabled = true, taobaoOrdersVerified = true)
        val merged = TaobaoAccountBinding.merge(original, "renewed-payload", false, true, true)
        assertTrue(merged.taobaoOrdersEnabled)
        assertTrue(merged.taobaoOrdersVerified)
        assertTrue(merged.cainiaoVerified)
        val newStationOnly = TaobaoAccountBinding.merge(BoundAccount("station"), "new-payload", false, true, true)
        assertFalse(newStationOnly.taobaoOrdersEnabled)
        assertFalse(newStationOnly.taobaoOrdersVerified)
        assertTrue(newStationOnly.cainiaoVerified)
    }

    @Test fun successfulOrdersDoNotEraseAnExistingStationCapability() {
        val original = BoundAccount("first", payload = first, cainiaoVerified = true)
        val merged = TaobaoAccountBinding.merge(original, "renewed-payload", true, false, false)
        assertTrue(merged.taobaoOrdersVerified)
        assertTrue(merged.taobaoOrdersEnabled)
        assertTrue(merged.cainiaoVerified)
    }
}
