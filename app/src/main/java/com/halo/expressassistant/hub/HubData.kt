package com.halo.expressassistant.hub

import android.content.Context
import android.content.Intent
import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.Store
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object HubData {
    const val CHANGED = "com.songoku.parcelhub.CHANGED"
    val channels = linkedMapOf("jd" to "京东", "taobao" to "淘宝", "pdd" to "拼多多")
    private val json = Json { ignoreUnknownKeys = true }
    fun prefs(context: Context) = context.getSharedPreferences("parcelhub", Context.MODE_PRIVATE)
    /** Legacy history choice wins over the formerly independent sync choice. */
    fun migrateHistoryRange(context:Context):Int {
        val settings=prefs(context)
        val days=HistorySelectionPolicy.resolve(
            if(settings.contains("history_days"))settings.getInt("history_days",HistoryRangePolicy.DEFAULT_DAYS) else null,
            if(settings.contains("sync_days"))settings.getInt("sync_days",HistoryRangePolicy.DEFAULT_DAYS) else null
        )
        if(!settings.contains("history_days") || !settings.contains("sync_days") ||
            settings.getInt("history_days",days)!=days || settings.getInt("sync_days",days)!=days || settings.contains("recent_days")) {
            settings.edit().putInt("history_days",days).putInt("sync_days",days).remove("recent_days").apply()
        }
        return days
    }
    fun historyRange(context:Context)=migrateHistoryRange(context)
    /** True asks the coordinator for a full selected-range read, not an exact missing-date query. */
    fun applyHistoryRange(context:Context,days:Int):Boolean {
        val selected=HistorySelectionPolicy.select(days,SyncLedger.needsExpansion(context,HistoryRangePolicy.clamp(days)))
        prefs(context).edit().putInt("history_days",selected.days).putInt("sync_days",selected.days).remove("recent_days").apply()
        return selected.readSelectedRange
    }
    @Synchronized fun local(context: Context): List<ExpressItem> = runCatching {
        json.decodeFromString<List<ExpressItem>>(prefs(context).getString("local_parcels", "[]").orEmpty())
    }.getOrDefault(emptyList())
    @Synchronized fun saveLocal(context: Context, items: List<ExpressItem>) {
        prefs(context).edit().putString("local_parcels", json.encodeToString(items)).apply()
        context.sendBroadcast(Intent(CHANGED).setPackage(context.packageName))
    }
    fun items(context: Context): List<ExpressItem> {
        val merged = LinkedHashMap<String, ExpressItem>()
        Store.items(context).forEach { merged[it.mailNo.uppercase(java.util.Locale.ROOT)] = it }
        local(context).forEach { item ->
            val key=item.mailNo.uppercase(java.util.Locale.ROOT)
            val old = merged[key]
            merged[key] = if (old == null) item else old.copy(
                pickupCode=item.pickupCode.ifBlank {old.pickupCode},
                pickupCodeSource=if(item.pickupCode.isNotBlank())item.pickupCodeSource.ifBlank {"local"} else old.pickupCodeSource
            )
        }
        val goods=Store.jdGoods(context)
        return merged.values.filterNot {ParcelEligibility.isVirtual(it,goods[it.mailNo]?.name.orEmpty())}
    }
    fun collectedTimes(context: Context): Map<String, Long> {
        val times = runCatching { json.decodeFromString<Map<String,Long>>(prefs(context).getString("collected_times","{}").orEmpty()) }.getOrDefault(emptyMap()).toMutableMap()
        // v0.1 had no collection times. Keep history without inventing a date.
        prefs(context).getStringSet("collected",emptySet()).orEmpty().forEach { times.putIfAbsent(it,0L) }
        return times
    }
    fun collected(context: Context, item: ExpressItem) = collectedTimes(context).containsKey(item.mailNo)
    fun collect(context: Context, item: ExpressItem, done: Boolean) {
        val times = collectedTimes(context).toMutableMap()
        if(done) times[item.mailNo]=System.currentTimeMillis() else times.remove(item.mailNo)
        prefs(context).edit().putString("collected_times",json.encodeToString(times)).putStringSet("collected",times.keys).apply()
    }
    fun saveCollectedTimes(context:Context,times:Map<String,Long>) {
        prefs(context).edit().putString("collected_times",json.encodeToString(times)).putStringSet("collected",times.keys).apply()
    }
    fun migrateV3(context:Context) {
        migrateHistoryRange(context)
        val notificationPrefs=prefs(context)
        if(!notificationPrefs.getBoolean("notification_import_removed",false)) {
            val edit=notificationPrefs.edit().remove("monitor").remove("listener_connected")
            notificationPrefs.all.keys.filter {it.startsWith("pkg_")}.forEach(edit::remove)
            edit.putBoolean("notification_import_removed",true).apply()
        }
        if(!prefs(context).getBoolean("pdd_qr_only_v17",false))prefs(context).edit()
            .remove("pdd_web_sync").remove("native_import_until").remove("native_import_count").remove("native_import_report")
            .putBoolean("pdd_qr_only_v17",true).apply()
        if(!prefs(context).getBoolean("goods_names_v14",false)) {
            val goods=Store.jdGoods(context).toMutableMap()
            Store.items(context).filter {it.source=="pdd"}.forEach {item->
                goods[item.mailNo]?.let {goods[item.mailNo]=GoodsCache.clean(it,item.originalName)}
            }
            Store.saveJdGoods(context,goods)
            prefs(context).edit().putBoolean("goods_names_v14",true)
                .putInt("history_days",HistoryRangePolicy.clamp(prefs(context).getInt("history_days",HistoryRangePolicy.DEFAULT_DAYS)))
                .putInt("sync_days",HistoryRangePolicy.clamp(prefs(context).getInt("sync_days",HistoryRangePolicy.DEFAULT_DAYS))).apply()
        }
        if(!prefs(context).getBoolean("jd_logistics_v6",false)) {
            Store.saveItems(context,Store.items(context).map {item->
                if(item.source=="jd" && !item.queryChannel.startsWith("https://kd.jd.com/express/orderDetail") && ParcelPresentation.stage(item) in setOf(ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.TRANSIT))
                    item.copy(stateName="旧版状态待核实") else item
            })
            prefs(context).edit().putBoolean("jd_logistics_v6",true).apply()
        }
        if(prefs(context).getBoolean("metadata_v3",false))return
        val items=Store.items(context).map {item->
            val time=ParcelTime.parse(item.latestTime)
            item.copy(companyName=item.companyName.takeUnless {it=="拼多多快递" || it=="京东商品快递"}.orEmpty(),
                isOrderNumber=item.isOrderNumber || (item.source=="pdd" && item.mailNo==item.queryChannel) || (item.source=="jd" && item.id==item.mailNo),
                orderStatus=item.orderStatus.ifBlank {item.stateName},statusUpdatedAt=item.statusUpdatedAt.takeIf {it>0} ?: time,
                completedAt=item.completedAt.takeIf {it>0} ?: if(item.state==3)time else 0)
        }
        Store.saveItems(context,items)
        val edit=prefs(context).edit().putBoolean("metadata_v3",true)
        edit.apply()
    }
    @Synchronized fun importDetailed(context:Context,text:String,source:String,companyName:String=source):ImportPolicy.Result {
        val result=ImportPolicy.plan(text,companyName,local(context),Store.items(context))
        if(result.changed)saveLocal(context,result.localItems)
        return result
    }
    @Synchronized fun import(context:Context,text:String,source:String,companyName:String=source):Boolean =
        importDetailed(context,text,source,companyName).changed
}
