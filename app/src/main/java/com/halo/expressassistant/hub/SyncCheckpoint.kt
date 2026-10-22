package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

/** A previous-run snapshot. A time boundary alone could skip a new order sharing that timestamp. */
class SyncCheckpoint(cached:List<ExpressItem>,private val historyImport:Boolean=false) {
    private val known=cached.mapTo(HashSet()) {it.id}
    val newestOrderAt=cached.maxOfOrNull {it.orderCreatedAt} ?: 0L
    fun reached(page:List<ExpressItem>):Boolean = !historyImport && page.isNotEmpty() && known.isNotEmpty() &&
        page.all {it.id in known && (newestOrderAt==0L || it.orderCreatedAt==0L || it.orderCreatedAt<=newestOrderAt)}
}
