package com.halo.expressassistant.hub

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/** Shared light visual contract from the accepted blue/white boards. */
object HubUi {
    val ink=Color.rgb(17,24,49)
    val muted=Color.rgb(113,122,155)
    val accent=Color.rgb(0,106,254)
    val surface=Color.WHITE
    val paleBlue=Color.rgb(232,243,255)
    fun background()=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(
        Color.rgb(240,250,255),Color.rgb(229,245,255),Color.rgb(237,249,255)))
}
