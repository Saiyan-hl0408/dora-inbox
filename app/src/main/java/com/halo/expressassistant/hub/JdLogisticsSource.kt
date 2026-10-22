package com.halo.expressassistant.hub

object JdLogisticsSource {
    const val LIST_URL="https://kd.jd.com/express/scannedCopies?source=wlgwj&lang=zh-CN"
    const val LOGIN_URL="https://plogin.m.jd.com/login/login?appid=158&returnurl=https%3A%2F%2Fkd.jd.com%2Fexpress%2F%3Fsource%3Dwlgwj"
    const val COVERAGE_HINT="仅能查询收件手机号为当前登录账号或官方已验证关联手机号的京东快递。给其他手机号购买的包裹，请在京东物流「查快递」左上角进入「管理多账号」，完成绑定验证；同步时会默认查询所有已验证账号。官网列表最多提供近半年记录，选择更大范围时自动按半年查询；更早记录保留本机已缓存内容。"
    fun oldestAvailable(now:Long=System.currentTimeMillis()):Long=java.time.Instant.ofEpochMilli(now).atZone(ParcelTime.zone).minusMonths(6).toLocalDate().atStartOfDay(ParcelTime.zone).toInstant().toEpochMilli()
    fun detailUrl(number:String):String {
        require(number.matches(Regex("[A-Za-z0-9]{8,32}"))){"缺少有效快递运单号"}
        return "https://kd.jd.com/express/orderDetail?source=wlgwj&type=2&deliveryId=$number&isOpenGiftCard=0&lang=zh-CN"
    }
}
