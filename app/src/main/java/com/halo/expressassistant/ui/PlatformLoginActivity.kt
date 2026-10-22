package com.halo.expressassistant.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.view.*
import android.webkit.*
import android.widget.*
import com.halo.expressassistant.data.Store
import com.halo.expressassistant.hub.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Reuse each platform's session. A native App launch alone never means authorization succeeded. */
open class PlatformLoginActivity(private val channel:String):Activity() {
    protected lateinit var web:WebView
    protected lateinit var state:TextView
    protected open val sessionReady:Boolean get()=true
    protected open val independentlyVerifiedLanding:Boolean get()=false
    protected open fun createLoginWebView():WebView=WebView(this)
    protected open fun configureLoginWebView(view:WebView) {}
    protected open fun addPlatformTop(root:LinearLayout) {}
    protected open fun addPlatformActions(root:LinearLayout) {}
    protected lateinit var officialPageFrame:FrameLayout
    private var officialCard:LinearLayout?=null
    private var loginScroll:ScrollView?=null
    private var loginBody:LinearLayout?=null
    private var orderCapability:TextView?=null
    private var capabilitySummary:TextView?=null
    private var pageOverlay:LinearLayout?=null
    private var cainiaoCapability:TextView?=null
    private var completionAlternative:Button?=null
    protected open fun onOfficialPage(view:WebView,url:String,isOfficial:Boolean,isLanding:Boolean) {}
    protected open fun onManualLoginCheck() {}
    protected open fun onBindingBack() {finish()}
    protected open fun onSessionObserved(hasCredentials:Boolean) {}
    protected open val requiresExpandedOfficialPage:Boolean get()=false
    protected open val compactOfficialPageHeight:Int get()=222
    protected open fun initialLoginUrl(defaultUrl:String):String=defaultUrl
    protected open fun prepareSession(action:()->Unit){action()}
    protected open fun saveAuthorizedSession(cookies:String):Intent {
        when(channel){"jd"->Store.addJdAccount(this,cookies);"taobao"->Store.addTbAccount(this,cookies);else->Store.addPddAccount(this,cookies)}
        return Intent().putExtra("bound_channel",channel)
    }
    protected open fun capabilityStatusText():String = if(channel=="taobao")"淘宝商城订单：待验证\n菜鸟取件服务：待验证\n\n两项共用淘宝账号会话，分别检查可用能力。"
        else if(channel=="jd")"检查登录状态与官网「我收」收件列表是否可读取，检查通过后保存账号。"
        else "在原 App 扫码授权后返回，检查登录与订单页是否可读取，检查通过后保存账号。"
    /** A successful order binding remains usable if optional station authorization is deferred. */
    protected fun offerOrdersOnlyCompletion(action:()->Unit) {
        if(completionAlternative!=null)return
        val body=loginBody ?: return
        completionAlternative=OfficialFlowUi.button(this,"稍后授权菜鸟，先完成绑定") {action()}.also {
            body.addView(it,LinearLayout.LayoutParams(-1,dp(44)).apply {topMargin=dp(8)})
        }
    }
    protected open val loginHelpText:String get()="在官方页面完成登录，已有会话会优先恢复。登录信息加密保存在本机。平台要求验证时，请在官方页面完成后点检查登录。"
    protected fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    protected open fun hasCredentials(keys:Map<String,String>):Boolean=when(channel) {
        "jd"->!keys["pt_key"].isNullOrBlank() && !keys["pt_pin"].isNullOrBlank()
        "taobao"->!keys["cookie2"].isNullOrBlank() && (!keys["unb"].isNullOrBlank() || !keys["cookie1"].isNullOrBlank())
        else->PddLoginSession.hasCredentials(keys)
    }
    protected fun checkLoginSession() {poll.run()}
    private val handler=Handler(Looper.getMainLooper())
    private var sawLanding=false
    private var selectedAccount:com.halo.expressassistant.data.BoundAccount?=null
    private var sessionPrepared=false
    private lateinit var loading:ProgressBar
    private var pageLoading=false
    private var pageFailed=false
    private val revealLoading=Runnable {if(pageLoading)loading.visibility=View.VISIBLE}
    private val title get()=HubData.channels[channel].orEmpty()
    private val officialHost get()=when(channel){"jd"->"jd.com";"taobao"->"taobao.com";else->"yangkeduo.com"}
    private val loginUrl get()=when(channel) {
        "jd"->JdLogisticsSource.LOGIN_URL
        "taobao"->"https://login.m.taobao.com/login.htm?redirectURL=https%3A%2F%2Fmy.m.taobao.com%2FmyTaobao.htm"
        else->"https://mobile.yangkeduo.com/login.html"
    }
    private val poll=object:Runnable {
        override fun run() {
            if(isFinishing || isDestroyed || !sessionPrepared)return
            handler.removeCallbacks(this)
            val cookies=SessionCookies.current(channel)
            val keys=CookiePairs.parse(cookies)
            val credential=hasCredentials(keys)
            onSessionObserved(credential)
            if(credential && (sawLanding || independentlyVerifiedLanding) && sessionReady) {
                val result=saveAuthorizedSession(cookies)
                Store.accounts(this@PlatformLoginActivity,channel).firstOrNull {
                    it.enabled && IdentitySessionPolicy.matchesOwner(channel,Store.cookieOf(it.payload),cookies)
                }?.let {SessionCookies.persistOwned(this@PlatformLoginActivity,channel,it)}
                Toast.makeText(this@PlatformLoginActivity,"$title 登录会话已保存",Toast.LENGTH_LONG).show()
                setResult(RESULT_OK,result);finish();return
            }
            handler.postDelayed(this,1500)
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState:Bundle?) {
        Themes.apply(this);super.onCreate(savedInstanceState)
        val requested=intent.getStringExtra("login_account_id").orEmpty()
        selectedAccount=if(requested.isNotBlank())Store.accountById(this,channel,requested)?.takeIf {it.enabled}
            else if(!intent.getBooleanExtra("freshLogin",false))Store.firstEnabledAccount(this,channel) else null
        if(requested.isNotBlank() && selectedAccount==null){Toast.makeText(this,"所选账号已停用或移除，请重新选择",Toast.LENGTH_LONG).show();setResult(RESULT_CANCELED);finish();return}
        WebView.setWebContentsDebuggingEnabled(false)
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;background=HubUi.background()}
        WindowCompat.setDecorFitsSystemWindows(window,false)
        ViewCompat.setOnApplyWindowInsetsListener(root) {view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets
        }
        val heading=OfficialFlowUi.header(this,"绑定"+title+"账号",onBack={onBindingBack()}).apply {minimumHeight=dp(64)}
        (heading.getChildAt(0) as? TextView)?.apply {
            textSize=18.2f;gravity=Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams=FrameLayout.LayoutParams(-1,dp(64)).apply {leftMargin=dp(64);rightMargin=dp(98)}
        }
        val check=OfficialFlowUi.text(this,"检查登录",12.5f,true,HubUi.accent).apply {
            gravity=Gravity.CENTER;minimumHeight=dp(44);setPadding(dp(6),0,dp(6),0);isClickable=true;isFocusable=true
            setOnClickListener {onManualLoginCheck();poll.run()}
        }
        heading.addView(check,FrameLayout.LayoutParams(dp(86),dp(48),Gravity.END or Gravity.CENTER_VERTICAL).apply {rightMargin=dp(12)})
        root.addView(heading)
        val scroll=ScrollView(this).apply {isFillViewport=true;clipToPadding=false}
        val body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(4),dp(12),dp(16))}
        loginScroll=scroll;loginBody=body
        scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        state=OfficialFlowUi.text(this,"正在恢复登录状态…",13f,false,HubUi.muted).apply {
            isClickable=true;isFocusable=true;contentDescription="当前登录状态，点按查看完整提示"
            setOnClickListener {android.app.AlertDialog.Builder(this@PlatformLoginActivity).setTitle("登录状态").setMessage(text).setPositiveButton("关闭",null).show()}
        }
        val statusBar=LinearLayout(this).apply {gravity=Gravity.TOP;setPadding(dp(4),dp(3),dp(4),dp(13))}
        statusBar.tag="binding-status"
        val statusSpinner=OfficialFlowUi.spinner(this)
        val statusInfo=ImageView(this).apply {setImageDrawable(HubIcon("info",HubUi.muted,dp(17)))}
        val statusIcon=FrameLayout(this).apply {addView(statusInfo,FrameLayout.LayoutParams(-1,-1));addView(statusSpinner,FrameLayout.LayoutParams(-1,-1))}
        statusBar.addView(statusIcon,LinearLayout.LayoutParams(dp(17),dp(19)).apply {rightMargin=dp(8)})
        statusBar.addView(state,LinearLayout.LayoutParams(0,-2,1f));body.addView(statusBar)
        state.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                val message=s.toString();val checking=message.contains("正在") || message.contains("等待官网")
                statusSpinner.visibility=if(checking)View.VISIBLE else View.GONE;statusInfo.visibility=if(checking)View.GONE else View.VISIBLE
                capabilitySummary?.text=when {sessionReady->"已通过";checking->"检查中";else->"待检查"}
            }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        val official=OfficialFlowUi.card(this,12)
        officialCard=official
        val brand=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(49);setPadding(dp(3),0,dp(3),dp(12))}
        brand.addView(OfficialFlowUi.text(this,title,if(channel=="pdd")22.4f else 25.2f,true,PlatformStyle.accent(channel)))
        brand.tag="binding-brand"
        brand.addView(OfficialFlowUi.text(this,if(channel=="pdd")"官方扫码授权" else "官方网站",12f,false,HubUi.muted).apply {tag="binding-subtitle"},LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=dp(10)})
        brand.addView(ImageView(this).apply {setImageDrawable(HubIcon("identity",HubUi.muted,dp(17)))},LinearLayout.LayoutParams(dp(17),dp(17)))
        brand.addView(OfficialFlowUi.text(this," 安全登录",11f,false,HubUi.muted));official.addView(brand)
        addPlatformTop(official)
        loading=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {max=100;isIndeterminate=true;visibility=View.GONE;progressTintList=android.content.res.ColorStateList.valueOf(HubUi.accent)}
        official.addView(loading,LinearLayout.LayoutParams(-1,dp(3)))
        officialPageFrame=FrameLayout(this).apply {background=OfficialFlowUi.rounded(this@PlatformLoginActivity,Color.rgb(247,250,253),12);clipToOutline=true}
        official.addView(officialPageFrame,LinearLayout.LayoutParams(-1,0,1f))
        body.addView(official,LinearLayout.LayoutParams(-1,dp(if(channel=="pdd")600 else 590)))
        web=createLoginWebView()
        web.settings.apply {javaScriptEnabled=true;domStorageEnabled=true;databaseEnabled=true;useWideViewPort=true;loadWithOverviewMode=true;allowFileAccess=false;allowContentAccess=false;setSupportMultipleWindows(true)}
        configureLoginWebView(web)
        web.setOnTouchListener {view,event->
            if(channel=="pdd" && requiresExpandedOfficialPage) {
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE->view.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->view.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true)
        val restoring=savedInstanceState?.getBundle("login_web")
        web.webViewClient=object:WebViewClient() {
            override fun onPageStarted(view:WebView,url:String,icon:android.graphics.Bitmap?){sawLanding=false;pageFailed=false;pageLoading=true;showPageOverlay("正在加载官方页面",false);handler.removeCallbacks(revealLoading);loading.visibility=View.GONE;handler.postDelayed(revealLoading,350)}
            override fun onPageFinished(view:WebView,url:String) {
                if(pageFailed){pageLoading=false;handler.removeCallbacks(revealLoading);loading.visibility=View.GONE;showPageOverlay("官方页面暂时无法打开",true);return}
                pageLoading=false;handler.removeCallbacks(revealLoading);loading.visibility=View.GONE;pageOverlay?.visibility=View.GONE
                val uri=Uri.parse(url);val host=uri.host.orEmpty();val path=uri.path.orEmpty().lowercase()
                val isOfficial=host==officialHost || host.endsWith(".$officialHost")
                sawLanding=isOfficial && !host.startsWith("login.") && !host.startsWith("plogin.") && !path.contains("login") && !path.contains("passport")
                state.text=if(sawLanding)"官方页面已打开，正在检查登录会话…" else "请在官方页面完成登录，完成后检查登录状态"
                CookieManager.getInstance().flush()
                onOfficialPage(view,url,isOfficial,sawLanding)
                poll.run()
            }
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=handleLink(request.url)
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {
                if(request.isForMainFrame) {
                    pageFailed=true
                    pageLoading=false;handler.removeCallbacks(revealLoading);loading.visibility=View.GONE
                    state.text="官方页面加载失败，请检查网络后重新加载。";showPageOverlay("官方页面暂时无法打开",true)
                }
            }
            override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) {
                if(request.isForMainFrame) {
                    pageFailed=true
                    pageLoading=false;handler.removeCallbacks(revealLoading);loading.visibility=View.GONE
                    state.text="官方页面暂时不可用（${response.statusCode}），请稍后重新加载。";showPageOverlay("官方页面暂时不可用",true)
                }
            }
            @Suppress("DEPRECATION") override fun shouldOverrideUrlLoading(view:WebView,url:String)=handleLink(Uri.parse(url))
        }
        web.webChromeClient=object:WebChromeClient(){
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message)=LoginPopup.forward(web,isUserGesture,resultMsg,::handleLink)
            override fun onProgressChanged(view:WebView,value:Int){loading.isIndeterminate=false;loading.progress=value;if(value==100){pageLoading=false;handler.removeCallbacks(revealLoading);loading.visibility=View.GONE}}
        }
        officialPageFrame.addView(web,FrameLayout.LayoutParams(-1,-1))
        addPlatformActions(official)
        val capability=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(48);setPadding(dp(13),dp(10),dp(13),dp(10));background=OfficialFlowUi.rounded(this@PlatformLoginActivity,Color.WHITE,13)}
        capability.tag="binding-capability"
        capability.addView(ImageView(this).apply {setImageDrawable(HubIcon(if(channel=="jd")"truck" else "identity",HubUi.muted,dp(20)))},LinearLayout.LayoutParams(dp(20),dp(20)).apply {rightMargin=dp(8)})
        capability.addView(OfficialFlowUi.text(this,when(channel){"jd"->"收件列表验证";"pdd"->"订单页验证";else->"授权与能力状态"},12.7f),LinearLayout.LayoutParams(0,-2,1f))
        capabilitySummary=OfficialFlowUi.text(this,"待检查",11.5f,false,HubUi.muted);capability.addView(capabilitySummary)
        capability.addView(ImageView(this).apply {setImageDrawable(HubIcon("chevron",HubUi.muted,dp(15)))},LinearLayout.LayoutParams(dp(15),dp(18)).apply {leftMargin=dp(8)})
        body.addView(capability,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12)})
        capability.isClickable=true;capability.isFocusable=true;capability.setOnClickListener {
            val message=capabilityStatusText()
            android.app.AlertDialog.Builder(this).setTitle(if(channel=="taobao")"授权与能力状态" else "登录验证").setMessage(message).setPositiveButton("知道了",null).show()
        }
        val hint=OfficialFlowUi.text(this,when(channel){"jd"->"同步「我收」中本人及关联账号的快递。";"pdd"->"授权完成后返回，登录状态会自动检查。";else->"商城订单读取与菜鸟服务分别验证。"},11.5f,false,HubUi.muted)
        hint.tag="binding-hint"
        body.addView(hint,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(14);leftMargin=dp(6);rightMargin=dp(6)})
        body.addView(OfficialFlowUi.text(this,"登录帮助 ›",12f,false,HubUi.accent).apply {tag="binding-help";minimumHeight=dp(44);gravity=Gravity.CENTER_VERTICAL;isClickable=true;isFocusable=true;setOnClickListener {android.app.AlertDialog.Builder(this@PlatformLoginActivity).setTitle("$title 登录帮助").setMessage(loginHelpText).setPositiveButton("知道了",null).show()}},LinearLayout.LayoutParams(-1,-2).apply {leftMargin=dp(6)})
        setContentView(root)
        scroll.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->fitOfficialPage()}
        body.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->fitOfficialPage()}
        handler.postDelayed(poll,1500)
        prepareSession {
            if(intent.getBooleanExtra("freshLogin",false)) {
                selectedAccount=null
                if(restoring==null) {
                    SessionCookies.clearChannel(channel)
                    SessionCookies.domains(channel).forEach {WebStorage.getInstance().deleteOrigin(it)}
                }
            } else if(selectedAccount?.let {!SessionCookies.restoreOwned(this,channel,it)}==true) {
                sessionPrepared=false;web.visibility=View.INVISIBLE
                state.text="所选账号的会话无法恢复，请重新登录。已有包裹保留。"
                val retry=OfficialFlowUi.button(this,"重新登录",true){}.apply {
                    text="重新登录";minHeight=dp(48)
                    setOnClickListener {
                        selectedAccount=null;intent.putExtra("freshLogin",true)
                        SessionCookies.clearChannel(channel)
                        SessionCookies.domains(channel).forEach {WebStorage.getInstance().deleteOrigin(it)}
                        sessionPrepared=true;web.visibility=View.VISIBLE;visibility=View.GONE
                        web.loadUrl(loginUrl)
                    }
                }
                body.addView(retry,0,LinearLayout.LayoutParams(-1,dp(48)))
                return@prepareSession
            }
            sessionPrepared=true
            if(restoring==null || web.restoreState(restoring)==null)web.loadUrl(initialLoginUrl(loginUrl))
        }
    }
    private fun showPageOverlay(title:String,failed:Boolean) {
        if(channel=="pdd" || !::officialPageFrame.isInitialized)return
        pageOverlay?.let {officialPageFrame.removeView(it)}
        val content=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=OfficialFlowUi.rounded(this@PlatformLoginActivity,Color.WHITE,9);setPadding(dp(22),dp(22),dp(22),dp(22))}
        if(!failed)content.addView(OfficialFlowUi.spinner(this),LinearLayout.LayoutParams(dp(26),dp(26)).apply {bottomMargin=dp(18)})
        else content.addView(ImageView(this).apply {setImageDrawable(HubIcon("info",HubUi.muted,dp(30)))},LinearLayout.LayoutParams(dp(30),dp(30)).apply {bottomMargin=dp(18)})
        content.addView(OfficialFlowUi.text(this,title,17f,true).apply {gravity=Gravity.CENTER})
        content.addView(OfficialFlowUi.text(this,if(failed)"请检查网络后重新加载" else "请稍候，已有账号信息会优先恢复",13f,false,HubUi.muted).apply {gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12)})
        if(failed)content.addView(OfficialFlowUi.button(this,"重新加载"){web.reload()},LinearLayout.LayoutParams(-1,dp(44)).apply {topMargin=dp(20)})
        officialPageFrame.addView(content,FrameLayout.LayoutParams(-1,-1));pageOverlay=content
    }
    /** Approved large web viewport; compact helper rows may scroll below it. */
    protected fun fitOfficialPage() {
        val scroll=loginScroll ?: return;val official=officialCard ?: return
        if(scroll.height<=0)return
        val verification=requiresExpandedOfficialPage
        if(channel=="pdd") {
            official.findViewWithTag<TextView>("binding-subtitle")?.text=if(verification)"官方验证" else "官方扫码授权"
            (official.findViewWithTag<View>("binding-brand"))?.apply {minimumHeight=dp(if(verification)42 else 49);setPadding(dp(3),0,dp(3),dp(if(verification)10 else 12))}
            listOf("binding-capability","binding-help").forEach {tag->loginBody?.findViewWithTag<View>(tag)?.visibility=if(verification)View.GONE else View.VISIBLE}
            loginBody?.findViewWithTag<TextView>("binding-hint")?.apply {
                text=if(verification)"在白色网页区域内滑动，可查看全部验证内容" else "授权完成后返回，登录状态会自动检查。"
                gravity=if(verification)Gravity.CENTER else Gravity.START
            }
        }
        official.post {
            val ime=ViewCompat.getRootWindowInsets(scroll)?.isVisible(WindowInsetsCompat.Type.ime())==true
            var chrome=official.paddingTop+official.paddingBottom
            for(i in 0 until official.childCount) {val child=official.getChildAt(i);if(child===officialPageFrame || child.visibility==View.GONE)continue;val params=child.layoutParams as? LinearLayout.LayoutParams;chrome+=child.measuredHeight+(params?.topMargin ?: 0)+(params?.bottomMargin ?: 0)}
            val footer=loginBody?.findViewWithTag<View>("binding-hint")
            val footerParams=footer?.layoutParams as? LinearLayout.LayoutParams
            val reserved=(loginBody?.findViewWithTag<View>("binding-status")?.measuredHeight ?: dp(44))+
                (footer?.measuredHeight ?: dp(18))+(footerParams?.topMargin ?: 0)+(footerParams?.bottomMargin ?: 0)+
                (loginBody?.paddingTop ?: 0)+(loginBody?.paddingBottom ?: 0)
            val viewport=when {
                channel=="pdd" && verification->maxOf(dp(240),scroll.height-reserved-chrome)
                channel=="pdd"->dp(compactOfficialPageHeight)
                ime->maxOf(dp(240),(scroll.height*.65f).toInt())
                else->maxOf(dp(420),minOf(dp(512),(scroll.height*.72f).toInt()))
            }
            val params=official.layoutParams as LinearLayout.LayoutParams
            val height=viewport+chrome
            if(params.height!=height){params.height=height;official.layoutParams=params}
        }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        if(::web.isInitialized)outState.putBundle("login_web",Bundle().also {web.saveState(it)})
        super.onSaveInstanceState(outState)
    }
    private fun handleLink(uri:Uri):Boolean {
        return OfficialLoginLinks.open(this,channel,uri,blockTaobaoAppLaunch=channel=="taobao"){state.text=it}
    }
    override fun onResume() {super.onResume();if(::web.isInitialized){web.onResume();handler.removeCallbacks(poll);handler.postDelayed(poll,1000)}}
    override fun onPause() {if(::web.isInitialized)web.onPause();handler.removeCallbacks(poll);super.onPause()}
    @Deprecated("Deprecated in Java") override fun onBackPressed() {if(web.canGoBack())web.goBack() else super.onBackPressed()}
    override fun onDestroy() {handler.removeCallbacks(revealLoading);handler.removeCallbacks(poll);if(::web.isInitialized){selectedAccount?.let {SessionCookies.persistOwned(this,channel,it)};web.stopLoading();web.destroy()};super.onDestroy()}
}
