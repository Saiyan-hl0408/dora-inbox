package com.halo.expressassistant.hub

/** Cookie values may contain '='; replace by name and add absent refreshed tokens. */
object CookiePairs {
    fun sameTaobaoAccount(live:Map<String,String>,saved:Map<String,String>):Boolean {
        if(!live["unb"].isNullOrBlank() && !saved["unb"].isNullOrBlank())return live["unb"]==saved["unb"]
        return !live["cookie1"].isNullOrBlank() && live["cookie1"]==saved["cookie1"]
    }
    fun samePddAccount(live:Map<String,String>,saved:Map<String,String>):Boolean {
        val shared=listOf("pdd_user_id","pdduid","uid").filter {!live[it].isNullOrBlank() && !saved[it].isNullOrBlank()}
        return shared.isNotEmpty() && shared.all {live[it]==saved[it]}
    }
    fun sameJdAccount(live:Map<String,String>,saved:Map<String,String>):Boolean {
        val identityKeys=listOf("pt_pin","pin","wq_uin")
        val shared=identityKeys.filter {!live[it].isNullOrBlank() && !saved[it].isNullOrBlank()}
        return shared.isNotEmpty() && shared.all {live[it]==saved[it]}
    }
    fun parse(header: String): LinkedHashMap<String, String> = linkedMapOf<String, String>().apply {
        header.split(';').forEach { part ->
            val pair = part.trim().split('=', limit = 2)
            if (pair.size == 2 && pair[0].isNotBlank()) put(pair[0], pair[1])
        }
    }
    fun merge(header: String, updates: Map<String, String>): String = parse(header).apply { putAll(updates) }
        .entries.joinToString("; ") { "${it.key}=${it.value}" }
}
