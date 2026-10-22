package com.halo.expressassistant.hub

import android.webkit.WebView

/** A single outer-page retry for Cainiao's first-entry empty code state. */
internal class IdentityAutomaticRefresh(
    private val page: WebView,
    private val eligible: (String) -> Boolean,
    private val onRefreshing: () -> Unit
) {
    private val gate = IdentityEntryRefresh()
    private var pending: Runnable? = null

    fun onNavigation() {
        pending?.let { page.removeCallbacks(it) }
        pending = null
        gate.cancelPending()
    }

    /** Returns true while the caller should retain its loading UI. */
    fun afterLoad(url: String): Boolean {
        if (!gate.schedule(url, eligible(url))) return gate.isPending(url)
        onRefreshing()
        val retry = Runnable {
            pending = null
            val current = page.url.orEmpty()
            if (gate.take(url, current == url && page.isAttachedToWindow && eligible(current))) page.reload()
        }
        pending = retry
        page.postDelayed(retry, 600)
        return true
    }

    fun close() {
        pending?.let { page.removeCallbacks(it) }
        pending = null
        gate.close()
    }
}
