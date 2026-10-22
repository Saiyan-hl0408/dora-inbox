package com.halo.expressassistant.hub

import android.graphics.*
import android.graphics.drawable.Drawable

/** Native, scalable empty-state illustration. Geometry is also exported with the UI assets. */
class HubStateArt(private val search:Boolean=false):Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas:Canvas) {
        val save=canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/120f,bounds.height()/120f)
        fun fill(color:Int){paint.color=color;paint.style=Paint.Style.FILL}
        fun path(vararg xy:Float):Path=Path().apply {moveTo(xy[0],xy[1]);for(i in 2 until xy.size step 2)lineTo(xy[i],xy[i+1]);close()}
        fill(Color.rgb(232,246,255));canvas.drawCircle(60f,60f,53f,paint)
        if(search) {
            paint.color=Color.rgb(0,112,255);paint.style=Paint.Style.STROKE;paint.strokeWidth=5f;paint.strokeCap=Paint.Cap.ROUND
            canvas.drawCircle(55f,54f,20f,paint);canvas.drawLine(70f,69f,85f,84f,paint)
        } else {
            val left=path(25f,51f,60f,65f,60f,105f,27f,91f)
            val right=path(60f,65f,95f,51f,93f,91f,60f,105f)
            fill(Color.rgb(217,238,255));canvas.drawPath(left,paint);fill(Color.rgb(192,225,255));canvas.drawPath(right,paint)
            val flaps=listOf(path(60f,28f,25f,42f,10f,63f,46f,78f,60f,65f),path(60f,28f,95f,42f,110f,63f,75f,78f,60f,65f))
            flaps.forEach {fill(Color.rgb(247,252,255));canvas.drawPath(it,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=1.6f;paint.color=Color.rgb(89,159,255);canvas.drawPath(it,paint)}
            paint.style=Paint.Style.STROKE;paint.strokeWidth=1.6f;paint.color=Color.rgb(89,159,255);canvas.drawPath(left,paint);canvas.drawPath(right,paint)
            paint.strokeWidth=3.5f;paint.strokeCap=Paint.Cap.ROUND;canvas.drawLine(92f,21f,96f,10f,paint);canvas.drawLine(106f,29f,116f,25f,paint)
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha}
    override fun setColorFilter(colorFilter:ColorFilter?){paint.colorFilter=colorFilter}
    override fun getOpacity()=PixelFormat.TRANSLUCENT
}
