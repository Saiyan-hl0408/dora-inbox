package com.halo.expressassistant.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.halo.expressassistant.data.*
import com.halo.expressassistant.hub.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.coroutines.resume

/** Official number/consent interactions stay visible and under the explicitly selected account. */
class CainiaoPickupActivity:AppCompatActivity() {
    companion object {const val NUMBER="waybill";const val ACCOUNT="taobaoAccount";const val MANAGE="managePhones";const val DETAILS="showDetails"}
    private var web:WebView?=null
    private lateinit var hint:TextView
    private lateinit var holder:FrameLayout
    private lateinit var progress:ProgressBar
    private lateinit var reload:MaterialButton
    private var number=""
    private var accountId=""
    private var account:BoundAccount?=null
    private var job:Job?=null
    private var filled=false
    private var pageFailed=false
    private var guard:IdentitySessionPolicy?=null
    private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        number=intent.getStringExtra(NUMBER).orEmpty().takeIf {it.matches(Regex("[A-Za-z0-9]{8,32}"))}.orEmpty()
        accountId=intent.getStringExtra(ACCOUNT).orEmpty()
        val managing=intent.getBooleanExtra(MANAGE,false)
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;background=HubUi.background()}
        OfficialFlowUi.insets(this,root)
        root.addView(OfficialFlowUi.header(this,if(managing)"号码管理 / 亲友授权" else "菜鸟取件码核验",onBack={returnToCaller()}))
        val scroll=ScrollView(this).apply {isFillViewport=true;clipToPadding=false}
        val body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(14))}
        scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val selectedLabel=Store.accountById(this,"taobao",accountId)?.label?.ifBlank {"已绑定淘宝账号"} ?: "未选择淘宝账号"
        val parcel=Store.items(this).firstOrNull {it.mailNo.equals(number,true)}
        val context=OfficialFlowUi.card(this,14)
        context.addView(OfficialFlowUi.text(this,if(managing)"当前淘宝账号" else "当前运单",14f,true),LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(10)})
        val summary=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        if(managing)summary.addView(OfficialFlowUi.badge(this,"taobao",44))
        else summary.addView(ImageView(this).apply {setImageDrawable(HubIcon("truck",HubUi.accent,dp(28)));background=OfficialFlowUi.rounded(this@CainiaoPickupActivity,HubUi.paleBlue,12);setPadding(dp(9),dp(9),dp(9),dp(9))},LinearLayout.LayoutParams(dp(48),dp(48)))
        val labels=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        labels.addView(OfficialFlowUi.text(this,if(managing)selectedLabel else number.ifBlank {"未提供运单号"},16f,true))
        if(!managing && parcel?.companyName?.isNotBlank()==true)labels.addView(OfficialFlowUi.text(this,parcel.companyName,12f,false,HubUi.muted))
        summary.addView(labels,LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=dp(12)})
        fun copyNumber() {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("运单号",number))
            Toast.makeText(this,"已复制运单号",Toast.LENGTH_SHORT).show()
        }
        if(number.isNotBlank())summary.addView(OfficialFlowUi.icon(this,"copy","复制运单号"){copyNumber()},LinearLayout.LayoutParams(dp(44),dp(44)))
        context.addView(summary);body.addView(context,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(10)})
        if(!managing)body.addView(OfficialFlowUi.text(this,"查询账号："+selectedLabel,13f,false,HubUi.muted),LinearLayout.LayoutParams(-1,-2).apply {leftMargin=dp(6);bottomMargin=dp(10)})
        hint=OfficialFlowUi.text(this,if(managing)"在官方页面管理授权号码，完成后返回本应用继续查询。" else "请在官方页面确认收件号码，完成后返回继续查询。",13f,false,HubUi.muted)
        body.addView(OfficialFlowUi.info(this,hint),LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(12)})
        val official=OfficialFlowUi.card(this,10)
        val officialTitle=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(dp(6),dp(4),dp(6),dp(10))}
        officialTitle.addView(OfficialFlowUi.text(this,"菜鸟官方页面",14f,true),LinearLayout.LayoutParams(0,-2,1f))
        officialTitle.addView(OfficialFlowUi.text(this,"安全核验",11f,false,HubUi.muted));official.addView(officialTitle)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {isIndeterminate=true;visibility=View.GONE;indeterminateTintList=ColorStateList.valueOf(HubUi.accent)}
        official.addView(progress,LinearLayout.LayoutParams(-1,dp(3)))
        holder=FrameLayout(this).apply {background=OfficialFlowUi.rounded(this@CainiaoPickupActivity,Color.WHITE,12);clipToOutline=true}
        official.addView(holder,LinearLayout.LayoutParams(-1,0,1f))
        val room=((resources.displayMetrics.heightPixels/resources.displayMetrics.density).toInt()-380).coerceIn(280,560)
        body.addView(official,LinearLayout.LayoutParams(-1,dp(room)))
        if(managing)body.addView(OfficialFlowUi.info(this,OfficialFlowUi.text(this,"请按官方页面提示完成授权。\n请先在官方页面完成授权后，再返回本应用。",12f,false,HubUi.muted)),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
        reload=action("重新加载页面"){reloadPage()}.apply {visibility=View.GONE}
        body.addView(reload,LinearLayout.LayoutParams(-1,dp(48)).apply {topMargin=dp(10)})
        val footer=OfficialFlowUi.card(this,14)
        val controls=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        fun addControl(view:View){if(view is TextView)view.textSize=if(!managing && number.isNotBlank())13f else 15f;controls.addView(view,LinearLayout.LayoutParams(0,dp(48),1f).apply {if(controls.childCount>0)leftMargin=dp(8)})}
        if(!managing && number.isNotBlank())addControl(action("复制运单号"){copyNumber()}.apply {icon=HubIcon("copy",HubUi.accent,dp(16));iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START;iconPadding=dp(4);setPadding(dp(6),0,dp(6),0)})
        addControl(action(if(managing)"刷新状态" else "前往官方查询"){reloadPage()})
        addControl(action("完成并返回",true){returnToCaller()})
        footer.addView(controls)
        footer.addView(OfficialFlowUi.text(this,"返回后重新检查，不直接判定授权成功。",11f,false,HubUi.muted).apply {gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8)})
        root.addView(footer);setContentView(root)
        fun fitOfficialRegion() {
            if(scroll.height<=0)return
            body.post {
                var used=body.paddingTop+body.paddingBottom
                for(index in 0 until body.childCount) {
                    val child=body.getChildAt(index)
                    if(child===official || child.visibility==View.GONE)continue
                    val params=child.layoutParams as? LinearLayout.LayoutParams
                    used+=child.measuredHeight+(params?.topMargin ?: 0)+(params?.bottomMargin ?: 0)
                }
                val params=official.layoutParams as LinearLayout.LayoutParams
                val available=(scroll.height-used-params.topMargin-params.bottomMargin).coerceAtLeast(dp(240))
                if(params.height!=available){params.height=available;official.layoutParams=params}
            }
        }
        scroll.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->fitOfficialRegion()}
        body.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->fitOfficialRegion()}
    }
    override fun onStart(){super.onStart();startOwnedPage()}
    private fun block(message:String) {
        web?.stopLoading();web?.visibility=View.INVISIBLE;hint.text=message
        progress.visibility=View.GONE;reload.visibility=View.GONE
    }
    private fun ownerValid():Boolean {
        val expected=account ?: return false
        val current=Store.accountById(this,"taobao",accountId)?.takeIf {it.enabled}
        val available=current!=null && IdentitySessionPolicy.matchesOwner("taobao",Store.cookieOf(expected.payload),Store.cookieOf(current.payload))
        if(guard?.check(available,SessionCookies.current("taobao"))!=IdentitySessionPolicy.State.ACTIVE) {
            block("当前账号已变化或不可用，已隐藏原页面；请返回重新选择对应账号")
            return false
        }
        return true
    }
    private fun startOwnedPage() {
        if(job?.isActive==true)return
        val selected=Store.accountById(this,"taobao",accountId)?.takeIf {it.enabled}
        if(accountId.isBlank() || selected==null){block("请返回选择明确的淘宝 / 菜鸟账号后再核验");return}
        account=selected;hint.text="等待当前淘宝查询结束…"
        job=lifecycleScope.launch {
            CainiaoWebFetcher.sessionMutex.withLock {
                var page:WebView?=null
                try {
                    if(!SessionCookies.restoreOwned(this@CainiaoPickupActivity,"taobao",selected)){block("账号会话需要恢复，请返回对应平台登录");return@withLock}
                    guard=IdentitySessionPolicy("taobao",Store.cookieOf(selected.payload))
                    if(!ownerValid())return@withLock
                    page=createPage();web=page;holder.addView(page,FrameLayout.LayoutParams(-1,-1))
                    page.loadUrl(CainiaoPickupService.pageUrl(this@CainiaoPickupActivity,intent.getBooleanExtra(MANAGE,false)))
                    while(isActive && !isFinishing) {
                        delay(500)
                        if(!ownerValid())break
                        if(number.isBlank() || !CainiaoWebFetcher.official(page.url.orEmpty()))continue
                        page.evaluateJavascript(CainiaoWebFetcher.script(this@CainiaoPickupActivity),null)
                        if(!filled)filled=eval(page,"window.__parcelCainiao.fillSearch("+JsonPrimitive(number)+")")=="true"
                        val raw=eval(page,"JSON.stringify(window.__parcelCainiao.events.splice(0,1))")
                        val event=runCatching {Json.parseToJsonElement(Json.parseToJsonElement(raw).jsonPrimitive.content).jsonArray.firstOrNull()}.getOrNull() ?: continue
                        if(!ownerValid())break
                        val result=CainiaoPickup.read(event.toString(),number).copy(accountId=selected.id)
                        CainiaoPickupService.save(this@CainiaoPickupActivity,number,result,selected.id)
                        hint.text=CainiaoPickup.message(result)
                        if(PickupImport.stage(result)!=null) {
                            synchronized(Store){Store.saveItems(this@CainiaoPickupActivity,PickupImport.reconcile(Store.items(this@CainiaoPickupActivity),result))}
                            if(intent.getBooleanExtra(DETAILS,false))continue
                            persist();setResult(Activity.RESULT_OK,Intent().putExtra("cainiao_returned",true).putExtra("cainiao_waybill",number).putExtra("cainiao_code",result.code));finish()
                        }
                    }
                } finally {
                    persist()
                    page?.stopLoading();page?.let {holder.removeView(it);it.destroy()}
                    if(web===page)web=null
                }
            }
        }
    }
    private fun hintText()=if(number.isBlank())"请在官方页面管理收件号码与授权；返回后重新查询核对。" else
        "请在官方页面完成收件核验；返回后按真实查询结果更新。"
    private fun reloadPage() {
        if(!ownerValid())return
        pageFailed=false;reload.visibility=View.GONE;progress.visibility=View.VISIBLE
        web?.let {it.visibility=View.VISIBLE;it.reload()}
    }
    private fun handle(uri:android.net.Uri):Boolean {
        if(uri.scheme in setOf("http","https")) {
            if(DetailPresentation.allowedOfficialUrl("taobao",uri.toString()))return false
            hint.text="此链接不属于淘宝 / 菜鸟官方页面，请返回核验页";return true
        }
        return OfficialLoginLinks.open(this,"taobao",uri){hint.text=it}
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun createPage()=WebView(this).apply {
        settings.apply {javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false;setSupportMultipleWindows(true);mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW}
        CookieManager.getInstance().setAcceptThirdPartyCookies(this,true)
        webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=if(request.isForMainFrame)handle(request.url) else false
            override fun onPageStarted(view:WebView,url:String,icon:android.graphics.Bitmap?) {
                filled=false;pageFailed=false
                if(!DetailPresentation.allowedOfficialUrl("taobao",url)){block("无法打开此核验页面，请返回重试");return}
                if(ownerValid()){hint.text="正在加载官方页面…";this@CainiaoPickupActivity.progress.visibility=View.VISIBLE;reload.visibility=View.GONE}
            }
            override fun onPageFinished(view:WebView,url:String) {
                if(!ownerValid() || pageFailed)return
                this@CainiaoPickupActivity.progress.visibility=View.GONE;hint.text=hintText()
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame)failure()}
            override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse){if(request.isForMainFrame)failure()}
        }
        webChromeClient=object:WebChromeClient() {
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:android.os.Message)=LoginPopup.forward(view,isUserGesture,resultMsg){uri->handle(uri)}
        }
    }
    private fun failure(){pageFailed=true;hint.text="官方页面加载失败，请检查网络后重新加载。已有记录保留。";progress.visibility=View.GONE;reload.visibility=View.VISIBLE}
    private suspend fun eval(page:WebView,script:String):String=suspendCancellableCoroutine {cont->page.evaluateJavascript(script){if(cont.isActive)cont.resume(it)}}
    private fun persist(){if(guard?.mayPersist==true)account?.let {SessionCookies.persistOwned(this,"taobao",it)}}
    private fun returnToCaller(){persist();setResult(Activity.RESULT_OK,Intent().putExtra("cainiao_returned",true));finish()}
    private fun action(value:String,primary:Boolean=false,click:()->Unit)=OfficialFlowUi.button(this,value,primary,click).apply {textSize=13f;setPadding(dp(6),0,dp(6),0)}
    private fun icon(name:String,description:String,click:()->Unit)=ImageView(this).apply {
        setImageDrawable(HubIcon(name,HubUi.ink,dp(22)));setPadding(dp(12),dp(12),dp(12),dp(12))
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(48));contentDescription=description;isClickable=true;isFocusable=true;setOnClickListener {click()}
    }
    override fun onBackPressed(){returnToCaller()}
    override fun onStop(){job?.cancel();super.onStop()}
}
