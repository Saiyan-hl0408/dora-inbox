package com.halo.expressassistant.hub

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.core.graphics.PathParser

/** Native components matching the approved light detail boards; contains no parcel facts. */
internal class DetailUi(private val context:Context) {
    fun dp(value:Int)=(value*context.resources.displayMetrics.density+.5f).toInt()
    fun column()=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
    fun text(value:String,size:Int=14,bold:Boolean=false,color:Int=HubUi.ink)=TextView(context).apply {
        text=value;textSize=size.toFloat();setTextColor(color);includeFontPadding=false
        typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(dp(if(size>=17)7 else 6).toFloat(),1f)
    }
    fun round(color:Int,radius:Int=16,stroke:Int?=null)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(radius).toFloat();if(stroke!=null)setStroke(dp(1),stroke)
    }
    fun card(padding:Int=14)=column().apply {
        setPadding(dp(padding),dp(padding),dp(padding),dp(padding));background=round(HubUi.surface)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(10)}
    }
    fun icon(name:String,color:Int=HubUi.muted,size:Int=20)=ImageView(context).apply {
        setImageDrawable(if(name in setOf("image-placeholder","signed-shield"))DetailGlyph(name,color,dp(size)) else HubIcon(name,color,dp(size)));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun iconButton(name:String,description:String,action:()->Unit)=icon(name,HubUi.ink,22).apply {
        setPadding(dp(11),dp(11),dp(11),dp(11));layoutParams=LinearLayout.LayoutParams(dp(44),dp(44))
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES;contentDescription=description
        isClickable=true;isFocusable=true;setOnClickListener {action()}
    }
    fun badge(value:String,foreground:Int,backgroundColor:Int)=text(value,13,true,foreground).apply {
        background=round(backgroundColor,10);gravity=Gravity.CENTER
        setPadding(dp(12),dp(6),dp(12),dp(6))
    }
    fun button(value:String,primary:Boolean=false,outline:Boolean=false,iconName:String?=null,iconAtEnd:Boolean=false,action:()->Unit)=MaterialButton(context).apply {
        text=value;textSize=15f;isAllCaps=false;includeFontPadding=false
        typeface=Typeface.create("sans-serif",Typeface.BOLD)
        minHeight=dp(46);minimumHeight=dp(46);minWidth=0;minimumWidth=0
        insetTop=0;insetBottom=0;cornerRadius=dp(12);setPadding(dp(12),0,dp(12),0)
        backgroundTintList=ColorStateList.valueOf(if(primary)HubUi.accent else if(outline)Color.WHITE else Color.rgb(224,239,255))
        setTextColor(if(primary)Color.WHITE else HubUi.accent)
        strokeWidth=if(outline)dp(1)else 0;strokeColor=ColorStateList.valueOf(HubUi.accent)
        if(iconName!=null){icon=HubIcon(iconName,if(primary)Color.WHITE else HubUi.accent,dp(20));iconTint=ColorStateList.valueOf(if(primary)Color.WHITE else HubUi.accent);iconSize=dp(20);iconPadding=dp(8);iconGravity=if(iconAtEnd)MaterialButton.ICON_GRAVITY_TEXT_END else MaterialButton.ICON_GRAVITY_TEXT_START}
        setOnClickListener {action()}
    }
    fun spacer(height:Int)=View(context).apply {layoutParams=LinearLayout.LayoutParams(1,dp(height))}
    fun fact(parent:LinearLayout,iconName:String,value:String,color:Int=HubUi.muted) {
        val row=LinearLayout(context).apply {gravity=Gravity.TOP;setPadding(0,dp(5),0,dp(5))}
        row.addView(icon(iconName,color),LinearLayout.LayoutParams(dp(20),dp(22)).apply {marginEnd=dp(9)})
        row.addView(text(value,13,color=color).apply {setTextIsSelectable(true)},LinearLayout.LayoutParams(0,-2,1f))
        parent.addView(row)
    }
}

/** Two detail-specific glyphs, mirrored in independently delivered SVG/vector assets. */
internal class DetailGlyph(private val kind:String,private val tint:Int,private val size:Int):Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    override fun getIntrinsicWidth()=size
    override fun getIntrinsicHeight()=size
    override fun draw(canvas:Canvas) {
        canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
        paint.color=tint
        if(kind=="signed-shield") {
            paint.style=Paint.Style.FILL
            canvas.drawPath(PathParser.createPathFromPathData("M12,2 L21,6 V12 C21,17 17,21 12,23 C7,21 3,17 3,12 V6 Z")!!,paint)
            paint.color=Color.WHITE;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
            canvas.drawPath(PathParser.createPathFromPathData("M7,12 L10,15 L17,8")!!,paint)
        } else {
            paint.style=Paint.Style.STROKE;paint.strokeWidth=1.7f
            canvas.drawPath(PathParser.createPathFromPathData("M5,3 H19 Q21,3 21,5 V19 Q21,21 19,21 H5 Q3,21 3,19 V5 Q3,3 5,3 M4,18 L9,12 L13,16 L16,12 L20,18")!!,paint)
            paint.style=Paint.Style.FILL;canvas.drawCircle(8f,8f,1.5f,paint)
        }
        canvas.restore()
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter;invalidateSelf()}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

