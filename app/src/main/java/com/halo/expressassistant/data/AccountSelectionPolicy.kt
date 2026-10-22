package com.halo.expressassistant.data

/** A parcel with a recorded owner must never silently query another account. */
object AccountSelectionPolicy {
    fun forItem(accounts: List<BoundAccount>, accountId: String): BoundAccount? =
        if (accountId.isNotBlank()) accounts.firstOrNull { it.id == accountId && it.enabled }
        else accounts.firstOrNull { it.enabled }
}
