package com.halo.expressassistant.data

import kotlinx.serialization.Serializable

@Serializable
data class JdGoods(
    val name: String = "",
    val imageUrl: String = "",
    val count: String = "",
    /** AI 优化后的短名（厂商+产品名，卡片一行） */
    val shortName: String = ""
)

/**
 * 多源绑定：某一平台的绑定账号（一组登录凭证 + 展示名 + 启停状态）。
 * payload 为平台私有 JSON：xiaomi={token,cUser,accountId,oaid,vaid,phones[]}；jd/tb/pdd={cookie}。
 */
@Serializable
data class BoundAccount(
    val id: String,
    val label: String = "",
    val enabled: Boolean = true,
    val payload: String = "",
    /** Legacy accounts may attempt an order read, but are not advertised as verified. */
    val taobaoOrdersEnabled:Boolean = false,
    val taobaoOrdersVerified:Boolean = false,
    val cainiaoVerified:Boolean = false
)

/** 多地址管理：一条收件地址（label 用于显示，address 为具体地址） */
@Serializable
data class HomeAddress(
    val id: String,
    val label: String = "地址",
    val address: String = ""
)

@Serializable
data class ExpressItem(
    val id: String,
    val companyCode: String,
    val companyName: String,
    val mailNo: String,
    val latestText: String = "",
    val latestTime: String = "",
    val state: Int = -1,
    val stateName: String = "",
    val phone: String? = null,
    val provider: String = "",
    val stateNum: Int = 0,
    val queryChannel: String = "",
    val iconUrl: String = "",
    val originalName: String = "",
    val eta: String = "",
    val tracked: Boolean = false,
    val notifiedText: String = "",
    val notifiedTime: String = "",
    val stateOverride: String = "",
    val partitionOverride: String = "",
    val aiProgress: Int = -1,
    val aiEta: String = "",
    val aiProgressAt: String = "",
    val jumpLinks: String = "",
    /** 数据来源渠道：xiaomi / jd / taobao / pdd */
    val source: String = "xiaomi",
    /** 多源绑定：该件来自哪个账号（BoundAccount.id） */
    val accountId: String = "",
    /** 多源绑定：归属账号展示名（详情页标注） */
    val accountLabel: String = "",
    /** 指定收件地址（HomeAddress.id；空 = 使用全局当前地址） */
    val addressId: String = "",
    /** 取件码（从轨迹文本解析，聚合显示） */
    val pickupCode: String = "",
    val orderStatus: String = "",
    val orderCreatedAt: Long = 0,
    val completedAt: Long = 0,
    val arrivedAt: Long = 0,
    val statusUpdatedAt: Long = 0,
    /** Some order lists omit the EXPRESS waybill; never query an order ID as a waybill. */
    val isOrderNumber: Boolean = false,
    val identityChannel: String = "",
    val pickupStation: String = "",
    val pickupAddress: String = "",
    val recipientPhoneTail: String = "",
    /** 1 = reconciled logistics destination; 0 = legacy location of unknown origin. */
    val pickupLocationVersion: Int = 0,
    /** Official current logistics status; kept independent of commerce confirmation. */
    val logisticsStatus: String = "",
    /** Commerce completion is only an upper bound for history scanning, never a signature time. */
    val orderCompletedAt: Long = 0,
    val logisticsVersion: Int = 0,
    val pickupHint: String = "",
    val deliveryAddress: String = "",
    /** Local read time, never a logistics event time. */
    val observedAt: Long = 0,
    /** Supplemental code provenance, independent of the shopping account and classification. */
    val pickupCodeSource: String = "",
    val pickupQueryAccountId: String = "",
    val pickupCodeCheckedAt: Long = 0,
    val pickupVerificationNeeded: Boolean = false,
    /** Latest supplemental lookup outcome; independent of shopping-platform logistics status. */
    val pickupQueryOutcome: String = "",
    val historicalPickupCode:String = "",
    /** Independently observed PDD code retained when Cainiao is selected; never an ID suffix. */
    val pddPickupCode:String = "",
    val pddPickupCodeAt:Long = 0,
    val pddPickupStation:String = "",
    /** Direct multi-duoduo pickup-service evidence; observation time is not a pickup event time. */
    val pddPickupServiceState:String = "",
    val pddPickupServiceAt:Long = 0,
    val pddPickupServiceCompletedAt:Long = 0,
    val pddPickupServiceCode:String = "",
    val pddPickupServiceStation:String = "",
    val pddReadIntegrityVersion:Int = 0,
    val pddReadRepairRequired:Boolean = false,
    /** Per-order product facts must survive shared terminal waybills. */
    val orderImageUrl:String = "",
    val orderGoodsCount:String = "",
    val orderWaybill:String = "",
    @kotlinx.serialization.Transient val relatedGoods:List<ParcelOrderGoods> = emptyList()
) {
    fun stateLabel(): String {
        if (stateOverride.isNotBlank()) return stateOverride
        if (stateName.isNotEmpty()) return stateName
        return when (state) {
            0 -> "运输中"
            1 -> "已揽件"
            3 -> "已签收"
            4 -> "已拒签"
            5 -> "派送中"
            else -> "未知"
        }
    }
}

data class ParcelOrderGoods(val orderId:String,val name:String,val imageUrl:String="",val count:String="",val originalWaybill:String="")

fun sectionKeyOf(item: ExpressItem): String {
    if (item.partitionOverride.isNotBlank()) return item.partitionOverride
    return when {
        item.stateNum == 105 || item.stateNum == 106 || item.state == 5 -> "delivering"
        item.stateNum in 106..107 || item.state == 3 -> "done"
        item.stateNum in 108..111 || item.state == 4 -> "abnormal"
        item.stateNum == 101 || item.stateNum == 103 || item.state == 1 -> "notshipped"
        else -> "shipped"
    }
}



fun progressFor(item: ExpressItem): Int = when (item.stateNum) {
    101 -> 5
    102 -> 15
    103 -> 25
    104 -> 55
    105 -> 85
    106 -> 95
    107 -> 100
    108, 109, 110, 111 -> 0
    else -> when (item.state) {
        1 -> 25
        3 -> 100
        5 -> 85
        0 -> 55
        else -> 0
    }
}

/**
 * 展示用进度：AI 结果 0% 只在“未发货/已揽收”阶段合理，
 * 一旦已经开始运输（状态码 104 及以上）就视为无效，走重算或默认映射。
 */
fun displayProgress(item: ExpressItem): Int {
    if (item.state == 3) return 100 // 已签收：进度即 100%，无需 AI 计算
    val p = item.aiProgress
    if (p < 0) return -1
    if (p == 0 && item.stateNum !in 101..103) return -1
    return p
}

@Serializable
data class ExpressDetail(
    val mailNo: String,
    val companyName: String,
    val state: Int,
    val isReceived: Boolean,
    val data: List<DetailPoint> = emptyList(),
    val eta: String = ""
)

@Serializable
data class DetailPoint(
    val context: String,
    val time: String,
    val formattedTime: String = ""
)

/** 拼多多轨迹缓存：抓取一次存 N 小时，避免每次进详情重新走 WebView 流程 */
@Serializable
data class PddTraceCache(
    val fetchedAt: Long = 0L,
    val points: List<DetailPoint> = emptyList(),
    val done: Boolean = false
)
