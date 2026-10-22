package com.halo.expressassistant.hub

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.CompoundButton

/** Approved 38 x 22 track within a separate 48 dp accessible touch target. */
internal class CompactSyncSwitch(context:Context):CompoundButton(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var position=0f
    private var animator:ValueAnimator?=null
    private fun px(n:Float)=n*resources.displayMetrics.density
    init {
        buttonDrawable=null
        minimumWidth=px(48f).toInt();minimumHeight=px(48f).toInt()
        contentDescription="自动同步";isClickable=true;isFocusable=true
        setPadding(0,0,0,0)
    }
    override fun getAccessibilityClassName()="android.widget.Switch"
    override fun setChecked(checked:Boolean) {
        val changed=checked!=isChecked
        super.setChecked(checked)
        if(!changed)return
        animator?.cancel()
        val end=if(checked)1f else 0f
        if(isLaidOut && isShown)animator=ValueAnimator.ofFloat(position,end).apply {
            duration=160L
            addUpdateListener {position=it.animatedValue as Float;invalidate()}
            start()
        } else {position=end;invalidate()}
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        setMeasuredDimension(resolveSize(px(48f).toInt(),widthMeasureSpec),resolveSize(px(48f).toInt(),heightMeasureSpec))
    }
    override fun onDraw(canvas:Canvas) {
        val left=(width-px(38f))/2f;val top=(height-px(22f))/2f
        val from=Color.rgb(204,222,239);val to=HubUi.accent
        fun mix(a:Int,b:Int)=(a+(b-a)*position).toInt()
        paint.color=Color.rgb(mix(Color.red(from),Color.red(to)),mix(Color.green(from),Color.green(to)),mix(Color.blue(from),Color.blue(to)))
        paint.alpha=if(isEnabled)255 else 125
        canvas.drawRoundRect(left,top,left+px(38f),top+px(22f),px(11f),px(11f),paint)
        paint.color=Color.WHITE
        canvas.drawCircle(left+px(11f+16f*position),height/2f,px(8f),paint)
    }
    override fun onDetachedFromWindow(){animator?.cancel();position=if(isChecked)1f else 0f;super.onDetachedFromWindow()}
}
