package com.halo.expressassistant.hub

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.*

/** Use real system insets, including a cutout that moves to the side in landscape. */
object SafeArea {
    private val installed=java.util.WeakHashMap<View,Boolean>()
    fun install(activity:Activity) {
        if(activity is HubActivity)return // Hub has its own IME-aware root inset listener.
        val attrs=activity.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsFloating))
        val floating=attrs.getBoolean(0,false);attrs.recycle();if(floating)return
        val content=activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root=content.getChildAt(0) ?: return
        if(installed.put(root,true)==true)return
        val left=root.paddingLeft;val top=root.paddingTop;val right=root.paddingRight;val bottom=root.paddingBottom
        WindowCompat.setDecorFitsSystemWindows(activity.window,false)
        ViewCompat.setOnApplyWindowInsetsListener(root) {view,insets->
            val safe=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(left+safe.left,top+safe.top,right+safe.right,bottom+safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
