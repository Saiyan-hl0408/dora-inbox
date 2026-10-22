package com.halo.expressassistant.hub

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.halo.expressassistant.data.Store
import com.halo.expressassistant.ui.JdLoginActivity

/** Owned live official JD page; neither a bitmap code cache nor an account fallback. */
class IdentityCodeActivity:AppCompatActivity() {
    private lateinit var shell:IdentityPageShell
    private var web:WebView?=null
    private var accountId=""
    private var guard:IdentitySessionPolicy?=null
    private var owner:com.halo.expressassistant.data.BoundAccount?=null
    private var started=false
    private var authInProgress=false
    private var requiresSelection=false
    private val handler=Handler(Looper.getMainLooper())
    private val accountChanges=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
        if(started && web!=null && key in setOf("jd_accounts","browser_cookie_jar"))verifyOwner()
    }
    private val watch=object:Runnable {override fun run(){if(started && web!=null && verifyOwner())handler.postDelayed(this,500)}}
    private val auth=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        authInProgress=false
        if(result.resultCode==RESULT_OK) {
            val live=CookiePairs.parse(SessionCookies.current("jd"))
            val matches=Store.accounts(this,"jd").filter {it.enabled && CookiePairs.sameJdAccount(live,CookiePairs.parse(Store.cookieOf(it.payload)))}
            val confirmed=if(accountId.isNotBlank())matches.firstOrNull {it.id==accountId} else matches.singleOrNull()
            requiresSelection=confirmed==null
            if(confirmed!=null)accountId=confirmed.id
        }
        if(started)startSession()
    }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val channel=intent.getStringExtra("channel") ?: "jd"
        if(!IdentitySessionPolicy.allowsEmbeddedPage(channel) || channel!="jd") {
            IdentityLauncher.open(this,channel,intent.getStringExtra("account_id").orEmpty());finish();return
        }
        accountId=state?.getString("account_id") ?: intent.getStringExtra("account_id").orEmpty()
        authInProgress=state?.getBoolean("auth_in_progress",false) ?: false
        requiresSelection=state?.getBoolean("requires_selection",false) ?: false
        IdentityViewport.prepareWindow(window)
        shell=IdentityPageShell(this,"京东身份码",{startSession()},{IdentityLauncher.openApp(this,"jd")},{login()},{chooseAccount()})
        window.setLayout(-1,-1)
    }
    override fun onStart(){super.onStart();started=true;getSharedPreferences("express_store",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(accountChanges);if(::shell.isInitialized && !authInProgress)startSession()}
    override fun onStop(){started=false;getSharedPreferences("express_store",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(accountChanges);if(::shell.isInitialized)shell.holder.visibility=View.INVISIBLE;disposePage();super.onStop()}
    override fun onDestroy(){handler.removeCallbacks(watch);disposePage();super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle){out.putString("account_id",accountId);out.putBoolean("auth_in_progress",authInProgress);out.putBoolean("requires_selection",requiresSelection);super.onSaveInstanceState(out)}
    private fun login() {
        disposePage();authInProgress=true
        auth.launch(Intent(this,JdLoginActivity::class.java).putExtra("login_account_id",accountId))
    }
    private fun chooseAccount() {
        disposePage()
        val accounts=Store.accounts(this,"jd").filter {it.enabled}
        if(accounts.isEmpty()){login();return}
        shell.chooseAccount(accounts,accountId){selected->accountId=selected.id;requiresSelection=false;startSession()}
    }
    private fun startSession() {
        disposePage();shell.holder.visibility=View.INVISIBLE;shell.login.visibility=View.GONE
        if(accountId.isBlank() && !requiresSelection)accountId=Store.firstEnabledAccount(this,"jd")?.id.orEmpty()
        val account=if(requiresSelection)null else Store.accountById(this,"jd",accountId)?.takeIf {it.enabled}
        if(account==null) {
            shell.account.text="请选择已绑定京东账号";shell.message.text="原账号已移除或暂停，请重新选择或登录。"
            shell.login.visibility=View.VISIBLE;shell.reload.isEnabled=false;shell.showRecovery();return
        }
        shell.account.text=account.label.ifBlank {"已绑定京东账号"}
        owner=account
        if(!SessionCookies.restoreOwned(this,"jd",account)) {
            shell.message.text="无法确认该账号的官方会话，请恢复登录。";shell.login.visibility=View.VISIBLE;shell.reload.isEnabled=false;shell.showRecovery();return
        }
        guard=IdentitySessionPolicy("jd",Store.cookieOf(account.payload))
        if(!verifyOwner())return
        shell.reload.isEnabled=true;shell.message.text="正在加载京东官方页面…";shell.showLoading()
        val page=createPage();web=page;shell.holder.addView(page,android.widget.FrameLayout.LayoutParams(-1,-1))
        shell.holder.visibility=View.VISIBLE;page.loadUrl(IdentityRoutes.JD_URL);handler.post(watch)
    }
    private fun verifyOwner():Boolean {
        val selected=Store.accountById(this,"jd",accountId)
        val expected=owner?.let {Store.cookieOf(it.payload)}.orEmpty()
        val available=selected?.enabled==true && IdentitySessionPolicy.matchesOwner("jd",expected,Store.cookieOf(selected.payload))
        val state=guard?.check(available,SessionCookies.current("jd")) ?: return false
        if(state==IdentitySessionPolicy.State.ACTIVE)return true
        val wasVisible=shell.holder.visibility==View.VISIBLE
        shell.holder.visibility=View.INVISIBLE;if(wasVisible)web?.stopLoading();shell.progress.visibility=View.GONE;shell.reload.isEnabled=false
        shell.login.visibility=View.VISIBLE;shell.showAccountChanged()
        shell.message.text=if(state==IdentitySessionPolicy.State.ACCOUNT_UNAVAILABLE)"账号已移除或暂停，旧码已隐藏。请重新选择账号。" else "账号会话已变化，旧码已隐藏。请重新选择账号或恢复登录。"
        return false
    }
    private fun persistIfOwned() {
        if(guard?.mayPersist==true && verifyOwner())owner?.let {SessionCookies.persistOwned(this,"jd",it)}
    }
    private fun disposePage() {
        handler.removeCallbacks(watch)
        val old=web
        if(old!=null) {
            shell.holder.visibility=View.INVISIBLE;persistIfOwned();web=null
            old.stopLoading();shell.holder.removeView(old);old.destroy()
        }
        guard=null;owner=null
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun createPage()=WebView(this).apply {
        settings.apply {javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false;useWideViewPort=true;loadWithOverviewMode=true}
        CookieManager.getInstance().setAcceptThirdPartyCookies(this,true)
        addOnLayoutChangeListener {view,_,_,_,_,_,_,_,_->
            if(view===web && verifyOwner() && IdentityWebPolicy.allowed(url.orEmpty(),"jd") && !IdentityWebPolicy.loginPage(url.orEmpty(),"jd"))IdentityViewport.fitJd(this)
        }
        webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                if(!request.isForMainFrame)return false
                if(!verifyOwner())return true
                if(IdentityWebPolicy.allowed(request.url.toString(),"jd"))return false
                shell.message.text="该链接不在官方页面范围内，可使用原 App 出示。";return true
            }
            override fun onPageStarted(view:WebView,url:String,icon:android.graphics.Bitmap?) {
                if(view!==web || !verifyOwner())return
                shell.showLoading();shell.message.text="正在加载京东官方页面…"
            }
            override fun onPageFinished(view:WebView,url:String) {
                if(view!==web || !verifyOwner())return
                shell.showReady()
                shell.message.text=if(IdentityWebPolicy.loginPage(url,"jd"))"请在官方页面完成登录，或使用登录 / 恢复。" else "码由京东官方页面实时提供；未显示时可重新加载页面。"
                shell.login.visibility=if(IdentityWebPolicy.loginPage(url,"jd"))View.VISIBLE else View.GONE
                if(IdentityWebPolicy.allowed(url,"jd") && !IdentityWebPolicy.loginPage(url,"jd"))IdentityViewport.fitJd(view)
                persistIfOwned()
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {
                if(request.isForMainFrame && view===web && verifyOwner()){shell.showFailure();shell.message.text="官方页面加载失败，可重新加载页面或使用原 App。"}
            }
            override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) {
                if(request.isForMainFrame && view===web && verifyOwner()){shell.showFailure();shell.message.text="官方页面暂不可用，可重新加载页面或使用原 App。"}
            }
        }
    }
}