/** Dot and connecting rail stretch with the actual node text height. */
internal class DetailTimelineRail(context:Context,private val color:Int,private val first:Boolean,private val last:Boolean):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val density=resources.displayMetrics.density
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val x=width/2f;val y=9*density
        paint.strokeWidth=1.2f*density;paint.color=Color.rgb(220,231,244)
        if(!first)canvas.drawLine(x,0f,x,y,paint)
        if(!last)canvas.drawLine(x,y,x,height.toFloat(),paint)
        if(first){paint.color=Color.argb(28,0,119,245);canvas.drawCircle(x,y,8*density,paint)}
        paint.color=if(first)color else Color.rgb(156,169,188)
        canvas.drawCircle(x,y,4.3f*density,paint)
    }
}

/** Quiet parcel artwork for empty data; never represents a shipment or provider response. */
internal class DetailEmptyArtwork(context:Context):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val side=minOf(width,height).toFloat()
        canvas.save();canvas.translate((width-side)/2f,(height-side)/2f);canvas.scale(side/100f,side/100f)
        paint.shader=RadialGradient(50f,42f,48f,intArrayOf(Color.rgb(224,239,255),Color.rgb(240,248,255),Color.WHITE),floatArrayOf(0f,.72f,1f),Shader.TileMode.CLAMP)
        canvas.drawCircle(50f,47f,47f,paint);paint.shader=null
        fun face(color:Int,vararg points:Float) {
            val path=Path().apply {moveTo(points[0],points[1]);for(i in 2 until points.size step 2)lineTo(points[i],points[i+1]);close()}
            paint.color=color;paint.style=Paint.Style.FILL;canvas.drawPath(path,paint)
        }
        face(Color.rgb(211,232,255),26f,39f,49f,27f,75f,38f,51f,53f)
        face(Color.rgb(188,220,255),26f,39f,51f,53f,51f,80f,26f,65f)
        face(Color.rgb(158,201,252),51f,53f,75f,38f,75f,65f,51f,80f)
        face(Color.rgb(239,248,255),36f,34f,42f,31f,66f,43f,60f,47f)
        face(Color.rgb(224,241,255),60f,47f,66f,43f,66f,53f,60f,57f)
        paint.color=Color.rgb(156,208,255);paint.strokeWidth=1.7f;paint.strokeCap=Paint.Cap.ROUND
        canvas.drawLine(33f,21f,27f,17f,paint);canvas.drawLine(66f,21f,72f,17f,paint);canvas.drawLine(50f,16f,50f,9f,paint)
        paint.color=Color.rgb(93,172,255)
        for(point in arrayOf(19f to 63f,82f to 35f,76f to 22f)) {
            val x=point.first;val y=point.second
            face(paint.color,x,y-2f,x+.8f,y-.8f,x+2f,y,x+.8f,y+.8f,x,y+2f,x-.8f,y+.8f,x-2f,y,x-.8f,y-.8f)
        }
        canvas.restore()
    }
}

/** Loading geometry is shown only while a real request is running. */
internal class DetailTraceSkeleton(context:Context):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val density=resources.displayMetrics.density
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        fun d(value:Float)=value*density
        paint.color=Color.rgb(226,237,249);paint.strokeWidth=d(1f)
        canvas.drawLine(d(11f),d(15f),d(11f),height-d(30f),paint)
        for(index in 0..2) {
            val y=d(20f+index*88f)
            paint.color=Color.rgb(218,232,247);canvas.drawCircle(d(11f),y,d(11f),paint)
            canvas.drawRoundRect(d(42f),y-d(10f),width*.70f,y+d(10f),d(6f),d(6f),paint)
            paint.color=Color.rgb(234,242,252)
            canvas.drawRoundRect(d(42f),y+d(20f),width.toFloat(),y+d(33f),d(6f),d(6f),paint)
            canvas.drawRoundRect(d(42f),y+d(42f),width*.55f,y+d(55f),d(6f),d(6f),paint)
        }
    }
}
