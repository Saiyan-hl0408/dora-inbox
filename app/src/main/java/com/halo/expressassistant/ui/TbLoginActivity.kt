package com.halo.expressassistant.ui

import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.halo.expressassistant.api.TbOrders
import com.halo.expressassistant.data.Store
import com.halo.expressassistant.hub.*
import kotlinx.coroutines.*

/** One shared official session, with independently verified orders and station capabilities. */
class TbLoginActivity : PlatformLoginActivity("taobao") {
    override val loginHelpText:String get()="登录淘宝后，自动验证商城订单并继续检查菜鸟取件服务。所有入口使用同一绑定流程；官网要求确认授权时，请在当前页完成。"
    private var ordersVerified=false
    private var cainiaoVerified=false
    private var ordersOnlyCompletion=false
    private var cainiaoStage=false
    private var ownerCookies=""
    private var savedAccountId=""
    private var verifying=false
    private var attemptedAt=0L
    private var verificationGeneration=0
    private val verificationHandler=Handler(Looper.getMainLooper())
    private val sessionScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var ownsSession=false
    override fun prepareSession(action:()->Unit) {
        state.text="正在准备淘宝登录…"
        sessionScope.launch {CainiaoWebFetcher.sessionMutex.lock();ownsSession=true;action()}
    }
    override val sessionReady:Boolean get()=ordersVerified && (cainiaoVerified || ordersOnlyCompletion) && ownerIsCurrent()
    override val independentlyVerifiedLanding:Boolean get()=sessionReady
    override fun capabilityStatusText()="淘宝商城订单：${if(ordersVerified)"已验证" else "待验证"}\n菜鸟取件服务：${if(cainiaoVerified)"已验证" else if(cainiaoStage)"检查中，必要时需官方授权" else "等待淘宝登录"}\n\n所有登录入口都验证这两项能力，分别保存实际结果。"
    override fun initialLoginUrl(defaultUrl:String)="https://login.m.taobao.com/login.htm?redirectURL=https%3A%2F%2Fh5.m.taobao.com%2Fmlapp%2Folist.html"

