package com.halo.expressassistant.hub

import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

/** Same 24-unit vector geometry as the separately delivered SVG/Android assets. */
class HubIcon(private val name:String,color:Int,private val size:Int):Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=color;style=Paint.Style.STROKE;strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    override fun getIntrinsicWidth()=size
    override fun getIntrinsicHeight()=size
    override fun draw(canvas:Canvas) {
        canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
        if(name=="nav-parcel" || name=="nav-parcel-filled") {
            val filled=name.endsWith("filled")
            paint.style=if(filled)Paint.Style.FILL else Paint.Style.STROKE
            val shape=Path().apply {moveTo(3f,9f);lineTo(12f,2.5f);lineTo(21f,9f);lineTo(21f,21f);lineTo(3f,21f);close()}
            canvas.drawPath(shape,paint)
            val original=paint.color;paint.style=Paint.Style.STROKE
            if(filled)paint.color=Color.WHITE
            canvas.drawLine(8f,14f,16f,14f,paint);paint.color=original
        } else if(name=="user-filled") {
            paint.style=Paint.Style.FILL;canvas.drawCircle(12f,7f,4f,paint)
            canvas.drawPath(Path().apply {moveTo(3f,22f);cubicTo(3f,10f,21f,10f,21f,22f);close()},paint)
            paint.style=Paint.Style.STROKE
        } else if(name=="qr-filled") {
            val original=paint.color;paint.style=Paint.Style.FILL
            listOf(3f to 3f,14f to 3f,3f to 14f).forEach {(x,y)->
                canvas.drawRoundRect(x,y,x+7f,y+7f,1f,1f,paint)
                paint.color=Color.WHITE;canvas.drawRect(x+2f,y+2f,x+5f,y+5f,paint);paint.color=original
            }
            canvas.drawRect(14f,14f,17f,17f,paint);canvas.drawRect(18f,18f,21f,21f,paint)
            canvas.drawRect(18f,14f,21f,16f,paint);canvas.drawRect(14f,18f,16f,21f,paint)
            paint.style=Paint.Style.STROKE
        } else if(name=="pdd") {
            paint.style=Paint.Style.FILL;canvas.drawRoundRect(1f,1f,23f,23f,4f,4f,paint)
            val old=paint.color;paint.color=Color.WHITE;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
            paint.textSize=17f;paint.textAlign=Paint.Align.CENTER;canvas.drawText("拼",12f,18f,paint)
            paint.textAlign=Paint.Align.LEFT;paint.color=old;paint.style=Paint.Style.STROKE
        } else {
            val key=if(name=="checkbox")"check" else name.replace('_','-')
            if(name=="checkbox")canvas.drawRoundRect(3f,3f,21f,21f,4f,4f,paint)
            else paths[key]?.let {canvas.drawPath(it,paint)}
        }
        canvas.restore()
    }
    override fun setAlpha(alpha:Int) {paint.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?) {paint.colorFilter=filter;invalidateSelf()}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
    companion object {
        private val paths=mapOf(
        "back" to "M15,5 L8,12 L15,19",
        "chevron" to "M9,6 L15,12 L9,18",
        "down" to "M6,9 L12,15 L18,9",
        "external" to "M14,3 H21 V10 M21,3 L10,14 M10,4 H3 V21 H20 V14",
        "copy" to "M9,6 H18 Q20,6 20,8 V19 Q20,21 18,21 H9 Q7,21 7,19 V8 Q7,6 9,6 M16,6 V3 H3 V17 H7",
        "search" to "M16.5,10 A6.5,6.5 0,1 1,3.5,10 A6.5,6.5 0,1 1,16.5,10 M15,15 L21,21",
        "refresh" to "M18.128,17.142 A8,8 0,1 1,18.928,8 M20,4 V10 H15",
        "clock" to "M21,12 A9,9 0,1 1,3,12 A9,9 0,1 1,21,12 M12,6 V12 L16,14",
        "location" to "M15,9 A3,3 0,1 1,9,9 A3,3 0,1 1,15,9 M12,22 C10,18 4,13 4,9 C4,-1 20,-1 20,9 C20,13 14,18 12,22",
        "identity" to "M4,4 H20 Q22,4 22,6 V18 Q22,20 20,20 H4 Q2,20 2,18 V6 Q2,4 4,4 M10,9 A2,2 0,1 1,6,9 A2,2 0,1 1,10,9 M4,15.5 A4,3.5 0,0 1,12,15.5 M14,9 H19 M14,14 H19",
        "parcel" to "M12,2 L22,7 V18 L12,23 L2,18 V7 Z M2,7 L12,12 L22,7 M12,12 V23 M7,4.5 L17,9.5",
        "box" to "M6,7 H18 Q20,7 20,9 V19 Q20,21 18,21 H6 Q4,21 4,19 V9 Q4,7 6,7 M4,7 L8,3 H16 L20,7 M12,3 V12 M9,12 H15",
        "truck" to "M2,4 H14 V18 H2 Z M14,9 H18 L22,14 V18 H14 M8,18 A2,2 0,1 1,4,18 A2,2 0,1 1,8,18 M21,18 A2,2 0,1 1,17,18 A2,2 0,1 1,21,18",
        "qr" to "M4,3 H9 Q10,3 10,4 V9 Q10,10 9,10 H4 Q3,10 3,9 V4 Q3,3 4,3 M15,3 H20 Q21,3 21,4 V9 Q21,10 20,10 H15 Q14,10 14,9 V4 Q14,3 15,3 M4,14 H9 Q10,14 10,15 V20 Q10,21 9,21 H4 Q3,21 3,20 V15 Q3,14 4,14 M15,15 H18 M15,15 V21 M18,18 H21 M21,15 V21 H18",
        "user" to "M16,7 A4,4 0,1 1,8,7 A4,4 0,1 1,16,7 M4,19.5 A8,6.5 0,0 1,20,19.5 M4,19 V21 M20,19 V21",
        "info" to "M21,12 A9,9 0,1 1,3,12 A9,9 0,1 1,21,12 M12,11 V17 M12,7 V7.1",
        "help" to "M21,12 A9,9 0,1 1,3,12 A9,9 0,1 1,21,12 M9,9 C9,5 15,5 15,9 C15,11 12,11 12,14 M12,17 V17.1",
        "circle-check" to "M21,12 A9,9 0,1 1,3,12 A9,9 0,1 1,21,12 M7,12 L10,15 L17,8",
        "check" to "M7,12 L10,15 L17,8",
        "close" to "M6,6 L18,18 M18,6 L6,18",
        "pause" to "M8,5 V19 M16,5 V19",
        "more" to "M13,5 A1,1 0,1 1,11,5 A1,1 0,1 1,13,5 M13,12 A1,1 0,1 1,11,12 A1,1 0,1 1,13,12 M13,19 A1,1 0,1 1,11,19 A1,1 0,1 1,13,19",
        "phone" to "M8,2 H16 Q18,2 18,4 V20 Q18,22 16,22 H8 Q6,22 6,20 V4 Q6,2 8,2 M10,5 H14 M11,19 H13"
        ).mapValues {PathParser.createPathFromPathData(it.value)!!}
    }
}
