package com.halo.expressassistant.hub

/** The official code belongs to the selected account, not merely a loaded URL. */
class IdentitySessionPolicy(private val channel: String, private val expectedCookies: String) {
    enum class State { WAITING, ACTIVE, ACCOUNT_UNAVAILABLE, ACCOUNT_CHANGED }
    private var state = State.WAITING
    val mayPersist: Boolean get() = state == State.ACTIVE

    fun check(accountAvailable: Boolean, liveCookies: String): State {
        if (state == State.ACCOUNT_UNAVAILABLE || state == State.ACCOUNT_CHANGED) return state
        state = when {
            !accountAvailable -> State.ACCOUNT_UNAVAILABLE
            !sameOwner(liveCookies) -> State.ACCOUNT_CHANGED
            else -> State.ACTIVE
        }
        return state
    }

    private fun sameOwner(liveCookies: String) = matchesOwner(channel, expectedCookies, liveCookies)

    companion object {
        fun allowsEmbeddedPage(channel: String) = channel in setOf("jd", "taobao", "cainiao")
        fun matchesOwner(channel:String, expectedCookies:String, liveCookies:String):Boolean {
            val live=CookiePairs.parse(liveCookies)
            val expected=CookiePairs.parse(expectedCookies)
            return when(channel) {
                "jd"->CookiePairs.sameJdAccount(live,expected)
                "taobao","cainiao"->CookiePairs.sameTaobaoAccount(live,expected)
                "pdd"->CookiePairs.samePddAccount(live,expected)
                "douyin"->DouyinSessionPolicy.sameOwner(expectedCookies,liveCookies)
                else->false
            }
        }
    }
}
