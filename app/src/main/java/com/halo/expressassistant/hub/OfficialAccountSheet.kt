package com.halo.expressassistant.hub

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.halo.expressassistant.data.BoundAccount

/** Same explicit selection semantics as the official identity entry; no default account is committed. */
object OfficialAccountSheet {
    fun show(context:Context,channel:String,accounts:List<BoundAccount>,selectedId:String="",onSelected:(BoundAccount)->Unit) {
        fun dp(n:Int)=OfficialFlowUi.dp(context,n)
        val dialog=BottomSheetDialog(context)
        val body=OfficialFlowUi.card(context,16)
        val heading=FrameLayout(context)
        heading.addView(OfficialFlowUi.text(context,"选择身份码账号",18f,true).apply {gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,dp(44)).apply {leftMargin=dp(34);rightMargin=dp(34)})
        heading.addView(OfficialFlowUi.icon(context,"close","关闭账号选择"){dialog.dismiss()},FrameLayout.LayoutParams(dp(44),dp(44),Gravity.END))
        body.addView(heading,LinearLayout.LayoutParams(-1,dp(44)))
        body.addView(OfficialFlowUi.info(context,OfficialFlowUi.text(context,"请选择与本次取件对应的账号。",13f,false,HubUi.muted)),LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(12)})
        accounts.forEachIndexed {index,account->
            val row=LinearLayout(context).apply {
                gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(10),dp(10),dp(10))
                background=OfficialFlowUi.rounded(context,Color.WHITE,12)
                isClickable=true;isFocusable=true;contentDescription=account.label.ifBlank {"已绑定账号"}+if(account.id==selectedId)"，已选中" else "，选择账号"
            }
            if(index==0)row.addView(OfficialFlowUi.badge(context,channel,44))
            else row.addView(ImageView(context).apply {setImageDrawable(HubIcon("user-filled",HubUi.ink,dp(28)));setPadding(dp(8),dp(8),dp(8),dp(8));background=OfficialFlowUi.rounded(context,Color.rgb(229,239,248),12)},LinearLayout.LayoutParams(dp(44),dp(44)))
            row.addView(OfficialFlowUi.text(context,account.label.ifBlank {if(channel=="jd")"已绑定京东账号" else "已绑定淘宝账号"},15f,true),LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=dp(14)})
            row.addView(RadioButton(context).apply {isChecked=account.id==selectedId;isClickable=false;isFocusable=false;buttonTintList=android.content.res.ColorStateList.valueOf(HubUi.accent)},LinearLayout.LayoutParams(dp(40),dp(44)))
            row.setOnClickListener {dialog.dismiss();onSelected(account)}
            body.addView(row,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(8)})
        }
        body.addView(OfficialFlowUi.button(context,"取消"){dialog.dismiss()}.apply {setTextColor(HubUi.ink);backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(229,239,248));strokeWidth=0},LinearLayout.LayoutParams(-1,dp(48)).apply {topMargin=dp(10)})
        dialog.setContentView(ScrollView(context).apply {isFillViewport=true;addView(body)})
        dialog.show()
    }
}

