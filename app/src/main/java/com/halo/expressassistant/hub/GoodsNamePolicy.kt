package com.halo.expressassistant.hub

/** A quantity, numeric ID or boolean cannot replace a readable product name. */
object GoodsNamePolicy {
    fun usable(value:String):Boolean {
        val text=value.trim()
        return text.any(Char::isLetter) && text.lowercase() !in setOf("null","true","false") &&
            !text.matches(Regex("[xX×*]\\s*\\d+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?\\s*(?:件|份|箱|个|盒|包|双)"))
    }
}
