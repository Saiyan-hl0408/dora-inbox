package com.halo.expressassistant.hub

import android.content.Context
import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.Store
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object PddReadRecovery {
    fun prepare(context:Context) = synchronized(Store) {
        val prefs=HubData.prefs(context)
        if(prefs.getBoolean("pdd_fix9_recovery_prepared",false))return@synchronized
        val usedFix8=prefs.getString("pdd_detail_diagnostic","").orEmpty().contains("fix8：") ||
            prefs.getString("pdd_binding_sync_diagnostic_v1","").orEmpty().contains("\"diagnosticRevision\":2")
        if(usedFix8) {
            val items=Store.items(context)
            check(prefs.edit().putString("pdd_items_before_fix9",Json.encodeToString(ListSerializer(ExpressItem.serializer()),items.filter {it.source=="pdd"})).commit())
            Store.invalidatePddReadCache(context)
            Store.saveItems(context,items.map(PddReadRecoveryPolicy::reset))
            val edit=prefs.edit()
            prefs.all.keys.filter {it.startsWith("pdd_logistics_link_") || it.startsWith("pdd_detail_retry_")}.forEach(edit::remove)
            edit.apply()
        }
        prefs.edit().putBoolean("pdd_fix9_recovery_prepared",true).apply()
    }
}
