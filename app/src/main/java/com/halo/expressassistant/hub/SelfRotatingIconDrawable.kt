package com.halo.expressassistant.hub

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** Rotates only the vector, around its own bounds; the button and its touch target stay still. */
internal class SelfRotatingIconDrawable(
    private var delegate:Drawable,
    private val host:View,
    owner:LifecycleOwner
):Drawable(),Animatable,DefaultLifecycleObserver,ViewTreeObserver.OnGlobalLayoutListener,View.OnAttachStateChangeListener {
    private val lifecycle=owner.lifecycle
    private var requested=false
    private var rotationDegrees=0f
    private var observedTree:ViewTreeObserver?=null
    private val animator=ValueAnimator.ofFloat(0f,360f).apply {
        duration=1200L
        repeatCount=ValueAnimator.INFINITE
        interpolator=LinearInterpolator()
        addUpdateListener {setRotationDegrees(it.animatedValue as Float)}
    }
    init {
        lifecycle.addObserver(this)
        host.addOnAttachStateChangeListener(this)
        if(host.isAttachedToWindow)observeLayout()
    }
    override fun getIntrinsicWidth()=delegate.intrinsicWidth
    override fun getIntrinsicHeight()=delegate.intrinsicHeight
    override fun onBoundsChange(bounds:Rect){delegate.bounds=bounds}
    override fun draw(canvas:Canvas) {
        delegate.bounds=bounds
        val saved=canvas.save()
        canvas.rotate(rotationDegrees,bounds.exactCenterX(),bounds.exactCenterY())
        delegate.draw(canvas)
        canvas.restoreToCount(saved)
    }
    fun setRotationDegrees(value:Float){rotationDegrees=value;invalidateSelf()}
    override fun setAlpha(alpha:Int){delegate.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?){delegate.colorFilter=filter;invalidateSelf()}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
    override fun start(){requested=true;reconcile()}
    override fun stop(){requested=false;animator.cancel();setRotationDegrees(0f)}
    override fun isRunning()=animator.isRunning
    override fun setVisible(visible:Boolean,restart:Boolean):Boolean {
        val changed=super.setVisible(visible,restart)
        reconcile()
        return changed
    }
    private fun reconcile() {
        val active=requested && isVisible && host.isAttachedToWindow && host.isShown && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if(active && !animator.isRunning)animator.start()
        else if(!active && animator.isStarted)animator.cancel()
    }
    private fun observeLayout() {
        observedTree=host.viewTreeObserver.also {it.addOnGlobalLayoutListener(this)}
    }
    private fun stopObservingLayout(){observedTree?.takeIf {it.isAlive}?.removeOnGlobalLayoutListener(this);observedTree=null}
    override fun onGlobalLayout(){reconcile()}
    override fun onViewAttachedToWindow(view:View){observeLayout();reconcile()}
    override fun onViewDetachedFromWindow(view:View){stopObservingLayout();animator.cancel()}
    override fun onStart(owner:LifecycleOwner){reconcile()}
    override fun onStop(owner:LifecycleOwner){animator.cancel()}
    override fun onDestroy(owner:LifecycleOwner){stop();stopObservingLayout();host.removeOnAttachStateChangeListener(this);lifecycle.removeObserver(this)}
}
