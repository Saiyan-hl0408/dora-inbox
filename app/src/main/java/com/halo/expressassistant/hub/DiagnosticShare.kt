package com.halo.expressassistant.hub

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticShare {
    fun file(activity:Activity,channel:String,text:String):File {
        val folder=File(activity.cacheDir,"diagnostics").apply {mkdirs()}
        val name="dora-inbox-$channel-diagnostic-${SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(Date())}.txt"
        return File(folder,name).apply {writeText(text,Charsets.UTF_8)}
    }
    fun open(activity:Activity,file:File) {
        val uri=FileProvider.getUriForFile(activity,"${activity.packageName}.diagnostics",file)
        share(activity,uri)
    }
    fun share(activity:Activity,uri:Uri) {
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"哆啦收件箱完整诊断")
            putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri("完整诊断",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent,"分享完整诊断"))
    }
    fun view(activity:Activity,uri:Uri) {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri,"text/plain");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },"打开诊断文件"))
    }
    fun savedLocation(activity:Activity,uri:Uri):String {
        val documentId=runCatching {DocumentsContract.getDocumentId(uri)}.getOrDefault("")
        if(uri.authority=="com.android.externalstorage.documents" && documentId.contains(':')) {
            val volume=documentId.substringBefore(':')
            return (if(volume=="primary")"内部存储" else "存储卷 $volume")+"/"+documentId.substringAfter(':')
        }
        val name=runCatching {activity.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
            if(it.moveToFirst())it.getString(0) else null
        }}.getOrNull()
        return "系统文件中选择的位置\n"+(name ?: uri.lastPathSegment.orEmpty())
    }
}
