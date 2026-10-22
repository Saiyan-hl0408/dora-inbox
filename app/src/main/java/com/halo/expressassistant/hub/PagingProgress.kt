package com.halo.expressassistant.hub

/** Stop a stalled page with a partial-result error, rather than calling it a completed history. */
class PagingProgress {
    private var previous=-1
    private var unchanged=0
    fun stalled(responseCount:Int):Boolean {
        unchanged=if(responseCount==previous)unchanged+1 else 0
        previous=responseCount
        return unchanged>=5
    }
    fun reset(){previous=-1;unchanged=0}
}