    private fun ownerIsCurrent():Boolean {
        if(ownerCookies.isBlank() || !CookiePairs.sameTaobaoAccount(CookiePairs.parse(ownerCookies),CookiePairs.parse(SessionCookies.current("taobao"))))return false
        val id=savedAccountId.ifBlank {intent.getStringExtra("login_account_id").orEmpty()}
        if(id.isBlank())return true
        val account=Store.accountById(this,"taobao",id) ?: return false
        return account.enabled && CookiePairs.sameTaobaoAccount(CookiePairs.parse(ownerCookies),CookiePairs.parse(Store.cookieOf(account.payload)))
    }
    private fun recordCapabilities(cookies:String) {
        Store.addTbAccount(this,cookies,ordersVerified=ordersVerified,cainiaoVerified=cainiaoVerified,cainiaoOnly=false)
        savedAccountId=Store.accounts(this,"taobao").firstOrNull {
            it.enabled && CookiePairs.sameTaobaoAccount(CookiePairs.parse(cookies),CookiePairs.parse(Store.cookieOf(it.payload)))
        }?.id.orEmpty()
    }
    override fun saveAuthorizedSession(cookies:String):Intent {
        check(ownerIsCurrent()) {"账号会话已变化，请重新绑定"}
        recordCapabilities(cookies)
        return Intent().putExtra("bound_channel","taobao").putExtra("cainiao_bound",cainiaoVerified)
            .putExtra("cainiao_verified",cainiaoVerified).putExtra("authorized_account_id",savedAccountId)
    }
    override fun onManualLoginCheck(){attemptedAt=0}
    override fun onSessionObserved(hasCredentials:Boolean) {
        if(ownerCookies.isNotBlank() && !ownerIsCurrent()) {
            verificationGeneration++;verifying=false
            state.text="账号已切换、停用或移除，请重新进入绑定。已保存的订单能力保留。"
            return
        }
        if(!hasCredentials || sessionReady || verifying || System.currentTimeMillis()-attemptedAt<15000)return
        if(cainiaoStage){verifyCainiao();return}
        val uri=android.net.Uri.parse(web.url.orEmpty())
        if(uri.scheme!="https" || !(uri.host=="taobao.com" || uri.host.orEmpty().endsWith(".taobao.com")) || uri.host.orEmpty().startsWith("login."))return
        verifying=true;attemptedAt=System.currentTimeMillis()
        val cookies=SessionCookies.current("taobao")
        val accountAtStart=intent.getStringExtra("login_account_id").orEmpty().ifBlank {
            Store.accounts(this,"taobao").firstOrNull {it.enabled && CookiePairs.sameTaobaoAccount(CookiePairs.parse(cookies),CookiePairs.parse(Store.cookieOf(it.payload)))}?.id.orEmpty()
        }
        state.text="正在验证淘宝商城订单…"
        sessionScope.launch {
            try {
                com.halo.expressassistant.api.TbOrderRead.page(
                    {TbOrders.fetchBoughtPageWith(cookies)},
                    {TbWebOrders.probeCurrentSession(this@TbLoginActivity) {
                        CookiePairs.sameTaobaoAccount(CookiePairs.parse(cookies),CookiePairs.parse(SessionCookies.current("taobao")))
                    }})
                if(isFinishing || isDestroyed)return@launch
                ownerCookies=cookies
                savedAccountId=accountAtStart
                if(!ownerIsCurrent()){state.text="账号已变化，请重新进入绑定。";return@launch}
                ordersVerified=true
                // Save the successful capability before optional official station authorization.
                recordCapabilities(SessionCookies.current("taobao"))
                cainiaoStage=true;attemptedAt=0L
                state.text="淘宝订单已验证，正在继续检查菜鸟服务…"
                offerOrdersOnlyCompletion {completeOrdersOnly()}
                web.loadUrl(CainiaoPickupService.pageUrl(this@TbLoginActivity))
            } catch(e:CancellationException){throw e}
            catch(e:Exception){state.text=e.message ?: "淘宝订单未验证，请完成商城登录后检查"}
            finally {verifying=false}
        }
    }
    private fun verifyCainiao() {
        if(!CainiaoWebFetcher.official(web.url.orEmpty()))return
        if(ownerCookies.isBlank()) {
            ownerCookies=SessionCookies.current("taobao")
            savedAccountId=intent.getStringExtra("login_account_id").orEmpty().ifBlank {
                Store.accounts(this,"taobao").firstOrNull {it.enabled && CookiePairs.sameTaobaoAccount(CookiePairs.parse(ownerCookies),CookiePairs.parse(Store.cookieOf(it.payload)))}?.id.orEmpty()
            }
        }
        if(!ownerIsCurrent())return
        verifying=true;attemptedAt=System.currentTimeMillis()
        val generation=++verificationGeneration
        state.text=if(ordersVerified)"淘宝订单已验证，正在检查菜鸟取件服务…" else "正在检查菜鸟取件服务…"
        web.evaluateJavascript(CainiaoWebFetcher.script(this),null)
        web.evaluateJavascript("(function(){window.__parcelCainiaoLogin=null;window.__parcelCainiao.verifyAccount().then(ok=>window.__parcelCainiaoLogin=ok);return true;})()",null)
        val started=System.currentTimeMillis()
        fun poll() {
            if(isFinishing || isDestroyed || generation!=verificationGeneration)return
            if(!ownerIsCurrent()){verifying=false;state.text="账号已变化，请重新进入绑定。";return}
            web.evaluateJavascript("window.__parcelCainiaoLogin") {raw->
                if(isFinishing || isDestroyed || generation!=verificationGeneration)return@evaluateJavascript
                if(!ownerIsCurrent()){verifying=false;state.text="账号已变化，请重新进入绑定。";return@evaluateJavascript}
                when {
                    raw=="true"->{cainiaoVerified=true;verifying=false;state.text="菜鸟取件服务已验证";checkLoginSession()}
                    raw=="false" || System.currentTimeMillis()-started>16000->{
                        verifying=false
                        state.text=if(ordersVerified)"淘宝已绑定。请在官方页面继续完成菜鸟授权，也可稍后再授权。" else "请完成淘宝登录及菜鸟频道授权。"
                    }
                    else->verificationHandler.postDelayed({poll()},400)
                }
            }
        }
        verificationHandler.postDelayed({poll()},400)
    }
    private fun completeOrdersOnly() {
        if(!ordersVerified || !ownerIsCurrent())return
        verificationGeneration++;verifying=false;ordersOnlyCompletion=true
        checkLoginSession()
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if(ordersVerified && ownerIsCurrent())completeOrdersOnly() else super.onBackPressed()
    }
    override fun onBindingBack() {
        if(ordersVerified && ownerIsCurrent())completeOrdersOnly() else super.onBindingBack()
    }
    override fun onDestroy() {
        verificationGeneration++;verificationHandler.removeCallbacksAndMessages(null)
        sessionScope.cancel();super.onDestroy()
        if(ownsSession){ownsSession=false;CainiaoWebFetcher.sessionMutex.unlock()}
    }
}
