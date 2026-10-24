package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

object PddReadRecoveryPolicy {
    fun reset(item:ExpressItem):ExpressItem {
        if(item.source!="pdd")return item
        val repair=ParcelPresentation.stage(item)!=ParcelPresentation.Stage.AFTERSALE && !ParcelEligibility.isVirtual(item)
        return item.copy(latestText=item.orderStatus,latestTime="",state=-1,stateNum=0,stateName=item.orderStatus,
            logisticsStatus="",logisticsVersion=0,statusUpdatedAt=0,completedAt=0,arrivedAt=0,
            pickupCode="",pickupCodeSource="",pickupStation="",pickupAddress="",pickupHint="",historicalPickupCode="",
            pddPickupCode="",pddPickupCodeAt=0,pddPickupStation="",pickupQueryOutcome="",pickupCodeCheckedAt=0,
            pddPickupServiceState="",pddPickupServiceAt=0,pddPickupServiceCompletedAt=0,pddPickupServiceCode="",pddPickupServiceStation="",
            pddReadIntegrityVersion=0,pddReadRepairRequired=repair)
    }
}
