package com.halo.expressassistant.hub

/** Exactly one first-entry retry, scoped to a single live WebView and its owned session. */
internal class IdentityEntryRefresh {
    private var pendingUrl: String? = null
    private var consumed = false
    private var closed = false

    fun isPending(url: String) = !closed && !consumed && pendingUrl == url

    fun schedule(url: String, eligible: Boolean): Boolean {
        if (closed || consumed || pendingUrl != null || !eligible) return false
        pendingUrl = url
        return true
    }

    fun take(url: String, eligible: Boolean): Boolean {
        val matches = pendingUrl == url
        if (!matches) return false
        pendingUrl = null
        if (closed || consumed || !matches || !eligible) return false
        consumed = true
        return true
    }

    fun cancelPending() { pendingUrl = null }
    fun close() { closed = true; pendingUrl = null }
}
