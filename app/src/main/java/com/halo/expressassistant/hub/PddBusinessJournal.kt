package com.halo.expressassistant.hub

import android.content.Context
import java.io.File
import java.util.concurrent.Executors
import kotlinx.serialization.json.*

/** Keep full sanitized business data outside SharedPreferences and off the UI thread. */
object PddBusinessJournal {
    private val worker=Executors.newSingleThreadExecutor {r->Thread(r,"pdd-diagnostic-writer").apply {isDaemon=true}}
    private var seen=mutableSetOf<String>()
    private var writeFailures=0
    private fun file(context:Context):File? {
        val run=HubData.prefs(context).getLong("pdd_business_run",0)
        return if(run==0L)null else File(context.filesDir,"diagnostics/pdd-business-$run.jsonl")
    }
    fun begin(context:Context) {
        val app=context.applicationContext
        HubData.prefs(app).edit().putLong("pdd_business_run",System.currentTimeMillis()).apply()
        val destination=file(app) ?: return
        worker.execute {
            seen=mutableSetOf();writeFailures=0
            runCatching {
                destination.parentFile?.mkdirs();destination.writeText("",Charsets.UTF_8)
                destination.parentFile?.listFiles()?.filter {it.name.startsWith("pdd-business-") && it.extension=="jsonl"}?.sortedByDescending {it.name}?.drop(2)?.forEach {it.delete()}
            }.onFailure {writeFailures++}
        }
    }
    fun append(context:Context,section:String,metadata:Map<String,String>,body:String,request:String="") {
        val destination=file(context.applicationContext) ?: return
        val at=System.currentTimeMillis()
        worker.execute {
            runCatching {
                val signature=PddDiagnosticPayload.ref(section+metadata.toString()+body+request)
                if(seen.add(signature)) {
                    val entry=buildJsonObject {
                        put("at",at);put("section",section);put("metadata",PddDiagnosticBusiness.clean(buildJsonObject {metadata.forEach {(k,v)->put(k,v)}}))
                        if(request.isNotBlank())put("request",PddDiagnosticBusiness.read(request))
                        val response=PddDiagnosticBusiness.read(body)
                        put("response",response)
                        if(section=="官方业务响应" && metadata["expectedType"] in setOf("all","unreceived"))put("parseInputs",PddDiagnosticBusiness.inputs(response))
                    }
                    destination.appendText(entry.toString()+"\n",Charsets.UTF_8)
                }
            }.onFailure {writeFailures++}
        }
    }
    fun report(context:Context):String {
        val source=file(context.applicationContext) ?: return "未记录完整业务响应；请在此版本重新同步。"
        return runCatching {worker.submit<String> {
            val contents=if(source.exists())source.readText(Charsets.UTF_8) else "未取得业务响应。"
            "业务响应日志（完整字段，逐行JSON；写入失败 $writeFailures 次）\n$contents"
        }.get()}.getOrElse {"业务响应日志读取失败：${it.javaClass.simpleName}"}
    }
}
