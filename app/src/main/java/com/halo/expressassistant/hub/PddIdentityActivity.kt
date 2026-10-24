package com.halo.expressassistant.hub

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.halo.expressassistant.data.BoundAccount
import com.halo.expressassistant.data.Store
import com.halo.expressassistant.ui.PddLoginActivity
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive

/** Account-owned, in-memory identity with the server's remaining lifetime and no background reuse. */
open class PddIdentityActivity:AppCompatActivity() {
    private lateinit var accountLabel:TextView
    private lateinit var mobile:TextView
    private lateinit var shell:IdentityPageShell
    private lateinit var mode:Button
    private lateinit var qrButton:Button
    private var expanded=false
    private lateinit var remaining:TextView
    private lateinit var auto:TextView
    private lateinit var holder:FrameLayout
    private lateinit var web:WebView
    private var accountId=""
    private var ownerCookies=""
    private var value=""
    private var deadline=0L
    private var qrMode=false
    private var foreground=false
    private var rendererReady=false
    private var authenticating=false
    private var request:Job?=null
    private var generation=0
    private var renewal:Runnable?=null
    private val handler=Handler(Looper.getMainLooper())
    private val changes=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
        if(foreground && key=="pdd_accounts" && ownerCookies.isNotBlank() && !ownsSession())accountChanged()
    }
    private val tick=object:Runnable {override fun run(){
        if(!foreground)return
        if(ownerCookies.isNotBlank() && !ownsSession()){accountChanged();return}
        if(value.isNotBlank()) {
            val ms=deadline-SystemClock.elapsedRealtime()
            if(ms<=0){load();return}
            remaining.text="%02d:%02d 后更新".format((ms/1000)/60,(ms/1000)%60);shell.expandedInfo.text=remaining.text
        }
        handler.postDelayed(this,500)
    }}
    private val login=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        authenticating=false
        if(result.resultCode==RESULT_OK) {
            val confirmed=result.data?.getStringExtra("authorized_account_id").orEmpty()
            if(confirmed.isNotBlank())accountId=confirmed
        }
        if(foreground)load()
    }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        accountId=state?.getString("account_id") ?: intent.getStringExtra("account_id").orEmpty()
        qrMode=state?.getBoolean("qr",false) ?: false
        authenticating=state?.getBoolean("authenticating",false) ?: false
        IdentityViewport.prepareWindow(window)
        buildUi()
    }
    override fun onStart(){super.onStart();foreground=true;getSharedPreferences("express_store",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(changes);if(!authenticating)load()}
    override fun onStop(){foreground=false;getSharedPreferences("express_store",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(changes);invalidateCode();super.onStop()}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);web.stopLoading();holder.removeView(web);web.destroy();super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle){out.putString("account_id",accountId);out.putBoolean("qr",qrMode);out.putBoolean("authenticating",authenticating);super.onSaveInstanceState(out)}
    private fun dp(n:Int)=OfficialFlowUi.dp(this,n)
    private fun text(s:String,size:Float=14f,bold:Boolean=false,color:Int=Color.rgb(32,33,36))=OfficialFlowUi.text(this,s,size,bold,color)
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUi() {
        shell=IdentityPageShell(this,"拼多多身份码",{load()},{IdentityLauncher.openApp(this,"pdd")},{startLogin()},{chooseAccount()})
        accountLabel=shell.account;mobile=shell.accountSubtitle;holder=shell.holder
        shell.onCollapse={expanded=false;shell.setExpanded(false);draw()}
        val tabs=LinearLayout(this).apply {gravity=Gravity.CENTER;setPadding(dp(4),dp(4),dp(4),dp(4));background=OfficialFlowUi.rounded(this@PddIdentityActivity,Color.rgb(222,236,248),13)}
        fun select(qr:Boolean){if(value.isNotBlank() && PddIdentityPayload.valid(deadline,SystemClock.elapsedRealtime()) && ownsSession()){qrMode=qr;updateMode();draw()}}
        mode=OfficialFlowUi.button(this,"条码",false){select(false)}.apply {strokeWidth=0;maxLines=1;isSingleLine=true;isEnabled=false}
        qrButton=OfficialFlowUi.button(this,"二维码",false){select(true)}.apply {strokeWidth=0;maxLines=1;isSingleLine=true;isEnabled=false}
        tabs.addView(mode,LinearLayout.LayoutParams(0,dp(48),1f));tabs.addView(qrButton,LinearLayout.LayoutParams(0,dp(48),1f).apply {leftMargin=dp(4)})
        shell.addAboveCode(tabs)
        val line=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(dp(3),0,dp(3),0)}
        remaining=text("暂未显示身份码",12f,false,HubUi.muted).apply {gravity=Gravity.CENTER_VERTICAL};auto=text("",12f,false,HubUi.muted)
        line.addView(remaining,LinearLayout.LayoutParams(0,dp(30),1f));line.addView(auto);shell.addBelowCode(line)
        web=WebView(this).apply {
            setBackgroundColor(Color.WHITE);visibility=View.INVISIBLE
            settings.apply {javaScriptEnabled=true;domStorageEnabled=false;allowFileAccess=false;allowContentAccess=false;blockNetworkLoads=true;setSupportZoom(false)}
            addJavascriptInterface(object {
                @JavascriptInterface fun expand(){post {if(this@PddIdentityActivity.foreground && value.isNotBlank() && ownsSession() && PddIdentityPayload.valid(deadline,SystemClock.elapsedRealtime())){expanded=true;shell.setExpanded(true);draw()}}}
            },"DoraPddLayout")
            webViewClient=object:WebViewClient(){
                override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=true
                override fun onPageFinished(v:WebView,url:String){rendererReady=true;draw()}
            }
        }
        holder.addView(web,FrameLayout.LayoutParams(-1,-1))
        web.loadDataWithBaseURL(null,PddIdentityRenderer.document(this),"text/html","UTF-8",null)
        updateMode()
    }
    private fun ownsSession():Boolean {
        val current=Store.accountById(this,"pdd",accountId)?.takeIf {it.enabled} ?: return false
        return IdentitySessionPolicy.matchesOwner("pdd",ownerCookies,Store.cookieOf(current.payload))
    }
    private fun invalidateCode() {
        generation++;request?.cancel();request=null;handler.removeCallbacks(tick)
        renewal?.let {handler.removeCallbacks(it)};renewal=null
        value="";deadline=0;mode.isEnabled=false;qrButton.isEnabled=false;expanded=false;shell.setExpanded(false);web.visibility=View.INVISIBLE
        if(rendererReady)web.evaluateJavascript("window.clearIdentity&&window.clearIdentity()",null)
    }
    private fun state(title:String,copy:String,action:String="",onAction:(()->Unit)?=null,loading:Boolean=false) {
        shell.updateOfficialLayout(false,420);shell.showReady()
        holder.childrenToRemove().forEach(holder::removeView)
        val center=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(30),dp(24),dp(30),dp(24))}
        if(loading)center.addView(OfficialFlowUi.spinner(this),LinearLayout.LayoutParams(dp(48),dp(48)))
        else center.addView(ImageView(this).apply {setImageDrawable(OfficialFlowUi.stateArt(title.contains("变化")))},LinearLayout.LayoutParams(dp(64),dp(64)))
        center.addView(text(title,19f,true).apply {gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(22)})
        center.addView(text(copy,14f,false,HubUi.muted).apply {gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
        if(action.isNotBlank() && onAction!=null)center.addView(OfficialFlowUi.button(this,action,true,onAction),LinearLayout.LayoutParams(-1,dp(48)).apply {topMargin=dp(24)})
        holder.addView(center,FrameLayout.LayoutParams(-1,-1));remaining.text=if(loading)"旧码已隐藏" else "暂未显示身份码";auto.text=""
    }
    private fun FrameLayout.childrenToRemove()=(0 until childCount).map {getChildAt(it)}.filter {it!==web}
    private fun accountChanged(){invalidateCode();ownerCookies="";state("当前账号已变化","旧码已隐藏，请重新选择本次取件的账号。","重新选择账号",{chooseAccount()})}
    private fun chooseAccount() {
        invalidateCode();ownerCookies="";state("请选择拼多多账号","选择与本次取件对应的账号。")
        val accounts=Store.accounts(this,"pdd").filter {it.enabled}
        if(accounts.isEmpty()){startLogin();return}
        OfficialAccountSheet.show(this,"pdd",accounts,accountId){accountId=it.id;load()}
    }
    private fun startLogin(){invalidateCode();ownerCookies="";authenticating=true;login.launch(Intent(this,PddLoginActivity::class.java).putExtra("login_account_id",accountId))}
    protected open suspend fun queryIdentity(cookies:String)=PddIdentityService.query(cookies)
    private fun load(renewalAttempt:Int=0) {
        if(!foreground || authenticating)return
        invalidateCode();ownerCookies=""
        val accounts=Store.accounts(this,"pdd").filter {it.enabled}
        if(accountId.isBlank()) {
            if(accounts.size>1){chooseAccount();return}
            accountId=accounts.singleOrNull()?.id.orEmpty()
        }
        val selected=accounts.firstOrNull {it.id==accountId}
        if(selected==null){state("请选择已绑定拼多多账号","请重新选择账号或恢复登录。","登录 / 恢复",{startLogin()});return}
        accountLabel.text=selected.label.ifBlank {"已绑定拼多多账号"};mobile.text="当前出示账号"
        ownerCookies=Store.cookieOf(selected.payload)
        state("正在更新身份码","请稍候，更新后即可出示。",loading=true)
        val version=generation;val started=SystemClock.elapsedRealtime()
        request=lifecycleScope.launch {
            val result=try {queryIdentity(ownerCookies)}catch(e:CancellationException){throw e}catch(_:Exception){PddIdentityPayload.Result(PddIdentityPayload.Outcome.FAILED)}
            if(!foreground || version!=generation)return@launch
            if(!ownsSession()){accountChanged();return@launch}
            when(result.outcome) {
                PddIdentityPayload.Outcome.LOGIN->state("登录状态需要恢复","请恢复当前拼多多账号的登录，完成后即可继续出示身份码。","登录 / 恢复",{startLogin()})
                PddIdentityPayload.Outcome.FAILED->state("暂时无法获取身份码","请检查网络后重试，或使用原 App 出示身份码。","重新获取",{load()})
                PddIdentityPayload.Outcome.EXPIRING->renewAfter(result.lifetimeMs+500,version,renewalAttempt)
                PddIdentityPayload.Outcome.SUCCESS->{
                    deadline=PddIdentityPayload.deadline(started,result.lifetimeMs)
                    if(!PddIdentityPayload.valid(deadline,SystemClock.elapsedRealtime())){renewAfter(1000,version,renewalAttempt);return@launch}
                    value=result.value;mobile.text=result.mobileLabel.ifBlank {"当前出示账号"}
                    holder.childrenToRemove().forEach(holder::removeView);mode.isEnabled=true;qrButton.isEnabled=true;auto.text="到期自动更新";shell.showReady()
                    updateMode();draw();handler.post(tick)
                }
            }
        }
    }
    private fun renewAfter(delayMs:Long,version:Int,attempt:Int) {
        if(attempt>=2){state("暂时无法获取新身份码","旧码已到期，请稍后重新获取。","重新获取",{load()});return}
        state("正在更新身份码","旧码即将到期，正在等待新码。",loading=true)
        renewal=Runnable {renewal=null;if(foreground && generation==version && ownsSession())load(attempt+1)}.also {handler.postDelayed(it,delayMs)}
    }
    private fun updateMode(){
        listOf(mode,qrButton).forEachIndexed {index,button->val selected=(index==1)==qrMode
            button.isSelected=selected;button.setBackgroundColor(if(selected)Color.WHITE else Color.TRANSPARENT)
            button.setTextColor(if(selected)HubUi.accent else HubUi.muted)
        }
        if(!expanded)shell.updateOfficialLayout(false,if(qrMode)520 else 430)
    }
    private fun draw() {
        if(!rendererReady || !foreground || value.isBlank())return
        if(!PddIdentityPayload.valid(deadline,SystemClock.elapsedRealtime()) || !ownsSession()){web.visibility=View.INVISIBLE;return}
        val drawingGeneration=generation
        val drawingValue=value
        web.evaluateJavascript("window.drawIdentity(${JsonPrimitive(value)},$qrMode,$expanded,${JsonPrimitive(mobile.text.toString())})"){if(foreground && generation==drawingGeneration && value==drawingValue && value.isNotBlank() && ownsSession() && PddIdentityPayload.valid(deadline,SystemClock.elapsedRealtime()))web.visibility=View.VISIBLE}
    }
}
