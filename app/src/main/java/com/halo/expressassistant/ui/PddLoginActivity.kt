package com.halo.expressassistant.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.CookieManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import android.view.Gravity
import android.view.View
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import com.halo.expressassistant.hub.OfficialFlowUi
import com.halo.expressassistant.hub.OfficialStepsView
import com.halo.expressassistant.hub.HubUi
import com.halo.expressassistant.hub.PlatformStyle
import com.halo.expressassistant.hub.SessionCookies
import com.halo.expressassistant.hub.CookiePairs
import com.halo.expressassistant.hub.HubData
import com.halo.expressassistant.hub.PddDiagnostics
import com.halo.expressassistant.hub.PddDiagnosticPayload
import com.halo.expressassistant.hub.OfficialScanArt
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor

/** Keep the official QR page alive while its user confirms login in the original App. */
open class PddLoginActivity : PlatformLoginActivity("pdd") {
    override fun saveAuthorizedSession(cookies:String):android.content.Intent {
        val account=com.halo.expressassistant.data.Store.addPddAccount(this,cookies)
        PddDiagnostics.login(this,"账号已保存",mapOf("accountRef" to PddDiagnosticPayload.ref(account.id),"enabled" to account.enabled.toString()))
        return android.content.Intent().putExtra("bound_channel","pdd").putExtra("authorized_account_id",account.id)
    }
    override val loginHelpText:String get()="保存官方二维码，在拼多多 App 的扫一扫中选择相册并授权，返回后自动检查并绑定。\n\n如需选择姓名、地址或完成安全验证，请在官方页面完成后等待检查。二维码过期时重新获取；可点检查登录重试。"
    private val imageWorker=Executors.newSingleThreadExecutor()
    private val qrHandler=Handler(Looper.getMainLooper())
    private var qrEntryAttempts=0
    private lateinit var qrEntryScript:String
    private val openQrEntry=object:Runnable {
        override fun run() {
            if(isFinishing || isDestroyed || Uri.parse(web.url.orEmpty()).path!="/login.html")return
            web.evaluateJavascript(qrEntryScript) {raw ->
                if(isFinishing || isDestroyed)return@evaluateJavascript
                val result=runCatching {org.json.JSONTokener(raw).nextValue().toString()}.getOrDefault("waiting")
                if(result=="ready"){web.visibility=View.VISIBLE;probeQrUiReady()}
                else if(result=="verification") {qrReady=false;updateQrActions();web.visibility=View.VISIBLE;state.text="请完成官网要求的安全验证，之后继续扫码登录。"}
                else {qrReady=false;updateQrActions();web.visibility=View.INVISIBLE}
                if(++qrEntryAttempts<20)qrHandler.postDelayed(this,700)
                else if(result !in setOf("ready","verification"))state.text="二维码未显示，请重新获取。"
            }
        }
    }
    private lateinit var saveButton:Button
    private lateinit var retryButton:Button
    private lateinit var stepsView:OfficialStepsView
    private lateinit var qrOverlay:LinearLayout
    private lateinit var qrTitle:TextView
    private lateinit var qrDescription:TextView
    private lateinit var qrSpinner:View
    private lateinit var qrWarning:android.widget.ImageView
    private lateinit var qrDecoration:android.widget.ImageView
    private lateinit var qrGuidance:TextView
    private var qrReady=false
    private var qrExpired=false
    private var probingQrUi=false
    private var qrUiVersion=0
    private var qrSaved=false
    private var verificationPageVisible=false
    private var qrViewportHeight=222
    override val requiresExpandedOfficialPage:Boolean get()=verificationPageVisible
    override val compactOfficialPageHeight:Int get()=qrViewportHeight
    private var returnedForCheck=false
    private var saving=false
    private var verified=false
    private var waitingInApp=false
    private var orderCheckAttempts=0
    private var readingQrStatus=false
    private var checkingDisplayedOrder=false
    private var verifier:PddSessionVerifier?=null
    private var checkingOrderSession=false
    private var nextVerificationAt=0L
    private var credentialSeenAt=0L
    private var verificationAttempts=0
    private var foreground=false
    override val sessionReady:Boolean get()=verified
    override val independentlyVerifiedLanding:Boolean get()=verified

