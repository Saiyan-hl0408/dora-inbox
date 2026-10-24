package com.halo.expressassistant.hub

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.halo.expressassistant.data.BoundAccount

/** Shared approved chrome. Official JD/Cainiao pages retain the actual code and handlers. */
internal class IdentityPageShell(
    private val activity:AppCompatActivity,title:String,private val onReload:()->Unit,
    private val onNative:()->Unit,private val onLogin:()->Unit,private val onSwitch:()->Unit
) {
    private fun dp(n:Int)=OfficialFlowUi.dp(activity,n)
    private val channel=when {title.startsWith("京东")->"jd";title.startsWith("拼多多")->"pdd";else->"cainiao"}
    private val platform=when(channel){"jd"->"京东";"pdd"->"拼多多";else->"菜鸟"}
    val root=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;background=HubUi.background()}
    private val scroll=ScrollView(activity).apply {isFillViewport=false;clipToPadding=false}
    private val body=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(4),dp(16),dp(12))}
    private val context=LinearLayout(activity).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(dp(2),dp(6),0,dp(16))}
    private val badge=OfficialFlowUi.badge(activity,channel,44)
    val account=OfficialFlowUi.text(activity,"请选择已绑定账号",15f,true).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
    val accountSubtitle=OfficialFlowUi.text(activity,"当前出示账号",12f,false,HubUi.muted)
    val expandedInfo=OfficialFlowUi.text(activity,"自动更新",11f,false,HubUi.muted).apply {visibility=View.GONE;maxLines=1}
    private val names=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;addView(account);addView(accountSubtitle,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(5)})}
    val switch=OfficialFlowUi.button(activity,"切换 ›",false,onSwitch).apply {strokeWidth=0;maxLines=1;isSingleLine=true;minWidth=dp(64);minimumWidth=dp(64);insetLeft=0;insetRight=0;setPadding(dp(6),0,dp(6),0);backgroundTintList=ColorStateList.valueOf(Color.TRANSPARENT)}
    private val official=FrameLayout(activity).apply {background=OfficialFlowUi.rounded(activity,Color.WHITE,23);clipToOutline=true}
    val holder=FrameLayout(activity).apply {setBackgroundColor(Color.WHITE)}
    val message=OfficialFlowUi.text(activity,"${platform}实时身份码",12f,false,HubUi.muted).apply {gravity=Gravity.CENTER;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;compoundDrawablePadding=dp(5)}
    val progress=OfficialFlowUi.spinner(activity).apply {visibility=View.GONE}
    val reload=OfficialFlowUi.button(activity,"重新加载",false,onReload).apply {textSize=14f;cornerRadius=dp(14);icon=HubIcon("refresh",HubUi.accent,dp(18));iconSize=dp(18);iconPadding=dp(7);iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START;isSingleLine=true}
    val login=OfficialFlowUi.button(activity,"登录 / 恢复",true,onLogin).apply {visibility=View.GONE}
    private val native=OfficialFlowUi.button(activity,"${platform} App 出示",false,onNative).apply {textSize=if(channel=="pdd")15f else 14f;cornerRadius=dp(14);strokeWidth=dp(1);strokeColor=ColorStateList.valueOf(Color.rgb(196,223,243));backgroundTintList=ColorStateList.valueOf(Color.rgb(224,239,255));icon=HubIcon("external",HubUi.accent,dp(18));iconSize=dp(18);iconPadding=dp(7);iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START;isSingleLine=true}
    private val footer=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(16))}
    private val footerActions=LinearLayout(activity).apply {gravity=Gravity.CENTER_VERTICAL}
    private val heading=OfficialFlowUi.header(activity,title,onBack={back()})
    private val shrink=OfficialFlowUi.button(activity,"缩小",false){onCollapse()}.apply {strokeWidth=0;textSize=14f;icon=HubIcon("shrink",HubUi.accent,dp(18));iconSize=dp(18);backgroundTintList=ColorStateList.valueOf(HubUi.paleBlue);visibility=View.GONE;minWidth=0}
    private val overlay=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(24),dp(20),dp(24),dp(20));setBackgroundColor(Color.WHITE)}
    private val stateIcon=ImageView(activity)
    private val stateTitle=OfficialFlowUi.text(activity,"正在加载官方页面",19f,true).apply {gravity=Gravity.CENTER}
    private val stateBody=OfficialFlowUi.text(activity,"请稍候",14f,false,HubUi.muted).apply {gravity=Gravity.CENTER}
    private val stateAction=OfficialFlowUi.button(activity,"重新选择账号",true,onSwitch)
    private val stateSecondary=OfficialFlowUi.button(activity,"关闭",false){activity.finish()}
    private val stateNative=OfficialFlowUi.button(activity,"原 App 出示",false,onNative)
    private val extras=mutableListOf<View>()
    private var ready=false
    private var normalHeight=if(channel=="cainiao")450 else 405
    private var expandedVersion=0
    var isExpanded=false;private set
    var onCollapse:()->Unit={setExpanded(false)}
    init {
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));activity.window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        activity.window.decorView.setPadding(0,0,0,0);activity.window.setLayout(-1,-1)
        OfficialFlowUi.insets(activity,root)
        heading.addView(shrink,FrameLayout.LayoutParams(dp(86),dp(48),Gravity.END or Gravity.CENTER_VERTICAL).apply {rightMargin=dp(8)})
        root.addView(heading)
        context.addView(badge,LinearLayout.LayoutParams(dp(44),dp(44)))
        context.addView(names,LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=dp(12)})
        context.addView(switch,LinearLayout.LayoutParams(-2,dp(48)));context.addView(expandedInfo)
        body.addView(context)
        official.addView(holder,FrameLayout.LayoutParams(-1,-1))
        overlay.addView(progress,LinearLayout.LayoutParams(dp(48),dp(48)).apply {bottomMargin=dp(22)})
        overlay.addView(stateIcon,LinearLayout.LayoutParams(dp(64),dp(64)).apply {bottomMargin=dp(20)})
        overlay.addView(stateTitle,LinearLayout.LayoutParams(-1,-2));overlay.addView(stateBody,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12);bottomMargin=dp(16)})
        listOf(login,stateAction,stateNative,stateSecondary).forEach {overlay.addView(it,LinearLayout.LayoutParams(-1,dp(48)).apply {topMargin=dp(8)})}
        official.addView(overlay,FrameLayout.LayoutParams(-1,-1));body.addView(official,LinearLayout.LayoutParams(-1,dp(normalHeight)))
        scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        footer.addView(message,LinearLayout.LayoutParams(-1,dp(28)).apply {bottomMargin=dp(8)})
        footerActions.addView(reload,LinearLayout.LayoutParams(dp(128),dp(50)).apply {rightMargin=dp(10)})
        footerActions.addView(native,LinearLayout.LayoutParams(0,dp(50),1f))
        footer.addView(footerActions,LinearLayout.LayoutParams(-1,dp(50)))
        root.addView(footer);activity.setContentView(root)
        scroll.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->fitHeight()}
        activity.onBackPressedDispatcher.addCallback(activity,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){back()}})
        showLoading()
    }
    private fun back(){
        if(!isExpanded){activity.finish();return}
        val version=expandedVersion
        onCollapse()
        // A changed official DOM must never trap the user in an enlarged page.
        root.postDelayed({if(isExpanded && expandedVersion==version && !activity.isFinishing && !activity.isDestroyed)activity.finish()},700)
    }
    fun addAboveCode(view:View){extras.add(view);body.addView(view,body.indexOfChild(official),LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(12)})}
    fun addBelowCode(view:View){extras.add(view);body.addView(view,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})}
    fun updateOfficialLayout(expanded:Boolean,heightCss:Int){normalHeight=heightCss.coerceIn(240,720);setExpanded(expanded);fitHeight()}
    fun setExpanded(value:Boolean) {
        if(isExpanded!=value)expandedVersion++
        isExpanded=value
        root.background=if(value)ColorDrawable(Color.WHITE) else HubUi.background()
        shrink.visibility=if(value)View.VISIBLE else View.GONE
        heading.getChildAt(1).visibility=if(value)View.GONE else View.VISIBLE
        (heading.getChildAt(0) as? TextView)?.apply {gravity=if(value)Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            (layoutParams as FrameLayout.LayoutParams).also {it.leftMargin=dp(if(value)20 else 52);it.rightMargin=dp(if(value)100 else 52);layoutParams=it}}
        switch.visibility=if(value)View.GONE else View.VISIBLE;accountSubtitle.visibility=if(value)View.GONE else View.VISIBLE
        account.textSize=if(value)13f else 15f
        val badgeSize=dp(if(value)26 else 44);badge.layoutParams=LinearLayout.LayoutParams(badgeSize,badgeSize);badge.textSize=if(value)16f else 26f
        context.setPadding(dp(2),dp(6),0,dp(if(value)12 else 16));expandedInfo.visibility=if(value)View.VISIBLE else View.GONE
        extras.forEach {it.visibility=if(value)View.GONE else View.VISIBLE}
        official.background=if(value)ColorDrawable(Color.WHITE) else OfficialFlowUi.rounded(activity,Color.WHITE,23)
        message.visibility=if(channel=="pdd" && !value)View.GONE else View.VISIBLE
        if(value)message.text="请勿随意分享身份码" else if(ready)message.text="${platform}实时身份码"
        message.setCompoundDrawablesWithIntrinsicBounds(if(value)null else HubIcon("circle-check",HubUi.muted,dp(15)),null,null,null)
        footerActions.visibility=if(ready && !value)View.VISIBLE else View.GONE
        reload.visibility=if(ready && !value && channel!="pdd")View.VISIBLE else View.GONE
        native.visibility=if(ready && !value)View.VISIBLE else View.GONE
        fitHeight()
    }
    private fun fitHeight(){if(scroll.height<=0)return;body.post {
        val height=if(isExpanded)(scroll.height-context.measuredHeight-body.paddingTop-body.paddingBottom).coerceAtLeast(dp(240)) else dp(normalHeight)
        val params=official.layoutParams as LinearLayout.LayoutParams
        if(params.height!=height){params.height=height;official.layoutParams=params}
    }}
    private fun reset(){ready=false;setExpanded(false);listOf(login,stateAction,stateSecondary,stateNative,progress,stateIcon).forEach {it.visibility=View.GONE};overlay.visibility=View.VISIBLE;overlay.bringToFront();message.visibility=View.GONE;native.visibility=View.GONE;reload.visibility=View.GONE;normalHeight=420;fitHeight()}
    fun showLoading(){reset();progress.visibility=View.VISIBLE;stateTitle.text="正在加载${platform}身份码";stateBody.text="请稍候，正在打开官方服务"}
    fun showReady(){ready=true;overlay.visibility=View.GONE;progress.visibility=View.GONE;setExpanded(isExpanded)}
    fun showRecovery(){reset();stateIcon.visibility=View.VISIBLE;stateIcon.setImageDrawable(OfficialFlowUi.stateArt());stateTitle.text="登录状态需要恢复";stateBody.text="请先恢复登录，再按官方页面提示完成核验。";login.visibility=View.VISIBLE;stateNative.visibility=View.VISIBLE}
    fun showAccountChanged(){reset();stateIcon.visibility=View.VISIBLE;stateIcon.setImageDrawable(OfficialFlowUi.stateArt(true));stateTitle.text="当前账号已变化";stateBody.text="旧码已隐藏，请重新选择账号后出示。";stateAction.text="重新选择账号";stateAction.setOnClickListener {onSwitch()};stateAction.visibility=View.VISIBLE;stateSecondary.visibility=View.VISIBLE}
    fun showFailure(){reset();stateIcon.visibility=View.VISIBLE;stateIcon.setImageDrawable(HubIcon("info",HubUi.muted,dp(56)));stateTitle.text="官方页面暂未加载";stateBody.text="请检查网络后重新加载页面，或使用原 App 出示。";stateAction.text="重新加载页面";stateAction.setOnClickListener {onReload()};stateAction.visibility=View.VISIBLE;stateNative.visibility=View.VISIBLE}
    fun chooseAccount(accounts:List<BoundAccount>,selectedId:String,onSelected:(BoundAccount)->Unit){OfficialAccountSheet.show(activity,if(channel=="cainiao")"taobao" else channel,accounts,selectedId,onSelected)}
}
