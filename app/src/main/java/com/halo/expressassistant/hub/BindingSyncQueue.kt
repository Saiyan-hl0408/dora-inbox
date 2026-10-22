package com.halo.expressassistant.hub

/** Binding return requests must survive another in-flight platform read. */
class BindingSyncQueue {
    data class Request(val channels:Set<String>?=null,val historyDays:Int?=null,val manual:Boolean=false)
    private val channels=linkedSetOf<String>()
    private val manual=linkedSetOf<String>()
    private var range:Int?=null
    fun request(channel:String) {if(channel in setOf("jd","pdd","taobao"))channels.add(channel)}
    fun take(working:Boolean,foreground:Boolean):Set<String> {
        if(working || !foreground)return emptySet()
        return channels.toSet().also {channels.clear()}
    }
    fun pending():Set<String> = channels.toSet()
    fun requestManual(channel:String){request(channel);if(channel in channels)manual.add(channel)}
    fun pendingManual():Set<String> = manual.toSet()
    fun requestRange(days:Int){range=HistoryRangePolicy.clamp(days)}
    fun pendingRange()=range
    /** A cached selection needs no replacement read, so keep explicit platform requests. */
    fun discardRange(){range=null}
    fun takeNext(working:Boolean,foreground:Boolean):Request? {
        if(working || !foreground)return null
        range?.let {days->clear();return Request(historyDays=days)}
        manual.firstOrNull()?.let {channel->manual.remove(channel);channels.remove(channel);return Request(channels=setOf(channel),manual=true)}
        val next=take(false,true)
        return next.takeIf {it.isNotEmpty()}?.let {Request(channels=it)}
    }
    fun clear(){channels.clear();manual.clear();range=null}
}
