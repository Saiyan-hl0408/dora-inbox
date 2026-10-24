package com.halo.expressassistant.hub

import android.graphics.Color

/** Distinct platform accents; the parcel workspace keeps its blue theme. */
object PlatformStyle {
    fun accent(channel:String)=when(channel){"jd"->Color.rgb(235,0,20);"taobao"->Color.rgb(255,77,0);"pdd"->Color.rgb(231,32,35);"douyin"->Color.rgb(31,31,39);else->HubUi.accent}
    fun tint(channel:String)=when(channel){"jd"->Color.rgb(255,239,241);"taobao"->Color.rgb(255,245,232);"pdd"->Color.rgb(255,240,234);"douyin"->Color.rgb(241,241,246);else->Color.rgb(232,240,255)}
    fun badge(channel:String)=when(channel){"jd"->"JD";"taobao"->"淘";"pdd"->"拼";"douyin"->"抖";else->"码"}
}
