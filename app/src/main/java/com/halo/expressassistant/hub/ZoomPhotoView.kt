package com.halo.expressassistant.hub

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/** Fit the original image first; pinch, drag, or double tap to inspect it. */
class ZoomPhotoView(context:Context):AppCompatImageView(context) {
    private val transform=Matrix()
    private var base=1f
    private var zoom=1f
    private var x=0f
    private var y=0f
    private val pinch=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d:ScaleGestureDetector):Boolean {
            if(!d.scaleFactor.isFinite() || d.scaleFactor<=0f)return false
            val next=(zoom*d.scaleFactor).coerceIn(1f,6f)
            transform.postScale(next/zoom,next/zoom,d.focusX,d.focusY);zoom=next;bound();return true
        }
    })
    private val tap=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e:MotionEvent)=true
        override fun onSingleTapConfirmed(e:MotionEvent):Boolean {performClick();return true}
        override fun onDoubleTap(e:MotionEvent):Boolean {
            if(zoom>1.1f)fit() else {transform.postScale(2.5f,2.5f,e.x,e.y);zoom=2.5f;bound()};return true
        }
    })
    init {scaleType=ScaleType.MATRIX;contentDescription="商品大图，双指缩放、双击放大、单指拖动查看";isFocusable=true;isClickable=true}
    override fun setImageDrawable(drawable:Drawable?) {super.setImageDrawable(drawable);if(width>0)post {fit()}}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);fit()}
    private fun fit() {
        val d=drawable ?: return
        if(width<=0 || height<=0 || d.intrinsicWidth<=0 || d.intrinsicHeight<=0)return
        base=min(width.toFloat()/d.intrinsicWidth,height.toFloat()/d.intrinsicHeight);zoom=1f
        transform.reset();transform.postScale(base,base)
        transform.postTranslate((width-d.intrinsicWidth*base)/2,(height-d.intrinsicHeight*base)/2);imageMatrix=transform
    }
    private fun bound() {
        val d=drawable ?: return
        val rect=android.graphics.RectF(0f,0f,d.intrinsicWidth.toFloat(),d.intrinsicHeight.toFloat());transform.mapRect(rect)
        val dx=if(rect.width()<=width)(width-rect.width())/2-rect.left else when {rect.left>0->-rect.left;rect.right<width->width-rect.right;else->0f}
        val dy=if(rect.height()<=height)(height-rect.height())/2-rect.top else when {rect.top>0->-rect.top;rect.bottom<height->height-rect.bottom;else->0f}
        transform.postTranslate(dx,dy);imageMatrix=transform
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(drawable==null)return super.onTouchEvent(e)
        pinch.onTouchEvent(e);tap.onTouchEvent(e)
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN->{x=e.x;y=e.y;parent?.requestDisallowInterceptTouchEvent(true)}
            MotionEvent.ACTION_MOVE->{if(!pinch.isInProgress && e.pointerCount==1 && zoom>1f){transform.postTranslate(e.x-x,e.y-y);bound()};x=e.x;y=e.y}
            MotionEvent.ACTION_POINTER_UP->{val i=if(e.actionIndex==0)1 else 0;if(i<e.pointerCount){x=e.getX(i);y=e.getY(i)}}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}
