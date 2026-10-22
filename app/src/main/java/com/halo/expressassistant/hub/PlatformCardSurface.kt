package com.halo.expressassistant.hub

import android.graphics.*
import android.graphics.drawable.Drawable

/** Native gradients with a soft sheen and fine grain; no bitmap download or animation. */
class PlatformCardSurface(private val channel:String,private val radius:Float,private val density:Float):Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas:Canvas) {
        val rect=RectF(bounds)
        if(rect.isEmpty)return
        val palette=when(channel) {
            "jd"->intArrayOf(Color.rgb(124,23,49),Color.rgb(168,36,64),Color.rgb(195,65,82))
            "taobao"->intArrayOf(Color.rgb(168,67,6),Color.rgb(196,93,16),Color.rgb(229,145,36))
            else->intArrayOf(Color.rgb(185,48,24),Color.rgb(215,63,31),Color.rgb(246,104,66))
        }
        paint.shader=LinearGradient(rect.left,rect.top,rect.right,rect.bottom,palette,null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect,radius,radius,paint)
        val checkpoint=canvas.save()
        canvas.clipPath(Path().apply {addRoundRect(rect,radius,radius,Path.Direction.CW)})
        paint.shader=RadialGradient(rect.right*.88f,rect.top,rect.width()*.72f,Color.argb(30,255,255,255),Color.TRANSPARENT,Shader.TileMode.CLAMP)
        canvas.drawRect(rect,paint)
        paint.shader=null;paint.color=Color.argb(6,255,255,255);paint.strokeWidth=density*.5f
        val step=density*5
        var y=rect.top+step
        while(y<rect.bottom){var x=rect.left+step;while(x<rect.right){canvas.drawPoint(x,y,paint);x+=step};y+=step}
        canvas.restoreToCount(checkpoint)
        paint.color=Color.argb(42,255,255,255);paint.style=Paint.Style.STROKE;paint.strokeWidth=density
        canvas.drawRoundRect(RectF(rect.left+density/2,rect.top+density/2,rect.right-density/2,rect.bottom-density/2),radius,radius,paint)
        paint.style=Paint.Style.FILL
    }
    override fun getOutline(outline:Outline){outline.setRoundRect(bounds,radius)}
    override fun setAlpha(alpha:Int){}
    override fun setColorFilter(filter:ColorFilter?){}
    @Suppress("DEPRECATION") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
