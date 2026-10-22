package com.halo.expressassistant.hub

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.halo.expressassistant.data.BoundAccount
import com.halo.expressassistant.data.Store
import com.halo.expressassistant.ui.CainiaoWebFetcher
import com.halo.expressassistant.ui.TbLoginActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

/** Own the shared Taobao session only while started; never retain or cache a code bitmap. */
class TaobaoIdentityActivity:AppCompatActivity() {
    private var accountId=""
    private var requiresSelection=false
    private var web:WebView?=null
    private var sessionJob:Job?=null
    private lateinit var shell:IdentityPageShell
    private var pageFailed=false
    private var authInProgress=false
    private var owner:BoundAccount?=null
    private var activeGuard:IdentitySessionPolicy?=null
    private var entryRefresh:IdentityAutomaticRefresh?=null
    private val accountChanges=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
        if(key in setOf("tb_accounts","browser_cookie_jar")) {
            val page=web;val selected=owner;val guard=activeGuard
            if(page!=null && selected!=null && guard!=null)verifyOwner(page,selected,guard)
        }
    }
    private val auth=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        authInProgress=false
        if(result.resultCode==RESULT_OK) {
            val authorizedId=result.data?.getStringExtra("authorized_account_id").orEmpty()
            val selected=Store.accountById(this,"taobao",authorizedId)?.takeIf {it.enabled}
            if(selected!=null){accountId=selected.id;requiresSelection=false;startSession()}
            else {
                hidePage();requiresSelection=true;shell.reload.isEnabled=false
                shell.message.text="未确认本次授权账号，请重新选择。";shell.login.visibility=View.VISIBLE;shell.showAccountChanged()
            }
        } else startSession()
    }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        accountId=state?.getString("account_id") ?: intent.getStringExtra("account_id").orEmpty()
        requiresSelection=state?.getBoolean("requires_selection",false) ?: false
        authInProgress=state?.getBoolean("auth_in_progress",false) ?: false
        IdentityViewport.prepareWindow(window)
        shell=IdentityPageShell(this,"菜鸟身份码",{startSession()},{IdentityLauncher.openApp(this,"taobao")},{login()},{chooseAccount()})
        window.setLayout(-1,-1)
    }
    override fun onStart(){super.onStart();getSharedPreferences("express_store",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(accountChanges);if(!authInProgress)startSession()}
    override fun onStop(){getSharedPreferences("express_store",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(accountChanges);entryRefresh?.close();hidePage();sessionJob?.cancel();super.onStop()}
    override fun onSaveInstanceState(out:Bundle){
        out.putString("account_id",accountId);out.putBoolean("auth_in_progress",authInProgress)
        out.putBoolean("requires_selection",requiresSelection);super.onSaveInstanceState(out)
    }
    private fun hidePage(){
        if(!::shell.isInitialized)return
        val wasVisible=shell.holder.visibility==View.VISIBLE
        shell.holder.visibility=View.INVISIBLE;shell.progress.visibility=View.GONE
        if(wasVisible)web?.stopLoading()
    }
    private fun login() {
        entryRefresh?.close();hidePage();authInProgress=true;sessionJob?.cancel()
        auth.launch(Intent(this,TbLoginActivity::class.java).putExtra("cainiao_only",true).putExtra("login_account_id",accountId))
    }
    private fun chooseAccount() {
        entryRefresh?.close();hidePage();sessionJob?.cancel()
        val accounts=Store.accounts(this,"taobao").filter {it.enabled}
        if(accounts.isEmpty()){login();return}
        shell.chooseAccount(accounts,accountId){selected->accountId=selected.id;requiresSelection=false;startSession()}
    }
    private fun startSession() {
        entryRefresh?.close();hidePage();sessionJob?.cancel()
        if(accountId.isBlank() && !requiresSelection)accountId=Store.firstEnabledAccount(this,"taobao")?.id.orEmpty()
        val account=if(requiresSelection)null else Store.accountById(this,"taobao",accountId)?.takeIf {it.enabled}
        if(account==null) {
            shell.account.text="请选择已绑定淘宝账号";shell.message.text="请先选择启用的淘宝账号，或登录 / 恢复。"
            shell.login.visibility=View.VISIBLE;shell.reload.isEnabled=false;shell.showRecovery();return
        }
        shell.account.text=account.label.ifBlank {"已绑定淘宝账号"}
        shell.message.text="正在等待淘宝查询结束…";shell.reload.isEnabled=false;shell.login.visibility=View.GONE;shell.showLoading()
        sessionJob=lifecycleScope.launch {
            CainiaoWebFetcher.sessionMutex.withLock {
                var page:WebView?=null
                val guard=IdentitySessionPolicy("taobao",Store.cookieOf(account.payload))
                try {
                    ensureActive()
                    if(!SessionCookies.restoreOwned(this@TaobaoIdentityActivity,"taobao",account)) {
                        shell.message.text="无法确认该淘宝账号的官方会话，请恢复登录。";shell.login.visibility=View.VISIBLE;shell.showRecovery();return@withLock
                    }
                    if(guard.check(true,SessionCookies.current("taobao"))!=IdentitySessionPolicy.State.ACTIVE)return@withLock
                    val livePage=createPage(account,guard);page=livePage;web=livePage;owner=account;activeGuard=guard
                    shell.holder.addView(livePage,FrameLayout.LayoutParams(-1,-1))
                    shell.holder.visibility=View.VISIBLE;shell.reload.isEnabled=true;shell.progress.visibility=View.VISIBLE
                    shell.message.text="正在加载菜鸟官方页面…"
                    livePage.loadUrl(IdentityRoutes.TB_URL)
                    while(isActive) {
                        delay(500)
                        if(!verifyOwner(livePage,account,guard))break
                    }
                } finally {
                    if(page!=null) {
                        if(guard.mayPersist && SessionCookies.persistOwned(this@TaobaoIdentityActivity,"taobao",account)) {
                            Store.accountById(this@TaobaoIdentityActivity,"taobao",account.id)?.let {
                                Store.updateAccount(this@TaobaoIdentityActivity,"taobao",it.copy(payload=Store.cookiePayload(SessionCookies.current("taobao"))))
                            }
                        }
                        if(web===page){hidePage();web=null;owner=null;activeGuard=null}
                        entryRefresh?.close();entryRefresh=null
                        page.stopLoading();shell.holder.removeView(page);page.destroy()
                    }
                }
            }
        }
    }
    private fun verifyOwner(page:WebView,account:BoundAccount,guard:IdentitySessionPolicy):Boolean {
        if(web!==page)return false
        val selected=Store.accountById(this,"taobao",account.id)
        val available=selected?.enabled==true && IdentitySessionPolicy.matchesOwner("taobao",Store.cookieOf(account.payload),Store.cookieOf(selected.payload))
        val state=guard.check(available,SessionCookies.current("taobao"))
        if(state==IdentitySessionPolicy.State.ACTIVE)return true
        entryRefresh?.close();hidePage();shell.reload.isEnabled=false;shell.login.visibility=View.VISIBLE;shell.showAccountChanged()
        shell.message.text="账号已变化、移除或暂停，旧码已隐藏。请重新选择账号或恢复登录。"
        return false
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun createPage(account:BoundAccount,guard:IdentitySessionPolicy)=WebView(this).apply {
        settings.apply {javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false;useWideViewPort=true;loadWithOverviewMode=true}
        CookieManager.getInstance().setAcceptThirdPartyCookies(this,true)
        val automatic=IdentityAutomaticRefresh(this,
            {url->!pageFailed && web===this && !isFinishing && !isDestroyed &&
                IdentityWebPolicy.allowed(url) && !IdentityWebPolicy.loginPage(url) && verifyOwner(this,account,guard)},
            {shell.showLoading();shell.message.text="正在准备菜鸟身份码…"})
        entryRefresh=automatic
        webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                if(!request.isForMainFrame)return false
                if(!verifyOwner(view,account,guard))return true
                if(IdentityWebPolicy.allowed(request.url.toString()))return false
                shell.message.text="该操作需要原 App，可使用下方出示按钮";return true
            }
            override fun onPageStarted(view:WebView,url:String,icon:android.graphics.Bitmap?){
                if(!verifyOwner(view,account,guard))return
                automatic.onNavigation()
                pageFailed=false;shell.showLoading()
            }
            override fun onPageFinished(view:WebView,url:String) {
                if(!verifyOwner(view,account,guard) || pageFailed)return
                if(IdentityWebPolicy.loginPage(url)){shell.showReady();shell.message.text="请在官方页面完成登录或号码授权";shell.login.visibility=View.VISIBLE;return}
                if(automatic.afterLoad(url))return
                shell.showReady()
                shell.holder.visibility=View.VISIBLE
                shell.message.text="身份码由菜鸟官方页面实时提供。"
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){
                if(request.isForMainFrame && verifyOwner(view,account,guard)){pageFailed=true;shell.showFailure();shell.message.text="官方页面加载失败，可重新加载页面重试。"}
            }
            override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse){
                if(request.isForMainFrame && verifyOwner(view,account,guard)){pageFailed=true;shell.progress.visibility=View.GONE;shell.message.text="官方页面暂不可用，可重新加载页面重试。"}
            }
        }
    }
}
