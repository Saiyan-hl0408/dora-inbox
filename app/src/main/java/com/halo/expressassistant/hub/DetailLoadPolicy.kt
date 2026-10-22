package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressItem

/** Refresh only replaces usable trace data; errors never erase the visible cached snapshot. */
object DetailLoadPolicy {
    data class State(val points:List<DetailPoint> = emptyList(),val refreshing:Boolean=false,val message:String="",val failed:Boolean=false)
    fun cached(points:List<DetailPoint>)=State(DetailPresentation.points(points))
    fun start(old:State)=old.copy(refreshing=true,message="正在刷新详情，已有信息保留",failed=false)
    fun success(old:State,fresh:List<DetailPoint>):State {
        val usable=DetailPresentation.points(fresh)
        return State(if(usable.isEmpty())old.points else DetailPresentation.points(usable+old.points),message=if(usable.isEmpty())"暂未取得更多物流信息，已有信息保留" else "")
    }
    fun failure(old:State)=old.copy(refreshing=false,message="详情读取未完成，已有信息保留。请重试或在官方页面核对。",failed=true)
    /** An official waybill can arrive before the first trace. An unchanged order ID is not one. */
    fun mayApplyMetadata(item:ExpressItem,number:String,points:List<DetailPoint>):Boolean =
        points.any {it.context.isNotBlank()} ||
            (DetailPresentation.hasWaybill(item.copy(mailNo=number,isOrderNumber=false)) && (!item.isOrderNumber || number!=item.mailNo))
}
