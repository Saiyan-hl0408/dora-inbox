package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

/** A purchase-window checkpoint never freezes the later life of a known physical order. */
object ActiveParcelRefresh {
    fun includes(item:ExpressItem)=item.source in setOf("jd","taobao","pdd") && !ParcelEligibility.isVirtual(item) &&
        ParcelPresentation.stage(item) in setOf(ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.TRANSIT,
            ParcelPresentation.Stage.UNVERIFIED,ParcelPresentation.Stage.NOT_SHIPPED)
}
