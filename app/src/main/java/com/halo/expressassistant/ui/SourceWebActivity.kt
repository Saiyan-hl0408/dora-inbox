package com.halo.expressassistant.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.halo.expressassistant.data.BoundAccount
import com.halo.expressassistant.data.Store
import com.halo.expressassistant.hub.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

/** An account-owned official page, rather than an arbitrary browser with a shared-cookie fallback. */
class SourceWebActivity:AppCompatActivity() {
    companion object {
        const val EXTRA_URL="url"
        const val EXTRA_SOURCE="source"
        const val EXTRA_TITLE="title"
        const val EXTRA_COOKIES="cookies" // Legacy extra intentionally not used as a session fallback.
        const val EXTRA_ACCOUNT_ID="accountId"
        fun domainsFor(source:String):List<String> = when(source) {
            "jd"->listOf("https://kd.jd.com","https://passport.jd.com","https://trade.m.jd.com","https://wqs.jd.com","https://api.m.jd.com","https://www.jd.com","https://m.jd.com","https://plogin.m.jd.com","https://tuihuan.jd.com","https://afs.m.jd.com","https://convenient-h5.jd.com")
            "taobao"->listOf("https://main.m.taobao.com","https://h5api.m.taobao.com","https://m.taobao.com","https://www.taobao.com","https://pages-fast.m.taobao.com")
            "pdd"->listOf("https://mobile.yangkeduo.com","https://yangkeduo.com")
            "douyin"->listOf("https://www.douyin.com","https://douyin.com","https://login.douyin.com","https://sso.douyin.com")
            else->emptyList()
        }
    }
    private var source=""
    private var accountId=""
    private var initialUrl=""
    private var page:WebView?=null
    private var owner:BoundAccount?=null
    private var session:Job?=null
    private var guard:IdentitySessionPolicy?=null
    private var pageFailed=false
    private lateinit var holder:FrameLayout
    private lateinit var status:TextView
    private lateinit var progress:View
    private lateinit var statePanel:FrameLayout
    private lateinit var stateTitle:TextView
    private lateinit var stateBody:TextView
    private lateinit var stateArt:ImageView
    private lateinit var reload:MaterialButton
    private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        source=intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        accountId=intent.getStringExtra(EXTRA_ACCOUNT_ID).orEmpty()
        initialUrl=intent.getStringExtra(EXTRA_URL).orEmpty()
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE)}
        OfficialFlowUi.insets(this,root)
        val bar=OfficialFlowUi.header(this,"官方页面",onBack={if(page?.canGoBack()==true)page?.goBack() else finish()})
        bar.setBackgroundColor(Color.WHITE)
        val headerActions=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        headerActions.addView(OfficialFlowUi.icon(this,"refresh","重新加载页面"){reloadPage()},LinearLayout.LayoutParams(dp(44),dp(48)))
        headerActions.addView(OfficialFlowUi.icon(this,"close","关闭官方页面"){finish()},LinearLayout.LayoutParams(dp(44),dp(48)))
        bar.addView(headerActions,FrameLayout.LayoutParams(-2,dp(48),Gravity.END or Gravity.CENTER_VERTICAL))
        root.addView(bar)
        root.addView(View(this).apply {setBackgroundColor(Color.rgb(235,241,248))},LinearLayout.LayoutParams(-1,dp(1)))
        val sourceRow=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12))}
        val brand=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;background=OfficialFlowUi.rounded(this@SourceWebActivity,PlatformStyle.tint(source),10);setPadding(dp(8),dp(6),dp(10),dp(6))}
        brand.addView(OfficialFlowUi.badge(this,source,20).apply {textSize=11f})
        brand.addView(OfficialFlowUi.text(this,when(source){"jd"->"京东物流";"pdd"->"拼多多";"taobao"->"淘宝 / 菜鸟";else->"官方页面"},12f,true,PlatformStyle.accent(source)),LinearLayout.LayoutParams(-2,-2).apply {leftMargin=dp(6)})
        sourceRow.addView(brand)
        status=OfficialFlowUi.text(this,"正在加载",13f,false,HubUi.muted).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        sourceRow.addView(status,LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=dp(16)})
        root.addView(sourceRow)
        val content=FrameLayout(this).apply {setPadding(dp(14),0,dp(14),dp(16))}
        holder=FrameLayout(this).apply {setBackgroundColor(Color.WHITE)}
        content.addView(holder,FrameLayout.LayoutParams(-1,-1))
        statePanel=FrameLayout(this).apply {background=OfficialFlowUi.rounded(this@SourceWebActivity,Color.rgb(238,246,255),14)}
        val scroll=ScrollView(this).apply {isFillViewport=true}
        val stateColumn=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;setPadding(dp(20),dp(64),dp(20),dp(30))}
        stateArt=ImageView(this).apply {setImageDrawable(OfficialPageArt())}
        progress=OfficialFlowUi.spinner(this)
        stateTitle=OfficialFlowUi.text(this,"正在加载官方页面",21f,true).apply {gravity=Gravity.CENTER}
        stateBody=OfficialFlowUi.text(this,"请稍候，正在打开当前平台的官方页面。",14f,false,HubUi.muted).apply {gravity=Gravity.CENTER}
        stateColumn.addView(stateArt,LinearLayout.LayoutParams(dp(112),dp(94)).apply {bottomMargin=dp(20)})
        stateColumn.addView(progress,LinearLayout.LayoutParams(dp(44),dp(44)).apply {bottomMargin=dp(24)})
        stateColumn.addView(stateTitle,LinearLayout.LayoutParams(-1,-2))
        stateColumn.addView(stateBody,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(14);bottomMargin=dp(24)})
        reload=OfficialFlowUi.button(this,"重试",true){reloadPage()}
        stateColumn.addView(reload,LinearLayout.LayoutParams(-1,dp(48)))
        stateColumn.addView(OfficialFlowUi.button(this,"返回 App"){finish()}.apply {backgroundTintList=ColorStateList.valueOf(HubUi.paleBlue);strokeWidth=0},LinearLayout.LayoutParams(-1,dp(48)).apply {topMargin=dp(12)})
        scroll.addView(stateColumn);statePanel.addView(scroll,FrameLayout.LayoutParams(-1,-1))
        content.addView(statePanel,FrameLayout.LayoutParams(-1,-1))
        root.addView(content,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        showLoading()
    }
    override fun onStart(){super.onStart();startOwnedPage()}
    private fun validOwner()=Store.accountById(this,source,accountId)?.takeIf {it.enabled}
    private fun block(message:String) {
        page?.stopLoading();page?.visibility=View.INVISIBLE
        status.text=message;progress.visibility=View.GONE;reload.visibility=View.GONE
        showProblem("官方页面暂不可用",message,false)
    }
    private fun startOwnedPage() {
        if(session?.isActive==true)return
        if(!DetailPresentation.allowedOfficialUrl(source,initialUrl)){block("无法打开此官方页面，请返回检查入口");return}
        val selected=validOwner()
        if(accountId.isBlank() || selected==null){block("此页面所属账号不可用，请关闭后恢复登录或重新选择账号");return}
        owner=selected
        status.text="正在准备所选账号的官方页面…";showLoading()
        session=lifecycleScope.launch {
            suspend fun showOwned() {
                var web:WebView?=null
                try {
                    if(!SessionCookies.restoreOwned(this@SourceWebActivity,source,selected)){block("账号会话需要恢复，请关闭后重新登录");return}
                    guard=IdentitySessionPolicy(source,Store.cookieOf(selected.payload))
                    if(!checkOwner())return
                    web=createPage();page=web;holder.addView(web,FrameLayout.LayoutParams(-1,-1))
                    web.loadUrl(initialUrl)
                    while(isActive && !isFinishing){delay(750);if(!checkOwner())break}
                } finally {
                    if(guard?.mayPersist==true)SessionCookies.persistOwned(this@SourceWebActivity,source,selected)
                    web?.stopLoading();web?.let {holder.removeView(it);it.destroy()}
                    if(page===web)page=null
                }
            }
            if(source=="taobao")CainiaoWebFetcher.sessionMutex.withLock {showOwned()} else showOwned()
        }
    }
    private fun checkOwner():Boolean {
        val expected=owner ?: return false
        val current=validOwner()
        val available=current!=null && IdentitySessionPolicy.matchesOwner(source,Store.cookieOf(expected.payload),Store.cookieOf(current.payload))
        val result=guard?.check(available,SessionCookies.current(source))
        if(result!=IdentitySessionPolicy.State.ACTIVE){block("账号会话已变化，已隐藏原页面；请关闭后恢复对应账号");return false}
        return true
    }
    private fun reloadPage() {
        if(!checkOwner())return
        reload.visibility=View.GONE;progress.visibility=View.VISIBLE;showLoading()
        page?.let {web->web.visibility=View.VISIBLE;if(DetailPresentation.allowedOfficialUrl(source,web.url.orEmpty()))web.reload() else web.loadUrl(initialUrl)}
    }
    private fun handle(uri:android.net.Uri):Boolean {
        if(uri.scheme in setOf("http","https")) {
            if(DetailPresentation.allowedOfficialUrl(source,uri.toString()))return false
            status.text="此链接不属于当前平台，请返回官方页面";return true
        }
        return OfficialLoginLinks.open(this,source,uri){status.text=it}
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun createPage()=WebView(this).apply {
        settings.apply {
            javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false
            setSupportMultipleWindows(true);mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString="Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this,true)
        webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=if(request.isForMainFrame)handle(request.url) else false
            override fun onPageStarted(view:WebView,url:String,favicon:android.graphics.Bitmap?) {
                pageFailed=false
                if(!DetailPresentation.allowedOfficialUrl(source,url)){block("官方页面已转到其他入口，请返回重试");return}
                if(checkOwner()){status.text="正在加载官方页面…";this@SourceWebActivity.progress.visibility=View.VISIBLE;reload.visibility=View.GONE;showLoading()}
            }
            override fun onPageFinished(view:WebView,url:String) {
                if(!checkOwner() || pageFailed)return
                if(!DetailPresentation.allowedOfficialUrl(source,url))return
                this@SourceWebActivity.progress.visibility=View.GONE;statePanel.visibility=View.GONE
                status.text="官方页面 · 请核对页面中的账号与物流信息"
                owner?.let {SessionCookies.persistOwned(this@SourceWebActivity,source,it)}
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {
                if(request.isForMainFrame)failure()
            }
            override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) {
                if(request.isForMainFrame)failure()
            }
        }
        webChromeClient=object:WebChromeClient() {
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:android.os.Message)=
                LoginPopup.forward(view,isUserGesture,resultMsg){uri->handle(uri)}
        }
    }
    private fun failure(){pageFailed=true;showProblem("官方页面加载失败","当前网络可能不稳定，请检查网络后重试，或返回 App。已有包裹记录保留。",true)}
    private fun showLoading() {
        status.text="正在加载";statePanel.visibility=View.VISIBLE;statePanel.bringToFront()
        stateArt.visibility=View.GONE;progress.visibility=View.VISIBLE;reload.visibility=View.GONE
        stateTitle.text="正在加载官方页面";stateBody.text="请稍候，正在打开当前平台的官方页面。"
    }
    private fun showProblem(title:String,message:String,canRetry:Boolean) {
        status.text="加载未完成";statePanel.visibility=View.VISIBLE;statePanel.bringToFront()
        stateArt.visibility=View.VISIBLE;progress.visibility=View.GONE;reload.visibility=if(canRetry)View.VISIBLE else View.GONE
        stateTitle.text=title;stateBody.text=message
    }
    private fun icon(name:String,description:String,action:()->Unit)=ImageView(this).apply {
        if(name=="close"){setImageResource(com.halo.expressassistant.R.drawable.ic_close);setColorFilter(HubUi.ink)} else setImageDrawable(HubIcon(name,HubUi.ink,dp(22)))
        setPadding(dp(12),dp(12),dp(12),dp(12))
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(48));contentDescription=description
        isClickable=true;isFocusable=true;setOnClickListener {action()}
    }
    override fun onBackPressed(){if(page?.canGoBack()==true)page?.goBack() else super.onBackPressed()}
    override fun onStop(){session?.cancel();super.onStop()}
}
