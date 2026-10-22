package com.halo.expressassistant.hub

/** Staging is not coverage: only explicit successful commit publishes a boundary. */
internal class SyncCoverageProofs<T> {
    private val pending=java.util.concurrent.ConcurrentHashMap<String,T>()
    fun stage(key:String,proof:T){pending[key]=proof}
    fun clear(){pending.clear()}
    fun commit(publish:(T)->Boolean) {
        pending.entries.toList().forEach {(key,proof)->
            if(publish(proof))pending.remove(key,proof)
        }
    }
}
