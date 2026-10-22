package com.halo.expressassistant.ui

import android.app.Activity
import com.halo.expressassistant.R
import com.halo.expressassistant.data.Store

object Themes {
    const val MONET = "monet"
    const val LARK = "lark"
    const val CUSTOM = "custom"
    const val FONT_SERIF = "serif"
    const val FONT_SANS = "sans"

    fun apply(activity: Activity) {
        activity.setTheme(R.style.Theme_ParcelHub_Light)
    }

    fun current(activity: Activity): String = Store.theme(activity)
}
