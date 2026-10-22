package com.halo.expressassistant.hub

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.halo.expressassistant.data.BoundAccount

/** Full-page app chrome. The official page alone owns the live identity code. */
internal class IdentityPageShell(
    private val activity: AppCompatActivity,
    title: String,
    private val onReload: () -> Unit,
    private val onNative: () -> Unit,
    private val onLogin: () -> Unit,
    private val onSwitch: () -> Unit
) {
    private fun dp(n:Int)=OfficialFlowUi.dp(activity,n)
    private val channel=if(title.startsWith("京东"))"jd" else "cainiao"
    val root=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;background=HubUi.background()}
    private val scroll=ScrollView(activity).apply {isFillViewport=true;clipToPadding=false}
    private val body=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(16))}
    private val context=LinearLayout(activity).apply {gravity=Gravity.CENTER_VERTICAL;background=OfficialFlowUi.rounded(activity);setPadding(dp(12),dp(10),dp(8),dp(10))}
    private val contextBadge=OfficialFlowUi.badge(activity,channel,48)
    private val contextUser=ImageView(activity).apply {setImageDrawable(HubIcon("user",HubUi.muted,dp(26)));visibility=View.GONE}
    private val stateBrand=LinearLayout(activity).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(12),0,dp(14));visibility=View.GONE}
    private val official=FrameLayout(activity).apply {background=OfficialFlowUi.rounded(activity);clipToOutline=true;setPadding(dp(12),dp(12),dp(12),dp(12))}
    private val heading=OfficialFlowUi.header(activity,title,true)
    private val stateReload=OfficialFlowUi.icon(activity,"refresh","重新加载官方页面",onReload).apply {visibility=View.GONE}
    val account=OfficialFlowUi.text(activity,"请选择已绑定账号",15f,true)
    val message=OfficialFlowUi.text(activity,"页面由官方实时提供。",13f,false,HubUi.muted)
    val holder=FrameLayout(activity).apply {setBackgroundColor(Color.WHITE)}
    val progress=OfficialFlowUi.spinner(activity).apply {visibility=View.GONE}
    val reload=OfficialFlowUi.button(activity,"重新加载页面",true,onReload)
    val login=OfficialFlowUi.button(activity,"登录 / 恢复",true,onLogin).apply {visibility=View.GONE}
    val switch=OfficialFlowUi.button(activity,"切换 ›",false,onSwitch).apply {strokeWidth=0;setPadding(dp(6),0,dp(6),0)}
    private val native=OfficialFlowUi.button(activity,"原 App 出示",false,onNative).apply {icon=HubIcon("external",HubUi.accent,dp(20));iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START}
    private val overlay=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(18),dp(26),dp(18),dp(26));background=OfficialFlowUi.rounded(activity,Color.rgb(245,250,255),14)}
    private val stateIcon=ImageView(activity)
    private val stateTitle=OfficialFlowUi.text(activity,"正在加载官方页面",19f,true).apply {gravity=Gravity.CENTER}
    private val stateBody=OfficialFlowUi.text(activity,"请稍候，正在打开官方页面",14f,false,HubUi.muted).apply {gravity=Gravity.CENTER}
    private val stateAction=OfficialFlowUi.button(activity,"重新选择账号",true,onSwitch)
    private val stateSecondary=OfficialFlowUi.button(activity,"关闭",false){activity.finish()}
    private val stateNative=OfficialFlowUi.button(activity,"原 App 出示",false,onNative)
    init {
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        activity.window.decorView.setPadding(0,0,0,0)
        activity.window.setLayout(-1,-1)
        OfficialFlowUi.insets(activity,root)
        heading.addView(stateReload,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.END or Gravity.CENTER_VERTICAL))
        root.addView(heading)
        scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        stateBrand.addView(OfficialFlowUi.badge(activity,channel,52))
        val brandLabels=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL}
        brandLabels.addView(OfficialFlowUi.text(activity,title,18f,true))
        brandLabels.addView(OfficialFlowUi.text(activity,"通过官方页面出示",13f,false,HubUi.muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(5)})
        stateBrand.addView(brandLabels,LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=dp(14)})
        body.addView(stateBrand)
        context.addView(contextBadge)
        context.addView(contextUser,LinearLayout.LayoutParams(dp(28),dp(34)))
        context.addView(account,LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=dp(14)})
        context.addView(switch,LinearLayout.LayoutParams(dp(64),dp(44)))
        body.addView(context,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(12)})
        official.addView(holder,FrameLayout.LayoutParams(-1,-1))
        overlay.addView(progress,LinearLayout.LayoutParams(dp(54),dp(54)).apply {bottomMargin=dp(22)})
        overlay.addView(stateIcon,LinearLayout.LayoutParams(dp(80),dp(80)).apply {bottomMargin=dp(20)})
        overlay.addView(stateTitle,LinearLayout.LayoutParams(-1,-2))
        overlay.addView(stateBody,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12);bottomMargin=dp(20)})
        listOf(login,stateAction,stateNative,stateSecondary).forEach {overlay.addView(it,LinearLayout.LayoutParams(-1,dp(48)).apply {topMargin=dp(8)})}
        official.addView(overlay,FrameLayout.LayoutParams(-1,-1))
        val room=(activity.resources.displayMetrics.heightPixels/activity.resources.displayMetrics.density).toInt()-380
        body.addView(official,LinearLayout.LayoutParams(-1,dp(room.coerceIn(300,460))))
        body.addView(OfficialFlowUi.info(activity,message),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12)})
        reload.icon=HubIcon("refresh",Color.WHITE,dp(22));reload.iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START
        body.addView(reload,LinearLayout.LayoutParams(-1,dp(50)).apply {topMargin=dp(12)})
        body.addView(native,LinearLayout.LayoutParams(-1,dp(50)).apply {topMargin=dp(10)})
        activity.setContentView(root)
        scroll.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->fitOfficialRegion()}
        body.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->fitOfficialRegion()}
        showLoading()
    }
    private fun fitOfficialRegion() {
        if(scroll.height<=0)return
        body.post {
            var used=body.paddingTop+body.paddingBottom
            for(index in 0 until body.childCount) {
                val child=body.getChildAt(index)
                if(child.visibility==View.GONE || child===official)continue
                val params=child.layoutParams as? LinearLayout.LayoutParams
                used+=child.measuredHeight+(params?.topMargin ?: 0)+(params?.bottomMargin ?: 0)
            }
            val params=official.layoutParams as LinearLayout.LayoutParams
            val height=(scroll.height-scroll.paddingTop-scroll.paddingBottom-used-params.topMargin-params.bottomMargin).coerceAtLeast(dp(240))
            if(params.height!=height){params.height=height;official.layoutParams=params}
        }
    }
    private fun stateChrome(show:Boolean) {
        stateBrand.visibility=if(show)View.VISIBLE else View.GONE
        contextBadge.visibility=if(show)View.GONE else View.VISIBLE
        contextUser.visibility=if(show)View.VISIBLE else View.GONE
        stateReload.visibility=if(show)View.VISIBLE else View.GONE
        (heading.getChildAt(1) as? ImageView)?.apply {setImageDrawable(HubIcon(if(show)"back" else "close",HubUi.ink,dp(24)));contentDescription=if(show)"返回" else "关闭"}
        val officialInset=if(show)0 else dp(12)
        official.setPadding(officialInset,officialInset,officialInset,officialInset)
        scroll.setPadding(if(show)dp(14) else 0,if(show)dp(8) else 0,if(show)dp(14) else 0,if(show)dp(14) else 0)
        body.setPadding(if(show)dp(12) else dp(14),if(show)0 else dp(8),if(show)dp(12) else dp(14),if(show)dp(12) else dp(16))
        context.background=OfficialFlowUi.rounded(activity,if(show)HubUi.paleBlue else Color.WHITE,14)
        switch.backgroundTintList=android.content.res.ColorStateList.valueOf(if(show)Color.rgb(225,241,255) else Color.WHITE)
        switch.text=if(show)"切换" else "切换 ›"
        body.background=if(show)OfficialFlowUi.rounded(activity) else null
        stateNative.backgroundTintList=android.content.res.ColorStateList.valueOf(HubUi.paleBlue);stateNative.strokeWidth=0
        stateSecondary.backgroundTintList=android.content.res.ColorStateList.valueOf(HubUi.paleBlue);stateSecondary.strokeWidth=0
        fitOfficialRegion()
    }
    private fun reset() {
        stateChrome(true)
        stateAction.text="重新选择账号";stateAction.setOnClickListener {onSwitch()}
        listOf(login,stateAction,stateSecondary,stateNative,progress,stateIcon).forEach {it.visibility=View.GONE}
        overlay.visibility=View.VISIBLE;overlay.bringToFront();reload.visibility=View.GONE;native.visibility=View.GONE
        overlay.background=OfficialFlowUi.rounded(activity,Color.rgb(245,250,255),14)
    }
    fun showLoading() {reset();progress.visibility=View.VISIBLE;stateTitle.text="正在加载官方页面";stateBody.text="请稍候，正在打开"+if(channel=="jd")"京东官方页面" else "菜鸟官方页面"}
    fun showReady() {stateChrome(false);overlay.visibility=View.GONE;progress.visibility=View.GONE;reload.visibility=View.VISIBLE;native.visibility=View.VISIBLE;fitOfficialRegion()}
    fun showRecovery() {
        reset();stateIcon.visibility=View.VISIBLE;stateIcon.setImageDrawable(OfficialFlowUi.stateArt())
        stateTitle.text="登录状态需要恢复";stateBody.text="请先恢复登录，再按官方页面提示完成核验。"
        login.visibility=View.VISIBLE;stateNative.visibility=View.VISIBLE
    }
    fun showAccountChanged() {
        reset();overlay.background=OfficialFlowUi.rounded(activity,Color.rgb(255,246,247),14)
        stateIcon.visibility=View.VISIBLE;stateIcon.setImageDrawable(OfficialFlowUi.stateArt(true))
        stateTitle.text="当前账号已变化";stateBody.text="旧码已隐藏，请重新选择账号后出示。"
        stateAction.visibility=View.VISIBLE;stateSecondary.visibility=View.VISIBLE
    }
    fun showFailure() {
        reset();stateIcon.visibility=View.VISIBLE;stateIcon.setImageDrawable(HubIcon("info",HubUi.muted,dp(56)))
        stateTitle.text="官方页面暂未加载";stateBody.text="请检查网络后重新加载页面，或使用原 App 出示。"
        stateAction.text="重新加载页面";stateAction.setOnClickListener {onReload()};stateAction.visibility=View.VISIBLE;stateNative.visibility=View.VISIBLE
    }
    fun chooseAccount(accounts:List<BoundAccount>,selectedId:String,onSelected:(BoundAccount)->Unit) {
        OfficialAccountSheet.show(activity,if(channel=="jd")"jd" else "taobao",accounts,selectedId,onSelected)
    }
}


