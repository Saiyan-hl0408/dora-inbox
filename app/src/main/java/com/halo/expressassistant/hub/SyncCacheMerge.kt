package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

object SyncCacheMerge {
    /** Reconcile against the store at commit time, including codes added while platform requests ran. */
    fun withLatest(latest:List<ExpressItem>,fresh:List<ExpressItem>):List<ExpressItem> {
        fun owner(item:ExpressItem)=Triple(item.source,item.accountId,if(item.source=="pdd")item.id else item.mailNo)
        val byOwner=latest.associateBy(::owner)
        val merged=fresh.map {item->byOwner[owner(item)]?.let {merge(it,item)} ?: item}
        val numbers=merged.mapTo(HashSet()) {it.mailNo.uppercase()}
        return merged+latest.filter {it.source=="cainiao" && it.mailNo.uppercase() !in numbers}
    }
    fun merge(old:ExpressItem,fresh:ExpressItem):ExpressItem {
        if(fresh.source=="pdd" && (old.source!=fresh.source || old.accountId!=fresh.accountId || old.id!=fresh.id))return fresh
        if(old.source=="douyin" && fresh.source=="douyin" && old.accountId==fresh.accountId)return old.copy(
            companyCode=fresh.companyCode.ifBlank {old.companyCode},companyName=fresh.companyName.ifBlank {old.companyName},
            originalName=fresh.originalName.ifBlank {old.originalName},orderStatus=fresh.orderStatus.ifBlank {old.orderStatus},
            orderCreatedAt=fresh.orderCreatedAt.takeIf {it>0} ?: old.orderCreatedAt,jumpLinks=fresh.jumpLinks.ifBlank {old.jumpLinks},observedAt=maxOf(old.observedAt,fresh.observedAt))
        if(old.source=="cainiao" && fresh.source=="cainiao" && old.observedAt>fresh.observedAt)return old
        val prompt=fresh.source=="pdd" && fresh.latestText in setOf("待收货","待发货","交易成功","待评价","已评价","待付款","待成团")
        val keepPddTrace=fresh.source=="pdd" && old.logisticsVersion>0 &&
            (fresh.logisticsVersion==0 || fresh.statusUpdatedAt<old.statusUpdatedAt)
        val changedPddStatus=keepPddTrace && fresh.logisticsStatus.isNotBlank() && fresh.logisticsStatus!=old.logisticsStatus
        val migrateJdTime=fresh.source=="jd" && fresh.logisticsVersion>=21 && old.logisticsVersion<21
        val keepCainiao=fresh.source=="pdd" && old.pickupCode.isNotBlank() && old.pickupCodeSource.startsWith("taobao-cainiao") &&
            !fresh.pickupCodeSource.startsWith("taobao-cainiao") &&
            !(fresh.pickupQueryOutcome in setOf("NO_CODE","NO_MATCH","COLLECTED") && fresh.pickupCodeCheckedAt>old.pickupCodeCheckedAt)
        val clearPddCode=fresh.source=="pdd" && (
            (fresh.pickupQueryOutcome in setOf("NO_CODE","NO_MATCH","COLLECTED") && fresh.pickupCodeCheckedAt>old.pickupCodeCheckedAt && fresh.pickupCode.isBlank()) ||
            (old.pickupQueryOutcome=="COLLECTED" && fresh.statusUpdatedAt<=old.pickupCodeCheckedAt &&
                !(fresh.pickupQueryOutcome=="FOUND" && fresh.pickupCodeCheckedAt>old.pickupCodeCheckedAt)))
        val freshPddLookup=fresh.source=="pdd" && fresh.pickupQueryOutcome in setOf("NO_CODE","NO_MATCH","COLLECTED") && fresh.pickupCodeCheckedAt>old.pickupCodeCheckedAt
        val service=if(fresh.pddPickupServiceAt>=old.pddPickupServiceAt)fresh else old
        return PddPickupPayload.reconcile(fresh.copy(
            pddReadIntegrityVersion=maxOf(old.pddReadIntegrityVersion,fresh.pddReadIntegrityVersion),
            pddReadRepairRequired=if(fresh.pddReadIntegrityVersion>0)false else old.pddReadRepairRequired || fresh.pddReadRepairRequired,
            originalName=fresh.originalName.takeIf(GoodsNamePolicy::usable) ?: old.originalName.takeIf(GoodsNamePolicy::usable).orEmpty(),
            orderImageUrl=fresh.orderImageUrl.ifBlank {old.orderImageUrl},orderGoodsCount=fresh.orderGoodsCount.ifBlank {old.orderGoodsCount},
            orderWaybill=fresh.orderWaybill.ifBlank {old.orderWaybill},
            mailNo=if(fresh.isOrderNumber && !old.isOrderNumber)old.mailNo else fresh.mailNo,
            isOrderNumber=fresh.isOrderNumber && old.isOrderNumber,
            companyName=fresh.companyName.ifBlank {old.companyName},
            latestText=if(prompt || keepPddTrace)old.latestText.ifBlank {fresh.latestText} else fresh.latestText,
            latestTime=if(prompt || keepPddTrace)old.latestTime.ifBlank {fresh.latestTime} else fresh.latestTime,
            pickupCode=if(clearPddCode)"" else if(keepCainiao)old.pickupCode else fresh.pickupCode.ifBlank {old.pickupCode},
            pickupCodeSource=if(clearPddCode)"" else if(keepCainiao || fresh.pickupCode.isBlank())old.pickupCodeSource else fresh.pickupCodeSource,
            pddPickupCode=fresh.pddPickupCode.ifBlank {if(fresh.pickupCodeSource==PddPickupCodes.SOURCE)fresh.pickupCode else old.pddPickupCode},
            pddPickupCodeAt=maxOf(fresh.pddPickupCodeAt,old.pddPickupCodeAt),
            pddPickupStation=fresh.pddPickupStation.ifBlank {old.pddPickupStation.ifBlank {if(PickupUiPolicy.pddStation(old))old.pickupStation else ""}},
            pickupQueryAccountId=fresh.pickupQueryAccountId.ifBlank {old.pickupQueryAccountId},
            identityChannel=fresh.identityChannel.ifBlank {old.identityChannel},
            pddPickupServiceState=service.pddPickupServiceState,pddPickupServiceAt=service.pddPickupServiceAt,pddPickupServiceCompletedAt=service.pddPickupServiceCompletedAt,
            pddPickupServiceCode=service.pddPickupServiceCode,pddPickupServiceStation=service.pddPickupServiceStation,
            pickupCodeCheckedAt=fresh.pickupCodeCheckedAt.takeIf {it>0} ?: old.pickupCodeCheckedAt,
            pickupQueryOutcome=fresh.pickupQueryOutcome.ifBlank {old.pickupQueryOutcome},
            pickupVerificationNeeded=fresh.pickupVerificationNeeded || (fresh.pickupCode.isBlank() && old.pickupVerificationNeeded),
            pickupStation=if(freshPddLookup)fresh.pickupStation.ifBlank {old.pickupStation} else if(keepCainiao)old.pickupStation.ifBlank {fresh.pickupStation} else if(fresh.source=="jd" && fresh.pickupLocationVersion>0)fresh.pickupStation else fresh.pickupStation.ifBlank {old.pickupStation},
            pickupAddress=if(freshPddLookup){if(fresh.pickupCode.isNotBlank() && fresh.pickupCodeSource==PddPickupCodes.SOURCE && fresh.pickupStation.isNotBlank() && fresh.pickupStation!=old.pickupStation)fresh.pickupAddress else fresh.pickupAddress.ifBlank {old.pickupAddress}} else if(keepCainiao)old.pickupAddress.ifBlank {fresh.pickupAddress} else if(fresh.source=="jd" && fresh.pickupLocationVersion>0)fresh.pickupAddress else fresh.pickupAddress.ifBlank {old.pickupAddress},
            recipientPhoneTail=fresh.recipientPhoneTail.ifBlank {old.recipientPhoneTail},
            deliveryAddress=fresh.deliveryAddress.ifBlank {old.deliveryAddress},
            pickupHint=fresh.pickupHint.ifBlank {old.pickupHint},
            tracked=old.tracked,addressId=old.addressId,notifiedText=old.notifiedText,notifiedTime=old.notifiedTime,
            stateOverride=old.stateOverride,partitionOverride=old.partitionOverride,
            aiProgress=old.aiProgress,aiEta=old.aiEta,aiProgressAt=old.aiProgressAt,
            state=if(keepPddTrace && !changedPddStatus)old.state else fresh.state,
            stateNum=if(keepPddTrace && !changedPddStatus)old.stateNum else fresh.stateNum,
            stateName=if(keepPddTrace && !changedPddStatus)old.stateName else fresh.stateName,
            logisticsStatus=if(keepPddTrace && !changedPddStatus)old.logisticsStatus else fresh.logisticsStatus,
            logisticsVersion=if(keepPddTrace && !changedPddStatus)old.logisticsVersion else fresh.logisticsVersion,
            statusUpdatedAt=if(keepPddTrace && !changedPddStatus)old.statusUpdatedAt else fresh.statusUpdatedAt,
            completedAt=if(fresh.source=="pdd") {
                if(keepPddTrace && !changedPddStatus)old.completedAt else fresh.completedAt
            } else if(migrateJdTime)fresh.completedAt else fresh.completedAt.takeIf {it>0} ?: old.completedAt,
            orderCompletedAt=fresh.orderCompletedAt.takeIf {it>0} ?: old.orderCompletedAt,
            arrivedAt=if(migrateJdTime)fresh.arrivedAt else fresh.arrivedAt.takeIf {it>0} ?: old.arrivedAt))
    }
}
