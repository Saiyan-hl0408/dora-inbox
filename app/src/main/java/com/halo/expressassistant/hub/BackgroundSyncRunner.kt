package com.halo.expressassistant.hub

import android.content.Context
import android.content.Intent
import com.halo.expressassistant.TrackingNotifier
import com.halo.expressassistant.data.Store
import com.halo.expressassistant.ui.SyncEngine
import kotlinx.coroutines.*

/** Run all bound platforms without a resident Activity; list success alone is not completion. */
object BackgroundSyncRunner {
    suspend fun run(ctx:Context) {
        val prefs=HubData.prefs(ctx)
        if(!Store.backgroundSyncEnabled(ctx) || !Store.hasAnyAccount(ctx) || !SyncRunCoordinator.due(ctx))return
        if(!SyncRunCoordinator.acquire(ctx))return
        var success=false
        var pddDiagnosticStarted=false
        var pddDiagnosticOutcome="后台同步未完成，已有数据保留"
        fun progress(label:String) {
            prefs.edit().putBoolean("background_run_running",true).putString("background_run_label",label).apply()
            ctx.sendBroadcast(Intent(HubData.CHANGED).setPackage(ctx.packageName))
        }
        try {
            if(!Store.backgroundSyncEnabled(ctx))return
            withTimeout(8*60_000L) {
                progress("后台同步中，已有订单可查看")
                val started=System.currentTimeMillis()
                pddDiagnosticStarted=Store.accounts(ctx,"pdd").any {it.enabled}
                val report=SyncEngine.sync(ctx,skip=setOf("xiaomi"),automatic=true,supplementPickup=false,onProgress={progress(it.label)})
                val pdd=report.statuses.firstOrNull {it.channel=="pdd"}
                val pending=Store.items(ctx).filter {it.source=="pdd" && PddDetailQueryPolicy.active(it) && !HubData.collected(ctx,it)}
                val candidates=if(PddDetailQueryPolicy.mayReadDetails(pdd!=null,pdd?.count ?: 0,pdd?.error))
                    pending.filter {item->val key="${item.accountId}_${item.id}";PddDetailQueryPolicy.mayAttempt(System.currentTimeMillis(),prefs.getLong("pdd_detail_retry_$key",0),prefs.getString("pdd_detail_status_$key","")==PddDetailQueryPolicy.fingerprint(item))}
                        .sortedBy {PddDetailQueryPolicy.priority(it)} else emptyList()
                var failed=0
                val locationRequests=mutableListOf<PddLocationSupplement.Request>()
                for((index,item) in candidates.withIndex()) {
                    ensureActive()
                    progress("后台更新物流 ${index+1}/${candidates.size}")
                    val key="${item.accountId}_${item.id}"
                    prefs.edit().putLong("pdd_detail_retry_$key",System.currentTimeMillis()+PddDetailQueryPolicy.RETRY_DELAY_MS).putString("pdd_detail_status_$key",PddDetailQueryPolicy.fingerprint(item)).apply()
                    try {
                        withTimeout(25000){ParcelMetadata.refresh(ctx,item){snapshot->locationRequests.add(PddLocationSupplement.Request(item,snapshot))}}
                        prefs.edit().putLong("detail_at_${item.source}_${item.accountId}_${item.id}",System.currentTimeMillis()).putInt("pdd_detail_version_$key",PddDetailQueryPolicy.VERSION).putLong("pdd_detail_retry_$key",0).apply()
                    } catch(e:TimeoutCancellationException){failed++}
                    catch(e:CancellationException){throw e}
                    catch(_:Exception){failed++}
                }
                CainiaoPickupService.supplement(ctx) {current,total->progress("后台补充取件码 $current/$total")}
                val locations=PddLocationSupplement.run(ctx,locationRequests,true){current,total->progress("物流状态已更新；后台补充取件地点 $current/$total")}
                if(pdd!=null)prefs.edit().putString("pdd_location_diagnostic","fix7：后台完整物流页同次读取状态与地点，无二次地点补查。").apply()
                SyncLedger.commit(ctx)
                val detailError=SyncStatusText.detailsError(pdd?.error,failed,if(pdd?.error==null)pending.size-candidates.size else 0)
                val final=report.copy(statuses=report.statuses.map {if(it.channel=="pdd")it.copy(error=detailError) else it})
                val days=HistoryRangePolicy.clamp(prefs.getInt("sync_days",7))
                final.statuses.forEach {entry->prefs.edit().putString("binding_sync_result_${entry.channel}",SyncStatusText.describe(entry.channel,entry.count,entry.error))
                    .putString("binding_sync_brief_${entry.channel}",SyncStatusText.brief(entry.count,entry.error)).putInt("binding_sync_days_${entry.channel}",days).putLong("binding_sync_at_${entry.channel}",System.currentTimeMillis()).apply()}
                if(pdd!=null)prefs.edit().putString("pdd_detail_diagnostic","触发：后台定时同步；本轮开始 ${ParcelTime.format(started)}。\n未完成物流候选 ${candidates.size}；未更新 $failed；失败保留已有信息。后台调度不保证准点。订单、物流详情与取件服务全部结束后才完成本轮。").apply()
                val summary=final.statuses.joinToString("；") {"${HubData.channels[it.channel]}：${SyncStatusText.describe(it.channel,it.count,it.error)}"}
                prefs.edit().putString("last_report","后台同步：$summary").apply()
                Store.saveItems(ctx,Store.items(ctx).map {item->
                    if(!item.tracked)item else if(item.notifiedText.isBlank() && item.notifiedTime.isBlank())item.copy(notifiedText=item.latestText,notifiedTime=item.latestTime)
                    else if(item.latestTime.isNotEmpty() && item.latestTime>item.notifiedTime){TrackingNotifier.notify(ctx,item);item.copy(notifiedText=item.latestText,notifiedTime=item.latestTime)}else item
                })
                success=final.statuses.isNotEmpty() && final.statuses.all {it.error==null}
                pddDiagnosticOutcome=final.statuses.firstOrNull {it.channel=="pdd"}?.let {it.error ?: "完成"}.orEmpty()
            }
        } catch(e:TimeoutCancellationException){SyncLedger.cancel();prefs.edit().putString("last_report","后台同步超时，已保留已有包裹。").apply()}
        catch(e:CancellationException){SyncLedger.cancel();throw e}
        catch(_:Exception){SyncLedger.cancel();prefs.edit().putString("last_report","后台同步未完成，已保留已有包裹；打开软件可查看或手动重试。").apply()}
        finally {
            if(pddDiagnosticStarted)PddDiagnostics.finish(ctx,pddDiagnosticOutcome)
            WebFetchRegistry.cancel(ctx);SyncRunCoordinator.finish(ctx,success)
            prefs.edit().putBoolean("background_run_running",false).apply()
            ctx.sendBroadcast(Intent(HubData.CHANGED).setPackage(ctx.packageName))
        }
    }
}
