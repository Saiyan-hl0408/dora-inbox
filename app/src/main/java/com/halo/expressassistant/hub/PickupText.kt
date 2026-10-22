package com.halo.expressassistant.hub

/** Pure parser: a waybill is never treated as a pickup code. */
object PickupText {
    data class Parsed(val codes: List<String>, val waybill: String, val platform: String)
    private val codePattern = Regex("""(?:取件验证码|自提码|驿站码|取件码|取货码|提货码|提货号|签收码|取件编号|取件编码|凭)\s*(?:为|是)?\s*[:：\[【（(“\"]*\s*([A-Za-z0-9]+(?:\s*[-—–－_]\s*[A-Za-z0-9]+){0,4})(?![A-Za-z0-9*＊])""")
    private val waybillPattern = Regex("""(?:快递单号|运单号|物流单号|(?<!订)(?<!订单)单号)\s*(?:为|是)?\s*[:：]?\s*([A-Za-z0-9]{8,32})(?![A-Za-z0-9])""")
    fun parse(text: String): Parsed {
        val normalized = text.replace(Regex("[—–－_]"), "-")
        val waybill = waybillPattern.find(normalized)?.groupValues?.get(1).orEmpty()
        val codes = codePattern.findAll(normalized).map { it.groupValues[1].replace(" ", "") }
            .filter { it.length in 2..24 && it.any(Char::isDigit) && it != waybill && !it.contains('*') }
            .filter { !it.matches(Regex("1[3-9][0-9]{9}")) && !(it.length >= 12 && it.all(Char::isDigit)) }
            .distinct().toList()
        val platform = when {
            text.contains("京东") -> "京东"
            text.contains("拼多多") || text.contains("多多买菜") -> "拼多多"
            text.contains("淘宝") || text.contains("菜鸟") -> "淘宝 / 菜鸟"
            else -> "未指定平台"
        }
        return Parsed(codes, waybill, platform)
    }
    fun hasParcelContext(text: String) = listOf("取件", "取货", "快递", "驿站", "提货", "包裹", "签收码", "自提码", "驿站码").any(text::contains)
}
