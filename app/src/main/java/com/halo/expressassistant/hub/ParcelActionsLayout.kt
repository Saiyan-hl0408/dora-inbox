package com.halo.expressassistant.hub

import android.content.Context
import android.graphics.Rect
import android.graphics.Color
import android.content.res.ColorStateList
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import kotlin.math.ceil

/** Allocate width from actual font metrics; keep labels and text size on small screens. */
class ParcelActionsLayout(context:Context):ViewGroup(context) {
    private val positions=mutableListOf<Rect>()
    private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()
    override fun generateDefaultLayoutParams()=LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT)
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec)
        val gap=dp(8);val usable=width-paddingLeft-paddingRight
        val buttons=(0 until childCount).map {getChildAt(it) as MaterialButton}
        if(buttons.isEmpty()){setMeasuredDimension(resolveSize(width,widthMeasureSpec),resolveSize(paddingTop+paddingBottom,heightMeasureSpec));return}
        val needs=buttons.map {ceil(it.paint.measureText(it.text.toString())).toInt()+it.paddingLeft+it.paddingRight+(if(it.icon!=null)it.iconSize+it.iconPadding else 0)+dp(4)}
        val rows=when {
            resources.configuration.fontScale>1.15f->buttons.indices.map {listOf(it)}
            needs.sum()+gap*(childCount-1)<=usable->listOf(buttons.indices.toList())
            childCount==3 && needs[1]+needs[2]+gap<=usable->listOf(listOf(0),listOf(1,2))
            else->buttons.indices.map {listOf(it)}
        }
        buttons.forEach {button->
            if(button.tag!="secondary" && button.contentDescription?.toString()?.contains("身份码")==true) {
                val expanded=rows.size>1
                button.backgroundTintList=ColorStateList.valueOf(if(expanded)HubUi.accent else Color.rgb(232,243,255))
                button.setTextColor(if(expanded)Color.WHITE else HubUi.accent)
                button.icon=HubIcon("identity",if(expanded)Color.WHITE else HubUi.accent,dp(20))
            }
        }
        positions.clear();repeat(childCount){positions.add(Rect())}
        var y=paddingTop
        for(row in rows) {
            val extra=(usable-gap*(row.size-1)-row.sumOf {needs[it]}).coerceAtLeast(0)
            var x=paddingLeft;var rowHeight=dp(48)
            for((column,index) in row.withIndex()) {
                val secondary=row.lastOrNull()?.takeIf {row.size==2 && buttons[it].tag=="secondary"}
                val share=if(secondary!=null){if(index==secondary)0 else extra}else extra/row.size+(if(column==row.lastIndex)extra%row.size else 0)
                val w=if(row.size==1)usable else needs[index]+share
                buttons[index].measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
                rowHeight=maxOf(rowHeight,buttons[index].measuredHeight)
                positions[index].set(x,y,x+w,y+buttons[index].measuredHeight);x+=w+gap
            }
            for(index in row)positions[index].bottom=y+rowHeight
            y+=rowHeight+gap
        }
        setMeasuredDimension(resolveSize(width,widthMeasureSpec),resolveSize(y-gap+paddingBottom,heightMeasureSpec))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        for(i in 0 until childCount)positions[i].let {getChildAt(i).layout(it.left,it.top,it.right,it.bottom)}
    }
}
