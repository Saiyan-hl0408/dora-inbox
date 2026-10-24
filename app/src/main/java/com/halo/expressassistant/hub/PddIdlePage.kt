package com.halo.expressassistant.hub

/** One short-lived reusable page, scoped to the exact operation owner and session. */
class PddIdlePage<T>(private val lifetimeMs:Long=5000L) {
    private data class Entry<T>(val owner:Any,val session:String,val value:T,val savedAt:Long)
    private var entry:Entry<T>?=null
    fun take(owner:Any,session:String,now:Long,usable:(T)->Boolean,discard:(T)->Unit):T? {
        val saved=entry ?: return null;entry=null
        if(saved.owner===owner && saved.session==session && now-saved.savedAt in 0..lifetimeMs && usable(saved.value))return saved.value
        discard(saved.value);return null
    }
    fun put(owner:Any,session:String,value:T,now:Long,discard:(T)->Unit) {
        entry?.value?.takeIf {it!==value}?.let(discard)
        entry=Entry(owner,session,value,now)
    }
    fun expire(now:Long,discard:(T)->Unit) {
        val saved=entry ?: return
        if(now-saved.savedAt>=lifetimeMs){entry=null;discard(saved.value)}
    }
}
