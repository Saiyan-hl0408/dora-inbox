package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint

object LatestLogistics {
    fun point(points:List<DetailPoint>):DetailPoint?=points.maxByOrNull {ParcelTime.parse(it.time)}
}
