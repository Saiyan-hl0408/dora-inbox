package com.halo.expressassistant.hub

import android.app.Dialog
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.halo.expressassistant.data.*
import com.halo.expressassistant.api.TbOrders
import com.halo.expressassistant.ui.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** Native facts first; official providers continue to own identity checks and full JD traces. */
class ParcelDetailActivity:AppCompatActivity() {
    companion object {
        const val EXTRA_ITEM_ID="parcel_item_id"
        const val EXTRA_SOURCE="parcel_source"
        const val EXTRA_ACCOUNT_ID="parcel_account_id"
        private const val SNAPSHOT="parcel_snapshot"
        fun open(context:Context,item:ExpressItem) {
            val intent=Intent(context,ParcelDetailActivity::class.java)
                .putExtra(EXTRA_ITEM_ID,item.id).putExtra(EXTRA_SOURCE,item.source).putExtra(EXTRA_ACCOUNT_ID,item.accountId)
                .putExtra(SNAPSHOT,Store.json.encodeToString(item))
            if(context !is android.app.Activity)intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
    private lateinit var parcel:ExpressItem
    private lateinit var content:LinearLayout
    private lateinit var scroll:ScrollView
    private lateinit var footer:LinearLayout
    private val ui by lazy {DetailUi(this)}
    private var state=DetailLoadPolicy.State()
    private var work:Job?=null
    private var historyExpanded=false
    private var resumedOnce=false
    private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val id=intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
        val source=intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        val owner=intent.getStringExtra(EXTRA_ACCOUNT_ID).orEmpty()
        val found=(Store.items(this)+HubData.local(this)).firstOrNull {it.id==id && it.source==source && it.accountId==owner}
            ?: runCatching {Store.json.decodeFromString<ExpressItem>(intent.getStringExtra(SNAPSHOT).orEmpty())}
                .getOrNull()?.takeIf {it.id==id && it.source==source && it.accountId==owner}
        if(found==null){Toast.makeText(this,"未找到此包裹记录",Toast.LENGTH_SHORT).show();finish();return}
        parcel=found
        historyExpanded=savedInstanceState?.getBoolean("history_expanded",false) ?: false
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;background=HubUi.background()}
        WindowCompat.setDecorFitsSystemWindows(window,false)
        window.statusBarColor=Color.TRANSPARENT;window.navigationBarColor=Color.TRANSPARENT
        WindowCompat.getInsetsController(window,window.decorView).apply {isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true}
        ViewCompat.setOnApplyWindowInsetsListener(root){v,insets->val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(0,bars.top,0,bars.bottom);insets}
        val heading=FrameLayout(this).apply {minimumHeight=dp(56);setPadding(dp(6),0,dp(6),0)}
        val orderOnly=!DetailPresentation.hasWaybill(parcel) && ParcelPresentation.stage(parcel) in setOf(ParcelPresentation.Stage.AFTERSALE,ParcelPresentation.Stage.NOT_SHIPPED,ParcelPresentation.Stage.CLOSED)
        heading.addView(ui.text(if(orderOnly)"订单详情" else "物流详情",18,true).apply {gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,-1))
        heading.addView(ui.iconButton("back","返回"){finish()},FrameLayout.LayoutParams(dp(44),dp(44),Gravity.START or Gravity.CENTER_VERTICAL))
        heading.addView(ui.iconButton("more","更多物流操作"){showMore()}.apply {rotation=90f},FrameLayout.LayoutParams(dp(44),dp(44),Gravity.END or Gravity.CENTER_VERTICAL))
        root.addView(heading,LinearLayout.LayoutParams(-1,dp(56)))
        scroll=ScrollView(this).apply {isFillViewport=true;clipToPadding=false}
        content=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(2),dp(12),dp(4))}
        scroll.isVerticalScrollBarEnabled=false
        scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        footer=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(6),dp(12),dp(10));background=HubUi.background()}
        root.addView(footer,LinearLayout.LayoutParams(-1,-2));setContentView(root)
        state=DetailLoadPolicy.cached(cachedPoints())
        render()
        if(needsTraceRefresh())refresh()
    }
    private fun needsTraceRefresh()=when(parcel.source) {
        "pdd"->Store.pddTraceNeedsRefresh(this,parcel.mailNo,ParcelPresentation.stage(parcel)==ParcelPresentation.Stage.CLOSED)
        "taobao"->Store.tbTraces(this,parcel.mailNo,ParcelPresentation.stage(parcel)==ParcelPresentation.Stage.CLOSED).isNullOrEmpty()
        else->false
    }
    private fun cachedPoints():List<DetailPoint> = when(parcel.source) {
        "jd"->DetailPresentation.points(Store.jdTracesCached(this,parcel.mailNo).orEmpty()).take(1).ifEmpty {DetailPresentation.localSummary(parcel)} // A cached JD node is still only a summary.
        "pdd"->Store.pddTraces(this,parcel.mailNo).orEmpty().ifEmpty {DetailPresentation.localSummary(parcel)}
        "taobao"->Store.tbTracesCached(this,parcel.mailNo).orEmpty().ifEmpty {DetailPresentation.localSummary(parcel)}
        else->DetailPresentation.localSummary(parcel)
    }
    private fun render() {
        val position=scroll.scrollY
        content.removeAllViews();footer.removeAllViews()
        val summary=DetailPresentation.summary(parcel,Store.jdGoods(this)[parcel.mailNo],HubData.collectedTimes(this)[parcel.mailNo])
        val stage=ParcelPresentation.stage(parcel)
        val orderOnly=summary.waybill==null && stage in setOf(ParcelPresentation.Stage.AFTERSALE,ParcelPresentation.Stage.NOT_SHIPPED,ParcelPresentation.Stage.CLOSED)
        val simpleRecord=parcel.source in setOf("cainiao","local") && stage==ParcelPresentation.Stage.PICKUP
        val transient=state.refreshing || state.failed || state.points.isEmpty()
        productCard(summary,transient && !simpleRecord)
        if(orderOnly) {
            val order=ui.card()
            ui.fact(order,"info","订单状态    ${parcel.orderStatus.ifBlank {summary.status}}")
            ui.fact(order,"box","商品信息    ${summary.title}")
            ui.fact(order,"truck","暂无运单号    平台未提供运单号")
            if(summary.orderCreated.isNotBlank())ui.fact(order,"clock","下单时间    ${summary.orderCreated}")
            content.addView(order)
            val note=ui.text(if(stage==ParcelPresentation.Stage.AFTERSALE)"该订单已结束，商品信息保留。\n暂无可用取件码。" else "平台尚未提供真实物流运单，\n发货后可在原平台核对物流。",14,color=HubUi.muted).apply {
                background=ui.round(HubUi.paleBlue,12);setPadding(dp(14),dp(14),dp(14),dp(14))
            }
            content.addView(note)
            footer.addView(ui.button("返回列表",primary=true){finish()},LinearLayout.LayoutParams(-1,dp(46)))
            addOfficialFooter("原平台查看")
            restorePosition(position);return
        }
        if(simpleRecord && summary.waybill!=null)content.addView(ui.card(10).apply {waybillRow(this,summary)})
        // A missing trace is independent of a valid current pickup code and station.
        if(!transient || simpleRecord) {
            val facts=ui.card()
            if(!simpleRecord)waybillRow(facts,summary)
            val statusLine=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(10),0,dp(6))}
            val statusColor=when(stage){ParcelPresentation.Stage.PICKUP->Color.rgb(237,143,0);ParcelPresentation.Stage.CLOSED->Color.rgb(0,163,91);ParcelPresentation.Stage.AFTERSALE->Color.rgb(185,89,22);else->HubUi.accent}
            val statusTint=when(stage){ParcelPresentation.Stage.PICKUP->Color.rgb(255,245,226);ParcelPresentation.Stage.CLOSED->Color.rgb(230,250,239);ParcelPresentation.Stage.AFTERSALE->Color.rgb(255,242,231);else->HubUi.paleBlue}
            val statusTitle=when(stage){ParcelPresentation.Stage.CLOSED->if(parcel.completedAt>0 || parcel.logisticsStatus.contains("签收") || parcel.logisticsStatus.contains("送达"))"已签收" else "订单已完成";else->summary.status}
            statusLine.addView(if(simpleRecord)ui.text("取件信息",16,true) else ui.badge(statusTitle,statusColor,statusTint))
            val timeText=if(stage==ParcelPresentation.Stage.CLOSED && parcel.completedAt>0)"签收于 ${ParcelTime.format(parcel.completedAt)}" else if(summary.eventTime.isNotBlank())"更新于 ${summary.eventTime}" else "物流时间暂未提供"
            statusLine.addView(ui.text(timeText,12,color=HubUi.muted).apply {gravity=Gravity.END},LinearLayout.LayoutParams(0,-2,1f))
            facts.addView(statusLine)
            if(stage==ParcelPresentation.Stage.PICKUP && !HubData.collected(this,parcel)) {
                val codeBox=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;background=ui.round(HubUi.paleBlue,12);setPadding(dp(12),dp(10),dp(4),dp(10))}
                codeBox.addView(ui.icon("qr",HubUi.ink,26),LinearLayout.LayoutParams(dp(28),dp(34)).apply {marginEnd=dp(14)})
                codeBox.addView(ui.column().apply {
                    addView(ui.text("取件码",12,color=HubUi.muted))
                    addView(ui.text(summary.currentCode.ifBlank {"暂未获取"},if(summary.currentCode.isBlank())20 else 26,true,if(summary.currentCode.isBlank())HubUi.ink else HubUi.accent).apply {setTextIsSelectable(true)})
                },LinearLayout.LayoutParams(0,-2,1f))
                if(summary.currentCode.isNotBlank())codeBox.addView(ui.iconButton("copy","复制取件码"){copy("取件码",summary.currentCode)})
                facts.addView(codeBox,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(2);bottomMargin=dp(3)})
                ui.fact(facts,"location",summary.station.ifBlank {"取件地点暂未提供"})
                if(summary.address.isNotBlank())ui.fact(facts,"truck",summary.address)
                if(summary.phoneTail.isNotBlank())ui.fact(facts,"phone","尾号 ${summary.phoneTail}")
                if(parcel.pickupHint.isNotBlank())ui.fact(facts,"info",parcel.pickupHint)
            }
            if(stage==ParcelPresentation.Stage.CLOSED) {
                val signed=parcel.completedAt>0 || parcel.logisticsStatus.contains("签收") || parcel.logisticsStatus.contains("送达")
                facts.addView(statusNotice("signed-shield",if(signed)"已签收" else "订单已完成",if(signed)"平台显示包裹已签收" else "物流签收时间暂未提供",Color.rgb(0,165,98),Color.rgb(237,251,243)))
                if(parcel.orderStatus.contains("待确认收货"))facts.addView(statusNotice("clock","商城待确认收货","包裹已签收，请在电商平台确认收货",Color.rgb(237,133,0),Color.rgb(255,247,235)))
            }
            if(summary.locallyCollected.isNotBlank())ui.fact(facts,"clock","本机标记已取    ${summary.locallyCollected}")
            content.addView(facts)
        }
        when {
            state.refreshing->loadingCard()
            state.failed->failureCard(summary)
            state.points.isEmpty()->emptyCard(simpleRecord)
            else->timelineCard(summary)
        }
        if(!state.refreshing) {
            val row=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
            if(simpleRecord) {
                if(parcel.source=="cainiao")row.addView(ui.button("官方物流",primary=true,iconName="external",iconAtEnd=true){openOfficial()},LinearLayout.LayoutParams(0,dp(46),1.2f).apply {marginEnd=dp(10)})
                row.addView(ui.button("返回列表"){finish()},LinearLayout.LayoutParams(0,dp(46),1f))
                footer.addView(row)
            } else if(state.failed || state.points.isEmpty()) {
                if(parcel.source in setOf("jd","pdd","taobao"))footer.addView(ui.button("刷新",primary=true,iconName="refresh"){refresh()},LinearLayout.LayoutParams(-1,dp(46)))
                else footer.addView(ui.button("返回列表",primary=true){finish()},LinearLayout.LayoutParams(-1,dp(46)))
                addOfficialFooter("原平台查看",outline=true)
            } else {
                if(parcel.source=="pdd" && stage==ParcelPresentation.Stage.PICKUP)row.addView(ui.button("多多代收点查询",iconName="external"){openPddCollection()},LinearLayout.LayoutParams(0,dp(46),1.25f).apply {marginEnd=dp(8)})
                if(DetailPresentation.officialUrl(parcel)!=null || parcel.source=="cainiao")row.addView(ui.button(if(parcel.source=="cainiao")"官方物流" else "原平台物流",iconName="external",iconAtEnd=true){openOfficial()},LinearLayout.LayoutParams(0,dp(46),1f))
                else row.addView(ui.button("返回列表"){finish()},LinearLayout.LayoutParams(-1,dp(46)))
                footer.addView(row)
            }
        }
        restorePosition(position,transient && !simpleRecord)
    }
    private fun restorePosition(position:Int,fillTransient:Boolean=false) {
        // Error/empty/loading boards use one white lower panel with a fixed action area.
        // Let the panel absorb unused viewport height without moving the action buttons.
        content.setPadding(dp(12),dp(2),dp(12),if(fillTransient)0 else dp(4))
        val params=footer.layoutParams as LinearLayout.LayoutParams
        params.marginStart=if(fillTransient)dp(12) else 0;params.marginEnd=if(fillTransient)dp(12) else 0
        params.bottomMargin=if(fillTransient)dp(12) else 0
        footer.layoutParams=params
        footer.background=if(fillTransient)ui.round(Color.WHITE,16) else HubUi.background()
        footer.visibility=if(footer.childCount==0)View.GONE else View.VISIBLE
        val scrollParams=scroll.layoutParams as LinearLayout.LayoutParams
        scrollParams.bottomMargin=if(fillTransient && footer.childCount==0)dp(12) else 0
        scroll.layoutParams=scrollParams
        val last=content.getChildAt(content.childCount-1)
        if(fillTransient && last!=null) {
            val lastParams=last.layoutParams as LinearLayout.LayoutParams
            lastParams.bottomMargin=0;last.layoutParams=lastParams
            last.background=ui.round(Color.WHITE,16)
        }
        scroll.post {
            if(fillTransient && last!=null) {
                last.minimumHeight=maxOf(last.minimumHeight,scroll.height-last.top-content.paddingBottom)
                last.background=ui.round(Color.WHITE,16).apply {if(footer.childCount>0)cornerRadii=floatArrayOf(dp(16).toFloat(),dp(16).toFloat(),dp(16).toFloat(),dp(16).toFloat(),0f,0f,0f,0f)}
                if(footer.childCount>0)footer.background=ui.round(Color.WHITE,16).apply {cornerRadii=floatArrayOf(0f,0f,0f,0f,dp(16).toFloat(),dp(16).toFloat(),dp(16).toFloat(),dp(16).toFloat())}
            }
            scroll.scrollTo(0,position)
        }
    }
    private fun productCard(summary:DetailPresentation.Summary,includeFacts:Boolean) {
        val card=ui.card(12)
        val row=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        val photoFrame=FrameLayout(this).apply {background=ui.round(Color.rgb(244,246,248),12);clipToOutline=true}
        val photo=ImageView(this).apply {scaleType=ImageView.ScaleType.CENTER_INSIDE;contentDescription="查看商品图片"}
        if(summary.image.isNotBlank()) {
            ProductPhotos.load(photo,summary.image,dp(100));photoFrame.addView(photo,FrameLayout.LayoutParams(-1,-1))
            photoFrame.isClickable=true;photoFrame.isFocusable=true;photoFrame.contentDescription="查看${summary.title}的商品大图"
            photoFrame.setOnClickListener {showImage(summary.image)}
        } else {
            val placeholder=ui.column().apply {gravity=Gravity.CENTER}
            placeholder.addView(ui.icon("image-placeholder",HubUi.muted,22),LinearLayout.LayoutParams(dp(24),dp(24)))
            placeholder.addView(ui.text("图片暂未提供",10,color=HubUi.muted).apply {gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(5)})
            photoFrame.addView(placeholder,FrameLayout.LayoutParams(-1,-1))
        }
        row.addView(photoFrame,LinearLayout.LayoutParams(dp(82),dp(82)).apply {marginEnd=dp(14)})
        row.addView(ui.column().apply {
            val productTitle=ui.text(summary.title,16,true).apply {maxLines=3}
            if(!includeFacts)addView(productTitle)
            val brandRow=LinearLayout(this@ParcelDetailActivity).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(0,if(includeFacts)0 else dp(9),0,0)}
            val channel=parcel.source
            val source=HubData.channels[channel] ?: when(channel){"cainiao"->"菜鸟查询";"local"->"手动导入";else->"包裹信息"}
            brandRow.addView(ui.badge(source,PlatformStyle.accent(channel),PlatformStyle.tint(channel)))
            if(summary.quantity.isNotBlank())brandRow.addView(ui.text(summary.quantity,12,color=HubUi.muted),LinearLayout.LayoutParams(-2,-2).apply {marginStart=dp(9)})
            addView(brandRow)
            if(includeFacts)addView(productTitle,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(9)})
        },LinearLayout.LayoutParams(0,-2,1f))
        card.addView(row)
        if(includeFacts) {
            card.addView(ui.spacer(9))
            if(summary.station.isNotBlank())ui.fact(card,"location",summary.station)
            if(summary.eventTime.isNotBlank())ui.fact(card,"clock","${summary.eventTime} · ${parcel.logisticsStatus.ifBlank {summary.status}}")
            waybillRow(card,summary,compact=true)
        }
        content.addView(card)
    }
    private fun waybillRow(parent:LinearLayout,summary:DetailPresentation.Summary,compact:Boolean=false) {
        val number=summary.waybill ?: return
        val largeType=!compact && resources.configuration.fontScale>1.15f
        val row=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        if(compact)row.addView(ui.icon("truck"),LinearLayout.LayoutParams(dp(20),dp(22)).apply {marginEnd=dp(8)})
        row.addView(ui.text(parcel.companyName.ifBlank {"承运商未提供"}+if(compact)" ·" else "",if(compact)12 else 14,!compact,if(compact)HubUi.muted else HubUi.ink),LinearLayout.LayoutParams(-2,-2).apply {marginEnd=dp(10)})
        if(largeType)row.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        else row.addView(ui.text(number,12,color=HubUi.muted).apply {setTextIsSelectable(true)},LinearLayout.LayoutParams(0,-2,1f))
        val copyAction=if(compact)ui.iconButton("copy","复制运单号"){copy("运单号",number)} else ui.text("复制",12,true,HubUi.accent).apply {
            gravity=Gravity.CENTER;background=ui.round(HubUi.paleBlue,9);setPadding(dp(10),dp(6),dp(10),dp(6));setSingleLine(true);minWidth=dp(52);minimumHeight=dp(36);isClickable=true;isFocusable=true;contentDescription="复制运单号";setOnClickListener {copy("运单号",number)}
        }
        row.addView(copyAction,LinearLayout.LayoutParams(if(compact)dp(36) else -2,if(compact)dp(36) else -2).apply {marginStart=dp(6)})
        TouchTargets.expand(copyAction)
        parent.addView(row)
        if(largeType)parent.addView(ui.text(number,13,color=HubUi.muted).apply {setTextIsSelectable(true)},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(4)})
    }
    private fun statusNotice(icon:String,title:String,body:String,color:Int,tint:Int):LinearLayout {
        val row=LinearLayout(this).apply {gravity=Gravity.TOP;background=ui.round(tint,11);setPadding(dp(12),dp(11),dp(12),dp(11));layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(5)}}
        row.addView(ui.icon(icon,color,24),LinearLayout.LayoutParams(dp(26),dp(28)).apply {marginEnd=dp(12)})
        row.addView(ui.column().apply {addView(ui.text(title,14,true,color));addView(ui.text(body,12,color=HubUi.muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(3)})},LinearLayout.LayoutParams(0,-2,1f))
        return row
    }
    private fun timelineCard(summary:DetailPresentation.Summary) {
        val card=ui.card(14)
        card.addView(ui.text(if(parcel.source=="jd")"物流摘要" else "物流轨迹",16,true),LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(14)})
        val color=when(ParcelPresentation.stage(parcel)){ParcelPresentation.Stage.PICKUP->Color.rgb(244,145,0);ParcelPresentation.Stage.CLOSED->Color.rgb(0,170,104);else->HubUi.accent}
        state.points.forEachIndexed {index,point->
            val row=LinearLayout(this).apply {gravity=Gravity.TOP}
            row.addView(DetailTimelineRail(this,color,index==0,index==state.points.lastIndex),LinearLayout.LayoutParams(dp(18),-1).apply {marginEnd=dp(8)})
            val parsed=ParcelTime.parse(point.time)
            row.addView(ui.text(if(parsed>0)ParcelTime.format(parsed) else "时间未提供",12,color=if(index==0)color else HubUi.muted),LinearLayout.LayoutParams(dp(84),-2).apply {marginEnd=dp(9);topMargin=dp(1)})
            val heading=PddLogistics.status(point.context).ifBlank {point.context.substringBefore('，').substringBefore('。')}
            val body=point.context.removePrefix(heading).trim(' ','·','，','。',':','：')
            row.addView(ui.column().apply {
                addView(ui.text(heading,14,true,if(index==0)color else HubUi.ink))
                if(body.isNotBlank() && body!=heading)addView(ui.text(body,12,color=HubUi.muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(4)})
                addView(ui.spacer(if(index==state.points.lastIndex)8 else 17))
            },LinearLayout.LayoutParams(0,-2,1f))
            card.addView(row)
        }
        if(parcel.source=="jd")card.addView(ui.text("完整物流轨迹请到官方页面查看",13,color=HubUi.muted).apply {gravity=Gravity.CENTER;setPadding(0,dp(16),0,dp(12))})
        if(summary.historicalCode.isNotBlank()) {
            val history=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;background=ui.round(HubUi.paleBlue,10);setPadding(dp(10),0,dp(7),0);minimumHeight=dp(46);isClickable=true;isFocusable=true;contentDescription="历史取件码，仅供核对";setOnClickListener {historyExpanded=!historyExpanded;render()}}
            history.addView(ui.icon("phone",HubUi.ink,20),LinearLayout.LayoutParams(dp(22),dp(24)).apply {marginEnd=dp(9)})
            history.addView(ui.text("历史取件码 · 仅供核对",13,true),LinearLayout.LayoutParams(0,-2,1f))
            history.addView(ui.icon("down",HubUi.ink,18).apply {rotation=if(historyExpanded)180f else 0f},LinearLayout.LayoutParams(dp(22),dp(22)))
            card.addView(history,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12)})
            if(historyExpanded)card.addView(ui.text(summary.historicalCode,19,true,HubUi.muted).apply {setTextIsSelectable(true);setPadding(dp(12),dp(11),0,dp(4))})
        }
        content.addView(card)
    }
    private fun loadingCard() {
        val card=ui.card(16)
        val row=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        row.addView(ProgressBar(this).apply {isIndeterminate=true;indeterminateTintList=ColorStateList.valueOf(HubUi.accent)},LinearLayout.LayoutParams(dp(20),dp(20)).apply {marginEnd=dp(12)})
        row.addView(ui.text("正在读取物流轨迹",15,true,HubUi.accent));card.addView(row)
        card.addView(ui.text("请稍等，正在从官方平台获取最新物流信息…",12,color=HubUi.muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8);marginStart=dp(32)})
        card.addView(DetailTraceSkeleton(this),LinearLayout.LayoutParams(-1,dp(276)).apply {topMargin=dp(22)})
        card.accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        content.addView(card)
    }
    private fun emptyCard(simpleRecord:Boolean=false) {
        val card=ui.card(14).apply {gravity=Gravity.CENTER_HORIZONTAL;minimumHeight=dp(if(simpleRecord)230 else 300)}
        if(simpleRecord)card.addView(ui.text("物流轨迹",16,true),LinearLayout.LayoutParams(-1,-2))
        val body=ui.column().apply {gravity=Gravity.CENTER_HORIZONTAL;if(simpleRecord){background=ui.round(Color.rgb(242,249,255),12);setPadding(dp(8),dp(4),dp(8),dp(8))}}
        body.addView(DetailEmptyArtwork(this),LinearLayout.LayoutParams(dp(if(simpleRecord)108 else 150),dp(if(simpleRecord)108 else 150)).apply {topMargin=dp(if(simpleRecord)4 else 14);bottomMargin=dp(8)})
        body.addView(ui.text(if(simpleRecord)"暂无物流轨迹" else "暂未取得更多轨迹",if(simpleRecord)16 else 18,true).apply {gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2))
        body.addView(ui.text(if(simpleRecord)"暂未取得更多物流轨迹，可到官方页面查看。" else "可刷新重试，或到官方页面查看。",13,color=HubUi.muted).apply {gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12);bottomMargin=dp(if(simpleRecord)10 else 24)})
        card.addView(body,LinearLayout.LayoutParams(-1,-2).apply {topMargin=if(simpleRecord)dp(14) else 0})
        content.addView(card)
    }
    private fun failureCard(summary:DetailPresentation.Summary) {
        val card=ui.card(12)
        val warning=LinearLayout(this).apply {gravity=Gravity.TOP;background=ui.round(Color.rgb(255,247,235),12,Color.rgb(255,222,175));setPadding(dp(13),dp(15),dp(13),dp(15));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        warning.addView(ui.text("!",25,true,Color.WHITE).apply {gravity=Gravity.CENTER;background=ui.round(Color.rgb(255,132,8),18)},LinearLayout.LayoutParams(dp(34),dp(34)).apply {marginEnd=dp(12)})
        warning.addView(ui.column().apply {
            addView(ui.text("物流更新失败，已有记录已保留",15,true,Color.rgb(230,112,0)))
            addView(ui.text(if(summary.eventTime.isBlank())"已有记录更新时间：暂未提供" else "已有记录更新时间：${summary.eventTime}",12,color=HubUi.muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7)})
        },LinearLayout.LayoutParams(0,-2,1f))
        card.addView(warning)
        if(state.points.isNotEmpty()) {
            val saved=ui.column().apply {background=ui.round(Color.WHITE,12,Color.rgb(233,239,247));setPadding(dp(14),dp(16),dp(14),dp(20))}
            saved.addView(ui.text("已有物流信息",16,true,HubUi.accent))
            state.points.forEach {point->
                val time=ParcelTime.parse(point.time).takeIf {it>0}?.let(ParcelTime::format).orEmpty()
                ui.fact(saved,"clock",listOf(time,point.context).filter {it.isNotBlank()}.joinToString(" · "),HubUi.ink)
            }
            card.addView(saved,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12)})
        } else card.addView(ui.text("暂未取得更多物流信息，可到官方页面核对。",13,color=HubUi.muted).apply {setPadding(dp(5),dp(22),dp(5),dp(20))})
        content.addView(card)
    }
    private fun addOfficialFooter(title:String,outline:Boolean=false) {
        if(DetailPresentation.officialUrl(parcel)!=null || parcel.source=="cainiao")footer.addView(ui.button(title,outline=outline,iconName="external",iconAtEnd=true){openOfficial()},LinearLayout.LayoutParams(-1,dp(46)).apply {topMargin=dp(8)})
    }
    private fun showMore() {
        val options=if(parcel.source in setOf("jd","pdd","taobao"))arrayOf("刷新详情","查看官方物流") else arrayOf("返回列表")
        MaterialAlertDialogBuilder(this).setItems(options){_,which->when(options[which]){"刷新详情"->refresh();"查看官方物流"->openOfficial();else->finish()}}.show()
    }
    private fun openPddCollection() {
        fun open(){try {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("pinduoduo://com.xunmeng.pinduoduo/mdkd/package?entry_source=11")).setPackage(IdentityRoutes.route("pdd").packageName))}catch(_:ActivityNotFoundException){Toast.makeText(this,"请在拼多多原App中打开多多买菜包裹查询",Toast.LENGTH_LONG).show()}catch(_:SecurityException){Toast.makeText(this,"暂时无法打开拼多多，请在原App查询",Toast.LENGTH_LONG).show()}}
        if(HubData.prefs(this).getBoolean("pdd_collection_help_seen",false)){open();return}
        MaterialAlertDialogBuilder(this).setTitle("多多买菜代收点查询").setMessage("将在官方页面查看绑定手机号的包裹，按提示登录或授权。能否查询到取件码以官方页面为准；返回软件不会自动标记已取件。")
            .setPositiveButton("打开官方页面"){_,_->HubData.prefs(this).edit().putBoolean("pdd_collection_help_seen",true).apply();open()}.setNegativeButton("取消",null).show()
    }
    private fun currentAccount():BoundAccount {
        check(parcel.accountId.isNotBlank()){ "请先恢复此包裹所属账号" }
        return Store.accountById(this,parcel.source,parcel.accountId)?.takeIf {it.enabled && Store.cookieOf(it.payload).isNotBlank()}
            ?: error("此包裹所属账号不可用，请返回恢复登录")
    }
    private fun refresh() {
        if(work?.isActive==true)return
        work=lifecycleScope.launch {
            state=DetailLoadPolicy.start(state);render()
            try {
                val owner=currentAccount();val cookies=Store.cookieOf(owner.payload)
                val before=parcel
                val points=withTimeout(45000) {when(before.source) {
                    "jd"->{
                        check(DetailPresentation.hasWaybill(before)){"尚无真实运单，请在官方收件列表核对"}
                        check(SessionCookies.restoreOwned(this@ParcelDetailActivity,"jd",owner)){"账号会话需恢复"}
                        val result=JdlWebFetcher.fetchMetadata(this@ParcelDetailActivity,owner,listOf(before.mailNo))
                        val fresh=result.items.firstOrNull {it.mailNo==before.mailNo} ?: error("暂未取得此运单详情")
                        requireOwner(owner)
                        check(IdentitySessionPolicy.matchesOwner("jd",cookies,SessionCookies.current("jd"))){"当前账号已变化"}
                        synchronized(Store) {
                            val all=Store.items(this@ParcelDetailActivity)
                            val latest=all.firstOrNull {it.id==before.id && it.source==before.source && it.accountId==before.accountId} ?: before
                            parcel=SyncCacheMerge.merge(latest,fresh.copy(id=before.id,source=before.source,accountId=owner.id)).copy(
                                historicalPickupCode=fresh.historicalPickupCode.ifBlank {latest.historicalPickupCode})
                            Store.saveItems(this@ParcelDetailActivity,all.filterNot {it.id==before.id && it.source==before.source && it.accountId==before.accountId}+parcel)
                        }
                        if(result.goods.isNotEmpty())Store.saveJdGoods(this@ParcelDetailActivity,GoodsCache.mergeAll(Store.jdGoods(this@ParcelDetailActivity),result.goods))
                        DetailPresentation.localSummary(parcel)
                    }
                    "pdd"->{
                        check(SessionCookies.restoreOwned(this@ParcelDetailActivity,"pdd",owner)){"账号会话需恢复"}
                        val result=PddTraceFetcher.fetchMetadata(this@ParcelDetailActivity,before,cookies) ?: error("未取得物流详情")
                        requireOwner(owner)
                        check(IdentitySessionPolicy.matchesOwner("pdd",cookies,SessionCookies.current("pdd"))){"当前账号已变化"}
                        if(result.points.isNotEmpty())parcel=ParcelMetadata.apply(this@ParcelDetailActivity,before,result.waybill,result.company,result.points,result)
                        result.goods?.let {goods->Store.saveJdGoods(this@ParcelDetailActivity,GoodsCache.mergeAll(Store.jdGoods(this@ParcelDetailActivity),mapOf(parcel.mailNo to goods)))}
                        result.points
                    }
                    "taobao"->{
                        val order=before.queryChannel.takeIf {it.matches(Regex("[0-9]{8,32}"))}
                        val detail=order?.let {TbOrders.fetchSsrDetailWith(cookies,it)}
                        val traces=order?.let {TbOrders.fetchSsrTracesWith(cookies,it)}
                            ?: if(DetailPresentation.hasWaybill(before))CaiNiaoGoodsResolver.fetchTracesWith(cookies,before.mailNo) else null
                        requireOwner(owner)
                        val found=DetailPresentation.points(traces.orEmpty())
                        if(DetailLoadPolicy.mayApplyMetadata(before,detail?.mailNo.orEmpty(),found)) {
                            parcel=ParcelMetadata.apply(this@ParcelDetailActivity,before,detail?.mailNo.orEmpty(),detail?.cpName.orEmpty(),found)
                            if(found.isNotEmpty())Store.saveTbTrace(this@ParcelDetailActivity,parcel.mailNo,found,done=ParcelPresentation.stage(parcel)==ParcelPresentation.Stage.CLOSED)
                        }
                        if(DetailPresentation.hasWaybill(parcel))CaiNiaoGoodsResolver.resolveWith(cookies,parcel.mailNo)?.let {goods->
                            requireOwner(owner);Store.saveJdGoods(this@ParcelDetailActivity,GoodsCache.mergeAll(Store.jdGoods(this@ParcelDetailActivity),mapOf(parcel.mailNo to goods)))
                        }
                        found
                    }
                    else->emptyList()
                }}
                state=DetailLoadPolicy.success(state,points)
                if(parcel.source=="jd")state=state.copy(points=state.points.take(1))
            } catch(_:TimeoutCancellationException){state=DetailLoadPolicy.failure(state)}
            catch(e:CancellationException){state=state.copy(refreshing=false,message="");throw e}
            catch(_:Exception){state=DetailLoadPolicy.failure(state)}
            finally {if(!isFinishing){state=state.copy(refreshing=false);render()}}
        }
    }
    private fun requireOwner(expected:BoundAccount) {
        val current=Store.accountById(this,parcel.source,expected.id)?.takeIf {it.enabled} ?: error("账号不可用")
        check(IdentitySessionPolicy.matchesOwner(parcel.source,Store.cookieOf(expected.payload),Store.cookieOf(current.payload))){"账号已变化"}
    }
    private fun openOfficial() {
        work?.cancel()
        if(parcel.source=="cainiao") {
            val id=parcel.pickupQueryAccountId.ifBlank {parcel.accountId}
            val owner=Store.accountById(this,"taobao",id)?.takeIf {it.enabled}
            if(owner==null){Toast.makeText(this,"请恢复此包裹所属淘宝 / 菜鸟账号",Toast.LENGTH_LONG).show();return}
            startActivity(Intent(this,CainiaoPickupActivity::class.java).putExtra(CainiaoPickupActivity.ACCOUNT,owner.id).putExtra(CainiaoPickupActivity.NUMBER,parcel.mailNo).putExtra(CainiaoPickupActivity.DETAILS,true));return
        }
        val url=DetailPresentation.officialUrl(parcel) ?: return
        val owner=runCatching {currentAccount()}.getOrNull()
        if(owner==null){Toast.makeText(this,"此包裹所属账号不可用，请返回恢复登录",Toast.LENGTH_LONG).show();return}
        startActivity(Intent(this,SourceWebActivity::class.java).putExtra(SourceWebActivity.EXTRA_URL,url).putExtra(SourceWebActivity.EXTRA_SOURCE,parcel.source).putExtra(SourceWebActivity.EXTRA_ACCOUNT_ID,owner.id).putExtra(SourceWebActivity.EXTRA_TITLE,"官方物流"))
    }
    private fun showImage(url:String) {
        val dialog=Dialog(this,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val layout=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(22,24,27))}
        val photo=ZoomPhotoView(this)
        val heading=FrameLayout(this)
        heading.addView(ui.text("商品图片",17,true,Color.WHITE).apply {gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,-1))
        fun photoAction(name:String,description:String,action:()->Unit)=ImageView(this).apply {
            setImageDrawable(HubIcon(name,Color.WHITE,dp(22)));setPadding(dp(11),dp(11),dp(11),dp(11));contentDescription=description
            isClickable=true;isFocusable=true;setOnClickListener {action()}
        }
        heading.addView(photoAction("close","关闭商品图片"){dialog.dismiss()},FrameLayout.LayoutParams(dp(44),dp(44),Gravity.END or Gravity.CENTER_VERTICAL).apply {marginEnd=dp(8)})
        layout.addView(heading,LinearLayout.LayoutParams(-1,dp(60)))
        val photoFrame=FrameLayout(this).apply {background=ui.round(Color.rgb(32,34,37),12);clipToOutline=true}
        photoFrame.addView(photo,FrameLayout.LayoutParams(-1,-1))
        layout.addView(photoFrame,LinearLayout.LayoutParams(-1,0,1f))
        val summary=DetailPresentation.summary(parcel,Store.jdGoods(this)[parcel.mailNo])
        layout.addView(ui.text(summary.title,16,true,Color.WHITE).apply {gravity=Gravity.CENTER;setPadding(dp(20),dp(21),dp(20),dp(9))})
        layout.addView(ui.text("双指缩放 · 双击放大 · 拖动查看",12,color=Color.rgb(181,211,233)).apply {gravity=Gravity.CENTER;setPadding(dp(12),dp(6),dp(12),dp(20))})
        ProductPhotos.load(photo,url)
        dialog.setContentView(layout);dialog.show()
        dialog.window?.let {window->
            WindowCompat.setDecorFitsSystemWindows(window,false)
            window.statusBarColor=Color.TRANSPARENT;window.navigationBarColor=Color.TRANSPARENT
            WindowCompat.getInsetsController(window,window.decorView).apply {isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false}
            ViewCompat.setOnApplyWindowInsetsListener(layout){view,insets->val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());view.setPadding(0,bars.top,0,bars.bottom);insets}
            ViewCompat.requestApplyInsets(layout)
        }
    }
    private fun copy(title:String,value:String) {(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(title,value));Toast.makeText(this,"已复制$title",Toast.LENGTH_SHORT).show()}
    override fun onResume() {
        super.onResume()
        if(!resumedOnce){resumedOnce=true;return}
        if(!::parcel.isInitialized || !::content.isInitialized || work?.isActive==true)return
        val fresh=(Store.items(this)+HubData.local(this)).firstOrNull {it.id==parcel.id && it.source==parcel.source && it.accountId==parcel.accountId}
        if(fresh!=null)parcel=fresh
        state=DetailLoadPolicy.cached(cachedPoints());render()
    }
    override fun onSaveInstanceState(outState:Bundle) {outState.putBoolean("history_expanded",historyExpanded);super.onSaveInstanceState(outState)}
    override fun onStop(){work?.cancel();super.onStop()}
}
