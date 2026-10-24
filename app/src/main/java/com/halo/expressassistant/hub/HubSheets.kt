package com.halo.expressassistant.hub

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

/** Shared native surfaces for the accepted white, round-top action and result panels. */
internal class HubSheets(private val context:Context) {
    val ink=HubUi.ink
    val muted=HubUi.muted
    val blue=HubUi.accent
    val pale=HubUi.paleBlue
    val green=Color.rgb(0,155,95)
    val orange=Color.rgb(229,116,0)
    val red=Color.rgb(235,56,61)
    fun dp(value:Int)=(value*context.resources.displayMetrics.density).toInt()
    fun text(value:String,size:Int=15,bold:Boolean=false,color:Int=ink)=TextView(context).apply {
        text=value;textSize=size.toFloat();setTextColor(color);includeFontPadding=false
        if(bold)setTypeface(typeface,Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(),1f)
    }
    fun column()=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
    fun row()=LinearLayout(context).apply {orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    fun background(color:Int=Color.WHITE,radius:Int=14,border:Int?=null)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(radius).toFloat();border?.let {setStroke(dp(1),it)}
    }
    fun gap(parent:LinearLayout,height:Int=12){parent.addView(View(context),LinearLayout.LayoutParams(1,dp(height)))}
    fun divider(parent:LinearLayout){parent.addView(View(context).apply {setBackgroundColor(Color.rgb(234,241,248))},LinearLayout.LayoutParams(-1,dp(1)))}
    fun card(parent:LinearLayout,color:Int=Color.WHITE,padding:Int=14):LinearLayout=column().apply {
        setPadding(dp(padding),dp(padding),dp(padding),dp(padding));background=background(color)
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(12)})
    }
    fun icon(name:String,color:Int=blue,size:Int=24)=ImageView(context).apply {
        setImageDrawable(if(name in setOf("trash","play","success","warning"))statusIcon(name,color,dp(size)) else HubIcon(name,color,dp(size)));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun statusIcon(name:String,color:Int,size:Int)=object:android.graphics.drawable.Drawable() {
        private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {this.color=color;strokeWidth=1.8f;strokeCap=android.graphics.Paint.Cap.ROUND;strokeJoin=android.graphics.Paint.Join.ROUND}
        override fun getIntrinsicWidth()=size
        override fun getIntrinsicHeight()=size
        override fun draw(canvas:android.graphics.Canvas){
            canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
            paint.color=color
            if(name=="success" || name=="warning"){
                paint.style=android.graphics.Paint.Style.FILL;canvas.drawCircle(12f,12f,11f,paint);paint.color=Color.WHITE;paint.style=android.graphics.Paint.Style.STROKE
                if(name=="success"){val path=android.graphics.Path().apply {moveTo(6f,12f);lineTo(10f,16f);lineTo(18f,8f)};canvas.drawPath(path,paint)}
                else {canvas.drawLine(12f,6f,12f,13f,paint);canvas.drawPoint(12f,17.5f,paint)}
            }else if(name=="play"){
                paint.style=android.graphics.Paint.Style.FILL;canvas.drawPath(android.graphics.Path().apply {moveTo(7f,3f);lineTo(21f,12f);lineTo(7f,21f);close()},paint)
            }else{
                paint.style=android.graphics.Paint.Style.STROKE;canvas.drawLine(4f,6f,20f,6f,paint);canvas.drawLine(9f,3f,15f,3f,paint);canvas.drawRoundRect(6f,6f,18f,22f,2f,2f,paint);canvas.drawLine(10f,10f,10f,18f,paint);canvas.drawLine(14f,10f,14f,18f,paint)
            };canvas.restore()
        }
        override fun setAlpha(alpha:Int){paint.alpha=alpha;invalidateSelf()}
        override fun setColorFilter(filter:android.graphics.ColorFilter?){paint.colorFilter=filter;invalidateSelf()}
        @Deprecated("Deprecated in Java") override fun getOpacity()=android.graphics.PixelFormat.TRANSLUCENT
    }
    fun brand(channel:String,size:Int=46)=text(when(channel){"jd"->"JD";"taobao"->"淘";"cainiao"->"菜";"douyin"->"抖";else->"拼"},if(size>40)27 else 21,true,Color.WHITE).apply {
        gravity=Gravity.CENTER;background=background(if(channel=="cainiao")blue else PlatformStyle.accent(channel),10)
        layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
    }
    fun badge(value:String,color:Int=green)=text(value,12,true,color).apply {
        setPadding(dp(10),dp(6),dp(10),dp(6));background=background(Color.argb(22,Color.red(color),Color.green(color),Color.blue(color)),10)
    }
    fun button(value:String,primary:Boolean=true,quiet:Boolean=false,action:()->Unit)=MaterialButton(context).apply {
        text=value;textSize=16f;includeFontPadding=false;isAllCaps=false
        minHeight=dp(48);minimumHeight=dp(48);minWidth=0;minimumWidth=0;maxLines=2
        insetTop=0;insetBottom=0;cornerRadius=dp(10);setPadding(dp(14),dp(9),dp(14),dp(9))
        backgroundTintList=ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(Color.rgb(222,228,238),if(primary)blue else if(quiet)pale else Color.WHITE))
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(muted,if(primary)Color.WHITE else blue)));strokeWidth=if(!primary && !quiet)dp(1) else 0
        strokeColor=ColorStateList.valueOf(Color.rgb(126,191,255));setOnClickListener {action()}
    }
    fun fullButton(parent:LinearLayout,title:String,primary:Boolean=true,quiet:Boolean=false,action:()->Unit):MaterialButton {
        val result=button(title,primary,quiet,action);parent.addView(result,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8)})
        return result
    }
    fun buttons(parent:LinearLayout,left:String,right:String,onLeft:()->Unit,onRight:()->Unit) {
        val line=ParcelActionsLayout(context)
        line.addView(button(left,false,action=onLeft));line.addView(button(right,true,action=onRight))
        parent.addView(line,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12)})
    }
    fun notice(parent:LinearLayout,title:String,message:String="",tone:String="info"):LinearLayout {
        val tint=when(tone){"error"->red;"warning"->orange;"success"->green;else->blue}
        val surface=when(tone){"error"->Color.rgb(255,235,235);"warning"->Color.rgb(255,247,226);"success"->Color.rgb(233,251,242);else->pale}
        return card(parent,surface).apply {
            val line=row();line.gravity=Gravity.TOP
            line.addView(icon(if(tone=="success")"success" else if(tone=="warning" || tone=="error")"warning" else "info",tint,24),LinearLayout.LayoutParams(dp(24),dp(24)).apply {marginEnd=dp(10)})
            line.addView(column().apply {addView(text(title,15,true,tint));if(message.isNotBlank())addView(text(message,13,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(5)})},LinearLayout.LayoutParams(0,-2,1f))
            addView(line)
        }
    }
    fun actionRow(parent:LinearLayout,title:String,subtitle:String="",iconName:String="chevron",value:String="",tint:Int=blue,channel:String?=null,endIcon:String?="chevron",onClick:()->Unit):LinearLayout {
        val line=row().apply {minimumHeight=dp(if(subtitle.isBlank())54 else 70);setPadding(dp(2),dp(10),dp(2),dp(10));isClickable=true;isFocusable=true;contentDescription=listOf(title,subtitle,value).filter {it.isNotBlank()}.joinToString("，");setOnClickListener {onClick()}}
        val image=if(channel!=null)brand(channel,42) else icon(iconName,tint,25)
        line.addView(image,LinearLayout.LayoutParams(dp(if(channel!=null)42 else 28),dp(if(channel!=null)42 else 28)).apply {marginEnd=dp(12)})
        line.addView(column().apply {addView(text(title,15,true));if(subtitle.isNotBlank())addView(text(subtitle,13,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(5)})},LinearLayout.LayoutParams(0,-2,1f))
        if(value.isNotBlank())line.addView(text(value,12,false,tint).apply {maxWidth=dp(114)},LinearLayout.LayoutParams(-2,-2).apply {marginStart=dp(8)})
        if(endIcon!=null)line.addView(icon(endIcon,muted,18),LinearLayout.LayoutParams(dp(18),dp(18)).apply {marginStart=dp(6)})
        parent.addView(line,LinearLayout.LayoutParams(-1,-2));return line
    }
    inner class Panel(title:String) {
        val dialog=BottomSheetDialog(context)
        val root=column()
        val content=column()
        val footer=column()
        private val heading=row().apply {setPadding(dp(22),0,dp(10),0);minimumHeight=dp(52)}
        private val headingTitle=text(title,20,true)
        init {
            root.background=GradientDrawable().apply {setColor(Color.WHITE);cornerRadii=floatArrayOf(dp(22).toFloat(),dp(22).toFloat(),dp(22).toFloat(),dp(22).toFloat(),0f,0f,0f,0f)}
            root.addView(View(context).apply {background=background(Color.rgb(211,220,233),4)},LinearLayout.LayoutParams(dp(44),dp(5)).apply {gravity=Gravity.CENTER_HORIZONTAL;topMargin=dp(10);bottomMargin=dp(8)})
            heading.addView(headingTitle,LinearLayout.LayoutParams(0,-2,1f))
            heading.addView(ImageButton(context).apply {setImageDrawable(HubIcon("close",muted,dp(22)));background=null;contentDescription="关闭";setOnClickListener {dialog.dismiss()}},LinearLayout.LayoutParams(dp(48),dp(48)))
            root.addView(heading)
            content.setPadding(dp(20),dp(8),dp(20),dp(16))
            root.addView(object:ScrollView(context) {
                override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
                    // LinearLayout measures this child before its footer. Reserve the footer
                    // explicitly so long diagnostics and the keyboard cannot push actions offscreen.
                    val width=View.MeasureSpec.getSize(widthMeasureSpec)
                    footer.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                    var headingHeight=0
                    for(index in 0 until root.indexOfChild(this)) {
                        val child=root.getChildAt(index);val params=child.layoutParams as? ViewGroup.MarginLayoutParams
                        headingHeight+=child.measuredHeight+(params?.topMargin ?: 0)+(params?.bottomMargin ?: 0)
                    }
                    val screenLimit=(context.resources.displayMetrics.heightPixels*.9f).toInt()-headingHeight-footer.measuredHeight
                    val incoming=if(View.MeasureSpec.getMode(heightMeasureSpec)==View.MeasureSpec.UNSPECIFIED)screenLimit else View.MeasureSpec.getSize(heightMeasureSpec)-footer.measuredHeight
                    val limit=minOf(screenLimit,incoming).coerceAtLeast(dp(1))
                    super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST))
                }
            }.apply {isFillViewport=false;addView(content)},LinearLayout.LayoutParams(-1,-2))
            footer.setPadding(dp(20),0,dp(20),dp(20));root.addView(footer)
            dialog.setContentView(root)
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        fun show() {
            dialog.show()
            val bottom=dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottom?.setBackgroundColor(Color.TRANSPARENT)
            dialog.behavior.apply {maxHeight=(context.resources.displayMetrics.heightPixels*.9f).toInt();state=BottomSheetBehavior.STATE_EXPANDED;skipCollapsed=true}
        }
        fun close(){dialog.dismiss()}
        fun hideTitle(){heading.visibility=View.GONE}
        fun brandTitle(channel:String) {
            heading.addView(brand(channel,46),0,LinearLayout.LayoutParams(dp(46),dp(46)).apply {marginEnd=dp(12)})
            headingTitle.textSize=18f
        }
        fun resultHeader(status:String="") {
            // Result sheets use a separate grip/close rail, leaving title and outcome aligned.
            val close=heading.getChildAt(heading.childCount-1);heading.removeView(close)
            val grip=root.getChildAt(0);root.removeView(grip)
            val rail=FrameLayout(context)
            rail.addView(grip,FrameLayout.LayoutParams(dp(44),dp(5),Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {topMargin=dp(16)})
            rail.addView(close,FrameLayout.LayoutParams(dp(48),dp(38),Gravity.TOP or Gravity.END).apply {marginEnd=dp(8)})
            root.addView(rail,0,LinearLayout.LayoutParams(-1,dp(40)))
            val resultHeight=minOf(dp(680),(context.resources.displayMetrics.heightPixels*.9f).toInt())
            root.minimumHeight=resultHeight
            root.layoutParams=(root.layoutParams ?: FrameLayout.LayoutParams(-1,-2)).apply {height=resultHeight}
            // Only result sheets reserve their extra height above the real footer.
            // Actions that belong to the result content keep their existing position.
            root.addView(View(context).apply {importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},root.indexOfChild(footer),LinearLayout.LayoutParams(1,0,1f))
            heading.setPadding(dp(22),0,dp(22),0)
            if(status.isNotBlank())heading.addView(text(status,13,true,green),LinearLayout.LayoutParams(-2,-2).apply {marginStart=dp(10)})
        }
    }
    fun panel(title:String)=Panel(title)
    /** The local-collected confirmation is a centered card in board 14-2, not a sheet. */
    inner class CenterPanel(title:String) {
        private val dialog=android.app.Dialog(context)
        private val root=column()
        val content=column()
        val footer=column()
        init {
            root.background=background(Color.WHITE,20)
            root.setPadding(dp(20),dp(22),dp(20),dp(20))
            val heading=text(title,20,true).apply {gravity=Gravity.CENTER}
            root.addView(heading,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(18)})
            root.addView(object:ScrollView(context) {
                override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
                    footer.measure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                    val max=(context.resources.displayMetrics.heightPixels*.82f).toInt()-root.paddingTop-root.paddingBottom-heading.measuredHeight-dp(18)-footer.measuredHeight
                    super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(max.coerceAtLeast(dp(1)),View.MeasureSpec.AT_MOST))
                }
            }.apply {addView(content)},LinearLayout.LayoutParams(-1,-2))
            root.addView(footer)
            dialog.setContentView(root)
        }
        fun show() {
            dialog.show()
            dialog.window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                setLayout(minOf(context.resources.displayMetrics.widthPixels-dp(48),dp(420)),ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER);setDimAmount(.38f)
            }
        }
        fun close(){dialog.dismiss()}
    }
    fun center(title:String)=CenterPanel(title)
}
