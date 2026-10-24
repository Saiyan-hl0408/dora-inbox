package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import com.halo.expressassistant.data.ExpressItem

/** Official node headings determine the physical parcel stage, not residual instructions. */
object PddLogistics {
    /** Forwarding warehouses are not recipient pickup stations. Use the current event only. */
    fun forwarding(text:String):Boolean {
        if(listOf("凭取件码","等待取件","本人签收","已取件","已领取").any(text::contains))return false
        return (text.contains("已入库") && text.contains("将尽快发出")) ||
            (text.contains("中转仓") && listOf("换单发往","装车发往","将尽快发往").any(text::contains))
    }
    fun status(text:String):String {
        return if(forwarding(text))"运输中" else ParcelStatusAliases.heading(text)
    }
    fun stage(status:String):ParcelPresentation.Stage? = ParcelStatusAliases.stage(status)
    fun apply(item:ExpressItem,points:List<DetailPoint>,officialStatus:String=""):ExpressItem {
        val latest=LatestLogistics.point(points) ?: return item
        val at=ParcelTime.parse(latest.time)
        if(item.logisticsVersion>0 && at>0 && at<item.statusUpdatedAt)return item
        val heading=if(at>0 && forwarding(latest.context))"运输中" else officialStatus.ifBlank {status(latest.context)}
        val stage=stage(heading)
        val arrival=points.filter {stage(status(it.context))==ParcelPresentation.Stage.PICKUP}
            .map {ParcelTime.parse(it.time)}.filter {it>0}.minOrNull() ?: item.arrivedAt
        val code=points.sortedByDescending {ParcelTime.parse(it.time)}.firstNotNullOfOrNull {
            PddPickupCodes.fromText(it.context,item.mailNo).takeIf(String::isNotBlank)
        }.orEmpty()
        val updated=item.copy(
            logisticsStatus=heading.ifBlank {item.logisticsStatus},logisticsVersion=if(stage!=null)1 else item.logisticsVersion,
            state=when(stage){ParcelPresentation.Stage.CLOSED->3;ParcelPresentation.Stage.PICKUP->0;ParcelPresentation.Stage.AFTERSALE->4;ParcelPresentation.Stage.TRANSIT->if(heading.startsWith("派"))5 else 0;else->item.state},
            stateNum=when(stage){ParcelPresentation.Stage.CLOSED->107;ParcelPresentation.Stage.PICKUP->106;ParcelPresentation.Stage.TRANSIT->104;else->item.stateNum},
            stateName=heading.ifBlank {item.stateName},latestText=latest.context.take(400),latestTime=latest.time,
            statusUpdatedAt=at.takeIf {it>0} ?: item.statusUpdatedAt,
            completedAt=if(stage==ParcelPresentation.Stage.CLOSED)at else if(stage!=null)0 else item.completedAt,
            arrivedAt=arrival
        )
        return PddPickupPayload.reconcile(PddPickupCodes.apply(updated,code))
    }
}
