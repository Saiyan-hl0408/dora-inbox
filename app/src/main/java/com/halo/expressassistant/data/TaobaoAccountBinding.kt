package com.halo.expressassistant.data

import com.halo.expressassistant.hub.CookiePairs

/** Exact seam used by Store.addTbAccount for selecting and updating an existing account. */
internal object TaobaoAccountBinding {
    fun findExisting(accounts: List<BoundAccount>, cookies: String,
                     cookieOf: (String) -> String): BoundAccount? {
        val incoming = CookiePairs.parse(cookies)
        return accounts.firstOrNull {
            CookiePairs.sameTaobaoAccount(incoming, CookiePairs.parse(cookieOf(it.payload)))
        }
    }

    fun merge(existing: BoundAccount, payload: String, ordersVerified: Boolean,
              cainiaoVerified: Boolean, cainiaoOnly: Boolean): BoundAccount =
        existing.copy(payload = payload,
            taobaoOrdersEnabled = existing.taobaoOrdersEnabled || !cainiaoOnly,
            taobaoOrdersVerified = existing.taobaoOrdersVerified || ordersVerified,
            cainiaoVerified = existing.cainiaoVerified || cainiaoVerified)
}
