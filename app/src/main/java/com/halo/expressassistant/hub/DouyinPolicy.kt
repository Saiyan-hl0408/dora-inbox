package com.halo.expressassistant.hub

/** Owner comparison is conservative; HTTP verification independently checks the actual user id. */
object DouyinSessionPolicy {
    fun hasCredentials(cookies:String)=CookiePairs.parse(cookies).let {m->listOf("sessionid","sessionid_ss").any {!m[it].isNullOrBlank()}}
    fun sameOwner(a:String,b:String):Boolean {
        val x=CookiePairs.parse(a);val y=CookiePairs.parse(b)
        val identities=listOf("uid_tt","uid_tt_ss").filter {!x[it].isNullOrBlank() && !y[it].isNullOrBlank()}
        if(identities.isNotEmpty())return identities.all {x[it]==y[it]}
        return listOf("sessionid","sessionid_ss").any {!x[it].isNullOrBlank() && x[it]==y[it]}
    }
    fun fingerprint(cookies:String)=CookiePairs.parse(cookies).let {it["sessionid"] ?: it["sessionid_ss"].orEmpty()}
}

/** A page budget is a partial result, and old cached active ids must not be date-truncated. */
class DouyinPagingPolicy(val since:Long,val until:Long,val activeIds:Set<String> = emptySet()) {
    private val seen=hashSetOf<String>();private val anchors=hashSetOf<String>();private var oldestPrevious=Long.MAX_VALUE
    var ordered=true;private set
    var missingDates=false;private set
    val unresolvedActive:Set<String> get()=activeIds-seen
    fun include(order:DouyinOrder)=order.id in activeIds || order.createdAt in since..until
    fun accept(page:DouyinOrderPayload.Page):String? {
        val fresh=page.orders.count {seen.add(it.id)}
        val dates=page.orders.map {it.createdAt};missingDates=missingDates || dates.any {it<=0}
        val valid=dates.filter {it>0};if(valid.isNotEmpty()){if(valid.max()>oldestPrevious)ordered=false;oldestPrevious=valid.min()}
        check(page.orders.isEmpty() || fresh>0){"抖音分页重复，读取未完成"}
        if(!page.hasMore)return "server_end"
        check(fresh>0){"抖音分页未推进，保留已有数据，请稍后重试"}
        return if(unresolvedActive.isEmpty() && ordered && !missingDates && dates.isNotEmpty() && dates.all {it in 1 until since})"order_time_boundary" else null
    }
    fun acceptAnchor(anchor:String){check(anchor.isNotBlank() && anchors.add(anchor)){"抖音分页游标未更新，读取未完成"}}
}
