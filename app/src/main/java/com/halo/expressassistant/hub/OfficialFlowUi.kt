package com.halo.expressassistant.hub

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

/** App-owned official-page chrome. Never draws or interprets official QR content. */
object OfficialFlowUi {
    fun dp(context:Context,n:Int)=(n*context.resources.displayMetrics.density+.5f).toInt()
    fun rounded(context:Context,color:Int=Color.WHITE,radius:Int=16)=GradientDrawable().apply {setColor(color);cornerRadius=dp(context,radius).toFloat()}
    fun text(context:Context,value:String,size:Float=14f,bold:Boolean=false,color:Int=HubUi.ink)=TextView(context).apply {
        text=value;textSize=size;setTextColor(color);includeFontPadding=false
        setTypeface(Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL));setLineSpacing(dp(context,4).toFloat(),1f)
    }
    fun card(context:Context,padding:Int=14)=LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL;background=rounded(context);setPadding(dp(context,padding),dp(context,padding),dp(context,padding),dp(context,padding))
    }
    fun button(context:Context,value:String,primary:Boolean=false,action:()->Unit)=MaterialButton(context).apply {
        text=value;isAllCaps=false;textSize=15f;includeFontPadding=false
        setTypeface(Typeface.create("sans-serif",Typeface.BOLD))
        minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0;insetTop=0;insetBottom=0
        setPadding(dp(context,12),0,dp(context,12),0);cornerRadius=dp(context,12)
        backgroundTintList=ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(Color.rgb(212,220,232),if(primary)HubUi.accent else Color.WHITE))
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(if(primary)Color.WHITE else HubUi.muted,if(primary)Color.WHITE else HubUi.accent)))
        strokeWidth=if(primary)0 else dp(context,1);strokeColor=ColorStateList.valueOf(Color.rgb(198,224,246))
        setOnClickListener {action()}
    }
    fun icon(context:Context,name:String,description:String,action:()->Unit)=ImageView(context).apply {
        setImageDrawable(HubIcon(name,HubUi.ink,dp(context,24)));setPadding(dp(context,12),dp(context,12),dp(context,12),dp(context,12))
        contentDescription=description;isClickable=true;isFocusable=true;setOnClickListener {action()}
    }
    fun header(activity:Activity,title:String,close:Boolean=false,onBack:()->Unit={activity.finish()}):FrameLayout {
        val frame=FrameLayout(activity).apply {minimumHeight=dp(activity,56)}
        frame.addView(text(activity,title,19f,true).apply {gravity=Gravity.CENTER;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},
            FrameLayout.LayoutParams(-1,dp(activity,56)).apply {leftMargin=dp(activity,52);rightMargin=dp(activity,52)})
        frame.addView(icon(activity,if(close)"close" else "back",if(close)"关闭" else "返回",onBack),FrameLayout.LayoutParams(dp(activity,48),dp(activity,48),Gravity.START or Gravity.CENTER_VERTICAL))
        return frame
    }
    fun badge(context:Context,channel:String,size:Int=48)=text(context,PlatformStyle.badge(channel),if(channel=="jd")26f else 28f,true,Color.WHITE).apply {
        gravity=Gravity.CENTER;background=rounded(context,if(channel=="taobao")Color.rgb(255,100,0) else if(channel=="cainiao")HubUi.accent else PlatformStyle.accent(channel),12)
        if(channel=="cainiao")text="菜"
        layoutParams=LinearLayout.LayoutParams(dp(context,size),dp(context,size))
    }
    fun lock(context:Context)=object:android.graphics.drawable.Drawable() {
        private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=HubUi.muted;style=Paint.Style.STROKE;strokeWidth=2.2f;strokeCap=Paint.Cap.ROUND}
        override fun draw(canvas:Canvas) {canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f);canvas.drawRoundRect(5f,10f,19f,22f,2f,2f,p);canvas.drawArc(8f,1f,16f,15f,180f,180f,false,p);canvas.drawLine(12f,15f,12f,18f,p);canvas.restore()}
        override fun setAlpha(a:Int){p.alpha=a;invalidateSelf()};override fun setColorFilter(f:ColorFilter?){p.colorFilter=f;invalidateSelf()}
        @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
    fun stateArt(warning:Boolean=false)=object:android.graphics.drawable.Drawable() {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas:Canvas) {
            canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/80f,bounds.height()/80f)
            paint.style=Paint.Style.FILL;paint.color=if(warning)Color.rgb(255,216,220) else Color.rgb(225,240,255)
            canvas.drawCircle(40f,40f,39f,paint)
            if(warning) {
                paint.color=Color.rgb(244,45,61);canvas.drawCircle(40f,40f,20f,paint)
                paint.color=Color.WHITE;paint.strokeWidth=3.8f;paint.strokeCap=Paint.Cap.ROUND
                canvas.drawLine(40f,28f,40f,43f,paint);canvas.drawCircle(40f,51f,2.2f,paint)
            } else {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;paint.color=HubUi.muted
                canvas.drawArc(30f,21f,50f,47f,180f,180f,false,paint)
                paint.style=Paint.Style.FILL;canvas.drawRoundRect(25f,34f,55f,57f,3f,3f,paint)
                paint.color=Color.WHITE;canvas.drawCircle(40f,43f,3f,paint);canvas.drawRoundRect(38.8f,43f,41.2f,50f,1f,1f,paint)
            }
            canvas.restore()
        }
        override fun setAlpha(alpha:Int){paint.alpha=alpha;invalidateSelf()}
        override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter;invalidateSelf()}
        @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
    fun spinner(context:Context,color:Int=HubUi.accent)=object:View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        init {contentDescription="正在加载";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES}
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            val radius=width.coerceAtMost(height)*.36f
            val dot=width.coerceAtMost(height)*.064f
            val phase=(android.os.SystemClock.uptimeMillis()/80L%12).toInt()
            for(index in 0 until 12) {
                val angle=(index*30-90)*Math.PI/180
                paint.color=color;paint.alpha=40+((index-phase+12)%12)*19
                canvas.drawCircle(width/2f+radius*kotlin.math.cos(angle).toFloat(),height/2f+radius*kotlin.math.sin(angle).toFloat(),dot,paint)
            }
            if(isShown)postInvalidateOnAnimation()
        }
    }
    fun info(context:Context,value:TextView):LinearLayout=LinearLayout(context).apply {
        gravity=Gravity.TOP;isBaselineAligned=false;background=rounded(context,HubUi.paleBlue,14);setPadding(dp(context,12),dp(context,12),dp(context,12),dp(context,12))
        addView(ImageView(context).apply {setImageDrawable(HubIcon("info",HubUi.accent,dp(context,22)))},LinearLayout.LayoutParams(dp(context,22),dp(context,22)).apply {rightMargin=dp(context,10)})
        addView(value,LinearLayout.LayoutParams(0,-2,1f))
    }
    fun insets(activity:Activity,root:View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window,false)
        activity.window.statusBarColor=Color.TRANSPARENT;activity.window.navigationBarColor=Color.TRANSPARENT
        WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true}
        ViewCompat.setOnApplyWindowInsetsListener(root){view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets
        }
    }
}

