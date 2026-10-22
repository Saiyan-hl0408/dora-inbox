package com.halo.expressassistant.hub

/** Current official headings only; historical instructions are not current status evidence. */
object ParcelStatusAliases {
    val packing=listOf("打包中","正在打包","备货中","待出库","未出库","等待出库","出库中","待发货","等待商家发货","等待卖家发货","打包")
    private val pickup=listOf("待取件","待取货","待自提","待提货","等待取件","等待取货","待领取","等待领取","到站待取")
    private val closed=listOf("已取出签收","已取件","已取货","已领取","已取出","已签收","本人签收","收件人签收","领取成功")
    val delivering=listOf("派送中","派件中","配送中","正在派送","正在配送")
    private val transit=listOf("运输中","在途中","转运中","已发货","已出库","已揽收","已揽件","揽收成功","出库")
    private val aftersale=listOf("已退回","已拒收","已取消")
    private val groups=listOf(packing to ParcelPresentation.Stage.NOT_SHIPPED,pickup to ParcelPresentation.Stage.PICKUP,
        closed to ParcelPresentation.Stage.CLOSED,delivering to ParcelPresentation.Stage.TRANSIT,
        transit to ParcelPresentation.Stage.TRANSIT,aftersale to ParcelPresentation.Stage.AFTERSALE)
    fun heading(text:String):String=groups.asSequence().flatMap {it.first.asSequence()}.firstOrNull {text.trim().startsWith(it)}.orEmpty()
    fun stage(text:String):ParcelPresentation.Stage?=groups.firstOrNull {group->group.first.any {text.trim().startsWith(it)}}?.second
}
