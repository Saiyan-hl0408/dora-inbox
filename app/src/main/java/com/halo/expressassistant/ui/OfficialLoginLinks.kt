package com.halo.expressassistant.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import java.util.Locale

/** Only official provider schemes; rebuild intent URIs without foreign components/extras. */
object OfficialLoginLinks {
    fun intentFor(channel:String,uri:Uri,blockTaobaoAppLaunch:Boolean=false):Intent? {
        val parsed=if(uri.scheme.equals("intent",true))runCatching {Intent.parseUri(uri.buildUpon().scheme("intent").build().toString(),Intent.URI_INTENT_SCHEME)}.getOrNull() ?: return null else null
        val rawTarget=parsed?.data ?: uri
        val scheme=rawTarget.scheme?.lowercase(Locale.ROOT) ?: return null
        if(blockTaobaoAppLaunch && channel=="taobao" && scheme in setOf("tbopen","taobao"))return null
        // Android intent-filter schemes are case sensitive, even though URL schemes are not.
        val target=rawTarget.buildUpon().scheme(scheme).build()
        val pkg=when(scheme) {
            "mqq","mqqapi","mqqopensdkapi"->if(channel=="jd")"com.tencent.mobileqq" else null
            "openapp.jdmobile"->if(channel=="jd")"com.jingdong.app.mall" else null
            "tbopen","taobao"->if(channel=="taobao")"com.taobao.taobao" else null
            "pinduoduo","xunmeng"->if(channel=="pdd")"com.xunmeng.pinduoduo" else null
            "weixin"->if(channel=="pdd")"com.tencent.mm" else null
            else->null
        } ?: return null
        if(parsed?.`package`!=null && parsed.`package`!=pkg)return null
        return Intent(Intent.ACTION_VIEW,target).setPackage(pkg).addCategory(Intent.CATEGORY_BROWSABLE)
    }
    fun open(activity:Activity,channel:String,uri:Uri,blockTaobaoAppLaunch:Boolean=false,onMessage:(String)->Unit):Boolean {
        if(uri.scheme.equals("http",true) || uri.scheme.equals("https",true))return false
        // The Taobao binding WebView opts in. Other official-page and app-entry routes keep their behavior.
        val target=if(uri.scheme.equals("intent",true))runCatching {Intent.parseUri(uri.buildUpon().scheme("intent").build().toString(),Intent.URI_INTENT_SCHEME).data}.getOrNull() else uri
        if(blockTaobaoAppLaunch && channel=="taobao" && target?.scheme?.lowercase(Locale.ROOT) in setOf("tbopen","taobao"))return true
        val intent=intentFor(channel,uri,blockTaobaoAppLaunch)
        if(intent!=null)try {
            // Do not rely on resolveActivity to decide whether the app is installed.
            activity.startActivity(intent)
            onMessage(if(intent.`package`=="com.tencent.mobileqq")"已请求 QQ 授权；完成后返回此页检查会话，不会自动发送验证码。" else "已打开官方 App，返回后检查网页会话。")
        } catch(_:Exception) {onMessage("未能拉起授权应用；请使用当前网页的账号密码登录。不会自动发送或重发验证码。")}
        return true
    }
}
