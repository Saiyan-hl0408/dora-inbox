package com.halo.expressassistant.ui

import android.content.Intent
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.webkit.WebView
import com.halo.expressassistant.api.DouyinOrders
import com.halo.expressassistant.data.*
import com.halo.expressassistant.hub.*
import kotlinx.coroutines.*

/** The actual App owns this WebView. Saving a binding requires a verified order read. */
class DouyinLoginActivity:PlatformLoginActivity("douyin") {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var verifying:Job?=null
    private var lastAttempt=""
    private var verified:DouyinOrders.Identity?=null
    private var verifiedCookies=""
    private var foreground=false
    private var readingPage=false
    private var challenge=false
    override val requiresExpandedOfficialPage=true
    override val loginHelpText="在官方手机号表单输入手机号及验证码，完成后点检查登录。若新设备要求扫码、人脸或其他安全验证，会显示完整官方页面，请本人按提示完成。\n\n抖音不支持从相册识别该登录码。登录成功且商城订单读取通过后才保存绑定。"
    override fun initialLoginUrl(defaultUrl:String)=DouyinOrders.HOME
    override fun configureLoginWebView(view:WebView){
        view.settings.apply {userAgentString=DouyinOrders.UA;useWideViewPort=false;loadWithOverviewMode=false;setSupportZoom(true);builtInZoomControls=true;displayZoomControls=false}
        WebView.setWebContentsDebuggingEnabled(com.halo.expressassistant.BuildConfig.ALLOW_TEST_SCREENSHOTS)
    }
    override fun onOfficialPage(view:WebView,url:String,isOfficial:Boolean,isLanding:Boolean){if(isOfficial)updateOfficialLayout()}
    private fun updateOfficialLayout() {
        if(readingPage || !foreground || !DouyinLoginPage.official(web.url))return
        readingPage=true
        web.evaluateJavascript(DouyinLoginPage.layout){raw->
            readingPage=false
            if(isFinishing || isDestroyed)return@evaluateJavascript
            val mode=runCatching {org.json.JSONObject(raw).optString("mode")}.getOrDefault("")
            val wasChallenge=challenge;challenge=mode=="challenge"
            if(wasChallenge && !challenge)lastAttempt=""
            if(challenge)state.text="请在官方页面完成扫码、人脸或其他安全验证，之后检查登录"
        }
    }
    override fun hasCredentials(keys:Map<String,String>)=listOf("sessionid","sessionid_ss").any {!keys[it].isNullOrBlank()}
    override val sessionReady:Boolean get()=verified!=null && !challenge && DouyinSessionPolicy.sameOwner(verifiedCookies,SessionCookies.current("douyin"))
    override val independentlyVerifiedLanding:Boolean get()=sessionReady
    override fun capabilityStatusText()="官方账号与商城订单：${if(sessionReady)"已验证" else "待验证"}\n商品与运单：${if(sessionReady)"可读取" else "等待登录"}\n完整物流轨迹：暂不可用\n\n电子券、充值等明确虚拟订单排除；履约未知的订单暂不导入。"
    override fun onSessionObserved(hasCredentials:Boolean) {
        val cookies=SessionCookies.current("douyin")
        updateOfficialLayout()
        if(!hasCredentials){verified=null;verifiedCookies="";state.text=if(challenge)"请在官方页面完成安全验证，之后检查登录" else "请输入手机号及验证码，完成后点检查登录";return}
        if(verified!=null && !DouyinSessionPolicy.sameOwner(verifiedCookies,cookies)){verified=null;verifiedCookies=""}
        val fingerprint=DouyinSessionPolicy.fingerprint(cookies)
        if(!sessionReady && fingerprint.isNotBlank() && fingerprint!=lastAttempt && verifying?.isActive!=true)verify(cookies,fingerprint)
    }
    override fun onManualLoginCheck(){lastAttempt="";onSessionObserved(DouyinSessionPolicy.hasCredentials(SessionCookies.current("douyin")))}
    private fun verify(cookies:String,fingerprint:String) {
        lastAttempt=fingerprint;state.text="正在验证抖音账号和商城订单…"
        verifying=scope.launch {
            try {
                val owner=DouyinOrders.verify(cookies)
                confirmNoPendingChallenge()
                if(!DouyinSessionPolicy.sameOwner(cookies,SessionCookies.current("douyin"))){state.text="登录账号已变化，请重新检查";return@launch}
                val selected=intent.getStringExtra("login_account_id").orEmpty()
                if(selected.isNotBlank() && Store.accountById(this@DouyinLoginActivity,"douyin",selected)?.let {DouyinOrders.userId(it.payload)!=owner.id}!=false){state.text="当前登录与所选绑定账号不同，请使用原账号或添加新账号";return@launch}
                verified=owner;verifiedCookies=cookies;state.text="抖音账号与订单读取验证通过，正在保存绑定…";checkLoginSession()
            }catch(_:TimeoutCancellationException){state.text="官方验证页面尚未响应，请完成后点「检查登录」"}catch(e:CancellationException){throw e}catch(_:Exception){state.text="订单尚未验证，请完成官方验证码或安全验证后点「检查登录」"}
        }
    }
    private suspend fun confirmNoPendingChallenge()=withTimeout(5000) {
        check(DouyinLoginPage.official(web.url)){"请在官方页面完成验证"}
        suspendCancellableCoroutine<Unit> {continuation->
            web.evaluateJavascript(DouyinLoginPage.inspect){raw->
                if(!continuation.isActive)return@evaluateJavascript
                val snapshot=runCatching {org.json.JSONObject(raw)}.getOrNull()
                if(snapshot==null){continuation.resumeWithException(IllegalStateException("官方验证页面尚未就绪"));return@evaluateJavascript}
                challenge=snapshot.optBoolean("challenge")
                if(challenge)continuation.resumeWithException(IllegalStateException("请先完成官方安全验证")) else continuation.resume(Unit)
            }
        }
    }
    override fun saveAuthorizedSession(cookies:String):Intent {
        val identity=checkNotNull(verified);check(DouyinSessionPolicy.sameOwner(verifiedCookies,cookies))
        val old=Store.accounts(this,"douyin").firstOrNull {DouyinOrders.userId(it.payload)==identity.id}
        val payload=kotlinx.serialization.json.buildJsonObject {put("cookie",kotlinx.serialization.json.JsonPrimitive(cookies));put("user_id",kotlinx.serialization.json.JsonPrimitive(identity.id))}.toString()
        val account=BoundAccount(old?.id ?: java.util.UUID.randomUUID().toString(),identity.nickname.take(24).ifBlank {"抖音账号"},true,payload)
        if(old==null)Store.addAccount(this,"douyin",account) else Store.updateAccount(this,"douyin",account)
        SessionCookies.persistOwned(this,"douyin",account)
        HubData.prefs(this).edit().putLong("douyin_verified_${account.id}",System.currentTimeMillis()).apply()
        return Intent().putExtra("bound_channel","douyin").putExtra("authorized_account_id",account.id)
    }
    override fun onResume(){foreground=true;lastAttempt="";super.onResume()}
    override fun onPause(){foreground=false;super.onPause()}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
