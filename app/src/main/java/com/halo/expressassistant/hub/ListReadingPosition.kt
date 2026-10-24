package com.halo.expressassistant.hub

import kotlinx.serialization.Serializable

/** Anchor to the visible package, so inserts or changed card heights above it do not move reading. */
object ListReadingPosition {
    @Serializable data class Bookmark(val rowKey:String="",val offset:Int=0,val scrollY:Int=0)
    data class Row(val key:String,val top:Int,val height:Int)
    fun scope(history:Boolean,days:Int,filter:String,search:String)=listOf(history.toString(),if(history)days.toString() else "",filter,search).joinToString("") {"${it.length}:$it"}
    fun capture(scrollY:Int,rows:List<Row>):Bookmark {
        val y=scrollY.coerceAtLeast(0)
        if(y==0)return Bookmark()
        val row=rows.firstOrNull {it.top+it.height>y}
        return Bookmark(row?.key.orEmpty(),row?.let {y-it.top} ?: 0,y)
    }
    fun restore(bookmark:Bookmark?,rows:List<Row>,maxScroll:Int):Int {
        if(bookmark==null)return 0
        val row=rows.firstOrNull {it.key==bookmark.rowKey}
        return (row?.let {it.top+bookmark.offset} ?: bookmark.scrollY).coerceIn(0,maxScroll.coerceAtLeast(0))
    }
}
