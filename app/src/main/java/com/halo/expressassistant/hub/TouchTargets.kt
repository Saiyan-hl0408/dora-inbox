package com.halo.expressassistant.hub

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View

/** Keep compact artwork without making its touch area or neighboring information rows tiny. */
object TouchTargets {
    private val groups=java.util.WeakHashMap<View,java.lang.ref.WeakReference<Group>>()
    fun expand(view:View,minimumDp:Int=48) {
        view.post {
            val parent=view.parent as? View ?: return@post
            val rect=Rect();view.getHitRect(rect)
            val size=(minimumDp*view.resources.displayMetrics.density).toInt()
            val dx=((size-rect.width())/2).coerceAtLeast(0);val dy=((size-rect.height())/2).coerceAtLeast(0)
            rect.inset(-dx,-dy)
            val group=groups[parent]?.get() ?: Group(parent).also {groups[parent]=java.lang.ref.WeakReference(it);parent.touchDelegate=it}
            group.targets[view]=TouchDelegate(rect,view)
        }
    }
    private class Group(parent:View):TouchDelegate(Rect(),parent) {
        val targets=java.util.WeakHashMap<View,TouchDelegate>()
        override fun onTouchEvent(event:MotionEvent):Boolean {
            for((view,delegate) in targets.toMap())if(view.isShown) {
                val copy=MotionEvent.obtain(event)
                try {if(delegate.onTouchEvent(copy))return true} finally {copy.recycle()}
            }
            return false
        }
    }
}
