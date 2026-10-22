package com.halo.expressassistant.ui

/** The QR flow can use the uid/uin pair instead of the SMS access-token cookie. */
object PddLoginSession {
    fun hasCredentials(keys:Map<String,String>):Boolean {
        val identity=listOf("pdduid","pdd_user_id","PASS_ID","uid").any { !keys[it].isNullOrBlank() }
        return identity && (!keys["PDDAccessToken"].isNullOrBlank() ||
            (!keys["pdd_user_id"].isNullOrBlank() && !keys["pdd_user_uin"].isNullOrBlank()))
    }
}
