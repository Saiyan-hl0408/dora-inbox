package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

/** Manual single-waybill results may create a record; automatic supplementation never does. */
object PickupImport {
    fun stage(result:CainiaoPickup.Result):ParcelPresentation.Stage?=when(result.outcome) {
        CainiaoPickup.Outcome.FOUND->ParcelPresentation.Stage.PICKUP
        CainiaoPickup.Outcome.COLLECTED->if(listOf("退回","退件","拒收").any(result.status::contains))ParcelPresentation.Stage.AFTERSALE else ParcelPresentation.Stage.CLOSED
        CainiaoPickup.Outcome.NO_CODE->if(result.pending)ParcelPresentation.Stage.PICKUP else ParcelStatusAliases.stage(result.status)
        else->null
    }
    fun reconcile(items:List<ExpressItem>,result:CainiaoPickup.Result,now:Long=System.currentTimeMillis()):List<ExpressItem> {
        val stage=stage(result) ?: return items
        if(!result.waybill.matches(Regex("[A-Za-z0-9]{8,32}")))return items
        val existing=items.firstOrNull {it.mailNo.equals(result.waybill,true)}
        // A shopping record owns its status; station history only supplements that record.
        if(existing!=null && existing.source in setOf("jd","pdd","taobao"))return items.map {old->
            if(old!==existing || ParcelPresentation.stage(old)!=ParcelPresentation.Stage.PICKUP || result.outcome!=CainiaoPickup.Outcome.FOUND)old
            else old.copy(pickupCode=result.code,pickupStation=result.station.ifBlank {old.pickupStation},
                pickupAddress=result.address.ifBlank {old.pickupAddress},pickupLocationVersion=1,
                pickupCodeSource="taobao-cainiao-school",pickupCodeCheckedAt=now,pickupQueryAccountId=result.accountId,
                pickupQueryOutcome=result.outcome.name,pickupVerificationNeeded=false)
        }
        fun realTime(raw:String)=ParcelTime.parse(raw,now).takeIf {it in 1..now} ?: 0L
        val signed=if(stage==ParcelPresentation.Stage.CLOSED)realTime(result.signedAt) else 0L
        val item=ExpressItem(id=existing?.id ?: "query-${result.waybill.uppercase()}",companyCode=result.companyCode,
            companyName=existing?.companyName ?: "快递",mailNo=existing?.mailNo ?: result.waybill,
            originalName=existing?.originalName ?: "快递包裹",source="cainiao",provider="淘宝菜鸟查询",identityChannel="taobao",pickupQueryAccountId=result.accountId,
            state=if(stage==ParcelPresentation.Stage.CLOSED)3 else if(stage==ParcelPresentation.Stage.AFTERSALE)4 else 0,
            stateName=when(stage){ParcelPresentation.Stage.PICKUP->"待取件";ParcelPresentation.Stage.CLOSED->"已签收";ParcelPresentation.Stage.AFTERSALE->"已退回";else->result.status},
            latestText=result.status,logisticsStatus=result.status,latestTime=if(stage==ParcelPresentation.Stage.CLOSED)result.signedAt else result.arrivedAt,
            completedAt=signed,arrivedAt=realTime(result.arrivedAt),statusUpdatedAt=if(stage==ParcelPresentation.Stage.CLOSED)signed else realTime(result.arrivedAt),
            pickupCode=if(stage==ParcelPresentation.Stage.PICKUP)result.code else "",historicalPickupCode=result.historicalCode,
            pickupStation=result.station,pickupAddress=result.address,pickupLocationVersion=1,
            pickupCodeSource="taobao-cainiao-school",pickupCodeCheckedAt=now,pickupQueryOutcome=result.outcome.name,observedAt=now)
        return if(existing==null)items+item else items.map {if(it===existing)item else it}
    }
}
