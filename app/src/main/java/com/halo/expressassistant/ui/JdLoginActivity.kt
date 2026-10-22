package com.halo.expressassistant.ui

import android.webkit.WebView
import com.halo.expressassistant.hub.JdLogisticsSource
import kotlinx.serialization.json.*

class JdLoginActivity : PlatformLoginActivity("jd") {
    private var validated=false
    private var checking=false
    override val sessionReady:Boolean get()=validated
    override fun hasCredentials(keys:Map<String,String>)=validated
    override fun initialLoginUrl(defaultUrl:String)=if(intent.getBooleanExtra("freshLogin",false) || com.halo.expressassistant.data.Store.accounts(this,"jd").isEmpty())JdLogisticsSource.LOGIN_URL else JdLogisticsSource.LIST_URL
    override fun configureLoginWebView(view:WebView) {
        view.settings.userAgentString="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
    override val loginHelpText:String get()="使用 QQ 或账号密码登录，平台要求时完成手机安全验证。软件不会自动获取或重发验证码。\n\n${JdLogisticsSource.COVERAGE_HINT}\n\n官网收件列表通过检查后自动保存；需要时可点检查登录。"
    override fun onManualLoginCheck() {
        web.evaluateJavascript("if(window.__parcelJdl)window.__parcelJdl.checkError='';void 0",null)
    }
    override fun onSessionObserved(hasCredentials:Boolean) {
        if(validated || checking || android.net.Uri.parse(web.url.orEmpty()).host!="kd.jd.com")return
        checking=true
        val script=assets.open("jdl-mobile.js").bufferedReader().use {it.readText()}
        web.evaluateJavascript(script,null)
        web.evaluateJavascript("window.__parcelJdl.check();JSON.stringify({valid:!!window.__parcelJdl.valid,error:window.__parcelJdl.checkError||''})") {result->
            checking=false
            if(isFinishing || isDestroyed)return@evaluateJavascript
            val data=runCatching {Json.parseToJsonElement(Json.parseToJsonElement(result).jsonPrimitive.content).jsonObject}.getOrNull()
            validated=data?.get("valid")?.jsonPrimitive?.booleanOrNull==true
            if(validated)checkLoginSession()
            else state.text=if(data?.get("error")?.jsonPrimitive?.content.orEmpty().isNotBlank())"请完成官网验证后点「检查登录」。" else "正在检查登录及收件列表…"
        }
    }
}
