package com.halo.expressassistant.hub

import android.graphics.*
import android.graphics.drawable.Drawable

/** Decorative browser status vector, never a website preview or a readable code. */
class OfficialPageArt:Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas:Canvas) {
        canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/120f,bounds.height()/100f)
        paint.color=Color.rgb(205,220,240);paint.style=Paint.Style.FILL;canvas.drawRoundRect(7f,9f,113f,91f,7f,7f,paint)
        paint.color=Color.rgb(244,249,255);canvas.drawRoundRect(12f,22f,108f,86f,3f,3f,paint)
        paint.color=Color.rgb(154,179,212);for(x in listOf(17f,24f,31f))canvas.drawCircle(x,16f,2f,paint)
        paint.color=Color.rgb(166,187,216);canvas.drawCircle(60f,54f,24f,paint)
        paint.color=Color.WHITE;paint.style=Paint.Style.STROKE;paint.strokeWidth=4f;paint.strokeCap=Paint.Cap.ROUND
        canvas.drawLine(60f,41f,60f,56f,paint);canvas.drawPoint(60f,65f,paint);paint.style=Paint.Style.FILL;canvas.restore()
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter;invalidateSelf()}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

/** Scan-document illustration only: deliberately contains text lines rather than a QR pattern. */
class OfficialScanArt(private val expired:Boolean=false):Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas:Canvas) {
        canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/120f,bounds.height()/120f)
        paint.style=Paint.Style.FILL;paint.color=if(expired)Color.rgb(243,246,250) else Color.rgb(231,246,255)
        canvas.drawRoundRect(2f,2f,118f,118f,14f,14f,paint)
        paint.color=Color.WHITE;canvas.drawRoundRect(23f,19f,97f,101f,9f,9f,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=4f;paint.strokeCap=Paint.Cap.ROUND
        paint.color=if(expired)Color.rgb(195,204,217) else Color.rgb(82,149,255)
        for(pair in listOf(30f to 28f,90f to 28f,30f to 92f,90f to 92f)) {
            val direction=if(pair.first<60)1 else -1;val vertical=if(pair.second<60)1 else -1
            canvas.drawLine(pair.first,pair.second,pair.first+direction*10,pair.second,paint)
            canvas.drawLine(pair.first,pair.second,pair.first,pair.second+vertical*10,paint)
        }
        paint.color=Color.rgb(197,205,219);paint.strokeWidth=3f
        canvas.drawLine(45f,48f,75f,48f,paint);canvas.drawLine(45f,60f,75f,60f,paint);canvas.drawLine(45f,72f,65f,72f,paint)
        if(expired) {
            paint.style=Paint.Style.FILL;paint.color=Color.rgb(244,45,61);canvas.drawCircle(84f,80f,15f,paint)
            paint.color=Color.WHITE;paint.strokeWidth=2.6f;canvas.drawLine(84f,72f,84f,81f,paint);canvas.drawCircle(84f,87f,1.7f,paint)
        }
        canvas.restore()
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter;invalidateSelf()}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

