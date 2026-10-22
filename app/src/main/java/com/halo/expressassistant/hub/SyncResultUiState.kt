package com.halo.expressassistant.hub

/** A zero count alone is never proof of an empty completed range. */
data class SyncResultUiState(
    val count:Int,
    val error:String?,
    val days:Int,
    val readAt:Long,
    val boundaryComplete:Boolean,
    val cachedRangeEmpty:Boolean
) {
    val emptyVerified:Boolean get()=count==0 && error==null && boundaryComplete && cachedRangeEmpty && readAt>0
}