    override fun configureLoginWebView(view:WebView) {
        qrEntryScript=assets.open("pdd-qr-entry.js").bufferedReader().use {it.readText()}
        view.visibility=android.view.View.INVISIBLE
        // The official consumer page exposes the QR entry for desktop user agents.
        view.settings.userAgentString="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        view.settings.useWideViewPort=false
        view.settings.loadWithOverviewMode=false
        if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))WebViewCompat.addDocumentStartJavaScript(
            view,assets.open("pdd-qr-status.js").bufferedReader().use {it.readText()},setOf("https://mobile.yangkeduo.com")
        )
    }

    override fun addPlatformTop(root:LinearLayout) {
        stepsView=OfficialStepsView(this)
        root.addView(stepsView,LinearLayout.LayoutParams(-1,dp(44)).apply {bottomMargin=dp(8)})
    }
    override fun addPlatformActions(root:LinearLayout) {
        qrOverlay=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(14),dp(14),dp(14),dp(14));background=OfficialFlowUi.rounded(this@PddLoginActivity,Color.rgb(247,251,255),13)}
        qrSpinner=OfficialFlowUi.spinner(this)
        qrDecoration=android.widget.ImageView(this).apply {setImageDrawable(com.halo.expressassistant.hub.HubIcon("identity",HubUi.muted,dp(32)))}
        qrWarning=android.widget.ImageView(this).apply {setImageDrawable(com.halo.expressassistant.hub.HubIcon("info",HubUi.muted,dp(32)));visibility=View.GONE}
        qrOverlay.addView(qrDecoration,LinearLayout.LayoutParams(dp(32),dp(32)).apply {bottomMargin=dp(12)})
        qrOverlay.addView(qrWarning,LinearLayout.LayoutParams(dp(32),dp(32)).apply {bottomMargin=dp(12)})
        qrOverlay.addView(qrSpinner,LinearLayout.LayoutParams(dp(26),dp(26)))
        officialPageFrame.addView(qrOverlay,android.widget.FrameLayout.LayoutParams(-1,-1))
        qrTitle=OfficialFlowUi.text(this,"正在获取二维码",17.5f,true).apply {gravity=Gravity.CENTER}
        root.addView(qrTitle,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(18)})
        qrDescription=OfficialFlowUi.text(this,"二维码来自拼多多官方页面，准备完成后即可保存到相册。",12.6f,false,HubUi.muted).apply {gravity=Gravity.CENTER}
        root.addView(qrDescription,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7);bottomMargin=dp(18)})
        saveButton=OfficialFlowUi.button(this,"保存二维码并打开拼多多",true) {
            if(returnedForCheck || checkingOrderSession || verificationPageVisible) {onManualLoginCheck();checkLoginSession()} else if(qrSaved && waitingInApp) {
                val launch=packageManager.getLaunchIntentForPackage("com.xunmeng.pinduoduo")
                if(launch!=null)startActivity(launch) else Toast.makeText(this,"请手动打开拼多多扫一扫",Toast.LENGTH_SHORT).show()
            } else saveQrAndOpenApp()
        }.apply {textSize=13.3f;maxLines=1;minHeight=dp(44);minimumHeight=dp(44)}
        root.addView(saveButton,LinearLayout.LayoutParams(-1,-2))
        retryButton=OfficialFlowUi.button(this,"重新获取二维码") {
            if(!saving) {
                waitingInApp=false;verified=false;verificationAttempts=0;credentialSeenAt=0;nextVerificationAt=0
                qrUiVersion++;probingQrUi=false
                qrReady=false;qrExpired=false;qrSaved=false;returnedForCheck=false
                state.text="正在准备官方二维码…"
                verificationPageVisible=false;updateQrActions()
                verifier?.close();verifier=null;checkingOrderSession=false
                web.loadUrl(LOGIN_URL)
            }
        }.apply {textSize=12.5f;minHeight=dp(44);minimumHeight=dp(44);strokeWidth=0;backgroundTintList=ColorStateList.valueOf(Color.TRANSPARENT)}
        root.addView(retryButton,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7)})
        qrGuidance=OfficialFlowUi.text(this,"二维码来自官方页面，仅用于本次登录授权。",10.8f,false,HubUi.muted).apply {gravity=Gravity.CENTER}
        root.addView(qrGuidance,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(9)})
        root.addView(OfficialFlowUi.button(this,"复制绑定与同步诊断") {
            (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("拼多多绑定与同步诊断",PddDiagnostics.summary(this)))
            Toast.makeText(this,"已复制脱敏诊断",Toast.LENGTH_SHORT).show()
        }.apply {textSize=11f;minHeight=dp(40);minimumHeight=dp(40);strokeWidth=0;backgroundTintList=ColorStateList.valueOf(Color.TRANSPARENT)},LinearLayout.LayoutParams(-1,-2))
        root.addView(OfficialFlowUi.button(this,"分享完整诊断（TXT）") {
            val report=PddDiagnostics.report(this)
            imageWorker.execute {
                val file=runCatching {com.halo.expressassistant.hub.DiagnosticShare.file(this,"pdd-login",report)}.getOrNull()
                runOnUiThread {
                    if(!isFinishing && !isDestroyed)runCatching {requireNotNull(file);com.halo.expressassistant.hub.DiagnosticShare.open(this,file)}
                        .onFailure {Toast.makeText(this,"暂时无法分享，请重试",Toast.LENGTH_SHORT).show()}
                }
            }
        }.apply {textSize=11f;minHeight=dp(40);minimumHeight=dp(40);strokeWidth=0;backgroundTintList=ColorStateList.valueOf(Color.TRANSPARENT)},LinearLayout.LayoutParams(-1,-2))
        state.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {updateQrActions()}
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        updateQrActions()
    }

    /** Keep the original official pixels fully visible even when the provider serves a larger QR. */
    private fun fitVisibleQr() {
        if(web.width<=0 || saving || isFinishing || isDestroyed)return
        web.evaluateJavascript(QR_RECT) {raw->
            if(isFinishing || isDestroyed || !qrReady || qrSaved || verificationPageVisible)return@evaluateJavascript
            val rect=runCatching {JSONObject(raw)}.getOrNull() ?: return@evaluateJavascript
            val width=rect.optDouble("viewportWidth",0.0)
            if(!rect.optBoolean("found") || width<=0)return@evaluateJavascript
            val needed=ceil(rect.optDouble("h",0.0)*web.width/width/resources.displayMetrics.density).toInt()+32
            if(needed>qrViewportHeight){qrViewportHeight=needed;fitOfficialPage()}
        }
    }
    private fun updateQrActions() {
        if(!::qrTitle.isInitialized)return
        val message=state.text.toString()
        if(listOf("安全验证","姓名","地址").any(message::contains))verificationPageVisible=true
        val verification=verificationPageVisible
        val returned=returnedForCheck || checkingOrderSession
        val unavailable=!qrReady && !qrSaved && !returned && listOf("失败","未显示","未找到").any(message::contains)
        val pending=!qrReady && !qrSaved && !returned && !qrExpired && !unavailable && !verification
        saveButton.isEnabled=!saving && !verified && !pending && !checkingOrderSession
        saveButton.visibility=if(qrExpired || unavailable)View.GONE else View.VISIBLE
        if(!saving)saveButton.text=when {checkingOrderSession->"正在检查登录…";returned || verification->"检查登录";qrSaved->"打开拼多多继续授权";pending->"正在准备二维码…";else->"保存二维码并打开拼多多"}
        retryButton.isEnabled=!saving && !checkingOrderSession && !pending
        retryButton.text=if(qrExpired || unavailable)"重新获取二维码" else "重新获取二维码"
        (retryButton as? MaterialButton)?.apply {backgroundTintList=ColorStateList.valueOf(if(qrExpired || unavailable)HubUi.accent else Color.TRANSPARENT);setTextColor(if(qrExpired || unavailable)Color.WHITE else if(isEnabled)HubUi.accent else HubUi.muted);strokeWidth=0}
        stepsView.current=when {returned || verification->2;qrSaved->1;else->0};stepsView.firstSaved=qrSaved
        qrOverlay.visibility=if(verification || (qrReady && !qrExpired && !returned && !qrSaved))View.GONE else View.VISIBLE
        qrSpinner.visibility=if(pending || checkingOrderSession)View.VISIBLE else View.GONE
        qrWarning.visibility=if(qrExpired || unavailable)View.VISIBLE else View.GONE
        qrDecoration.visibility=if(!pending && !checkingOrderSession && !qrExpired && !unavailable)View.VISIBLE else View.GONE
        qrTitle.text=when {qrExpired->"二维码已过期";unavailable->"暂未获取二维码";verification->"还需完成官方验证";checkingOrderSession || returned->"正在检查登录";qrSaved->"等待在拼多多确认";qrReady->"扫码绑定拼多多";else->"正在获取二维码"}
        qrDescription.text=when {qrExpired->"请重新获取二维码，扫码授权后返回此页。";unavailable->"恢复网络后可重新获取。";verification->"请在官方网页完成姓名、地址或安全验证，完成后自动继续检查。";returned->"正在确认订单页是否可以读取，检查通过后保存账号。";qrSaved->"授权后返回哆啦收件箱，返回时会自动检查。";qrReady->"拼多多 App → 扫一扫 → 相册\n选择刚保存的图片并确认登录";else->"二维码来自拼多多官方页面，准备完成后即可保存到相册。"}
        qrGuidance.text=if(verification)"按官方页面提示完成验证后继续。" else "二维码来自官方页面，仅用于本次登录授权。"
        // Verification is an official interactive page, not a QR confirmation screen.
        // Native QR instructions/actions return only after the official challenge is finished.
        stepsView.visibility=if(verification)View.GONE else View.VISIBLE
        listOf(qrTitle,qrDescription,retryButton,qrGuidance).forEach {it.visibility=if(verification)View.GONE else View.VISIBLE}
        if(verification)saveButton.visibility=View.GONE
        fitOfficialPage()
    }

    /** Read visible readiness only; official QR capture/ticket exchange stays unchanged. */
    private fun probeQrUiReady() {
        if(probingQrUi || saving || verified)return
        if(web.progress<100){qrReady=false;updateQrActions();return}
        if(Uri.parse(web.url.orEmpty()).path!="/login.html"){qrReady=false;updateQrActions();return}
        val version=qrUiVersion
        probingQrUi=true
        web.evaluateJavascript("""(function(){
          if(/二维码.*(已失效|已过期)|扫码.*已过期/.test(document.body?document.body.innerText:''))return 'expired';
          return Array.from(document.querySelectorAll('canvas,img,svg')).some(function(e){
            var r=e.getBoundingClientRect(),near=e.parentElement?e.parentElement.innerText:'';
            var label=(e.getAttribute('alt')||'')+' '+(e.getAttribute('class')||'');
            var score=e.tagName.toLowerCase()==='canvas'?100:0;
            if(/qr.?code|二维码/i.test(label))score+=80;if(/扫码|二维码/.test(near))score+=20;
            return score>0&&r.width>=120&&r.width<=500&&r.height>=120&&Math.abs(r.width-r.height)<12&&(e.tagName.toLowerCase()!=='img'||(e.complete&&e.naturalWidth>0));
          })?'ready':'waiting';
        })()""") {raw->
            if(version!=qrUiVersion)return@evaluateJavascript
            probingQrUi=false
            if(isFinishing || isDestroyed || Uri.parse(web.url.orEmpty()).path!="/login.html")return@evaluateJavascript
            val result=runCatching {org.json.JSONTokener(raw).nextValue().toString()}.getOrDefault("waiting")
            acceptQrReadiness(result)
            if(qrExpired && !verified && !checkingOrderSession)state.text="二维码已过期，请重新获取后在原 App 扫码授权。"
        }
    }

    private fun acceptQrReadiness(result:String) {
        qrReady=result=="ready";qrExpired=result=="expired"
        if(qrReady && !qrSaved && !returnedForCheck && !checkingOrderSession && verificationPageVisible) {
            verificationPageVisible=false
            state.text="保存官方二维码，到拼多多 App 扫码授权"
        }
        updateQrActions()
        if(qrReady && !qrSaved && !returnedForCheck && !verificationPageVisible)fitVisibleQr()
    }

    override fun onOfficialPage(view:WebView,url:String,isOfficial:Boolean,isLanding:Boolean) {
        PddDiagnostics.login(this,"网页跳转",mapOf("path" to PddDiagnosticPayload.endpoint(url),"official" to isOfficial.toString(),"landing" to isLanding.toString()))
        if(!isOfficial)return
        qrUiVersion++;probingQrUi=false
        qrReady=false;updateQrActions()
        qrHandler.removeCallbacks(openQrEntry)
        if(isLanding && Uri.parse(url).path=="/orders.html") {
            view.visibility=android.view.View.VISIBLE
            orderCheckAttempts=0
            verifyOrderPage()
            return
        }
        if(Uri.parse(url).path=="/login.html") {
            qrEntryAttempts=0;qrHandler.postDelayed(openQrEntry,500)
        }
        if(Uri.parse(url).path.orEmpty().contains("verification")) {
            view.visibility=android.view.View.VISIBLE
            state.text="请完成官网安全验证，之后继续扫码。";return
        }
        state.text=if(waitingInApp)"请在 App 扫码确认；过期时重新获取。"
            else "正在打开官方二维码…"
    }

    override fun initialLoginUrl(defaultUrl:String):String =
        if(!intent.getBooleanExtra("freshLogin",false) && PddLoginSession.hasCredentials(CookiePairs.parse(SessionCookies.current("pdd"))))ORDERS_URL else LOGIN_URL

    override fun onManualLoginCheck() {
        PddDiagnostics.login(this,"手动检查登录")
        if(Uri.parse(web.url.orEmpty()).path=="/orders.html") {
            verifyOrderPage()
            if(PddLoginSession.hasCredentials(CookiePairs.parse(SessionCookies.current("pdd"))))verifyLiveSession(true)
        }
        else observeQrSession(PddLoginSession.hasCredentials(CookiePairs.parse(SessionCookies.current("pdd"))),manual=true)
    }

    override fun onSessionObserved(hasCredentials:Boolean) {
        if(!foreground)return
        probeQrUiReady()
        if(Uri.parse(web.url.orEmpty()).path=="/orders.html") {
            if(hasCredentials && !verified && !checkingOrderSession && System.currentTimeMillis()>=nextVerificationAt)verifyOrderPage()
            return
        }
        observeQrSession(hasCredentials)
    }
    private fun observeQrSession(hasCredentials:Boolean,manual:Boolean=false) {
        // Scan success is shown BEFORE the official ticket-exchange request finishes.
        // Let the official callback redeem its ticket and navigate to its own return URL.
        // Cookie presence alone must never navigate/reload this WebView.
        if(verified || readingQrStatus || !PddLoginVerificationPolicy.canObserve(web.url.orEmpty()))return
        readingQrStatus=true
        web.evaluateJavascript("JSON.stringify({scanned:!!document.querySelector('.success-area'),status:window.__parcelPddQrStatus||{stage:'idle',http:0,code:''}})") {raw->
            readingQrStatus=false
            if(isFinishing || isDestroyed || !foreground)return@evaluateJavascript
            val data=runCatching {JSONObject(org.json.JSONTokener(raw).nextValue() as String)}.getOrNull() ?: return@evaluateJavascript
            val result=data.optJSONObject("status") ?: return@evaluateJavascript
            val stage=result.optString("stage")
            PddDiagnostics.login(this,"官方授权状态",mapOf("stage" to stage,"http" to result.optInt("http").toString(),"code" to result.optString("code"),"scanned" to data.optBoolean("scanned").toString(),"credentialPresent" to hasCredentials.toString()))
            state.text=if(qrExpired)"二维码已过期，请重新获取后在原 App 扫码授权。" else when(stage) {
                "rejected"->"扫码登录未完成，请重新扫码。"
                "network_error"->"登录请求未完成，请检查网络后重试。"
                "exchanging"->"已扫码，正在完成登录，请勿刷新。"
                "returned"->"正在检查登录结果…"
                else->if(hasCredentials || data.optBoolean("scanned"))"已收到授权，等待官网完成登录…" else "请在拼多多 App 扫码并确认。"
            }
            if(stage in setOf("rejected","network_error"))HubData.prefs(this).edit().putString("pdd_login_diagnostic",
                "${state.text}\n阶段 $stage；HTTP ${result.optInt("http")}；错误码 ${result.optString("code").ifBlank {"未提供"}}").apply()
            val now=System.currentTimeMillis()
            if(!hasCredentials){credentialSeenAt=0;return@evaluateJavascript}
            if(credentialSeenAt==0L)credentialSeenAt=now
            // A completed exchange can leave the official login route unchanged.
            // A stable live session is also checked after returning from the App.
            val verificationLanding=Uri.parse(web.url.orEmpty()).path!="/login.html"
            val ready=PddLoginVerificationPolicy.shouldVerify(hasCredentials,stage,now-credentialSeenAt,data.optBoolean("scanned"),waitingInApp,verificationLanding,manual)
            if(ready)verifyLiveSession(manual)
        }
    }

    protected open fun startOrderSessionVerification(callback:(Boolean)->Unit) {
        verifier=PddSessionVerifier(this,web.settings.userAgentString,callback).also {it.start()}
    }
    private fun verifyLiveSession(manual:Boolean) {
        val now=System.currentTimeMillis()
        if(!foreground || verified || checkingOrderSession || (!manual && now<nextVerificationAt))return
        checkingOrderSession=true;verificationAttempts++;nextVerificationAt=now+PddLoginVerificationPolicy.retryDelay(verificationAttempts)
        PddDiagnostics.login(this,"检查订单会话",mapOf("attempt" to verificationAttempts.toString(),"manual" to manual.toString()))
        returnedForCheck=true;updateQrActions()
        state.text="正在检查订单页登录状态…"
        CookieManager.getInstance().flush()
        startOrderSessionVerification {valid->
            checkingOrderSession=false;verifier=null
            if(isFinishing || isDestroyed)return@startOrderSessionVerification
            verified=valid && PddLoginSession.hasCredentials(CookiePairs.parse(SessionCookies.current("pdd")))
            PddDiagnostics.login(this,"订单会话检查结果",mapOf("officialPageAccepted" to valid.toString(),"verified" to verified.toString()))
            updateQrActions()
            if(verified){HubData.prefs(this).edit().remove("pdd_login_diagnostic").apply();state.text="登录已确认，正在绑定…";checkLoginSession()}
            else state.text="请完成官网姓名 / 地址等验证，之后自动检查。"
        }
    }

    private fun verifyOrderPage() {
        if(checkingDisplayedOrder || verified)return
        checkingDisplayedOrder=true;orderCheckAttempts=0
        readDisplayedOrder()
    }
    private fun readDisplayedOrder() {
        if(!foreground || isFinishing || isDestroyed || Uri.parse(web.url.orEmpty()).path!="/orders.html"){checkingDisplayedOrder=false;return}
        web.evaluateJavascript("(function(){var s=window.rawData&&window.rawData.ordersStore;return !!(s&&s.uid&&String(s.uid)!=='0');})()") {valid ->
            if(!foreground || isFinishing || isDestroyed || Uri.parse(web.url.orEmpty()).path!="/orders.html"){checkingDisplayedOrder=false;return@evaluateJavascript}
            verified=valid=="true"
            if(verified || orderCheckAttempts>=9)PddDiagnostics.login(this,"可见订单页检查",mapOf("verified" to verified.toString(),"attempt" to (orderCheckAttempts+1).toString()))
            if(verified){checkingDisplayedOrder=false;checkLoginSession()}
            else if(++orderCheckAttempts<10)qrHandler.postDelayed({readDisplayedOrder()},700)
            else {
                checkingDisplayedOrder=false
                if(PddLoginSession.hasCredentials(CookiePairs.parse(SessionCookies.current("pdd"))))verifyLiveSession(false)
                else state.text="登录尚未完成，请稍后点「检查登录」。"
            }
        }
    }

    override fun onCreate(savedInstanceState:android.os.Bundle?) {
        if(savedInstanceState==null)PddDiagnostics.beginLogin(this)
        waitingInApp=savedInstanceState?.getBoolean("pdd_waiting",false) ?: false
        qrSaved=savedInstanceState?.getBoolean("pdd_qr_saved",false) ?: false
        returnedForCheck=savedInstanceState?.getBoolean("pdd_returned_check",false) ?: false
        super.onCreate(savedInstanceState)
    }
    override fun onSaveInstanceState(outState:android.os.Bundle) {
        outState.putBoolean("pdd_waiting",waitingInApp)
        outState.putBoolean("pdd_qr_saved",qrSaved)
        outState.putBoolean("pdd_returned_check",returnedForCheck)
        super.onSaveInstanceState(outState)
    }

    private fun saveQrAndOpenApp() {
        if(saving || !qrReady || qrExpired)return
        if(web.progress<100){qrReady=false;updateQrActions();return}
        val uri=Uri.parse(web.url.orEmpty())
        if(uri.scheme!="https" || uri.host!="mobile.yangkeduo.com" || uri.path!="/login.html") {
            Toast.makeText(this,"请先打开官方扫码登录二维码",Toast.LENGTH_LONG).show();return
        }
        saving=true;updateQrActions();saveButton.text="正在准备二维码…"
        state.text="正在检查官方二维码…"
        web.evaluateJavascript(QR_RECT) {raw ->
            if(isFinishing || isDestroyed)return@evaluateJavascript
            val qr=runCatching {JSONObject(raw)}.getOrNull()
            if(qr==null || !qr.optBoolean("found")) {
                qrReady=false
                finishSaving("未找到二维码，请重新获取后保存。");return@evaluateJavascript
            }
            web.postOnAnimation {
                if(isFinishing || isDestroyed)return@postOnAnimation
                try {
                    val ratio=web.width.toDouble()/qr.getDouble("viewportWidth")
                    require(qr.getDouble("x")>=0 && qr.getDouble("y")>=0)
                    val left=floor(qr.getDouble("x")*ratio).toInt().coerceAtLeast(0)
                    val top=floor(qr.getDouble("y")*ratio).toInt().coerceAtLeast(0)
                    val width=ceil(qr.getDouble("w")*ratio).toInt()
                    val height=ceil(qr.getDouble("h")*ratio).toInt()
                    require(width>=64 && height>=64 && left+width<=web.width && top+height<=web.height)
                    val screen=Bitmap.createBitmap(web.width,web.height,Bitmap.Config.ARGB_8888)
                    web.draw(Canvas(screen))
                    val crop=Bitmap.createBitmap(screen,left,top,width,height)
                    val image=Bitmap.createBitmap(width+40,height+40,Bitmap.Config.ARGB_8888)
                    Canvas(image).apply {drawColor(Color.WHITE);drawBitmap(crop,20f,20f,null)}
                    if(crop!==screen)crop.recycle()
                    screen.recycle()
                    state.text="正在保存二维码到相册…";saveButton.text="正在保存到相册…"
                    imageWorker.execute {saveImage(image)}
                }catch(_:Exception){finishSaving("二维码没有完整显示，请调整页面后重试。")}
            }
        }
    }

    private fun saveImage(image:Bitmap) {
        var imageUri:Uri?=null
        try {
            val values=ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"dora-inbox-PDD-login-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/DoraInbox")
                put(MediaStore.Images.Media.IS_PENDING,1)
            }
            val destination=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:error("insert")
            imageUri=destination
            contentResolver.openOutputStream(destination)?.use {require(image.compress(Bitmap.CompressFormat.PNG,100,it))}?:error("output")
            require(contentResolver.update(destination,ContentValues().apply {put(MediaStore.Images.Media.IS_PENDING,0)},null,null)>0)
            runOnUiThread {
                if(isFinishing || isDestroyed)return@runOnUiThread
                qrSaved=true;returnedForCheck=false
                PddDiagnostics.login(this,"官方二维码已保存")
                finishSaving("二维码已保存。拼多多首页扫一扫 → 相册 → 选最新图片 → 确认登录，然后返回哆啦收件箱。")
                val launch=packageManager.getLaunchIntentForPackage("com.xunmeng.pinduoduo")
                if(launch!=null)try {waitingInApp=true;startActivity(launch)}catch(_:Exception){
                    waitingInApp=false;Toast.makeText(this,"图片已保存，请手动打开拼多多扫一扫",Toast.LENGTH_LONG).show()
                }else Toast.makeText(this,"图片已保存，请手动打开拼多多扫一扫",Toast.LENGTH_LONG).show()
            }
        }catch(_:Exception){
            // This URI is created by this unfinished save, never an existing user image.
            imageUri?.let {runCatching {contentResolver.delete(it,null,null)}}
            runOnUiThread {if(!isFinishing && !isDestroyed)finishSaving("保存失败，请检查手机剩余存储空间后重试。")}
        }finally{image.recycle()}
    }

    private fun finishSaving(message:String) {
        saving=false;updateQrActions();saveButton.text="保存二维码并打开拼多多";state.text=message
    }

    override fun onResume() {
        foreground=true
        super.onResume()
        if(waitingInApp && ::saveButton.isInitialized) {
            PddDiagnostics.login(this,"从拼多多返回，继续检查")
            // Do not reload: the original official page must redeem the same QR ticket.
            web.onResume()
            returnedForCheck=true;updateQrActions()
            state.text="已返回，正在检查官方登录结果；请按页面提示完成授权。"
        }
    }
    override fun onPause() {foreground=false;verifier?.close();verifier=null;checkingOrderSession=false;super.onPause()}
    override fun onDestroy() {verifier?.close();qrHandler.removeCallbacksAndMessages(null);imageWorker.shutdown();super.onDestroy()}

    companion object {
        private const val LOGIN_URL="https://mobile.yangkeduo.com/login.html?from=https%3A%2F%2Fmobile.yangkeduo.com%2Forders.html"
        private const val ORDERS_URL="https://mobile.yangkeduo.com/orders.html"
        // Find visible QR pixels. Do not obtain tickets or generate an unofficial QR.
        val QR_RECT="""
        (function(){
          if(/二维码.*(已失效|已过期)|扫码.*已过期/.test(document.body.innerText))return {found:false};
          var nodes=Array.from(document.querySelectorAll('canvas,img,svg')).map(function(e){
            var r=e.getBoundingClientRect(),p=e.parentElement,near=p?p.innerText:'';
            var label=(e.getAttribute('alt')||'')+' '+(e.getAttribute('class')||'');
            var score=e.tagName.toLowerCase()==='canvas'?100:0;
            if(/qr.?code|二维码/i.test(label))score+=80;
            if(/扫码|二维码/.test(near))score+=20;
            return {e:e,r:r,score:score};
          }).filter(function(v){return v.score>0&&v.r.width>=120&&v.r.width<=500&&v.r.height>=120&&Math.abs(v.r.width-v.r.height)<12;});
          nodes.sort(function(a,b){return b.score-a.score;});if(!nodes.length)return {found:false};
          var e=nodes[0].e;e.scrollIntoView({block:'center',inline:'center',behavior:'instant'});
          var r=e.getBoundingClientRect();return {found:true,x:r.left,y:r.top,w:r.width,h:r.height,viewportWidth:innerWidth};
        })()
        """.trimIndent()
    }
}