/** Native progress geometry; a completed mark means a real completed local step only. */
class OfficialStepsView(context:Context):View(context) {
    var current=0;set(value){field=value;updateDescription();invalidate()}
    var firstSaved=false;set(value){field=value;updateDescription();invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val labels=listOf("保存二维码","原 App 授权","返回检查")
    init {minimumHeight=OfficialFlowUi.dp(context,44);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;updateDescription()}
    private fun updateDescription(){contentDescription="登录步骤："+labels[current.coerceIn(0,2)]+if(firstSaved)"；二维码已保存" else ""}
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas);val d=resources.displayMetrics.density;val cell=width/3f;val cy=height/2f;val radius=9.5f*d
        paint.strokeWidth=d
        labels.forEachIndexed {i,label->
            val x=cell*i+radius;val done=i<current && (i>0 || firstSaved);val active=i==current
            paint.color=if(done)Color.rgb(230,248,238) else Color.rgb(234,242,250);canvas.drawCircle(x,cy,radius,paint)
            paint.color=if(done)Color.rgb(0,163,113) else if(active)HubUi.accent else Color.rgb(122,137,162);paint.textSize=12*d;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textAlign=Paint.Align.CENTER
            canvas.drawText((i+1).toString(),x,cy-(paint.ascent()+paint.descent())/2,paint)
            paint.textSize=11*resources.displayMetrics.scaledDensity;paint.typeface=Typeface.create("sans-serif",if(active)Typeface.BOLD else Typeface.NORMAL);paint.textAlign=Paint.Align.LEFT
            val available=cell-2*radius-10*d
            if(paint.measureText(label)>available)paint.textSize*=available/paint.measureText(label)
            canvas.drawText(label,x+radius+5*d,cy-(paint.ascent()+paint.descent())/2,paint)
        }
    }
}

