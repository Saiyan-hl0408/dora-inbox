package com.halo.expressassistant.hub

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import com.halo.expressassistant.data.Store

/** Approved compact toggle and inline interval choices. */
object BackgroundSyncSettings {
    fun add(context:Context,parent:LinearLayout) {
        fun dp(n:Int)=OfficialFlowUi.dp(context,n)
        fun row()=LinearLayout(context).apply {gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(56);setPadding(dp(14),0,dp(14),0)}
        fun copy(title:String,description:String)=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            addView(OfficialFlowUi.text(context,title,14f))
            addView(OfficialFlowUi.text(context,description,11f,false,HubUi.muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(4)})
        }
        val foreground=row()
        foreground.addView(copy("自动同步","打开软件时更新"),LinearLayout.LayoutParams(0,-2,1f))
        foreground.addView(CompactSyncSwitch(context).apply {isChecked=HubData.prefs(context).getBoolean("auto_open",true)
            setOnCheckedChangeListener {_,enabled->HubData.prefs(context).edit().putBoolean("auto_open",enabled).apply()}
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        parent.addView(foreground)
        val line=row();val labels=copy("后台定时同步","");val description=labels.getChildAt(1) as TextView
        line.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        val toggle=CompactSyncSwitch(context).apply {contentDescription="后台定时同步";isChecked=Store.backgroundSyncEnabled(context)}
        line.addView(toggle,LinearLayout.LayoutParams(dp(48),dp(48)));parent.addView(line)
        val options=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(5),dp(14),0)}
        val choices=row().apply {setPadding(0,0,0,0)};val buttons=mutableListOf<com.google.android.material.button.MaterialButton>()
        fun update() {
            val minutes=Store.backgroundSyncInterval(context)
            options.visibility=if(toggle.isChecked)View.VISIBLE else View.GONE
            description.text=if(toggle.isChecked)"每 $minutes 分钟更新包裹状态" else "在后台更新包裹状态"
            buttons.forEachIndexed {index,button->val selected=BackgroundSyncPolicy.intervals[index]==minutes
                button.text="${if(selected)"✓ " else ""}${BackgroundSyncPolicy.intervals[index]} 分钟";button.isSelected=selected
                button.backgroundTintList=ColorStateList.valueOf(if(selected)Color.rgb(230,241,255) else Color.WHITE)
                button.strokeColor=ColorStateList.valueOf(if(selected)HubUi.accent else Color.rgb(211,227,242))
                button.contentDescription="${BackgroundSyncPolicy.intervals[index]} 分钟${if(selected)"，已选择" else ""}"
            }
        }
        BackgroundSyncPolicy.intervals.forEachIndexed {index,minutes->
            val button=OfficialFlowUi.button(context,"$minutes 分钟") {Store.saveBackgroundSync(context,true,minutes);update()}.apply {textSize=13f;maxLines=1;isSingleLine=true;setPadding(dp(4),0,dp(4),0)}
            buttons.add(button);choices.addView(button,LinearLayout.LayoutParams(0,dp(48),1f).apply {if(index>0)leftMargin=dp(8)})
        }
        options.addView(choices)
        options.addView(OfficialFlowUi.text(context,"后台执行时间可能受系统调度影响",11f,false,HubUi.muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7);bottomMargin=dp(6)})
        parent.addView(options)
        toggle.setOnCheckedChangeListener {_,enabled->Store.saveBackgroundSync(context,enabled);update()}
        update()
    }
}
