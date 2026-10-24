package com.halo.expressassistant.hub

import android.content.*
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.halo.expressassistant.data.ExpressItem
import com.halo.expressassistant.data.JdGoods
import com.halo.expressassistant.data.Store
import com.halo.expressassistant.ui.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Personal pickup workspace over ExpressAssistant's account sync engine. */
class HubActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var statusChips:LinearLayout
    private var pendingLookup=""
    private var queuedLookup=""
    private var pendingLookupReady=false
    private var activeLookup=false
    private var activeHistoryDays:Int?=null
    private var activeManualSync=false
    private lateinit var header:LinearLayout
    private var bindingChannel:String?=null
    private var settingsPage:String?=null
    private var settingsParent:String?=null
    private var settingsPlatform="jd"
    private lateinit var syncButton: MaterialButton
    private var tab = 0
    private var filter = "全部平台"
    private var showHistory = false
    private var historyDays = HistoryRangePolicy.DEFAULT_DAYS
    private lateinit var loadingArea: LinearLayout
    private lateinit var loadingBar: View
    private lateinit var loadingLabel: TextView
    private var delayedLoading: Runnable? = null
    private val navViews = mutableListOf<LinearLayout>()
    private lateinit var navigation:LinearLayout
    private lateinit var navigationDivider:View
    private var search = ""
    private var platform = "jd"
    private var pendingImagePlatform = "jd"
    private var busy: Job? = null
    private val working get()=busy?.isCompleted==false
    private var didAutoSync = false
    private val bindingSyncQueue=BindingSyncQueue()
    private var syncForeground=false
    private var activeBindingChannels:Set<String> = emptySet()
    private var historyProgressDetail:TextView?=null
    private var supplementNumber:String?=null
    private var refreshFinishedUntil=0L
    private val drainBindingSync=object:Runnable {
        override fun run() {
            if(!syncForeground || isFinishing || isDestroyed)return
            if(working || SyncEngine.isBusy() || SyncRunCoordinator.isBusy()) {
                if(queuedLookup.isNotBlank() || pendingLookupReady || bindingSyncQueue.pending().isNotEmpty() || bindingSyncQueue.pendingRange()!=null)handler.postDelayed(this,300)
                return
            }
            if(queuedLookup.isNotBlank()){val number=queuedLookup;queuedLookup="";startQuery(number);return}
            if(pendingLookupReady && pendingLookup.isNotBlank()) {
                pendingLookupReady=false;startQuery(pendingLookup);return
            }
            bindingSyncQueue.pendingRange()?.let {if(!SyncLedger.needsExpansion(this@HubActivity,it)) {
                bindingSyncQueue.discardRange()
                if(tab==0 && showHistory)renderList()
            }}
            val request=bindingSyncQueue.takeNext(false,true) ?: return
            request.historyDays?.let {sync(it,historyImport=true);return}
            val channels=request.channels.orEmpty()
            val enabled=channels.filterTo(linkedSetOf()) {channel->Store.accounts(this@HubActivity,channel).any {it.enabled}}
            if(enabled.isNotEmpty())sync(automatic=!request.manual,channels=enabled) else handler.post(this)
        }
    }
    private data class PhotoSlot(var item:ExpressItem,val frame:FrameLayout,val image:ImageView,var loaded:String="")
    private val photoSlots=mutableListOf<PhotoSlot>()
    // A logistics update may replace the text card, but must not replace its picture.
    private val parcelPhotos=mutableMapOf<String,PhotoSlot>()
    private data class CardStamp(val item:ExpressItem,val goods:JdGoods?,val collectedAt:Long?,val compact:Boolean,val history:Boolean,val dark:Boolean,val fontScale:Float)
    private data class SavedCard(val stamp:CardStamp,val view:LinearLayout,val photos:List<PhotoSlot>)
    private val parcelCards=linkedMapOf<String,SavedCard>()
    private val renderedCards=mutableSetOf<String>()
    private var renderGoods:Map<String,JdGoods>?=null
    private var renderCollected:Map<String,Long>?=null
    private var photoScanScheduled=false
    private var scrollingUntil=0L
    private var parcelScroll:ScrollView?=null
    private var parcelScrollScope=""
    private var restoringReading=false
    private var readingRenderGeneration=0
    private val readingPositions=linkedMapOf<String,ListReadingPosition.Bookmark>()
    private fun readingScope()=ListReadingPosition.scope(showHistory,historyDays,filter,search)
    private fun readingRows():List<ListReadingPosition.Row> {
        if(!::list.isInitialized)return emptyList()
        return (0 until list.childCount).mapNotNull {index->
            val view=list.getChildAt(index)
            val key=(view.tag as? String)?.takeIf {it.startsWith("parcel-reading:")} ?: return@mapNotNull null
            ListReadingPosition.Row(key,list.top+view.top,view.height)
        }
    }
    private fun rememberParcelReading() {
        val scroll=parcelScroll ?: return
        if(restoringReading || scroll.height<=0 || parcelScrollScope.isBlank())return
        val rows=readingRows()
        if(rows.isEmpty() && readingPositions[parcelScrollScope]?.rowKey?.isNotBlank()==true)return
        val saved=ListReadingPosition.capture(scroll.scrollY,rows)
        readingPositions.remove(parcelScrollScope);readingPositions[parcelScrollScope]=saved
        while(readingPositions.size>16)readingPositions.remove(readingPositions.keys.first())
    }
    private fun readingState()=Store.json.encodeToString(readingPositions.toMap())
    private val updateParcels=object:Runnable {
        override fun run() {
            if(tab!=0 || !::list.isInitialized)return
            val delay=scrollingUntil-SystemClock.uptimeMillis()
            if(delay>0){handler.postDelayed(this,delay);return}
            renderList()
        }
    }
    private var photoJob:Job?=null
    private val scanPhotos=Runnable {photoScanScheduled=false;loadVisiblePhotos()}
    private lateinit var pageTitle:TextView
    private lateinit var brandTitle:ImageView
    private var receiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private val identityTick = object : Runnable {
        override fun run() { if (tab == 1) renderBody(); handler.postDelayed(this,30000) }
    }
    private var backgroundProgressVisible=false
    private fun reflectBackgroundSync() {
        if(working)return
        val prefs=HubData.prefs(this)
        val running=prefs.getBoolean("background_run_running",false) && SyncRunCoordinator.isBusy()
        if(running){backgroundProgressVisible=true;showLoading(prefs.getString("background_run_label","后台同步中，已有订单可查看").orEmpty(),immediate=true)}
        else if(backgroundProgressVisible){backgroundProgressVisible=false;hideLoading();refreshStatusChips()}
    }
    private val changed = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reflectBackgroundSync()
            if(tab==0 && ::list.isInitialized){handler.removeCallbacks(updateParcels);handler.postDelayed(updateParcels,80)}
        }
    }
    private var diagnosticExportText=""
    private val diagnosticExporter=registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->
        val text=diagnosticExportText;diagnosticExportText=""
        if(uri!=null && text.isNotBlank())lifecycleScope.launch {
            val saved=withContext(Dispatchers.IO){runCatching {contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {it.write(text)} ?: error("No destination")}.isSuccess}
            if(saved) {
                val location=withContext(Dispatchers.IO){DiagnosticShare.savedLocation(this@HubActivity,uri)}
                MaterialAlertDialogBuilder(this@HubActivity).setTitle("诊断已保存").setMessage("保存位置：\n$location")
                    .setPositiveButton("系统分享"){_,_->runCatching {DiagnosticShare.share(this@HubActivity,uri)}.onFailure {toast("没有可用的分享应用")}}
                    .setNeutralButton("打开文件"){_,_->runCatching {DiagnosticShare.view(this@HubActivity,uri)}.onFailure {toast("没有可打开TXT的应用，可使用系统分享")}}
                    .setNegativeButton("完成",null).show()
            } else toast("保存失败，请重试")
        }
    }
    private val platformLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result->
        renderBody()
        if(result.resultCode==RESULT_OK && result.data!=null && (result.data!!.hasExtra("bound_channel") || result.data!!.getBooleanExtra("cainiao_bound",false)))showBindingVerification(result.data!!)
        if(result.resultCode==RESULT_OK && result.data?.getBooleanExtra("cainiao_returned",false)==true && pendingLookup.isNotBlank()) {
            resumePendingLookup()
        }
        if(result.resultCode==RESULT_OK && result.data?.getBooleanExtra("cainiao_bound",false)==true && pendingLookup.isNotBlank()) {
            resumePendingLookup()
        }
        result.data?.getStringExtra("bound_channel")?.takeIf {result.resultCode==RESULT_OK}?.let {
            bindingSyncQueue.request(it);didAutoSync=true
            if(working || SyncEngine.isBusy() || SyncRunCoordinator.isBusy())status.text="${HubData.channels[it]}已绑定，当前读取结束后自动同步。"
            if(pendingLookup.isNotBlank())resumePendingLookup()
            handler.post(drainBindingSync)
        }
    }
    private val dark get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val ink get() = HubUi.ink
    private val muted get() = HubUi.muted
    private val surface get() = if (dark) Color.rgb(29,40,54) else Color.WHITE
    private val background get() = if (dark) Color.rgb(15,23,34) else Color.rgb(237,247,255)
    private val accent get() = HubUi.accent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        didAutoSync=savedInstanceState?.getBoolean("didAutoSync",false) ?: false
        savedInstanceState?.getStringArrayList("bindingSyncChannels")?.forEach(bindingSyncQueue::request)
        savedInstanceState?.getStringArrayList("manualSyncChannels")?.forEach(bindingSyncQueue::requestManual)
        pendingLookup=savedInstanceState?.getString("pendingLookup").orEmpty()
        queuedLookup=savedInstanceState?.getString("queuedLookup").orEmpty()
        pendingLookupReady=savedInstanceState?.getBoolean("pendingLookupReady",false) ?: false
        savedInstanceState?.getInt("pendingHistoryDays",0)?.takeIf {it>0}?.let(bindingSyncQueue::requestRange)
        HubData.migrateV3(this)
        PddLocationRecovery.prepare(this)
        runCatching {Store.json.decodeFromString<Map<String,ListReadingPosition.Bookmark>>(
            savedInstanceState?.getString("parcelReadingPositions") ?: HubData.prefs(this).getString("parcel_reading_positions","{}").orEmpty())}
            .getOrNull()?.let(readingPositions::putAll)
        historyDays=HistoryRangePolicy.clamp(savedInstanceState?.getInt("historyDays") ?: HubData.prefs(this).getInt("history_days",HistoryRangePolicy.DEFAULT_DAYS))
        tab = savedInstanceState?.getInt("tab") ?: 0
        showHistory = savedInstanceState?.getBoolean("history") ?: false
        bindingChannel=savedInstanceState?.getString("bindingChannel")?.takeIf {it in HubData.channels}
            ?: if(savedInstanceState?.getBoolean("jdBindingPage")==true)"jd" else null
        settingsPage=savedInstanceState?.getString("settingsPage")
        settingsParent=savedInstanceState?.getString("settingsParent")
        settingsPlatform=savedInstanceState?.getString("settingsPlatform") ?: "jd"
        search = savedInstanceState?.getString("search").orEmpty()
        filter = savedInstanceState?.getString("filter") ?: "全部平台"
        platform = savedInstanceState?.getString("platform") ?: "jd"
        pendingImagePlatform = savedInstanceState?.getString("pendingImagePlatform") ?: platform
        root = column().apply { background=HubUi.background() }
        setContentView(root)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowCompat.getInsetsController(window,root).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets
        }
        header = column().apply { setPadding(dp(16),dp(12),dp(16),dp(4)) }
        val heading = row().apply {minimumHeight=dp(64)}
        pageTitle=label("哆啦收件箱",28,true).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(0,0,0,0)}
        brandTitle=ImageView(this).apply {setImageResource(com.halo.expressassistant.R.drawable.dora_wordmark);scaleType=ImageView.ScaleType.FIT_START;contentDescription="哆啦收件箱"}
        heading.addView(brandTitle,LinearLayout.LayoutParams(dp(194),dp(64)))
        heading.addView(pageTitle,LinearLayout.LayoutParams(0,dp(64),1f))
        heading.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        syncButton = button("") { sync() }.apply {
            backgroundTintList=android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(muted); minWidth=dp(44); minimumWidth=dp(44); setPadding(0,0,0,0); textSize=14f;maxLines=1;strokeWidth=0
            icon=SelfRotatingIconDrawable(HubIcon("refresh",muted,dp(24)),this,this@HubActivity); iconSize=dp(24); iconPadding=0;iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START;contentDescription="刷新包裹"
        }
        heading.addView(syncButton,LinearLayout.LayoutParams(-2,-2))
        header.addView(heading)
        status = label(HubData.prefs(this).getString("last_report","绑定购物平台后，包裹会显示在这里").orEmpty(),12).apply {
            setTextColor(muted); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
            contentDescription="同步状态，点按查看完整结果"
            setOnClickListener { MaterialAlertDialogBuilder(this@HubActivity).setTitle("同步状态").setMessage(text).setPositiveButton("关闭",null).show() }
        }
        header.addView(status)
        statusChips=row()
        loadingArea=row().apply {visibility=View.GONE;setPadding(dp(14),dp(10),dp(14),dp(10));background=GradientDrawable().apply {setColor(Color.rgb(220,240,255));cornerRadius=dp(18).toFloat()};setOnClickListener {showSyncDetails()}}
        loadingLabel=label("",13,true).apply {setTextColor(accent);contentDescription="加载进度";maxLines=2;setPadding(dp(8),0,0,0)}
        loadingBar=OfficialFlowUi.spinner(this)
        loadingArea.addView(loadingBar,LinearLayout.LayoutParams(dp(22),dp(22)))
        loadingArea.addView(loadingLabel,LinearLayout.LayoutParams(0,-2,1f))
        loadingArea.addView(ImageView(this).apply {setImageDrawable(HubIcon("chevron",accent,dp(18)))},LinearLayout.LayoutParams(dp(18),dp(18)))
        root.addView(header)
        body = column()
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        navigationDivider=View(this).apply {setBackgroundColor(Color.rgb(217,238,252))}
        root.addView(navigationDivider,LinearLayout.LayoutParams(-1,dp(1)))
        val nav = row().apply { setPadding(dp(8),dp(4),dp(8),dp(4)); setBackgroundColor(Color.rgb(250,253,255));showDividers=LinearLayout.SHOW_DIVIDER_NONE }
        listOf("快递","身份码","我的").forEachIndexed { i,title ->
            val entry=column().apply {
                gravity=Gravity.CENTER; minimumHeight=dp(56); contentDescription=title
                addView(ImageView(this@HubActivity).apply { setImageDrawable(HubIcon(listOf("nav-truck","qr","user")[i],muted,dp(24))); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(24),dp(24)))
                addView(label(title,11).apply { gravity=Gravity.CENTER;setPadding(0,dp(3),0,0) })
                isClickable=true; isFocusable=true; setOnClickListener {tab=i;showHistory=false;bindingChannel=null;settingsPage=null;settingsParent=null;renderBody()}
            }
            navViews.add(entry)
            val holder=FrameLayout(this).apply {addView(entry,FrameLayout.LayoutParams(dp(96),dp(56),Gravity.CENTER))}
            nav.addView(holder,LinearLayout.LayoutParams(0,dp(58),1f))
        }
        navigation=nav;root.addView(nav)
        onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed(){if(settingsPage!=null){backSettingsPage()}else if(bindingChannel!=null){bindingChannel=null;renderBody()}else if(showHistory){showHistory=false;renderBody()}else if(tab!=0){tab=0;renderBody()}else{isEnabled=false;onBackPressedDispatcher.onBackPressed()}}
        })
        renderBody()
        if(savedInstanceState==null)handleShare(intent)
    }
    override fun onSaveInstanceState(out: Bundle) { rememberParcelReading();out.putString("parcelReadingPositions",readingState());out.putString("pendingLookup",pendingLookup);out.putString("queuedLookup",queuedLookup);out.putBoolean("pendingLookupReady",pendingLookupReady || activeLookup);out.putStringArrayList("manualSyncChannels",ArrayList(bindingSyncQueue.pendingManual()+if(activeManualSync)activeBindingChannels else emptySet()));out.putInt("pendingHistoryDays",bindingSyncQueue.pendingRange() ?: activeHistoryDays ?: 0);out.putBoolean("didAutoSync",didAutoSync);out.putStringArrayList("bindingSyncChannels",ArrayList(bindingSyncQueue.pending()+activeBindingChannels));out.putString("bindingChannel",bindingChannel);out.putString("settingsPage",settingsPage);out.putString("settingsParent",settingsParent);out.putString("settingsPlatform",settingsPlatform);out.putInt("historyDays",historyDays);out.putInt("tab",tab);out.putBoolean("history",showHistory);out.putString("search",search);out.putString("filter",filter); out.putString("platform",platform); out.putString("pendingImagePlatform",pendingImagePlatform); super.onSaveInstanceState(out) }
    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter(HubData.CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changed,intentFilter,RECEIVER_NOT_EXPORTED) else registerReceiver(changed,intentFilter)
        receiverRegistered = true
        handler.postDelayed(identityTick,30000)
    }
    override fun onResume() {
        super.onResume()
        syncForeground=true
        if (::body.isInitialized) renderBody()
        reflectBackgroundSync()
        val autoPrefs=HubData.prefs(this)
        val hasActive=Store.items(this).any {!HubData.collected(this,it) && !ParcelEligibility.isVirtual(it) && ParcelPresentation.stage(it) in setOf(ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.TRANSIT,ParcelPresentation.Stage.UNVERIFIED,ParcelPresentation.Stage.NOT_SHIPPED)}
        val now=System.currentTimeMillis()
        if ((!didAutoSync || hasActive) && SyncRunCoordinator.due(this) &&
            pendingLookup.isBlank() && autoPrefs.getBoolean("auto_open",true) && Store.hasAnyAccount(this) &&
            !working && !SyncEngine.isBusy() && !SyncRunCoordinator.isBusy() && bindingSyncQueue.pending().isEmpty() && bindingSyncQueue.pendingRange()==null) {
            didAutoSync=true
            autoPrefs.edit().putLong("foreground_sync_attempt_at",now).apply()
            sync(automatic=true)
        }
        handler.post(drainBindingSync)
    }
    override fun onPause() {
        rememberParcelReading()
        HubData.prefs(this).edit().putString("parcel_reading_positions",readingState()).apply()
        syncForeground=false;handler.removeCallbacks(drainBindingSync)
        super.onPause()
    }
    override fun onStop() {
        if (receiverRegistered) { unregisterReceiver(changed); receiverRegistered=false }
        handler.removeCallbacks(identityTick)
        handler.removeCallbacks(scanPhotos);photoScanScheduled=false;handler.removeCallbacks(updateParcels);photoJob?.cancel()
        super.onStop()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }
    private fun handleShare(incoming: Intent) {
        if (incoming.action == Intent.ACTION_SEND && incoming.type == "text/plain") incoming.getStringExtra(Intent.EXTRA_TEXT)?.let {
            incoming.action=Intent.ACTION_MAIN
            incoming.removeExtra(Intent.EXTRA_TEXT)
            importDialog(it)
        }
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
        dividerDrawable=GradientDrawable().apply { setColor(Color.TRANSPARENT);setSize(dp(8),dp(1)) }
        showDividers=LinearLayout.SHOW_DIVIDER_MIDDLE
    }
    private fun label(value: String, size: Int=16,bold: Boolean=false) = TextView(this).apply {
        text=value; textSize=size.toFloat(); setTextColor(ink); includeFontPadding=false;setPadding(0,0,0,0)
        if(bold) setTypeface(Typeface.create("sans-serif",Typeface.BOLD))
    }
    private fun button(title: String, action: () -> Unit) = MaterialButton(this).apply {
        text=title; isAllCaps=false; minHeight=dp(44); minimumHeight=dp(44); maxLines=3; includeFontPadding=false
        insetTop=0;insetBottom=0;setPadding(dp(12),0,dp(12),0);setOnClickListener { action() }
        backgroundTintList=android.content.res.ColorStateList.valueOf(surface)
        strokeWidth=dp(1);strokeColor=android.content.res.ColorStateList.valueOf(if(dark)Color.rgb(64,81,102) else Color.rgb(226,232,240))
        cornerRadius=dp(12);textSize=14f;setTextColor(accent)
    }
    private fun card(parent: LinearLayout): LinearLayout = column().apply {
        setPadding(dp(12),dp(12),dp(12),dp(12))
        background=GradientDrawable().apply { setColor(surface); cornerRadius=dp(16).toFloat() }
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
    }
    private fun scroll(): LinearLayout {
        val content = column().apply { setPadding(dp(16),dp(4),dp(16),dp(12)) }
        body.addView(ScrollView(this).apply {
            isFillViewport=true; addView(content)
            if(tab==0) {
                parcelScroll=this;parcelScrollScope=readingScope();restoringReading=true
                isFocusableInTouchMode=true;descendantFocusability=ViewGroup.FOCUS_BEFORE_DESCENDANTS
            }
            setOnScrollChangeListener {_:View,_,_,_,_->scrollingUntil=SystemClock.uptimeMillis()+120;schedulePhotos()}
        },LinearLayout.LayoutParams(-1,0,1f))
        return content
    }
    private fun renderBody() {
        rememberParcelReading()
        photoSlots.clear();handler.removeCallbacks(scanPhotos);photoScanScheduled=false;parcelScroll=null
        body.removeAllViews()
        pageTitle.text=when(tab){1->"身份码";2->"我的";else->"哆啦收件箱"}
        pageTitle.visibility=if(tab==0)View.GONE else View.VISIBLE
        header.setPadding(dp(if(tab==2)26 else 22),dp(12),dp(16),dp(4))
        brandTitle.visibility=if(tab==0)View.VISIBLE else View.GONE
        syncButton.visibility=if(tab==0)View.VISIBLE else View.GONE
        status.visibility=View.GONE
        statusChips.visibility=if(tab==0 && loadingArea.visibility!=View.VISIBLE)View.VISIBLE else View.GONE
        refreshStatusChips()
        header.visibility=if(((bindingChannel!=null || settingsPage!=null) && tab==2) || (showHistory && tab==0))View.GONE else View.VISIBLE
        val standalone=tab==2 && (settingsPage in setOf("help","result","verification") || (settingsPage=="results" && HubData.channels.keys.any {SyncStatusText.needsAttention(HubData.prefs(this).getString("binding_sync_brief_$it",""))}))
        navigation.visibility=if(standalone)View.GONE else View.VISIBLE;navigationDivider.visibility=navigation.visibility
        navViews.forEachIndexed { i,entry ->
            val icon=if(i==tab)listOf("nav-truck","qr-filled","user-filled")[i] else listOf("nav-truck","qr","user")[i]
            (entry.getChildAt(0) as ImageView).setImageDrawable(HubIcon(icon,if(i==tab)accent else muted,dp(24)))
            (entry.getChildAt(1) as TextView).setTextColor(if(i==tab)accent else muted)
            entry.isSelected=i==tab
            entry.background=GradientDrawable().apply {setColor(if(i==tab){if(dark)Color.rgb(35,58,87) else Color.rgb(230,241,255)}else Color.TRANSPARENT);cornerRadius=dp(16).toFloat()}
        }
        when {settingsPage!=null && tab==2->renderSettingsPage();bindingChannel!=null && tab==2->renderPlatformBinding(bindingChannel!!);tab==0->parcels();tab==1->identities();else->settings()}
    }

    private fun refreshStatusChips() {
        if(!::statusChips.isInitialized)return
        statusChips.removeAllViews()
        if(HubData.items(this).isEmpty() && !Store.hasAnyAccount(this)) {statusChips.visibility=View.GONE;return}
        val prefs=HubData.prefs(this)
        val at=HubData.channels.keys.maxOfOrNull {prefs.getLong("binding_sync_at_$it",0)} ?: 0L
        val failed=HubData.channels.keys.count {SyncStatusText.needsAttention(prefs.getString("binding_sync_brief_$it",""))}
        statusChips.background=null;statusChips.setPadding(0,0,0,0)
        if(failed>0) {
            statusChips.gravity=Gravity.CENTER_VERTICAL;statusChips.setPadding(dp(12),dp(9),dp(10),dp(9))
            statusChips.background=OfficialFlowUi.rounded(this,Color.rgb(255,243,223),18)
            statusChips.addView(ImageView(this).apply {setImageDrawable(HubIcon("info",Color.rgb(169,109,11),dp(18)))},LinearLayout.LayoutParams(dp(18),dp(18)).apply {marginEnd=dp(9)})
            statusChips.addView(label("部分平台未读完，已有记录保留",12).apply {setTextColor(Color.rgb(169,109,11));maxLines=2},LinearLayout.LayoutParams(0,-2,1f))
            statusChips.addView(label("查看 ›",12).apply {setTextColor(Color.rgb(169,109,11));minimumHeight=dp(26);gravity=Gravity.CENTER_VERTICAL})
            statusChips.isClickable=true;statusChips.isFocusable=true;statusChips.setOnClickListener {showSyncDetails()};return
        }
        val message=when {android.os.SystemClock.uptimeMillis()<refreshFinishedUntil->"更新完成 · 查看同步结果 ›";failed>0->"$failed 个平台未完成 · 已有记录保留 · 查看详情";at>0->"${ParcelTime.format(at)} 读取 · 查看同步详情 ›";else->"绑定平台后同步 · 查看详情 ›"}
        statusChips.gravity=Gravity.CENTER
        statusChips.addView(View(this).apply {background=GradientDrawable().apply {setColor(if(failed>0)Color.rgb(244,145,0) else Color.rgb(0,172,121));shape=GradientDrawable.OVAL}},LinearLayout.LayoutParams(dp(8),dp(8)))
        statusChips.addView(label(message,11,true).apply {setTextColor(if(failed>0)Color.rgb(172,92,0) else muted);gravity=Gravity.CENTER_VERTICAL;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;minimumHeight=dp(36);setOnClickListener {showSyncDetails()}},LinearLayout.LayoutParams(-2,dp(36)))
    }
    private fun showSyncDetails() {
        val ui=HubSheets(this);val panel=ui.panel("同步详情")
        val prefs=HubData.prefs(this)
        val progressText=ui.text(loadingLabel.text.toString().ifBlank {"正在同步"},16,true,accent)
        if(working)ui.card(panel.content,HubUi.paleBlue).apply {
            val line=ui.row();line.addView(ProgressBar(this@HubActivity),LinearLayout.LayoutParams(dp(28),dp(28)).apply {marginEnd=dp(14)})
            line.addView(ui.column().apply {addView(progressText);addView(ui.text("正在读取订单信息，请稍候…",13,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(6)})},LinearLayout.LayoutParams(0,-2,1f));addView(line)
        }
        for(channel in listOf("jd","pdd","taobao","douyin").filter {it in HubData.channels}) {
            val accounts=Store.accounts(this,channel)
            val enabled=accounts.any {it.enabled && (channel!="taobao" || it.taobaoOrdersEnabled)}
            val value=when {channel in activeBindingChannels && working->"读取中";channel in bindingSyncQueue.pending()->"已排队";!enabled->if(channel=="taobao" && accounts.any {it.enabled})"订单待验证" else "未登录";prefs.getString("binding_sync_brief_$channel","")=="已更新"->"已完成";else->prefs.getString("binding_sync_brief_$channel","待同步").orEmpty()}
            ui.card(panel.content,padding=8).apply {
                background=ui.background(Color.WHITE,12,Color.rgb(227,237,248));(layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(5)
                ui.actionRow(this,HubData.channels[channel].orEmpty(),value=value,channel=channel,tint=if(SyncStatusText.needsAttention(value))ui.orange else if(value=="已完成")ui.green else ui.blue){panel.close();if(enabled)showBindingResult(channel) else platformAccounts(channel)}
            }
        }
        if(working)ui.buttons(panel.footer,"查看当前结果","停止当前同步",{panel.close();showRecentResults()},{stopCurrentWork();panel.close()})
        else ui.fullButton(panel.footer,"查看完整同步结果"){panel.close();showRecentResults()}
        panel.show()
        val tick=object:Runnable {override fun run(){if(!panel.dialog.isShowing)return;progressText.text=if(working)loadingLabel.text else "平台读取已结束";if(working)handler.postDelayed(this,500)}}
        handler.post(tick);panel.dialog.setOnDismissListener {handler.removeCallbacks(tick)}
    }
    private fun quietAction(title:String, action:()->Unit) = label(title,13).apply {
        setTextColor(accent); gravity=Gravity.CENTER; minHeight=dp(48); setPadding(dp(8),0,dp(8),0)
        isClickable=true;isFocusable=true;setOnClickListener { action() }
    }
    private fun parcels() {
        if(showHistory) {
            val toolbar=row().apply {setPadding(dp(16),dp(8),dp(16),dp(8))}
            toolbar.addView(ImageButton(this).apply {setImageDrawable(HubIcon("back",ink,dp(24)));background=null;contentDescription="返回快递";setOnClickListener {showHistory=false;renderBody()}},LinearLayout.LayoutParams(dp(40),dp(44)))
            toolbar.addView(label("取件历史",20,true).apply {gravity=Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(0,dp(44),1f).apply {marginStart=dp(12)})
            toolbar.addView(View(this),LinearLayout.LayoutParams(dp(40),dp(44)))
            body.addView(toolbar)
        }
        val content=scroll()
        if(!showHistory)content.setPadding(dp(12),dp(4),dp(12),dp(12))
        val searchRow=row().apply {
            background=GradientDrawable().apply {setColor(surface);cornerRadius=dp(12).toFloat();setStroke(dp(1),if(dark)muted else Color.rgb(226,232,240))}
            setPadding(dp(12),0,dp(4),0)
        }
        searchRow.addView(ImageView(this).apply {setImageDrawable(HubIcon("search",muted,dp(20)));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(20),dp(20)))
        val input=EditText(this).apply {
            hint="输入运单号，查取件码";contentDescription=hint;textSize=14f;setTextColor(ink);setHintTextColor(muted)
            setSingleLine(true);setText(search);setPadding(0,0,0,0);background=null
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener {_,action,_->if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){queryDialog();true}else false}
        }
        searchRow.addView(input,LinearLayout.LayoutParams(0,dp(48),1f))
        val clearSearch=ImageButton(this).apply {
            setImageDrawable(HubIcon("close",muted,dp(16)));background=null;contentDescription="清除搜索";setPadding(dp(6),dp(6),dp(6),dp(6))
            visibility=if(search.isBlank())View.GONE else View.VISIBLE;setOnClickListener {input.setText("")}
        }
        searchRow.addView(clearSearch,LinearLayout.LayoutParams(dp(32),dp(40)))
        searchRow.addView(button("查询"){queryDialog()}.apply {backgroundTintList=android.content.res.ColorStateList.valueOf(accent);setTextColor(Color.WHITE);textSize=15f;maxLines=1;minWidth=0;minimumWidth=0;minHeight=dp(40);minimumHeight=dp(40);setPadding(dp(12),0,dp(12),0)},LinearLayout.LayoutParams(dp(76),dp(40)))
        if(!showHistory) {
            content.addView(searchRow,LinearLayout.LayoutParams(-1,-2).apply {setMargins(dp(4),0,dp(4),dp(4))})
            (statusChips.parent as? ViewGroup)?.removeView(statusChips)
            (loadingArea.parent as? ViewGroup)?.removeView(loadingArea)
            content.addView(statusChips,LinearLayout.LayoutParams(-1,-2).apply {marginStart=dp(4);marginEnd=dp(4)})
            content.addView(loadingArea,LinearLayout.LayoutParams(-1,-2).apply {setMargins(dp(4),dp(4),dp(4),dp(8))})
        }
        input.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {search=s.toString();clearSearch.visibility=if(search.isBlank())View.GONE else View.VISIBLE;statusChips.visibility=if(search.isBlank() && loadingArea.visibility!=View.VISIBLE)View.VISIBLE else View.GONE;renderList()}
            override fun afterTextChanged(s:Editable?) {}
        })
        list=column();content.addView(list,LinearLayout.LayoutParams(-1,-2));renderList()
    }
    private fun section(title:String,count:Int?=null,actionTitle:String?=null,rangeCaption:String?=null,action:(()->Unit)?=null) {
        val heading=row().apply {setPadding(dp(if(showHistory)0 else 4),dp(8),dp(if(showHistory)0 else 4),dp(8))}
        heading.addView(label(title,18,true))
        if(rangeCaption!=null)heading.addView(label(rangeCaption,11).apply {
            setTextColor(muted);setPadding(dp(3),dp(4),dp(3),dp(4))
        })
        if(count!=null)heading.addView(label(count.toString(),14,true).apply {setTextColor(accent);gravity=Gravity.CENTER;minWidth=dp(28);setPadding(dp(6),dp(2),dp(6),dp(2));background=GradientDrawable().apply {setColor(Color.rgb(219,240,255));cornerRadius=dp(14).toFloat()}})
        heading.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        if(actionTitle!=null && action!=null)heading.addView(label(actionTitle.replace(" ›",""),13).apply {
            maxLines=1;setTextColor(muted);gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(28)
            setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,HubIcon("chevron",muted,dp(15)),null)
            compoundDrawablePadding=dp(4);contentDescription=actionTitle;setOnClickListener {action()}
        })
        list.addView(heading)
    }
    private fun divider(parent:LinearLayout) {parent.addView(View(this).apply {setBackgroundColor(if(dark)Color.rgb(51,65,85) else Color.rgb(232,238,245))},LinearLayout.LayoutParams(-1,dp(1)))}
    private fun group():LinearLayout = column().apply {
        background=GradientDrawable().apply {setColor(surface);cornerRadius=dp(14).toFloat()}
        list.addView(this,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(20)})
    }
    private fun photoKey(item:ExpressItem)="${item.source}:${item.accountId}:${item.mailNo}"
    private fun productImage(item:ExpressItem,size:Int,reuse:Boolean=false):FrameLayout {
        val previous=if(reuse)parcelPhotos[photoKey(item)] else null
        if(previous!=null) {
            previous.item=item
            (previous.frame.parent as? ViewGroup)?.removeView(previous.frame)
            previous.frame.layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
            previous.frame.setOnClickListener {openProductPhoto(item)}
            photoSlots.add(previous)
            return previous.frame
        }
        val frame=FrameLayout(this).apply {
            background=GradientDrawable().apply {setColor(if(dark)Color.rgb(42,55,71) else Color.rgb(243,246,250));cornerRadius=dp(9).toFloat()}
            clipToOutline=true; contentDescription="商品图片"
        }
        frame.addView(ImageView(this).apply {setImageDrawable(HubIcon("box",muted,dp(24)));setPadding(dp(14),dp(14),dp(14),dp(14))},FrameLayout.LayoutParams(-1,-1))
        val image=ImageView(this).apply {scaleType=ImageView.ScaleType.CENTER_CROP;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
        frame.addView(image,FrameLayout.LayoutParams(-1,-1))
        val slot=PhotoSlot(item,frame,image)
        photoSlots.add(slot)
        if(reuse)parcelPhotos[photoKey(item)]=slot
        frame.isClickable=true;frame.isFocusable=true
        frame.setOnClickListener {openProductPhoto(item)}
        frame.layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
        return frame
    }
    private fun openProductPhoto(item:ExpressItem) {
        val url=ParcelGroups.image(item,Store.jdGoods(this)[item.mailNo])
        if(url.isBlank())toast("平台暂未提供商品图片") else showProductPhoto(item,url)
    }
    private fun showProductPhoto(item:ExpressItem,url:String) {
        val dialog=android.app.Dialog(this,android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val content=column().apply {setBackgroundColor(Color.rgb(14,18,25));setPadding(dp(12),dp(8),dp(12),dp(8))}
        val heading=row()
        heading.addView(label("商品图片",18,true).apply {setTextColor(Color.WHITE)},LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(button("关闭大图"){dialog.dismiss()}.apply {maxLines=1;textSize=13f;contentDescription="关闭商品大图"})
        content.addView(heading)
        val image=ZoomPhotoView(this);content.addView(image,LinearLayout.LayoutParams(-1,0,1f))
        content.addView(label(productTitle(item),14).apply {setTextColor(Color.WHITE);maxLines=2})
        content.addView(label("双指缩放 · 双击放大 · 拖动查看",12).apply {setTextColor(Color.rgb(180,192,207));gravity=Gravity.CENTER})
        dialog.setContentView(content);dialog.show()
        dialog.setOnDismissListener {ProductPhotos.clear(image)}
        ProductPhotos.load(image,url)
    }
    private fun schedulePhotos() {
        if(photoScanScheduled)return
        photoScanScheduled=true
        // Run once per frame, including while the finger is moving.
        handler.postDelayed(scanPhotos,16)
    }
    private fun visiblePhotos():List<PhotoSlot> {
        val viewport=android.graphics.Rect()
        if(tab!=0 || !body.getGlobalVisibleRect(viewport))return emptyList()
        return photoSlots.filter {slot->val rect=android.graphics.Rect();slot.frame.getGlobalVisibleRect(rect) && android.graphics.Rect.intersects(rect,viewport)}
    }
    private fun loadVisiblePhotos() {
        val visible=visiblePhotos()
        val goods=Store.jdGoods(this)
        for(slot in visible) {
            val url=ParcelGroups.image(slot.item,goods[slot.item.mailNo])
            if(url.isNotBlank() && url!=slot.loaded) {
                slot.loaded=url
                // Decode for this display size; Coil keeps the original compressed bytes on disk.
                ProductPhotos.load(slot.image,url,(slot.frame.width*2).coerceAtLeast(dp(96)))
            }
        }
        if(photoJob?.isActive==true || working || SyncEngine.isBusy() || SyncRunCoordinator.isBusy())return
        val now=System.currentTimeMillis();val prefs=HubData.prefs(this)
        val missing=visible.filter {slot->slot.item.source=="jd" && !slot.item.isOrderNumber &&
            slot.item.mailNo.matches(Regex("[A-Za-z0-9]{8,32}")) && ProductImagePolicy.canFetch(goods[slot.item.mailNo]?.imageUrl?.isNotBlank()==true,now,prefs.getLong("photo_retry_${slot.item.accountId}_${slot.item.mailNo}",0))}
        val account=Store.accounts(this,"jd").firstOrNull {a->a.enabled && missing.any {it.item.accountId==a.id}} ?: return
        val numbers=missing.filter {it.item.accountId==account.id}.map {it.item.mailNo}.distinct().take(6)
        if(numbers.isEmpty())return
        photoJob=lifecycleScope.launch {
            numbers.forEach {prefs.edit().putLong("photo_retry_${account.id}_$it",now+15*60000).apply()}
            try {
                val result=withTimeout(30000){JdlWebFetcher.fetchMetadata(this@HubActivity,account,numbers)}
                result.metadataRead.forEach {prefs.edit().putLong("photo_retry_${account.id}_$it",System.currentTimeMillis()+86400000).apply()}
            } catch(e:CancellationException){throw e} catch(_:Exception){} finally {
                photoJob=null
                if(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))schedulePhotos()
            }
        }
    }
    private fun productTitle(item:ExpressItem)=ParcelGroups.title(item,(renderGoods ?: Store.jdGoods(this))[item.mailNo])
    private fun collectedSnapshot()=renderCollected ?: HubData.collectedTimes(this)
    private fun station(item:ExpressItem):String {
        val bracket=Regex("""[【\[(]([^】\])]{2,40})[】\])]""").findAll(item.latestText).map {it.groupValues[1]}.firstOrNull {listOf("驿站","快递站","自提","快递柜","代收").any(it::contains)}
        return PickupCardInfo.station(item).ifBlank {bracket.orEmpty()}.takeIf {it.isNotBlank()} ?: if(ParcelPresentation.stage(item)==ParcelPresentation.Stage.PICKUP)"待取件 · ${HubData.channels[item.source] ?: item.provider}" else item.stateLabel()
    }
    private fun renderList() {
        if(!::list.isInitialized || tab!=0)return
        handler.removeCallbacks(updateParcels)
        renderGoods=Store.jdGoods(this);renderCollected=HubData.collectedTimes(this)
        renderedCards.clear()
        rememberParcelReading()
        parcelScrollScope=readingScope()
        val savedPosition=readingPositions[parcelScrollScope]
        val generation=++readingRenderGeneration
        restoringReading=true
        try {renderListContent()} finally {
            parcelCards.keys.retainAll(renderedCards)
            renderGoods=null;renderCollected=null
        }
        parcelScroll?.let {scroll->scroll.doOnPreDraw {
            if(parcelScroll===scroll && generation==readingRenderGeneration) {
                val maxScroll=((scroll.getChildAt(0)?.height ?: 0)-scroll.height+scroll.paddingTop+scroll.paddingBottom).coerceAtLeast(0)
                scroll.scrollTo(0,ListReadingPosition.restore(savedPosition,readingRows(),maxScroll))
                restoringReading=false
                rememberParcelReading()
            }
        }}
    }
    private fun renderListContent() {
        list.removeAllViews()
        body.findViewWithTag<View>("history-footer")?.let {body.removeView(it)}
        photoSlots.clear()
        schedulePhotos()
        val all=HubData.items(this,renderGoods.orEmpty())
        parcelPhotos.keys.retainAll(all.mapTo(mutableSetOf(),::photoKey))
        if(all.isEmpty() && loadingArea.visibility==View.VISIBLE && !showHistory) {
            section("待取件");repeat(3){skeletonParcel()}
            section("最近已取",rangeCaption="近7天",actionTitle="取件历史",action={showHistory=true;renderBody()});repeat(2){skeletonParcel()}
            return
        }
        val goods=renderGoods.orEmpty()
        val times=renderCollected.orEmpty()
        val matched=all.filter {item ->
            listOf(item.mailNo,item.latestText,item.pickupCode,item.companyName,productTitle(item)).any {it.contains(search,true)}
        }
        if(search.isNotBlank() && matched.isEmpty()) {
            section("搜索结果")
            emptyParcelCard(true,"没有匹配的包裹","未找到与「$search」相关的包裹\n请检查运单号是否正确","清除搜索"){search="";renderBody()}
            return
        }
        if(showHistory) {
            val history=ParcelPresentation.recent(matched.filter {filter=="全部平台" || HubData.channels[it.source]==filter || (filter=="菜鸟查询" && it.source=="cainiao")},times,null,historyDays.takeIf {it>0})
            val range=row().apply {setPadding(0,dp(4),0,dp(8))}
            range.addView(button(historyRangeLabel(historyDays)){showHistoryRanges()}.apply {
                textSize=13f;maxLines=1;minWidth=0;minimumWidth=0;setPadding(dp(12),0,dp(12),0);cornerRadius=dp(12);strokeWidth=0;setTextColor(ink)
                contentDescription="选择查询范围";icon=null;setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,HubIcon("down",muted,dp(14)),null);compoundDrawablePadding=dp(6)
            })
            range.addView(button(filter) {
                showHistorySources()
            }.apply {textSize=13f;maxLines=1;minWidth=0;minimumWidth=0;setPadding(dp(10),0,dp(10),0);strokeWidth=0;setTextColor(ink);cornerRadius=dp(12);icon=null;setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,HubIcon("down",muted,dp(14)),null);compoundDrawablePadding=dp(6);contentDescription="筛选历史平台"})
            range.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
            range.addView(label("共${history.size}件",12).apply {setTextColor(muted)})
            list.addView(range)
            renderHistoryTasks(list)
            if(!working && bindingSyncQueue.pendingRange()==null && !historyHasFailure() && SyncLedger.needsExpansion(this,historyDays)) {
                val footer=column().apply {tag="history-footer";setBackgroundColor(Color.rgb(239,249,255))}
                footer.addView(View(this).apply {setBackgroundColor(Color.rgb(223,236,247))},LinearLayout.LayoutParams(-1,dp(1)))
                val warning=row().apply {minimumHeight=dp(44);setPadding(0,0,0,0)}
                warning.addView(ImageButton(this).apply {setImageDrawable(HubIcon("info",muted,dp(17)));background=null;contentDescription="查看查询范围说明";setOnClickListener {showHistoryExplanation()}},LinearLayout.LayoutParams(dp(36),dp(44)))
                warning.addView(label("部分日期尚未查询",12).apply {setTextColor(muted);setOnClickListener {showHistoryExplanation()}},LinearLayout.LayoutParams(0,-2,1f))
                warning.addView(label("继续查询 ›",12).apply {setTextColor(accent);gravity=Gravity.CENTER;minimumHeight=dp(44);setPadding(dp(6),0,dp(6),0);isClickable=true;isFocusable=true;setOnClickListener {sync(historyDays,historyImport=true)}},LinearLayout.LayoutParams(-2,-2))
                footer.addView(warning);body.addView(footer,LinearLayout.LayoutParams(-1,-2).apply {setMargins(dp(16),dp(4),dp(16),0)})
            }
            if(history.isEmpty())group().addView(label("所选时间内没有已取件记录",14).apply {setPadding(dp(16),dp(24),dp(16),dp(24))})
            else history.forEach {item->parcelRow(list,item,true)}
            return
        }
        val pending=matched.filter {!times.containsKey(it.mailNo) && ParcelPresentation.stage(it)==ParcelPresentation.Stage.PICKUP}.sortedByDescending {ParcelPresentation.eventTime(it)}
        val transit=TransitGroup.ordered(matched.filter {!times.containsKey(it.mailNo)})
        if(pending.isNotEmpty()) {
            section("待取件",pending.size)
            pending.forEach {item->parcelRow(list,item,false)}
        }
        if(transit.isNotEmpty()) {
            section("运输中",transit.size)
            transit.forEach {item->transitRow(list,item)}
        }
        if(pending.isEmpty() && transit.isEmpty())emptyParcelCard(false,if(all.isEmpty())"你的包裹会汇总在这里" else "当前没有待取或运输中的包裹",if(all.isEmpty())"在我的绑定购物平台后同步，\n也可手动导入" else "已有记录可在取件历史中查看",if(all.isEmpty())"绑定购物平台" else "查看取件历史"){if(all.isEmpty())tab=2 else showHistory=true;renderBody()}
        val recentDays=HistoryRangePolicy.DEFAULT_DAYS
        val recent=ParcelPresentation.recent(matched,times,days=recentDays)
        section("最近已取",actionTitle="取件历史",rangeCaption=historyRangeLabel(recentDays),action={showHistory=true;renderBody()})
        if(recent.isEmpty())card(list).apply {val line=row().apply {setPadding(dp(4),dp(14),dp(4),dp(14))};line.addView(ImageView(this@HubActivity).apply {setImageDrawable(HubIcon("box",muted,dp(36)));background=GradientDrawable().apply {setColor(HubUi.paleBlue);cornerRadius=dp(14).toFloat()};setPadding(dp(12),dp(12),dp(12),dp(12))},LinearLayout.LayoutParams(dp(64),dp(64)).apply {marginEnd=dp(16)});line.addView(label("近7天暂无已取记录",14).apply {setTextColor(muted)});addView(line)}
        else recent.forEach {item->parcelRow(list,item,true)}
    }
    private fun historyRangeLabel(days:Int)=when(days){90->"近3个月";180->"近半年";else->"近${days}天"}
    private fun skeletonParcel() {
        card(list).apply {
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            fun bar(width:Int,height:Int)=View(this@HubActivity).apply {background=GradientDrawable().apply {setColor(Color.rgb(229,234,241));cornerRadius=dp(5).toFloat()};layoutParams=LinearLayout.LayoutParams(dp(width),dp(height))}
            val line=row();line.addView(bar(64,64),LinearLayout.LayoutParams(dp(64),dp(64)).apply {marginEnd=dp(16)})
            val text=column();text.addView(bar(94,16));text.addView(bar(140,12),LinearLayout.LayoutParams(dp(140),dp(12)).apply {topMargin=dp(9)});text.addView(bar(82,12),LinearLayout.LayoutParams(dp(82),dp(12)).apply {topMargin=dp(9)})
            line.addView(text,LinearLayout.LayoutParams(0,-2,1f));line.addView(bar(58,22),LinearLayout.LayoutParams(dp(58),dp(22)).apply {gravity=Gravity.TOP;topMargin=dp(4)});addView(line)
        }
    }
    private fun emptyParcelCard(searching:Boolean,title:String,description:String,action:String,onAction:()->Unit) {
        card(list).apply {
            if(!searching && HubData.items(this@HubActivity).isEmpty())(layoutParams as LinearLayout.LayoutParams).topMargin=dp(52)
            gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(18),dp(if(searching)18 else 34),dp(18),dp(24))
            addView(ImageView(this@HubActivity).apply {setImageDrawable(HubStateArt(searching))},LinearLayout.LayoutParams(dp(if(searching)86 else 120),dp(if(searching)86 else 120)))
            addView(label(title,18,true).apply {gravity=Gravity.CENTER;setPadding(0,dp(if(searching)4 else 12),0,dp(8))})
            addView(label(description,14).apply {gravity=Gravity.CENTER;setTextColor(muted);setLineSpacing(dp(4).toFloat(),1f)})
            addView(button(action,onAction).apply {setTextColor(if(searching)accent else Color.WHITE);strokeWidth=0;backgroundTintList=android.content.res.ColorStateList.valueOf(if(searching)HubUi.paleBlue else accent)},LinearLayout.LayoutParams(if(searching)dp(164) else -1,dp(if(searching)42 else 54)).apply {topMargin=dp(if(searching)16 else 24)})
        }
    }
    private fun showHistoryRanges() {
        val ui=HubSheets(this);val panel=ui.panel("查询范围")
        var selected=historyDays
        val controls=mutableMapOf<Int,LinearLayout>()
        fun update(){controls.forEach {(days,control)->
            (control.getChildAt(0) as RadioButton).isChecked=days==selected
            (control.getChildAt(1) as TextView).setTextColor(if(days==selected)accent else ink)
            control.background=ui.background(if(days==selected)HubUi.paleBlue else Color.WHITE,10)
            control.isSelected=days==selected
        }}
        for(days in HistoryRangePolicy.options) {
            val control=row().apply {minimumHeight=dp(46);setPadding(dp(4),0,dp(10),0);contentDescription="查询范围：${historyRangeLabel(days)}";setOnClickListener {selected=days;update()}}
            control.addView(RadioButton(this).apply {isClickable=false;isFocusable=false;minimumWidth=0;minimumHeight=0;setPadding(0,0,0,0);buttonTintList=android.content.res.ColorStateList.valueOf(accent)},LinearLayout.LayoutParams(dp(26),dp(32)))
            control.addView(label(when(days){90->"近 3 个月（90天）";180->"近半年（180天）";else->"近 $days 天"},16),LinearLayout.LayoutParams(0,-2,1f))
            if(!SyncLedger.needsExpansion(this,days))control.addView(ui.badge("已有记录"))
            panel.content.addView(control,LinearLayout.LayoutParams(-1,-2));ui.divider(panel.content)
            controls[days]=control
        }
        update();ui.gap(panel.content)
        panel.content.addView(ui.text("已有记录先显示，扩大范围后自动查询。",13,false,muted))
        ui.fullButton(panel.footer,"应用范围并查询") {
            historyDays=selected
            val needsRead=HubData.applyHistoryRange(this,selected)
            panel.close();renderBody()
            if(needsRead || working || SyncEngine.isBusy() || SyncRunCoordinator.isBusy())sync(selected,historyImport=true)
            else toast("已切换范围，先显示已有记录")
        };panel.show()
    }
    private fun showHistorySources() {
        val ui=HubSheets(this);val panel=ui.panel("订单来源筛选")
        val choices=listOf("" to "全部平台")+HubData.channels.entries.map {it.key to it.value}
        var selected=filter.takeIf {saved->choices.any {it.second==saved}} ?: "全部平台"
        val controls=mutableMapOf<String,Pair<LinearLayout,RadioButton>>()
        fun update(){controls.forEach {(key,pair)->pair.second.isChecked=key==selected;pair.first.background=ui.background(if(key==selected)HubUi.paleBlue else Color.WHITE,10)}}
        choices.forEach {(channel,value)->
            val line=row().apply {minimumHeight=dp(54);setPadding(dp(4),0,dp(8),0);setOnClickListener {selected=value;update()}}
            val radio=RadioButton(this).apply {isClickable=false;isFocusable=false;minimumWidth=0;minimumHeight=0;setPadding(0,0,0,0);buttonTintList=android.content.res.ColorStateList.valueOf(accent)}
            line.addView(radio,LinearLayout.LayoutParams(dp(26),dp(32)))
            if(channel.isNotBlank())line.addView(ui.brand(channel,36))
            line.addView(label(if(value=="全部平台")"全部来源" else value,16,true))
            controls[value]=line to radio;panel.content.addView(line);ui.divider(panel.content)
        }
        update();ui.fullButton(panel.footer,"确定") {filter=selected;panel.close();renderList()};panel.show()
    }
    private fun metadataLine(icon:String,value:String):LinearLayout = row().apply {
        minimumHeight=dp(26)
        addView(ImageView(this@HubActivity).apply {setImageDrawable(HubIcon(icon,muted,dp(16)));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(16),dp(20)))
        addView(label(value,12).apply {setTextColor(muted);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(0,-2,1f))
    }
    private fun stateChip(text:String,kind:String):TextView=label(text,11,true).apply {
        maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
        val color=when(kind){"done"->if(dark)Color.rgb(111,225,175) else Color.rgb(18,122,83);"pending"->if(dark)Color.rgb(255,204,120) else Color.rgb(160,92,0);else->accent}
        setTextColor(color);setPadding(dp(8),dp(4),dp(8),dp(4))
        background=GradientDrawable().apply {setColor(when(kind){"done"->if(dark)Color.rgb(29,66,54) else Color.rgb(230,246,237);"pending"->if(dark)Color.rgb(69,53,29) else Color.rgb(255,244,218);else->if(dark)Color.rgb(35,58,87) else Color.rgb(237,244,255)});cornerRadius=dp(8).toFloat()}
    }
    private fun copyButton(value:String,caption:String)=ImageButton(this).apply {
        setImageDrawable(HubIcon("copy",muted,dp(18)));background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);setPadding(dp(3),dp(3),dp(3),dp(3))
        contentDescription="复制$caption $value";isFocusable=true
        setOnClickListener {(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(caption,value));toast("${caption}已复制")}
        TouchTargets.expand(this)
    }
    private fun waybillRow(parent:LinearLayout,item:ExpressItem,details:Boolean=false) {
        val number=if(item.isOrderNumber || item.mailNo.startsWith("ORDER-") || item.mailNo.startsWith("LOCAL-"))"运单尚未返回" else item.mailNo
        val line=row().apply {minimumHeight=dp(28)}
        line.addView(ImageView(this).apply {setImageDrawable(HubIcon("truck",muted,dp(16)));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(16),dp(20)))
        line.addView(label("${item.companyName.ifBlank {"快递"}} · $number",if(details)11 else 12).apply {setTextColor(muted);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(0,-2,1f))
        if(number==item.mailNo)line.addView(copyButton(number,"运单号"),LinearLayout.LayoutParams(dp(24),dp(24)))
        parent.addView(line)
        if(details) {
            val action=label("物流详情",11,true).apply {setTextColor(ink);gravity=Gravity.CENTER_VERTICAL;contentDescription="查看物流详情";setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,HubIcon("chevron",muted,dp(13)),null);compoundDrawablePadding=dp(2);setOnClickListener {openDetails(item)};TouchTargets.expand(this)}
            line.addView(action,LinearLayout.LayoutParams(-2,dp(28)))
        }
    }

    private fun sourceBadge(item:ExpressItem)=label(HubData.channels[item.source] ?: if(item.source=="cainiao")"菜鸟查询" else "手动导入",14,true).apply {
        setTextColor(PlatformStyle.accent(item.source));setPadding(dp(10),dp(4),dp(10),dp(4));maxLines=1
        background=GradientDrawable().apply {setColor(if(dark)Color.rgb(42,51,67) else PlatformStyle.tint(item.source));cornerRadius=dp(7).toFloat()}
    }
    private fun parcelMenu(item:ExpressItem):View=ImageButton(this).apply {
        setImageDrawable(HubIcon("more",muted,dp(20)));background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        minimumWidth=0;minimumHeight=0;setPadding(0,0,0,0);contentDescription="更多包裹操作 ${productTitle(item)}"
        post {TouchTargets.expand(this,48)}
        setOnClickListener {
            val marked=HubData.collected(this@HubActivity,item)
            val ui=HubSheets(this@HubActivity);val panel=ui.panel("包裹操作").apply {hideTitle()}
            ui.actionRow(panel.content,"复制取件信息","含取件码、运单号和驿站信息","copy",endIcon="copy") {
                panel.close();val text=PickupCardInfo.copyText(item)
                if(text.isBlank())toast("暂无可复制的取件信息") else {(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("取件信息",text));toast("取件信息已复制")}
            }
            ui.divider(panel.content)
            ui.actionRow(panel.content,if(marked)"撤销本机已取标记" else "标记已取","仅在本机标记，不等同于平台签收","circle-check",endIcon=null) {
                panel.close()
                if(marked){HubData.collect(this@HubActivity,item,false);renderList()}
                else showCollectedConfirmation(item)
            }
            if(item.pickupCode.isBlank() && ParcelPresentation.stage(item)==ParcelPresentation.Stage.PICKUP){ui.divider(panel.content);ui.actionRow(panel.content,"重新查询取件码","从原平台重新获取取件码（缺码时）","refresh",endIcon=null){panel.close();runLookup(item)}}
            ui.divider(panel.content);ui.actionRow(panel.content,"选择取件服务","按实际取件服务出示","identity",endIcon=null){panel.close();chooseIdentity(item)}
            for(index in 0 until panel.content.childCount)(panel.content.getChildAt(index) as? LinearLayout)?.apply {minimumHeight=dp(64);setPadding(dp(2),dp(6),dp(2),dp(6))}
            ui.fullButton(panel.footer,"取消",false,true){panel.close()};panel.show()
        }
        layoutParams=LinearLayout.LayoutParams(dp(28),dp(28))
    }
    private fun chooseIdentity(item:ExpressItem?=null) {
        val services=listOf("jd","taobao","pdd")
        val ui=HubSheets(this);val panel=ui.panel("选择取件服务")
        services.forEach {key->ui.actionRow(panel.content,IdentityRoutes.route(key).title,"按该服务的官方页面出示",channel=key){panel.close();IdentityLauncher.open(this,key,item?.let {PickupUiPolicy.account(it,key)}.orEmpty())}}
        ui.fullButton(panel.footer,"取消",false,true){panel.close()};panel.show()
    }
    private fun showCollectedConfirmation(item:ExpressItem) {
        val ui=HubSheets(this);val confirm=ui.center("标记本机已取？")
        val line=ui.row();line.addView(sheetProductImage(item,64),LinearLayout.LayoutParams(dp(64),dp(64)).apply {marginEnd=dp(14)})
        line.addView(ui.text(productTitle(item),16,true),LinearLayout.LayoutParams(0,-2,1f));confirm.content.addView(line)
        ui.gap(confirm.content)
        confirm.content.addView(ui.text(station(item),14,false,muted).apply {setCompoundDrawablesRelativeWithIntrinsicBounds(HubIcon("location",muted,dp(20)),null,null,null);compoundDrawablePadding=dp(10)})
        val arrival=ParcelPresentation.arrivalTime(item);val time=if(arrival>0)ParcelTime.format(arrival) else item.latestTime
        if(time.isNotBlank() || item.latestText.isNotBlank()){ui.gap(confirm.content,8);confirm.content.addView(ui.text(listOf(time,item.latestText).filter {it.isNotBlank()}.joinToString(" · "),13,false,muted).apply {setCompoundDrawablesRelativeWithIntrinsicBounds(HubIcon("clock",muted,dp(18)),null,null,null);compoundDrawablePadding=dp(8)})}
        ui.gap(confirm.content,8)
        if(!item.isOrderNumber)confirm.content.addView(ui.text("${item.companyName} · ${item.mailNo}",13,false,muted).apply {setCompoundDrawablesRelativeWithIntrinsicBounds(HubIcon("truck",muted,dp(18)),null,null,null);compoundDrawablePadding=dp(8)})
        ui.gap(confirm.content,10)
        confirm.content.addView(ui.text("仅在本机标记，不代表平台签收",13,false,muted).apply {setCompoundDrawablesRelativeWithIntrinsicBounds(HubIcon("info",muted,dp(18)),null,null,null);compoundDrawablePadding=dp(8)})
        ui.fullButton(confirm.footer,"确认已取") {confirm.close();HubData.collect(this,item,true);renderList();Snackbar.make(root,"已标记本机已取",Snackbar.LENGTH_LONG).setAction("撤销"){HubData.collect(this,item,false);renderList()}.show()}
        ui.fullButton(confirm.footer,"取消",false,true){confirm.close()};confirm.show()
    }
    private fun parcelRow(parent:LinearLayout,item:ExpressItem,compact:Boolean) {
        val cacheKey="${item.source}:${item.accountId}:${item.mailNo}:$compact:$showHistory"
        renderedCards.add(cacheKey)
        val stamp=CardStamp(item,renderGoods?.get(item.mailNo),collectedSnapshot()[item.mailNo],compact,showHistory,dark,resources.configuration.fontScale)
        parcelCards[cacheKey]?.takeIf {it.stamp==stamp}?.let {saved->
            (saved.view.parent as? ViewGroup)?.removeView(saved.view)
            parent.addView(saved.view)
            photoSlots.addAll(saved.photos)
            return
        }
        val photoStart=photoSlots.size
        val block=card(parent)
        block.tag="parcel-reading:${item.source}:${item.accountId}:${item.mailNo}"
        val largeType=resources.configuration.fontScale>1.15f && !compact
        var wideCode:View?=null
        if(!compact)block.setPadding(dp(12),dp(12),dp(12),dp(14))
        val location=PickupCardInfo.station(item).ifBlank {PickupCardInfo.address(item)}.ifBlank {
            Regex("""[【\[(]([^】\])]{2,60})[】\])]""").findAll(item.latestText).map {it.groupValues[1]}.firstOrNull {name->listOf("驿站","代收点","自提点","快递柜").any(name::contains)}.orEmpty()
        }
        if(!compact || showHistory) {
            val heading=row();heading.addView(sourceBadge(item));heading.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
            heading.addView(stateChip(if(compact){if(collectedSnapshot().containsKey(item.mailNo))"本机已取" else if(item.source=="douyin" && item.completedAt==0L)"交易完成" else "平台已签收"} else "待取件",if(compact)"done" else "pending"))
            if(!compact)heading.addView(parcelMenu(item));block.addView(heading)
        }
        val product=row().apply {gravity=Gravity.TOP;setPadding(0,if(compact && !showHistory)0 else dp(8),0,dp(6))}
        product.addView(productImage(item,if(largeType)108 else if(compact && !showHistory)88 else if(compact)72 else 76,reuse=true))
        val info=column().apply {if(!compact)setPadding(0,dp(3),0,0)}
        val name=row();name.addView(label(productTitle(item),16,true).apply {maxLines=if(largeType)3 else 2;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(0,-2,1f))
        if(compact && !showHistory)name.addView(stateChip(if(ParcelPresentation.stage(item)==ParcelPresentation.Stage.AFTERSALE)"已结束" else "已取件","done"))
        info.addView(name)
        if(item.relatedGoods.size>1)info.addView(label("同一运单 · ${item.relatedGoods.size} 笔订单 · 查看全部商品 ›",12,true).apply {
            setTextColor(accent);setPadding(0,dp(5),0,dp(3));setOnClickListener {openDetails(item)}
        })
        if(compact && showHistory) {
            val historical=item.historicalPickupCode.ifBlank {item.pickupCode}
            run {
                val historyLine=row().apply {minimumHeight=dp(30);setPadding(dp(6),dp(3),dp(6),dp(3));background=GradientDrawable().apply {setColor(Color.rgb(247,251,255));cornerRadius=dp(8).toFloat();setStroke(dp(1),Color.rgb(234,243,253))}}
                historyLine.addView(label(if(historical.isBlank())"历史取件码 · 未提供" else "历史取件码 · 仅供核对",12).apply {setTextColor(muted)},LinearLayout.LayoutParams(0,-2,1f))
                if(historical.isNotBlank())historyLine.addView(ImageView(this).apply {setImageDrawable(HubIcon("down",muted,dp(14)))},LinearLayout.LayoutParams(dp(14),dp(14)))
                val value=label(historical,18,true).apply {visibility=View.GONE;setTextColor(muted);setTextIsSelectable(true)}
                historyLine.contentDescription=if(historical.isBlank())"历史取件码未提供" else "展开历史取件码"
                if(historical.isNotBlank())historyLine.setOnClickListener {value.visibility=if(value.visibility==View.VISIBLE)View.GONE else View.VISIBLE}
                info.addView(historyLine,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7)});info.addView(value)
            }
        } else if(compact) {
            val at=collectedSnapshot()[item.mailNo] ?: ParcelPresentation.eventTime(item)
            info.addView(metadataLine("clock",if(ParcelPresentation.stage(item)==ParcelPresentation.Stage.AFTERSALE)item.stateName.ifBlank {"包裹已结束"} else if(at>0)ParcelTime.format(at) else "签收时间未提供").apply {setPadding(0,dp(5),0,0)})
            if(location.isNotBlank())info.addView(metadataLine("location",location))
        } else {
            val code=row().apply {setPadding(0,dp(10),0,0)};code.addView(label("取件码",12).apply {setTextColor(muted)})
            code.addView(label(item.pickupCode.ifBlank {"暂未获取"},if(item.pickupCode.isBlank())17 else 27,true).apply {setTextColor(if(item.pickupCode.isBlank())muted else accent);maxLines=2;contentDescription=if(item.pickupCode.isBlank())"暂未获取取件码" else "取件码 ${item.pickupCode}"},LinearLayout.LayoutParams(0,-2,1f))
            if(item.pickupCode.isNotBlank())code.addView(copyButton(item.pickupCode,"取件码"),LinearLayout.LayoutParams(dp(24),dp(24)))
            if(largeType)wideCode=code else info.addView(code)
        }
        product.addView(info,LinearLayout.LayoutParams(0,-2,1f));block.addView(product)
        if(compact && !showHistory && item.orderStatus.contains("待确认收货"))block.addView(label("商城订单 · 待确认收货",11).apply {setTextColor(muted);setPadding(0,dp(2),0,dp(4))})
        wideCode?.let {block.addView(it)}
        if(!compact && item.pickupCode.isNotBlank()) {
            val issuer=when {item.pickupCodeSource.startsWith("taobao-cainiao")->"菜鸟提供";item.pickupCodeSource=="pdd-official"->"拼多多提供";else->""}
            if(issuer.isNotBlank())info.addView(label(issuer,11).apply {setTextColor(muted);setPadding(0,dp(2),0,0)})
        }
        if(!compact && item.source=="pdd" && item.pickupCode.isBlank() && item.pickupQueryOutcome in setOf("FAILED","LOGIN","VERIFY")) {
            block.addView(label(when(item.pickupQueryOutcome){"VERIFY"->"需在菜鸟核验收件号码 · 去核验 ›";"LOGIN"->"菜鸟连接需检查 · 去检查 ›";else->"菜鸟查询未完成 · 重试 ›"},12).apply {setTextColor(Color.rgb(189,121,0));minimumHeight=dp(32);gravity=Gravity.CENTER_VERTICAL;setOnClickListener {runLookup(item)}})
        }
        if(!compact && item.mailNo==supplementNumber) {
            val hint=row();hint.addView(OfficialFlowUi.spinner(this),LinearLayout.LayoutParams(dp(14),dp(14)))
            hint.addView(label(if(item.pickupCode.isBlank())"正在查询菜鸟取件码" else "正在补查菜鸟，已有取件码可查看",11).apply {setTextColor(muted)},LinearLayout.LayoutParams(0,-2,1f));block.addView(hint,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7)})
        }
        if(compact && showHistory) {
            if(location.isNotBlank())block.addView(metadataLine("location",location))
            val localAt=collectedSnapshot()[item.mailNo]
            val at=localAt ?: ParcelPresentation.eventTime(item)
            val event=metadataLine("clock",if(item.source=="douyin")ParcelPresentation.historySubtitle(item,localAt) else (if(at>0)ParcelTime.format(at) else "签收时间未提供")+if(localAt!=null)" · 本机标记已取" else " · 平台已签收")
            if(localAt!=null)event.addView(label("撤销",11).apply {setTextColor(accent);background=GradientDrawable().apply {setColor(HubUi.paleBlue);cornerRadius=dp(6).toFloat()};setPadding(dp(6),dp(4),dp(6),dp(4));setOnClickListener {HubData.collect(this@HubActivity,item,false);renderList()};TouchTargets.expand(this)})
            block.addView(event)
        }
        if(!compact)block.addView(metadataLine("location",location.ifBlank {"取件地点暂未提供"}))
        if(!compact) {
            val nodeTime=ParcelTime.parse(item.latestTime).takeIf {it>0} ?: item.statusUpdatedAt
            block.addView(metadataLine("clock",listOf(nodeTime.takeIf {it>0}?.let {ParcelTime.format(it)}.orEmpty(),item.latestText.ifBlank {item.stateLabel()}).filter {it.isNotBlank()}.joinToString(" · ")))
        }
        waybillRow(block,item,details=compact && !showHistory)
        if(compact && showHistory){block.isClickable=true;block.setOnClickListener {openDetails(item)}}
        if(compact) {
            parcelCards[cacheKey]=SavedCard(stamp,block,photoSlots.subList(photoStart,photoSlots.size).toList())
            return
        }
        val service=PickupUiPolicy.identity(item)
        val actions=ParcelActionsLayout(this)
        if(service.isNotBlank()) {
            actions.addView(button(IdentityRoutes.route(service).title){IdentityLauncher.open(this,service,PickupUiPolicy.account(item,service))}.apply {
                textSize=15f;setTypeface(Typeface.create("sans-serif",Typeface.BOLD));maxLines=2;minWidth=0;minimumWidth=0;setPadding(dp(8),0,dp(8),0);strokeWidth=0
                icon=HubIcon("identity",accent,dp(20));iconSize=dp(20);iconPadding=dp(6);iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START
                backgroundTintList=android.content.res.ColorStateList.valueOf(if(dark)Color.rgb(35,58,87) else Color.rgb(232,243,255))
                contentDescription="出示${IdentityRoutes.route(service).title}"
            })
        } else if(item.pickupCode.isBlank()) {
            actions.addView(button(when(item.pickupQueryOutcome){"VERIFY"->"确认收件号码";"LOGIN"->"登录淘宝菜鸟";"FAILED"->"重试查询";else->"查询取件码"}){runLookup(item)}.apply {
                textSize=13f;maxLines=2;minWidth=0;minimumWidth=0;setPadding(dp(8),0,dp(8),0)
                backgroundTintList=android.content.res.ColorStateList.valueOf(if(dark)Color.rgb(35,58,87) else Color.rgb(232,243,255))
            })
        } else actions.addView(button("选择取件服务"){chooseIdentity(item)}.apply {textSize=13f;minWidth=0;minimumWidth=0})
        actions.addView(button("物流详情"){openDetails(item)}.apply {tag="secondary";textSize=13f;maxLines=1;minWidth=0;minimumWidth=0;strokeWidth=0;setPadding(dp(16),0,dp(16),0);setTextColor(muted);backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(239,248,255));icon=HubIcon("chevron",muted,dp(14));iconSize=dp(14);iconPadding=dp(4);iconGravity=MaterialButton.ICON_GRAVITY_TEXT_END;contentDescription="查看物流详情"})
        block.addView(actions,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8)})
        parcelCards[cacheKey]=SavedCard(stamp,block,photoSlots.subList(photoStart,photoSlots.size).toList())
    }
    private fun transitRow(parent:LinearLayout,item:ExpressItem) {
        val block=card(parent)
        block.tag="parcel-reading:${item.source}:${item.accountId}:${item.mailNo}"
        val source=row();source.addView(sourceBadge(item));source.addView(View(this),LinearLayout.LayoutParams(0,1,1f));source.addView(stateChip(TransitGroup.label(item),"transit"));block.addView(source)
        val product=row().apply {gravity=Gravity.TOP;setPadding(0,dp(8),0,dp(8))};product.addView(productImage(item,72,reuse=true))
        val info=column();val heading=row()
        heading.addView(label(productTitle(item),16,true).apply {maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(0,-2,1f))
        info.addView(heading)
        if(item.relatedGoods.size>1)info.addView(label("同一运单 · ${item.relatedGoods.size} 笔订单 · 查看全部商品 ›",12,true).apply {
            setTextColor(accent);setPadding(0,dp(5),0,0);setOnClickListener {openDetails(item)}
        })
        val progress=item.latestText.ifBlank {item.orderStatus}.ifBlank {TransitGroup.label(item)}
        info.addView(metadataLine("truck",progress).apply {setPadding(0,dp(8),0,0);contentDescription="运输进度：$progress";setOnClickListener {openDetails(item)}})
        product.addView(info,LinearLayout.LayoutParams(0,-2,1f));block.addView(product)
        waybillRow(block,item,details=true)
    }
    private fun openDetails(item:ExpressItem) {ParcelDetailActivity.open(this,item)}
    private fun stopCurrentWork() {
        pendingLookupReady=false;pendingLookup="";queuedLookup="";bindingSyncQueue.clear();handler.removeCallbacks(drainBindingSync)
        busy?.cancel();status.text="已停止，已有包裹保留"
    }
    private fun sync(days:Int=HistoryRangePolicy.clamp(HubData.prefs(this).getInt("sync_days",7)),historyImport:Boolean=false,automatic:Boolean=false,channels:Set<String>?=null) {
        photoJob?.cancel()
        refreshFinishedUntil=0L
        if(SyncRunCoordinator.isBusy() && !working){reflectBackgroundSync();if(!automatic)toast("后台同步正在进行，已有订单可查看");return}
        if (working || SyncEngine.isBusy() || SyncRunCoordinator.isBusy()) {
            if(channels!=null && !automatic){channels.forEach(bindingSyncQueue::requestManual);toast("已加入同步队列");handler.post(drainBindingSync);return}
            if(historyImport){bindingSyncQueue.requestRange(days);busy?.cancel();status.text="正在切换到近 $days 天，旧查询清理后自动读取。";if(tab==0 && showHistory)renderList();handler.post(drainBindingSync);return}
            if(automatic){(channels ?: HubData.channels.keys).forEach(bindingSyncQueue::request);handler.post(drainBindingSync);return}
            stopCurrentWork();return
        }
        if (!Store.hasAnyAccount(this)) { tab=2;renderBody();toast("请先绑定购物平台账号");return }
        HubData.prefs(this).edit().putInt("sync_days",days).apply()
        activeHistoryDays=days.takeIf {historyImport};activeBindingChannels=channels.orEmpty();activeManualSync=channels!=null && !automatic
        busy=lifecycleScope.launch(start=CoroutineStart.LAZY) {
            if(!SyncRunCoordinator.acquire(this@HubActivity,recordAttempt=channels==null))return@launch
            var runSucceeded=false
            var pddDiagnosticStarted=false
            var pddDiagnosticOutcome="同步未完成，已有数据保留"
            if(!historyImport)HubData.prefs(this@HubActivity).edit().putLong("foreground_sync_attempt_at",System.currentTimeMillis()).apply()
            syncButton.text=""
            showLoading("正在同步近 $days 天记录与未完成包裹",immediate=true)
            try {
                status.text="正在同步已绑定账号…"
                val pddObservedSince=System.currentTimeMillis()
                val detailSinceByAccount=Store.accounts(this@HubActivity,"pdd").associate {account->
                    account.id to if(historyImport)ParcelTime.cutoff(days) else SyncLedger.window(this@HubActivity,"pdd",account.id).since
                }
                val skip=setOf("xiaomi")+(if(channels==null)emptySet() else Store.CHANNELS.filter {it !in channels}.toSet())
                pddDiagnosticStarted="pdd" !in skip && Store.accounts(this@HubActivity,"pdd").any {it.enabled}
                val synchronize:suspend ()->SyncEngine.Report={SyncEngine.sync(this@HubActivity,skip=skip,historyImport=historyImport,automatic=automatic,supplementPickup=false,onProgress={p->runOnUiThread {updateLoading(p.label,p.current,p.total)}})}
                val report=if(historyImport)synchronize() else withTimeout(180000){synchronize()}
                val names=report.statuses.joinToString("；") { "${HubData.channels[it.channel] ?: it.channel}：${SyncStatusText.describe(it.channel,it.count,it.error)}" }
                report.statuses.forEach {entry->HubData.prefs(this@HubActivity).edit()
                    .putString("binding_sync_result_${entry.channel}",SyncStatusText.describe(entry.channel,entry.count,entry.error))
                    .putString("binding_sync_brief_${entry.channel}",if(entry.channel=="pdd" && entry.error==null)"更新物流中" else SyncStatusText.brief(entry.count,entry.error))
                    .putInt("binding_sync_days_${entry.channel}",days).putLong("binding_sync_at_${entry.channel}",System.currentTimeMillis()).apply()}
                recordSyncUiResults(report,days,pddObservedSince)
                status.text=names
                if(tab==0)renderList()
                val detailPrefs=HubData.prefs(this@HubActivity)
                val pddStatus=report.statuses.firstOrNull {it.channel=="pdd"}
                val pddCanRefresh=PddDetailQueryPolicy.mayReadDetails(pddStatus!=null,pddStatus?.count ?: 0,pddStatus?.error)
                fun fetchedAt(item:ExpressItem)=detailPrefs.getLong("detail_at_${item.source}_${item.accountId}_${item.id}",0)
                fun withinDetailScope(item:ExpressItem):Boolean {
                    val latest=Store.pddTraces(this@HubActivity,item.mailNo)?.let {LatestLogistics.point(it)}
                    val physicalActive=latest!=null && ParcelTime.parse(latest.time)>0 && ParcelEligibility.hasShippingText(latest.context) &&
                        ParcelPresentation.stage(item.copy(latestText=latest.context,latestTime=latest.time)) in setOf(ParcelPresentation.Stage.PICKUP,ParcelPresentation.Stage.TRANSIT)
                    val freshAwaiting=item.observedAt>=pddObservedSince && (item.orderStatus.contains("待收货") || item.orderStatus.contains("待确认收货"))
                    return PddPagingPolicy.refreshInScope(item,detailSinceByAccount[item.accountId] ?: ParcelTime.cutoff(days),physicalActive,freshAwaiting)
                }
                var detailCacheSkipped=0;var detailRetrySkipped=0
                val metadata=Store.items(this@HubActivity).filter {item->
                    val key="${item.accountId}_${item.id}"
                    if(!pddCanRefresh || !withinDetailScope(item))false
                    else if(!PddDetailQueryPolicy.needsRefresh(item,System.currentTimeMillis(),fetchedAt(item),detailPrefs.getInt("pdd_detail_version_$key",0))) {detailCacheSkipped++;false}
                    else if(!PddDetailQueryPolicy.mayAttempt(System.currentTimeMillis(),detailPrefs.getLong("pdd_detail_retry_$key",0),detailPrefs.getString("pdd_detail_status_$key","")==PddDetailQueryPolicy.fingerprint(item),manual=!automatic)) {detailRetrySkipped++;false}
                    else true
                }.sortedWith(compareBy<ExpressItem> {PddDetailQueryPolicy.priority(it)}.thenBy {fetchedAt(it)})
                val selected=if(historyImport)metadata else PddDetailQueryPolicy.normalSelection(metadata)
                if(pddDiagnosticStarted)PddDiagnostics.note(this@HubActivity,"物流任务选择",mapOf("candidates" to metadata.size.toString(),"selected" to selected.size.toString(),"cacheSkipped" to detailCacheSkipped.toString(),"retrySkipped" to detailRetrySkipped.toString(),"listAndPickupElapsedMs" to (System.currentTimeMillis()-pddObservedSince).toString()))
                val detailStarted=System.currentTimeMillis()
                var backfillStarted=0L
                var detailDone=0;var detailFailed=0;var detailAttempted=0
                val detailNotes=mutableListOf<String>()
                val locationRequests=mutableListOf<PddLocationSupplement.Request>()
                var pendingReadBeforeHistory=false
                suspend fun supplementPending() {
                    CainiaoPickupService.supplement(this@HubActivity,channels,onCandidate={number->runOnUiThread {supplementNumber=number;if(tab==0)renderList()}}) {current,total->status.text="$names\n菜鸟补充取件码 $current/$total";updateLoading("菜鸟补充取件码",current,total)}
                }
                for((index,item) in selected.withIndex()) {
                    if(!PddDetailQueryPolicy.active(item) && !pendingReadBeforeHistory){supplementPending();pendingReadBeforeHistory=true}
                    val now=System.currentTimeMillis()
                    if(!PddDetailQueryPolicy.active(item) && backfillStarted==0L)backfillStarted=now
                    val timeout=PddDetailQueryPolicy.timeout(item,if(PddDetailQueryPolicy.active(item))detailStarted else backfillStarted,now,historyImport)
                    if(timeout<5000)continue
                    val key="${item.accountId}_${item.id}"
                    val readStarted=System.currentTimeMillis()
                    var readOutcome="成功"
                    detailAttempted++
                    updateLoading("更新物流，已有订单可查看",index,selected.size)
                    status.text="$names\n补充物流 ${index+1}/${selected.size}；订单已显示，可停止。"
                    detailPrefs.edit().remove("pdd_detail_path_$key").putLong("pdd_detail_retry_$key",System.currentTimeMillis()+PddDetailQueryPolicy.RETRY_DELAY_MS).putString("pdd_detail_status_$key",PddDetailQueryPolicy.fingerprint(item)).apply()
                    try {withTimeout(timeout){ParcelMetadata.refresh(this@HubActivity,item){snapshot->locationRequests.add(PddLocationSupplement.Request(item,snapshot))}};detailDone++;detailPrefs.edit().putLong("detail_at_${item.source}_${item.accountId}_${item.id}",System.currentTimeMillis()).putInt("pdd_detail_version_$key",PddDetailQueryPolicy.VERSION).putLong("pdd_detail_retry_$key",0).apply()}
                    catch(e:TimeoutCancellationException){detailFailed++;readOutcome="请求超时"}
                    catch(e:CancellationException){throw e}
                    catch(e:Exception){detailFailed++;readOutcome=if(SyncStatusText.requiresLogin(e.message.orEmpty()))"会话需要恢复" else "未取得有效物流"}
                    val readMs=System.currentTimeMillis()-readStarted
                    PddDiagnostics.note(this@HubActivity,"单票物流结束",mapOf("accountRef" to PddDiagnosticPayload.ref(item.accountId),"orderRef" to PddDiagnosticPayload.ref(item.id),"parcelRef" to PddDiagnosticPayload.ref(item.mailNo),"before" to ParcelPresentation.stage(item).name,"outcome" to readOutcome,"path" to detailPrefs.getString("pdd_detail_path_$key","网页或网络未完成").orEmpty(),"readMs" to readMs.toString(),"waitMs" to (readStarted-detailStarted).toString()))
                    detailNotes+="${if(item.isOrderNumber)"订单" else "运单"}尾号${item.mailNo.takeLast(4)}：$readOutcome；${detailPrefs.getString("pdd_detail_path_$key","网页或网络未完成")}；读取 ${readMs}ms；本轮等待 ${readStarted-detailStarted}ms。"
                    updateLoading("更新物流，已有订单可查看",index+1,selected.size)
                    if(tab==0)renderList()
                }
                SyncLedger.commit(this@HubActivity)
                // New PDD details may reveal a waybill after sync; retry limits skip earlier attempts.
                supplementPending()
                val locationStarted=System.currentTimeMillis()
                if(locationRequests.isNotEmpty())detailPrefs.edit().putString("binding_sync_brief_pdd","补充地点").apply()
                val locations=PddLocationSupplement.run(this@HubActivity,locationRequests,automatic){current,total->
                    updateLoading("补充取件地点，物流状态已更新",current,total)
                    status.text="$names\n物流状态已更新，正在补充取件地点 $current/$total；已有订单可查看。"
                    if(tab==0)renderList()
                }
                val locationMs=System.currentTimeMillis()-locationStarted
                if(pddDiagnosticStarted)PddDiagnostics.note(this@HubActivity,"地点补充结束",mapOf("candidates" to locationRequests.size.toString(),"attempted" to locations.attempted.toString(),"restored" to locations.restored.toString(),"deferred" to locations.deferred.toString(),"elapsedMs" to locationMs.toString()))
                if(report.statuses.any {it.channel=="pdd"})detailPrefs.edit().putString("pdd_location_diagnostic","fix8：状态与取件地点从完整物流页同次读取，不执行二次地点补查。").apply()
                val deferred=metadata.size-detailAttempted
                if(report.statuses.any {it.channel=="pdd"})detailPrefs.edit().putString("pdd_detail_diagnostic","触发：${if(automatic)"自动同步" else if(historyImport)"历史范围读取" else "手动刷新"}；本轮开始 ${ParcelTime.format(pddObservedSince)}。\n物流详情：候选 ${metadata.size}；尝试 $detailAttempted；成功 $detailDone；未取得有效详情 $detailFailed；待下次补充 $deferred；缓存间隔跳过 $detailCacheSkipped；失败重试间隔跳过 $detailRetrySkipped。\n订单与取件服务阶段 ${detailStarted-pddObservedSince}ms；详情及查码阶段 ${System.currentTimeMillis()-detailStarted}ms。\n${detailNotes.joinToString("\n")}\n0.9.9：同次读取状态与地点；区分站点名称与取件提示句，清理旧版误存提示并复用已核验的缓存轨迹。排除多多买菜业务订单；同末端运单保留全部关联商品。每单独立页面并核对订单归属，不追加地点补查。未完成包裹逐条更新，每条最多25秒，可停止；普通刷新历史补充最多4条、预算40秒，所选历史范围读取会继续补充范围内订单。自动同步失败同状态10分钟内不重复查，手动刷新可立即重试。订单列表读取完成不代表物流详情已更新；拼多多物流与多多取快递共同更新包裹：取快递返回的明确已取记录可完成包裹，菜鸟补充缺失取件码；收件核验由你在官方页面完成。").apply()
                val detailError=SyncStatusText.detailsError(report.statuses.firstOrNull {it.channel=="pdd"}?.error,detailFailed,detailRetrySkipped)
                val finalReport=report.copy(statuses=report.statuses.map {if(it.channel=="pdd")it.copy(error=detailError) else it})
                finalReport.statuses.firstOrNull {it.channel=="pdd"}?.let {entry->
                    detailPrefs.edit().putString("binding_sync_brief_pdd",SyncStatusText.brief(entry.count,entry.error))
                        .putString("binding_sync_result_pdd",SyncStatusText.describe("pdd",entry.count,entry.error)).apply()
                }
                recordSyncUiResults(finalReport,days,pddObservedSince)
                val finalNames=finalReport.statuses.joinToString("；") {"${HubData.channels[it.channel] ?: it.channel}：${SyncStatusText.describe(it.channel,it.count,it.error)}"}
                val final="$finalNames\n${SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(Date())} 更新 · 取件码仅展示平台实际提供的信息。"+
                    (if(deferred>0)"\n还有 $deferred 条拼多多物流待补充，下次刷新继续。" else "")+
                    (if(detailFailed>0)"\n$detailFailed 条拼多多详情未取得有效结果，保留已有信息。" else "")+
                    (if(locations.deferred>0)"\n${locations.deferred} 条取件地点待补充，已取得的物流状态不受影响。" else "")+
                    (if(SyncLedger.awaitingPddDetails(this@HubActivity))"\n部分拼多多签收时间尚未核实，已保留读取结果。" else "")
                runSucceeded=channels==null && finalReport.statuses.all {it.error==null}
                pddDiagnosticOutcome=finalReport.statuses.firstOrNull {it.channel=="pdd"}?.let {if(it.error==null)"完成" else it.error}.orEmpty()
                if(finalReport.statuses.all {it.error==null}) {
                    refreshFinishedUntil=android.os.SystemClock.uptimeMillis()+3000
                    handler.postDelayed({if(!isFinishing && !isDestroyed)refreshStatusChips()},3100)
                }
                status.text=final;HubData.prefs(this@HubActivity).edit().putString("last_report",final).apply()
            } catch(e:TimeoutCancellationException) { status.text="同步超时，已保留本地包裹。请检查网络或重新登录。" }
            catch(e:CancellationException) { SyncLedger.cancel();throw e }
            catch(e:Exception) { status.text=e.message ?: "同步失败，请稍后重试" }
            finally { if(pddDiagnosticStarted)PddDiagnostics.finish(this@HubActivity,pddDiagnosticOutcome);SyncRunCoordinator.finish(this@HubActivity,runSucceeded);supplementNumber=null;WebFetchRegistry.cancel(this@HubActivity);hideLoading();syncButton.text="";activeHistoryDays=null;activeBindingChannels=emptySet();activeManualSync=false;refreshStatusChips();handler.post {if(!isFinishing && !isDestroyed){if(tab==0)renderList() else if(tab==2)renderBody();drainBindingSync.run()}} }
        }
        busy?.start()
    }
    private fun queryDialog() {
        if(search.trim().matches(Regex("[A-Za-z0-9]{8,32}"))){startQuery(search.trim());return}
        queryInputPanel(search).show()
    }
    private fun queryInputPanel(initial:String):HubSheets.Panel {
        val ui=HubSheets(this);val panel=ui.panel("查取件码")
        panel.content.addView(ui.text("快递运单号",15,true));ui.gap(panel.content,10)
        val field=TextInputLayout(this).apply {isHintEnabled=false;isErrorEnabled=true;boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE;setBoxCornerRadii(dp(10).toFloat(),dp(10).toFloat(),dp(10).toFloat(),dp(10).toFloat());endIconMode=TextInputLayout.END_ICON_CLEAR_TEXT}
        val input=TextInputEditText(field.context).apply {setSingleLine(true);hint="输入完整运单号";setText(initial);textSize=17f;includeFontPadding=false;contentDescription="完整运单号";setTextColor(ink)};field.addView(input);panel.content.addView(field)
        ui.gap(panel.content,10);panel.content.addView(ui.text("京东运单查京东；其他运单通过已绑定淘宝菜鸟查询。",13,false,muted))
        ui.fullButton(panel.footer,"查询取件码") query@ {
            val number=input.text.toString().trim()
            if(!number.matches(Regex("[A-Za-z0-9]{8,32}"))){field.error="请输入8–32位字母或数字，请勿填写订单号";panel.footer.getChildAt(0).isEnabled=false;input.requestFocus();(getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);return@query}
            search=number;panel.close();startQuery(number)
        }
        input.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun afterTextChanged(s:Editable?){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){if(field.error!=null){val valid=s.toString().trim().matches(Regex("[A-Za-z0-9]{8,32}"));panel.footer.getChildAt(0).isEnabled=valid;if(valid)field.error=null}}
        });return panel
    }
    private fun queryProgressPanel(number:String):HubSheets.Panel {
        val ui=HubSheets(this);val panel=ui.panel("查取件码")
        panel.content.addView(ui.text("快递运单号",15,true));ui.gap(panel.content,12)
        panel.content.addView(ui.text(number,17,false,muted).apply {setPadding(dp(14),dp(16),dp(14),dp(16));background=ui.background(Color.WHITE,10,Color.rgb(210,230,255));setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,HubIcon("close",Color.rgb(169,180,201),dp(18)),null)})
        ui.gap(panel.content,24)
        val loading=ui.row().apply {gravity=Gravity.CENTER;minimumHeight=dp(54);background=ui.background(accent,12)}
        loading.addView(OfficialFlowUi.spinner(this,Color.WHITE),LinearLayout.LayoutParams(dp(22),dp(22)).apply {marginEnd=dp(14)})
        loading.addView(ui.text("正在查询",18,true,Color.WHITE));panel.content.addView(loading)
        ui.gap(panel.footer,12);panel.footer.addView(ui.text(if(number.startsWith("JD",true))"正在查询京东物流，请稍候…" else "正在查询淘宝菜鸟，请稍候…",14,false,muted).apply {gravity=Gravity.CENTER;setPadding(0,dp(6),0,dp(18))})
        return panel
    }
    private fun showQueryLogin(channel:String,number:String) {
        val ui=HubSheets(this);val login=ui.panel(if(channel=="jd")"登录京东后继续" else "登录淘宝菜鸟后继续").apply {brandTitle(channel)}
        login.content.addView(ui.text("快递运单号",15,true));ui.gap(login.content,10)
        val numberRow=ui.row().apply {setPadding(dp(12),dp(12),dp(8),dp(12));background=ui.background(Color.rgb(248,251,255),10,Color.rgb(219,232,247))}
        numberRow.addView(ui.text(number,16),LinearLayout.LayoutParams(0,-2,1f));numberRow.addView(copyButton(number,"运单号"),LinearLayout.LayoutParams(dp(28),dp(28)));login.content.addView(numberRow)
        ui.gap(login.content,16);login.content.addView(ui.text("登录并验证可读取订单后，继续查询当前运单。",14,false,muted))
        ui.fullButton(login.footer,"登录并继续"){login.close();startBindingLogin(channel,cainiaoOnly=channel=="taobao")}
        ui.fullButton(login.footer,"稍后",false){pendingLookup="";login.close()};login.show()
    }
    private fun startQuery(number:String,resolveItem:ExpressItem?=null) {
        if(working || SyncEngine.isBusy() || SyncRunCoordinator.isBusy()){queuedLookup=number;didAutoSync=true;handler.post(drainBindingSync);toast("运单查询已排队");return}
        pendingLookupReady=false;activeLookup=true
        pendingLookup=number
        busy=lifecycleScope.launch {
            val progress=queryProgressPanel(number);progress.show()
            showLoading("正在查询取件码…",true);syncButton.text=""
            try {
                val order=resolveItem ?: HubData.items(this@HubActivity).firstOrNull {it.mailNo==number && it.isOrderNumber}
                val actual=if(order!=null)ParcelMetadata.refresh(this@HubActivity,order).also {check(!it.isOrderNumber){"平台尚未返回真实运单号"}}.mailNo else number
                pendingLookup=actual
                val result=PickupCoordinator.lookupParcel(this@HubActivity,actual)
                pendingLookup="";search="";status.text=if(result.updated)"包裹信息已更新" else "包裹已导入"
                progress.close();renderBody();showQueryResult(result)
            } catch(e:PickupCoordinator.LoginRequired) {
                progress.close()
                pendingLookup=e.number
                showQueryLogin(e.channel,e.number)
            } catch(e:CainiaoPickupService.VerificationRequired){progress.close();pendingLookup=e.number;openCainiao(e.number,e.accountId)}
            catch(e:TimeoutCancellationException){progress.close();showLookupFailure(number,"查询超时","请检查网络或登录状态后重试，运单号已保留。")}
            catch(e:CancellationException){throw e}
            catch(e:Exception){progress.close();showLookupFailure(number,"暂未查到",e.message ?: "请核对运单号后重试")}
            finally {progress.close();activeLookup=false;WebFetchRegistry.cancel(this@HubActivity);hideLoading();syncButton.text="";handler.post(drainBindingSync)}
        }
    }
    private fun showLookupFailure(number:String,title:String,message:String) {
        val ui=HubSheets(this);val panel=ui.panel("查询结果").apply {resultHeader()}
        val state=ui.row().apply {setPadding(0,dp(28),0,dp(18))}
        state.addView(ui.icon("warning",Color.rgb(255,135,0),56),LinearLayout.LayoutParams(dp(56),dp(56)).apply {marginEnd=dp(16)})
        state.addView(ui.column().apply {addView(ui.text(title,23,true));addView(ui.text(message,14,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(9)})},LinearLayout.LayoutParams(0,-2,1f));panel.content.addView(state)
        panel.content.addView(ui.text("已保留该运单号，可核对账号或重新查询。",14,false,muted));ui.gap(panel.content)
        ui.notice(panel.content,"运单号：$number")
        ui.buttons(panel.footer,"核对账号","重新查询",{panel.close();platformAccounts(if(number.startsWith("JD",true))"jd" else "taobao")},{panel.close();startQuery(number)})
        panel.show()
    }
    private fun showQueryResult(result:PickupCoordinator.Result) {
        val ui=HubSheets(this);val panel=ui.panel("查询结果");val item=result.item
        val stage=ParcelPresentation.stage(item)
        if(item.isOrderNumber && stage==ParcelPresentation.Stage.AFTERSALE) {
            val header=ui.row();header.addView(sourceBadge(item));header.addView(View(this),LinearLayout.LayoutParams(0,1,1f));header.addView(ui.badge("已结束记录",muted));panel.content.addView(header)
            ui.gap(panel.content,18)
            val goods=ui.row();goods.addView(sheetProductImage(item,72),LinearLayout.LayoutParams(dp(72),dp(72)).apply {marginEnd=dp(14)});goods.addView(ui.text(productTitle(item),16,true),LinearLayout.LayoutParams(0,-2,1f));panel.content.addView(goods)
            ui.gap(panel.content,18);ui.card(panel.content,Color.rgb(248,251,255)).apply {
                val orderNumber=item.queryChannel.takeIf {it.matches(Regex("[A-Za-z0-9_-]{8,80}"))} ?: item.mailNo.takeUnless {it.startsWith("ORDER-",true) || it.startsWith("LOCAL-",true)}.orEmpty()
                addView(ui.text("订单编号",13,false,muted));ui.gap(this,6);addView(ui.text(orderNumber.ifBlank {"平台未提供"},15))
                if(item.orderCreatedAt>0){ui.gap(this,14);addView(ui.text("下单时间：${ParcelTime.format(item.orderCreatedAt)}",14,false,muted))}
                ui.gap(this,14);addView(ui.text("订单状态：${item.orderStatus.ifBlank {"已结束"}}",14,true,muted))
            }
            ui.notice(panel.content,"未提供物流信息","该订单已结束，平台未提供真实运单。")
            panel.content.addView(ui.text("查询来源：${result.source}",13,false,muted))
            ui.fullButton(panel.footer,"返回首页"){panel.close();tab=0;showHistory=false;search="";renderBody()}
            panel.show();return
        }
        val position=when(stage){ParcelPresentation.Stage.PICKUP->"待取件";ParcelPresentation.Stage.CLOSED->"已取记录";ParcelPresentation.Stage.AFTERSALE->"已结束记录";else->"运输中"}
        panel.resultHeader("${if(result.updated)"已更新" else "已导入"}$position");ui.divider(panel.content);ui.gap(panel.content)
        val headerLine=ui.row();headerLine.addView(sourceBadge(item));headerLine.addView(View(this),LinearLayout.LayoutParams(0,1,1f));headerLine.addView(ui.badge(position,if(stage==ParcelPresentation.Stage.PICKUP)ui.orange else ui.green));panel.content.addView(headerLine)
        ui.gap(panel.content);val goods=ui.row();goods.addView(sheetProductImage(item,72),LinearLayout.LayoutParams(dp(72),dp(72)).apply {marginEnd=dp(14)})
        goods.addView(ui.column().apply {addView(ui.text(productTitle(item),16,true));if(!item.isOrderNumber){val waybill=ui.row().apply {setPadding(0,dp(10),0,0)};waybill.addView(ui.icon("truck",muted,17),LinearLayout.LayoutParams(dp(19),dp(19)).apply {marginEnd=dp(4)});waybill.addView(ui.text("${item.companyName} · ${item.mailNo}",11,false,muted).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(0,-2,1f));waybill.addView(copyButton(item.mailNo,"运单号"),LinearLayout.LayoutParams(dp(24),dp(24)).apply {marginStart=dp(4)});addView(waybill)}},LinearLayout.LayoutParams(0,-2,1f));panel.content.addView(goods)
        ui.gap(panel.content,22)
        if(stage==ParcelPresentation.Stage.PICKUP){
            val code=ui.row();code.addView(ui.text("取件码",15,true,muted),LinearLayout.LayoutParams(-2,-2).apply {marginEnd=dp(16)})
            code.addView(ui.text(item.pickupCode.ifBlank {"暂未获取"},if(item.pickupCode.isBlank())29 else 44,true,if(item.pickupCode.isBlank())muted else accent),LinearLayout.LayoutParams(0,-2,1f))
            if(item.pickupCode.isNotBlank())code.addView(copyButton(item.pickupCode,"取件码"),LinearLayout.LayoutParams(dp(26),dp(26)));panel.content.addView(code);ui.gap(panel.content,18)
        }
        val location=ui.row();location.addView(ui.icon("location",muted),LinearLayout.LayoutParams(dp(22),dp(22)).apply {marginEnd=dp(10)});location.addView(ui.text(station(item),15,false,muted),LinearLayout.LayoutParams(0,-2,1f));panel.content.addView(location)
        val time=if(stage==ParcelPresentation.Stage.CLOSED)ParcelPresentation.historySubtitle(item,null) else item.latestTime.takeIf {it.isNotBlank()}?.let {raw->"${ParcelTime.parse(raw).takeIf {it>0}?.let(ParcelTime::format) ?: raw} · ${item.latestText}"} ?: item.latestText
        if(time.isNotBlank()){ui.gap(panel.content);val line=ui.row();line.addView(ui.icon("clock",muted),LinearLayout.LayoutParams(dp(22),dp(22)).apply {marginEnd=dp(10)});line.addView(ui.text(time,14,false,muted),LinearLayout.LayoutParams(0,-2,1f));panel.content.addView(line)}
        if(item.historicalPickupCode.isNotBlank()){ui.gap(panel.content,24);faq(panel.content,"历史取件码 · 仅供核对",item.historicalPickupCode)}
        ui.gap(panel.content,22)
        if(stage==ParcelPresentation.Stage.PICKUP){
            val service=PickupUiPolicy.identity(item)
            val actions=ParcelActionsLayout(this)
            actions.addView(ui.button(if(service.isBlank())"选择取件服务" else IdentityRoutes.route(service).title,false,true){panel.close();if(service.isBlank())chooseIdentity(item) else IdentityLauncher.open(this,service,PickupUiPolicy.account(item,service))}.apply {textSize=15f;icon=HubIcon("identity",accent,dp(23));iconSize=dp(23)})
            actions.addView(ui.button("物流详情 ›",false,true){panel.close();openDetails(item)}.apply {textSize=14f;setTextColor(muted)});panel.content.addView(actions)
        }else if(stage!=ParcelPresentation.Stage.CLOSED)ui.fullButton(panel.content,"物流详情",false,true){panel.close();openDetails(item)}
        if(stage!=ParcelPresentation.Stage.CLOSED){ui.gap(panel.content,22);ui.divider(panel.content);ui.gap(panel.content,10)
        panel.content.addView(ui.text(if(item.source=="pdd")"物流来源：拼多多" else "查询来源：${result.source}",13,false,muted))
        if(item.source=="pdd" && item.pickupCode.isBlank()){ui.gap(panel.content,10);panel.content.addView(ui.text(PickupUiPolicy.missingLabel(item),13,false,muted))}}
        if(stage!=ParcelPresentation.Stage.PICKUP && stage!=ParcelPresentation.Stage.CLOSED)ui.fullButton(panel.footer,"返回首页"){panel.close();tab=0;showHistory=false;search="";renderBody()}
        panel.show()
    }
    private fun sheetProductImage(item:ExpressItem,size:Int):FrameLayout {
        val frame=productImage(item,size)
        val image=frame.getChildAt(1) as? ImageView
        val url=Store.jdGoods(this)[item.mailNo]?.imageUrl.orEmpty()
        // Sheet windows do not share the list viewport scanner. Render already-cached
        // product metadata directly; this neither resolves orders nor starts platform sync.
        if(image!=null && url.isNotBlank())ProductPhotos.load(image,url,dp(size)*2)
        return frame
    }
    private fun runLookup(item:ExpressItem){startQuery(item.mailNo,if(item.isOrderNumber)item else null)}
    private fun resumePendingLookup() {
        didAutoSync=true;pendingLookupReady=true;handler.post(drainBindingSync)
    }
    private fun openCainiao(number:String="",accountId:String="",manage:Boolean=false,details:Boolean=false) {
        val accounts=CainiaoPickupService.accounts(this,Store.items(this).firstOrNull {it.mailNo==number})
        if(accounts.isEmpty()){toast("请先绑定淘宝账号");platformAccounts("taobao");return}
        fun open(id:String) {
            platformLauncher.launch(Intent(this,CainiaoPickupActivity::class.java).putExtra(CainiaoPickupActivity.NUMBER,number)
                .putExtra(CainiaoPickupActivity.ACCOUNT,id).putExtra(CainiaoPickupActivity.MANAGE,manage).putExtra(CainiaoPickupActivity.DETAILS,details))
        }
        val selected=accounts.firstOrNull {it.id==accountId}
        if(selected!=null)open(selected.id)
        else if(accounts.size==1)open(accounts.first().id)
        else {
            val ui=HubSheets(this);val panel=ui.panel("选择淘宝账号")
            var selectedId=accounts.first().id
            val rows=mutableListOf<Pair<String,RadioButton>>()
            accounts.forEach {account->
                val line=ui.row().apply {setPadding(dp(8),dp(8),dp(8),dp(8));minimumHeight=dp(52);background=ui.background(if(account.id==selectedId)HubUi.paleBlue else Color.WHITE,12)}
                val radio=RadioButton(this).apply {isChecked=account.id==selectedId;isClickable=false;isFocusable=false;buttonTintList=android.content.res.ColorStateList.valueOf(accent)};rows+=account.id to radio
                line.addView(radio,LinearLayout.LayoutParams(dp(34),dp(36)));line.addView(ui.brand("taobao",34),LinearLayout.LayoutParams(dp(34),dp(34)).apply {marginEnd=dp(10)})
                line.addView(ui.text(account.label,15,true),LinearLayout.LayoutParams(0,-2,1f));panel.content.addView(line)
                line.isClickable=true;line.isFocusable=true;line.contentDescription="选择${account.label}"
                line.setOnClickListener {selectedId=account.id;rows.forEach {(id,button)->button.isChecked=id==selectedId;(button.parent as View).background=ui.background(if(id==selectedId)HubUi.paleBlue else Color.WHITE,12)}}
            }
            ui.gap(panel.content,10);panel.content.addView(ui.text("使用所选淘宝账号及其官方授权查询。\n原归属账号失效时，请恢复或明确重选。",13,false,muted).apply {gravity=Gravity.CENTER})
            ui.buttons(panel.footer,"取消","确认选择",{panel.close()},{panel.close();open(selectedId)});panel.show()
        }
    }
    private fun changeCainiaoStation():HubSheets.Panel {
        val ui=HubSheets(this);val panel=ui.panel("站点高级设置")
        val current=CainiaoPickupService.stationCode(this)
        panel.content.addView(ui.text("当前站点",15,true));ui.gap(panel.content,12)
        val stationRow=ui.row();stationRow.addView(ui.brand("cainiao",48),LinearLayout.LayoutParams(dp(48),dp(48)).apply {marginEnd=dp(14)});stationRow.addView(ui.column().apply {addView(ui.text(if(current.isBlank())"尚未设置站点" else "站点 $current",17,true));addView(ui.text("当前用于查询取件码和物流信息",12,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(6)})},LinearLayout.LayoutParams(0,-2,1f));panel.content.addView(stationRow);ui.gap(panel.content,22)
        panel.content.addView(ui.text("菜鸟官方站点链接",15,true));ui.gap(panel.content,10)
        val field=TextInputLayout(this).apply {isHintEnabled=false;boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE;startIconDrawable=HubIcon("external",muted,dp(22));isErrorEnabled=true;setErrorIconDrawable(null);endIconMode=TextInputLayout.END_ICON_CLEAR_TEXT;setBoxCornerRadii(dp(10).toFloat(),dp(10).toFloat(),dp(10).toFloat(),dp(10).toFloat())}
        val input=TextInputEditText(field.context).apply {hint="粘贴含站点编号的官方链接";setSingleLine(true);textSize=14f;includeFontPadding=false};field.addView(input);panel.content.addView(field)
        val feedback=ui.column();panel.content.addView(feedback)
        ui.gap(panel.content,10);panel.content.addView(ui.text("保存配置不代表站点授权；\n可查范围以官方返回为准。",13,false,muted).apply {setCompoundDrawablesRelativeWithIntrinsicBounds(HubIcon("info",muted,dp(18)),null,null,null);compoundDrawablePadding=dp(8)})
        ui.buttons(panel.footer,"取消","保存",{panel.close()},save@ {
            val uri=Uri.parse(input.text.toString().trim())
            val code=runCatching {uri.getQueryParameter("stationCode")}.getOrNull().orEmpty()
            if(!CainiaoWebFetcher.official(uri.toString()) || !code.matches(Regex("[0-9]{1,20}"))){field.error="请粘贴含站点编号的官方菜鸟频道链接";return@save}
            field.error=null;HubData.prefs(this).edit().putString("cainiao_station_code",code).apply();panel.close();renderBody();showStationSavedPanel(code,uri.toString())
        });
        val buttons=panel.footer.getChildAt(panel.footer.childCount-1) as ViewGroup
        val save=buttons.getChildAt(buttons.childCount-1) as MaterialButton
        fun validLink():Boolean {val uri=Uri.parse(input.text.toString().trim());return CainiaoWebFetcher.official(uri.toString()) && runCatching {uri.getQueryParameter("stationCode")}.getOrNull().orEmpty().matches(Regex("[0-9]{1,20}"))}
        save.isEnabled=false
        input.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun afterTextChanged(s:Editable?){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){val valid=validLink();save.isEnabled=valid;field.error=if(s.isNullOrBlank() || valid)null else "请粘贴含站点编号的菜鸟官方链接。"}
        });panel.show();return panel
    }
    private fun showStationSavedPanel(code:String,url:String) {
        val ui=HubSheets(this);val panel=ui.panel("设置查询站点")
        ui.card(panel.content,HubUi.paleBlue,12).apply {
            val line=ui.row();line.addView(ui.icon("location",accent,24),LinearLayout.LayoutParams(dp(26),dp(26)).apply {marginEnd=dp(10)})
            line.addView(ui.text("当前站点 $code",15,true),LinearLayout.LayoutParams(0,-2,1f))
            line.addView(ui.text("更换站点 ›",14,true,accent));line.isClickable=true;line.isFocusable=true;line.contentDescription="更换菜鸟查询站点";line.setOnClickListener {panel.close();changeCainiaoStation()};addView(line)
        }
        val link=ui.row().apply {minimumHeight=dp(48);setPadding(dp(12),dp(10),dp(12),dp(10));background=ui.background(Color.WHITE,10,Color.rgb(209,228,250));isClickable=true;isFocusable=true;contentDescription="编辑官方站点链接";setOnClickListener {panel.close();changeCainiaoStation()}}
        link.addView(ui.icon("external",muted,22),LinearLayout.LayoutParams(dp(24),dp(24)).apply {marginEnd=dp(10)});link.addView(ui.text(url,13,false,muted).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.MIDDLE},LinearLayout.LayoutParams(0,-2,1f));panel.content.addView(link)
        ui.buttons(panel.footer,"取消","保存",{panel.close()},{HubData.prefs(this).edit().putString("cainiao_station_code",code).apply()})
        ui.gap(panel.footer,14);ui.notice(panel.footer,"站点已保存，查询结果以官方返回为准。",tone="success");panel.show()
    }
    private fun task(progress:String,showResult:Boolean=true,action:suspend ()->String) {
        if(working) { toast("正在处理，请稍候");return }
        busy=lifecycleScope.launch {
            syncButton.text="";showLoading(progress)
            var openedOfficialPage=false
            try { status.text=action() } catch(e:TimeoutCancellationException) { status.text="查询超时，请检查登录状态或稍后重试" }
            catch(e:CancellationException) { SyncLedger.cancel();throw e }
            catch(e:CainiaoPickupService.VerificationRequired) {
                status.text=e.message
                openedOfficialPage=true
                openCainiao(e.number,e.accountId)
            } catch(e:Exception) { status.text=e.message ?: "查询失败" }
            finally {
                WebFetchRegistry.cancel(this@HubActivity);hideLoading();syncButton.text="";if(tab==0)renderList()
                handler.post(drainBindingSync)
                if(showResult && !openedOfficialPage && isActive && !isFinishing)MaterialAlertDialogBuilder(this@HubActivity).setTitle("查询结果").setMessage(status.text).setPositiveButton("关闭",null).show()
            }
        }
    }
    private fun importDialog(initial:String=""):HubSheets.Panel {
        val ui=HubSheets(this);val panel=ui.panel("导入取件信息")
        val field=TextInputLayout(this).apply { hint="原平台取件信息";boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE;setBoxCornerRadii(dp(10).toFloat(),dp(10).toFloat(),dp(10).toFloat(),dp(10).toFloat()) }
        val input=TextInputEditText(field.context).apply { contentDescription="原平台取件信息";minLines=3;maxLines=5;textSize=15f;includeFontPadding=false;setText(initial);setTextColor(ink) }
        field.addView(input);panel.content.addView(field)
        val feedback=ui.column();panel.content.addView(feedback)
        ui.fullButton(panel.footer,"导入") {
            val result=HubData.importDetailed(this,input.text.toString(),"手动导入")
            feedback.removeAllViews();ui.gap(feedback)
            when(result.outcome) {
                ImportPolicy.Outcome.Added,ImportPolicy.Outcome.Updated->{panel.close();tab=0;renderBody();toast(if(result.outcome==ImportPolicy.Outcome.Added)"已导入取件信息" else "已有记录已更新")}
                ImportPolicy.Outcome.AlreadyExists->{ui.notice(feedback,"此信息已导入","无需重复添加，已有记录保留。","warning");input.requestFocus()}
                ImportPolicy.Outcome.NoInfo->{ui.notice(feedback,"未识别到有效信息","请粘贴含取件码或完整运单的原平台通知。","error");input.requestFocus()}
                ImportPolicy.Outcome.ParseError->{ui.notice(feedback,"暂时无法解析","输入已保留，请检查后重试。","error");input.requestFocus()}
            }
        };ui.fullButton(panel.footer,"取消",false,true){panel.close()};panel.show();return panel
    }
    private fun showLoading(text:String,immediate:Boolean=false) {
        hideLoading()
        syncButton.contentDescription="正在刷新，点按停止"
        (syncButton.icon as? android.graphics.drawable.Animatable)?.start()
        loadingLabel.text=text;statusChips.visibility=View.GONE
        if(immediate){statusChips.visibility=View.GONE;loadingArea.visibility=View.VISIBLE;if(tab==0)renderList() else if(tab==2)renderBody()}
        else {delayedLoading=Runnable {statusChips.visibility=View.GONE;loadingArea.visibility=View.VISIBLE};handler.postDelayed(delayedLoading!!,350)}
    }
    private fun updateLoading(text:String,current:Int=0,total:Int?=null) {
        loadingLabel.text=if(total!=null && total>0)"$text · $current/$total" else text
        if(bindingSyncQueue.pendingRange()==null)historyProgressDetail?.text=loadingLabel.text
        // Counts describe completed work, never a fabricated network percentage.
    }
    private fun hideLoading() {syncButton.clearAnimation();(syncButton.icon as? android.graphics.drawable.Animatable)?.stop();syncButton.contentDescription="刷新包裹";delayedLoading?.let {handler.removeCallbacks(it)};delayedLoading=null;if(::loadingArea.isInitialized)loadingArea.visibility=View.GONE;if(::statusChips.isInitialized && tab==0)statusChips.visibility=View.VISIBLE}
    override fun onDestroy(){WebFetchRegistry.cancel(this);hideLoading();super.onDestroy()}

    private fun serviceBadge(key:String):TextView=label(when(key){"jd"->"JD";"taobao"->"菜";else->"拼"},28,true).apply {
        gravity=Gravity.CENTER;setTextColor(Color.WHITE);setPadding(0,0,0,0)
        background=GradientDrawable().apply {setColor(if(key=="taobao")accent else PlatformStyle.accent(key));cornerRadius=dp(10).toFloat()}
    }
    private fun identities() {
        val content=scroll().apply {setPadding(dp(14),dp(4),dp(14),dp(12))};content.addView(label("按取件服务选择，出示官方身份码",13).apply {setTextColor(muted);setPadding(0,dp(8),0,dp(25))})
        val panel=card(content)
        listOf("jd","taobao","pdd").forEachIndexed {index,key->
            if(index>0)divider(panel)
            val line=row().apply {setPadding(0,dp(17),0,dp(17))}
            line.addView(serviceBadge(key),LinearLayout.LayoutParams(dp(56),dp(56)))
            line.addView(column().apply {addView(label(IdentityRoutes.route(key).title,16,true));addView(label(when(key){"jd"->"京东官方页面出示";"taobao"->"通过淘宝出示";else->"通过拼多多出示"},12).apply {setTextColor(muted);setPadding(0,dp(7),0,0)})},LinearLayout.LayoutParams(0,-2,1f))
            line.addView(button("出示"){IdentityLauncher.open(this,key)}.apply {textSize=15f;setTypeface(typeface,Typeface.BOLD);maxLines=1;minWidth=dp(76);minimumWidth=dp(76);setPadding(dp(10),0,dp(10),0);strokeWidth=0;backgroundTintList=android.content.res.ColorStateList.valueOf(HubUi.paleBlue);contentDescription="出示${IdentityRoutes.route(key).title}"},LinearLayout.LayoutParams(dp(76),dp(44)))
            panel.addView(line)
        }
        val help=row().apply {setPadding(dp(12),dp(14),dp(12),dp(14));background=GradientDrawable().apply {setColor(Color.TRANSPARENT);setStroke(dp(1),Color.rgb(217,238,252));cornerRadius=dp(14).toFloat()}}
        help.addView(ImageView(this).apply {setImageDrawable(HubIcon("help",muted,dp(28)))},LinearLayout.LayoutParams(dp(28),dp(28)))
        help.addView(column().apply {addView(label("取件卡片会匹配对应身份码",14,true).apply {setTextColor(muted)});addView(label("首次使用时，按官方页面提示登录或授权",12).apply {setTextColor(muted);setPadding(0,dp(8),0,0)})},LinearLayout.LayoutParams(0,-2,1f))
        content.addView(help,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12)})
    }

    private fun settings() {
        val content=scroll().apply {setPadding(dp(12),dp(4),dp(12),dp(12))}
        val brand=row().apply {setPadding(dp(8),dp(2),dp(8),dp(12))}
        brand.addView(ImageView(this).apply {setImageResource(com.halo.expressassistant.R.drawable.dora_pocket);contentDescription="百宝袋应用图标"},LinearLayout.LayoutParams(dp(56),dp(56)).apply {marginEnd=dp(14)})
        brand.addView(column().apply {addView(label("哆啦收件箱",18,true));addView(label("平台绑定与同步管理",12).apply {setTextColor(muted);setPadding(0,dp(4),0,0)})},LinearLayout.LayoutParams(0,-2,1f));content.addView(brand)
        val channels=listOf("jd","pdd","taobao","douyin").filter {it in HubData.channels}
        val count=channels.count {Store.accounts(this,it).isNotEmpty()}
        val heading=row().apply {minimumHeight=dp(36);setPadding(dp(4),0,dp(4),0)}
        heading.addView(label("平台账号",16,true),LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(label("已绑定 $count/${channels.size}",12).apply {setTextColor(muted)});content.addView(heading)
        val accountsCard=card(content).apply {tag="my-platform-list";setPadding(0,0,0,0)}
        channels.forEachIndexed {index,channel->
            if(index>0)accountsCard.addView(View(this).apply {setBackgroundColor(Color.rgb(237,243,250))},LinearLayout.LayoutParams(-1,dp(1)).apply {leftMargin=dp(14);rightMargin=dp(14)})
            val accounts=Store.accounts(this,channel);val enabled=accounts.filter {it.enabled};val bound=accounts.isNotEmpty()
            val failed=SyncStatusText.needsAttention(HubData.prefs(this).getString("binding_sync_brief_$channel",""))
            val checking=enabled.isNotEmpty() && (failed || channel=="taobao" && enabled.none {it.taobaoOrdersVerified})
            val paused=bound && enabled.isEmpty()
            val syncing=working && enabled.any {channel!="taobao" || it.taobaoOrdersEnabled} && (activeBindingChannels.isEmpty() || channel in activeBindingChannels)
            val queued=channel in bindingSyncQueue.pending()
            val line=row().apply {tag="my-platform-$channel";minimumHeight=dp(76);setPadding(dp(12),dp(12),dp(12),dp(12))}
            line.addView(OfficialFlowUi.badge(this,channel,44),LinearLayout.LayoutParams(dp(44),dp(44)).apply {marginEnd=dp(11)})
            val copy=column().apply {isClickable=true;isFocusable=true;contentDescription="${if(bound)"管理" else "绑定"}${HubData.channels[channel]}账号";setOnClickListener {if(bound)platformAccounts(channel) else startBindingLogin(channel)}}
            val name=row();name.addView(label(HubData.channels[channel].orEmpty(),15,true))
            val state=when {paused->"已暂停";checking->"需检查";bound->"已绑定";else->"未绑定"}
            val stateColor=when {checking->Color.rgb(207,121,0);bound && !paused->Color.rgb(0,163,113);else->muted}
            name.addView(label(state,10).apply {setTextColor(stateColor);setPadding(dp(6),dp(2),dp(6),dp(2));background=GradientDrawable().apply {setColor(when {checking->Color.rgb(255,242,220);bound && !paused->Color.rgb(233,248,241);else->Color.rgb(238,242,248)});cornerRadius=dp(8).toFloat()}},LinearLayout.LayoutParams(-2,-2).apply {marginStart=dp(7)})
            if(bound)name.addView(ImageView(this).apply {setImageDrawable(HubIcon("chevron",muted,dp(11)))},LinearLayout.LayoutParams(dp(11),dp(16)).apply {marginStart=dp(6)})
            copy.addView(name)
            val desc=when {paused->"保留账号，点账号可重新启用";checking->"上次读取未完成，点账号查看";channel=="jd"->if(bound)"收件快递 · ${enabled.size} 个账号" else "本人及关联手机号的快递";channel=="pdd"->if(bound)"商城订单与物流" else "扫码绑定订单与物流";channel=="douyin"->"商城订单与运单 · 轨迹待支持";else->if(bound)"订单已验证 · 菜鸟服务${if(enabled.any {it.cainiaoVerified})"已授权" else "待授权"}" else "商城订单 · 菜鸟取件服务"}
            copy.addView(label(desc,11).apply {setTextColor(muted);setPadding(0,dp(5),0,0);maxLines=2},LinearLayout.LayoutParams(-1,-2))
            line.addView(copy,LinearLayout.LayoutParams(0,-2,1f).apply {marginEnd=dp(8)})
            val title=when {paused->"已暂停";syncing->"同步中";queued->"已排队";bound->"同步";else->"绑定"}
            val action=button(title){if(!bound)startBindingLogin(channel) else if(channel=="taobao" && enabled.none {it.taobaoOrdersEnabled})platformAccounts(channel) else sync(channels=setOf(channel))}.apply {
                textSize=13f;maxLines=1;minWidth=dp(62);minimumWidth=dp(62);minHeight=dp(44);minimumHeight=dp(44);setPadding(dp(9),0,dp(9),0);strokeWidth=0;cornerRadius=dp(11)
                setTextColor(if(paused)muted else accent);backgroundTintList=android.content.res.ColorStateList.valueOf(if(paused)Color.rgb(237,241,247) else HubUi.paleBlue);isEnabled=!paused && !syncing && !queued
                contentDescription=when {paused->"${HubData.channels[channel]}账号已暂停";bound->"仅同步${HubData.channels[channel]}";else->"绑定${HubData.channels[channel]}账号"}
            }
            line.addView(action,LinearLayout.LayoutParams(-2,-2));accountsCard.addView(line)
        }
        card(content).apply {setPadding(dp(14),0,dp(14),0);(layoutParams as LinearLayout.LayoutParams).topMargin=dp(12);bindingRow(this,"取件历史","clock","查看"){tab=0;showHistory=true;historyDays=HubData.historyRange(this@HubActivity);renderBody()};bindingRow(this,"同步结果与帮助","help"){showRecentResults()}}
        BackgroundSyncSettings.add(this,content)
        val footer=row().apply {setPadding(dp(14),0,dp(14),0)}
        footer.addView(label("更多设置",12).apply {setTextColor(muted);minimumHeight=dp(44);gravity=Gravity.CENTER_VERTICAL;setOnClickListener {val ui=HubSheets(this@HubActivity);val panel=ui.panel("更多设置");ui.actionRow(panel.content,"手动导入取件信息","","parcel"){panel.close();importDialog()};ui.actionRow(panel.content,"设备与版本信息","","info"){panel.close();showVersionPanel()};panel.show()}},LinearLayout.LayoutParams(0,-2,1f))
        footer.addView(label("版本信息 ›",12).apply {setTextColor(muted);minimumHeight=dp(44);gravity=Gravity.CENTER_VERTICAL;setOnClickListener {showVersionPanel()}});content.addView(footer)
    }
    private fun platformAccounts(channel:String) {
        if(channel !in HubData.channels)return
        settingsPage=null;bindingChannel=channel;tab=2;renderBody()
    }
    private fun startBindingLogin(channel:String,fresh:Boolean=false,accountId:String="",cainiaoOnly:Boolean=false) {
        val cls=when(channel){"jd"->JdLoginActivity::class.java;"taobao"->TbLoginActivity::class.java;"douyin"->DouyinLoginActivity::class.java;else->PddLoginActivity::class.java}
        platformLauncher.launch(Intent(this,cls).putExtra("freshLogin",fresh).putExtra("login_account_id",accountId).putExtra("cainiao_only",cainiaoOnly))
    }
    private fun bindingRow(parent:LinearLayout,title:String,icon:String,value:String="",action:()->Unit) {
        if(parent.childCount>0)divider(parent)
        val line=row().apply {
            minimumHeight=dp(52);setPadding(0,dp(4),0,dp(4));isClickable=true;isFocusable=true
            val typed=obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            foreground=typed.getDrawable(0);typed.recycle()
            contentDescription=listOf(title,value).filter {it.isNotBlank()}.joinToString("，")
            setOnClickListener {action()}
        }
        line.addView(ImageView(this).apply {setImageDrawable(HubIcon(icon,ink,dp(26)));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(26),dp(26)))
        line.addView(label(title,15,true).apply {maxLines=2},LinearLayout.LayoutParams(0,-2,1f))
        if(value.isNotBlank())line.addView(label(value,12).apply {setTextColor(muted);maxWidth=dp(112);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
        line.addView(ImageView(this).apply {setImageDrawable(HubIcon("chevron",muted,dp(18)));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(18),dp(18)))
        parent.addView(line,LinearLayout.LayoutParams(-1,-2))
    }
    private fun renderPlatformBinding(channel:String) {
        val title=HubData.channels[channel].orEmpty()
        val ui=HubSheets(this)
        val toolbar=row().apply {setPadding(dp(8),0,dp(8),0)}
        toolbar.addView(ImageButton(this).apply {setImageDrawable(HubIcon("back",ink,dp(24)));background=null;contentDescription="返回我的";setOnClickListener {bindingChannel=null;renderBody()}},LinearLayout.LayoutParams(dp(48),dp(48)))
        toolbar.addView(label("${title}绑定",20,true).apply {gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,-2,1f))
        toolbar.addView(quietAction("帮助"){showBindingHelp(channel)}.apply {setTextColor(ink);setPadding(0,0,0,0);maxLines=1},LinearLayout.LayoutParams(dp(48),dp(48)))
        body.addView(toolbar)
        val content=scroll()
        val accounts=Store.accounts(this,channel)
        val enabled=accounts.count {it.enabled}
        val bindingPrefs=HubData.prefs(this)
        val needsRecovery=SyncStatusText.requiresLogin(bindingPrefs.getString("binding_sync_result_$channel","").orEmpty())
        fun entry(parent:LinearLayout,name:String,icon:String,value:String="",strong:Boolean=false,action:()->Unit) {
            val line=ui.row().apply {minimumHeight=dp(if(strong)40 else 36);setOnClickListener {action()};isClickable=true;isFocusable=true;contentDescription=listOf(name,value).filter {it.isNotBlank()}.joinToString("，")}
            line.addView(ui.icon(icon,ink,if(strong)23 else 19),LinearLayout.LayoutParams(dp(26),dp(if(strong)24 else 20)).apply {marginEnd=dp(10)})
            line.addView(ui.text(name,if(strong)16 else 13,strong,if(strong)ink else muted),LinearLayout.LayoutParams(0,-2,1f))
            if(value.isNotBlank())line.addView(ui.text(value,13,false,muted).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;maxWidth=dp(145)})
            line.addView(ui.icon("chevron",ink,16),LinearLayout.LayoutParams(dp(16),dp(16)).apply {marginStart=dp(8)})
            parent.addView(line,LinearLayout.LayoutParams(-1,-2))
        }
        // The primary action comes before every account list, service row and explanation.
        card(content).apply {
            val heading=row()
            heading.addView(ui.brand(channel,52),LinearLayout.LayoutParams(dp(52),dp(52)).apply {marginEnd=dp(12)})
            heading.addView(column().apply {addView(ui.text(if(channel=="jd")"京东物流" else title,17,true));addView(ui.text(when(channel){"jd"->"同步本人及已关联手机号的快递";"pdd"->"同步本人账号的商城订单与物流";"douyin"->"同步实物订单与运单，完整轨迹暂不可用";else->"同步商城订单，独立授权菜鸟服务"},11,false,muted).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(5)})},LinearLayout.LayoutParams(0,-2,1f))
            heading.addView(ui.badge(if(needsRecovery)"登录需恢复" else if(enabled>0)"已绑定" else if(accounts.isNotEmpty())"已暂停" else "未绑定",if(needsRecovery)ui.orange else if(enabled>0)ui.green else if(accounts.isEmpty())ui.red else muted))
            addView(heading)
            val prefs=HubData.prefs(this@HubActivity)
            val at=prefs.getLong("binding_sync_at_$channel",0)
            if(channel!="pdd" && (!needsRecovery || at>0))addView(ui.text(if(channel in bindingSyncQueue.pending())"等待当前读取完成后同步" else if(channel in activeBindingChannels && working)"正在同步…" else if(at>0)"上次读取：${prefs.getString("binding_sync_result_$channel",prefs.getString("binding_sync_brief_$channel","已更新"))}" else "尚未同步",12,false,muted).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8);bottomMargin=dp(8)})
            if(channel=="pdd")ui.card(this,Color.rgb(255,247,235),10).apply {addView(ui.text("绑定步骤",13,true,ui.red));ui.gap(this,7);val steps=ui.row();listOf("保存二维码","App 扫码","返回检查").forEachIndexed {index,name->val part=ui.row();part.addView(ui.text("${index+1}",11,true,Color.WHITE).apply {gravity=Gravity.CENTER;background=ui.background(accent,20)},LinearLayout.LayoutParams(dp(18),dp(18)).apply {marginEnd=dp(5)});part.addView(ui.text(name,11,false,muted));steps.addView(part,LinearLayout.LayoutParams(0,-2,1f))};addView(steps)}
            if(SyncStatusText.needsAttention(prefs.getString("binding_sync_brief_$channel","")))ui.notice(this,"上次读取未完成",if(needsRecovery)"已有记录保留；可恢复登录后重试。" else "已有记录保留，可直接重试同步。","warning")
            val login=ui.button(if(channel=="pdd")"扫码登录 / 恢复" else "登录 / 恢复",channel=="pdd"){startBindingLogin(channel)}.apply {
                textSize=14f;maxLines=1;contentDescription="${title}登录或恢复会话"
                if(channel=="pdd"){icon=HubIcon("qr",Color.WHITE,dp(22));iconSize=dp(22)}
            }
            val synchronize=ui.button("同步此平台"){sync(channels=setOf(channel));renderBody()}.apply {
                textSize=14f;maxLines=1
                isEnabled=accounts.any {it.enabled && (channel!="taobao" || it.taobaoOrdersEnabled)}
                if(!isEnabled){backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(235,241,250));setTextColor(muted)}
                contentDescription="仅同步$title"
            }
            if(channel=="pdd"){
                addView(login,LinearLayout.LayoutParams(-1,-2));addView(synchronize,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
            }else{
                val actions=ParcelActionsLayout(this@HubActivity);actions.addView(login);actions.addView(synchronize);addView(actions,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(4)})
            }
        }
        if(channel=="taobao")ui.card(content).apply {
            val top=ui.row();top.addView(ui.icon("circle-check",ink,22),LinearLayout.LayoutParams(dp(26),dp(22)).apply {marginEnd=dp(10)});top.addView(ui.text("平台能力",16,true),LinearLayout.LayoutParams(0,-2,1f));if(needsRecovery)top.addView(ui.text("恢复后重新确认",11,false,muted));addView(top);ui.gap(this,6)
            fun capability(name:String,subtitle:String,key:String,value:String,verified:Boolean,action:()->Unit){
                val line=ui.row().apply {minimumHeight=dp(48);setOnClickListener {action()};isClickable=true;isFocusable=true}
                line.addView(ui.brand(key,36),LinearLayout.LayoutParams(dp(36),dp(36)).apply {marginEnd=dp(10)})
                line.addView(ui.column().apply {addView(ui.text(name,14,true));addView(ui.text(subtitle,11,false,muted).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(4)})},LinearLayout.LayoutParams(0,-2,1f))
                line.addView(ui.badge(value,if(verified && !needsRecovery)ui.green else ui.orange));line.addView(ui.icon("chevron",ink,15),LinearLayout.LayoutParams(dp(15),dp(15)).apply {marginStart=dp(4)});addView(line)
            }
            val orders=accounts.any {it.enabled && it.taobaoOrdersVerified};val pickup=accounts.any {it.enabled && it.cainiaoVerified}
            capability("商城订单","同步淘宝商城的物流信息","taobao",if(needsRecovery)"待恢复" else if(orders)"上次验证通过" else "待验证",orders){startBindingLogin("taobao")}
            capability("菜鸟取件码","通过淘宝出示取件码","cainiao",if(needsRecovery)"待检查" else if(pickup)"已授权" else "按官方核验",pickup){startBindingLogin("taobao",cainiaoOnly=true)}
        }
        card(content).apply {
            setPadding(dp(12),dp(8),dp(12),dp(8))
            entry(this,"账号管理","user",strong=true){manageBindingAccounts(channel)}
            entry(this,"已绑定账号","phone",if(accounts.isEmpty())"暂无" else "${accounts.size} 个网站账号"){manageBindingAccounts(channel)}
            if(channel=="jd") {
                val verified=accounts.sumOf {HubData.prefs(this@HubActivity).getInt("jd_verified_count_${it.id}",0)}
                entry(this,"关联手机号","phone",if(verified>0)"已验证 $verified 个收件号码" else ""){
                    openJdBindingPage("https://kd.jd.com/express/family/account?source=wlgwj")
                }
            }else if(channel=="pdd")entry(this,"登录方式","phone","App 扫码"){showBindingHelp(channel)}
            else if(channel=="douyin")entry(this,"登录方式","phone","官方扫码 / 手机号"){showBindingHelp(channel)}
            else entry(this,"号码管理 / 亲友授权","phone"){openCainiao(manage=true)}
        }
        if(channel=="pdd")card(content).apply {
            entry(this,"取件服务","truck",strong=true){startBindingLogin("taobao",cainiaoOnly=true)}
            val line=ui.row();line.addView(ui.brand("cainiao",38),LinearLayout.LayoutParams(dp(38),dp(38)).apply {marginEnd=dp(10)})
            line.addView(ui.column().apply {addView(ui.text("菜鸟取件码",14,true));addView(ui.text("需另行绑定淘宝并授权",11,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(4)})},LinearLayout.LayoutParams(0,-2,1f))
            line.addView(ui.button("前往淘宝授权"){startBindingLogin("taobao",cainiaoOnly=true)}.apply {textSize=12f;minHeight=dp(34);minimumHeight=dp(34);setPadding(dp(10),0,dp(10),0)},LinearLayout.LayoutParams(-2,dp(34)));addView(line)
        }
        if(channel=="jd")ui.card(content).apply {entry(this,"官网收件列表","parcel",strong=true){openJdBindingPage(JdLogisticsSource.LIST_URL)};addView(ui.text("在京东官网「我收」查看收件记录",13,false,muted).apply {setPadding(dp(36),0,0,dp(6))})}
        ui.card(content).apply {entry(this,"最近同步结果","clock","查看",true){showBindingResult(channel)};val at=HubData.prefs(this@HubActivity).getLong("binding_sync_at_$channel",0);addView(ui.text(if(at>0)HubData.prefs(this@HubActivity).getString("binding_sync_result_$channel","已读取").orEmpty()+" · 查看详情" else "绑定后按当前查询范围读取订单",13,false,muted).apply {setPadding(dp(10),dp(9),dp(10),dp(9));background=ui.background(HubUi.paleBlue,10);setOnClickListener {showBindingResult(channel)}})}
        if(channel=="taobao")ui.card(content,padding=10).apply {entry(this,"号码与授权","location",strong=true){openCainiao(manage=true)};val sub=ui.row();sub.addView(ui.text("管理收件号码与菜鸟授权设置",12,false,muted),LinearLayout.LayoutParams(0,-2,1f));sub.addView(ui.text("站点设置",12,false,accent).apply {setPadding(dp(8),0,0,0);minimumHeight=dp(24);gravity=Gravity.CENTER;setOnClickListener {changeCainiaoStation()}});addView(sub)}
        ui.card(content).apply {entry(this,when(channel){"pdd"->"扫码登录帮助";"taobao"->"登录与查询帮助";else->"登录与查询帮助"},"help",strong=true){showBindingHelp(channel)}}
    }
    private fun openJdBindingPage(url:String) {
        if(Store.firstEnabledAccount(this,"jd")==null){toast("请先登录京东");startBindingLogin("jd");return}
        platformLauncher.launch(Intent(this,SourceWebActivity::class.java).putExtra(SourceWebActivity.EXTRA_URL,url)
            .putExtra(SourceWebActivity.EXTRA_SOURCE,"jd").putExtra(SourceWebActivity.EXTRA_ACCOUNT_ID,Store.firstEnabledAccount(this,"jd")?.id.orEmpty()))
    }
    private fun manageBindingAccounts(channel:String) {settingsPlatform=channel;settingsPage="accounts";tab=2;renderBody()}
    private fun showRecentResults() {settingsParent=null;settingsPage="results";tab=2;renderBody()}
    private fun showBindingResult(channel:String) {settingsParent=settingsPage.takeIf {it=="results"};settingsPlatform=channel;settingsPage="result";tab=2;renderBody()}
    private fun showBindingHelp(channel:String) {settingsParent=settingsPage.takeIf {it=="results"};settingsPlatform=channel;settingsPage="help";tab=2;renderBody()}
    private fun backSettingsPage() {settingsPage=settingsParent;settingsParent=null;renderBody()}
    private fun nestedContent(title:String,brand:Boolean=false):LinearLayout {
        val toolbar=row().apply {minimumHeight=dp(56);setPadding(dp(8),0,dp(16),0)}
        if(brand){
            toolbar.addView(ImageView(this).apply {setImageResource(com.halo.expressassistant.R.drawable.dora_wordmark);scaleType=ImageView.ScaleType.FIT_START;contentDescription="哆啦收件箱"},LinearLayout.LayoutParams(dp(178),dp(54)))
            toolbar.addView(View(this),LinearLayout.LayoutParams(0,1,1f));toolbar.addView(ImageButton(this).apply {setImageDrawable(HubIcon("refresh",ink,dp(24)));background=null;contentDescription="刷新平台记录";setOnClickListener {sync()}},LinearLayout.LayoutParams(dp(48),dp(48)))
        }else{
            toolbar.addView(button(""){backSettingsPage()}.apply {minWidth=dp(48);minimumWidth=dp(48);icon=HubIcon("back",ink,dp(20));iconSize=dp(20);strokeWidth=0;backgroundTintList=android.content.res.ColorStateList.valueOf(Color.TRANSPARENT);contentDescription="返回"},LinearLayout.LayoutParams(dp(48),dp(48)))
            toolbar.addView(label(title,20,true).apply {gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,-2,1f));toolbar.addView(View(this),LinearLayout.LayoutParams(dp(48),dp(48)))
        };body.addView(toolbar)
        val content=column().apply {setPadding(dp(16),dp(4),dp(16),dp(12))}
        body.addView(ScrollView(this).apply {isFillViewport=true;addView(content)},LinearLayout.LayoutParams(-1,0,1f));return content
    }
    private fun settingsFooter(ui:HubSheets,solid:Boolean=true,build:(LinearLayout)->Unit) {
        val footer=ui.column().apply {setPadding(dp(16),dp(8),dp(16),dp(16));setBackgroundColor(if(solid)Color.WHITE else Color.TRANSPARENT)}
        build(footer);body.addView(footer,LinearLayout.LayoutParams(-1,-2))
    }
    private fun platformResultCard(parent:LinearLayout,channel:String) {
        val ui=HubSheets(this);val prefs=HubData.prefs(this)
        val failed=SyncStatusText.needsAttention(prefs.getString("binding_sync_brief_$channel",""))
        ui.card(parent).apply {
            val line=ui.row();line.addView(ui.brand(channel,46),LinearLayout.LayoutParams(dp(46),dp(46)).apply {marginEnd=dp(12)})
            line.addView(ui.column().apply {
                val heading=ui.row();heading.addView(ui.text(HubData.channels[channel].orEmpty(),17,true),LinearLayout.LayoutParams(0,-2,1f));heading.addView(ui.badge(if(failed)"读取未完成" else prefs.getString("binding_sync_brief_$channel","尚未同步").orEmpty(),if(failed)ui.orange else ui.green));addView(heading)
                addView(ui.text(prefs.getString("binding_sync_result_$channel","尚未同步").orEmpty(),13,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8)})
            },LinearLayout.LayoutParams(0,-2,1f));addView(line)
            isClickable=true;isFocusable=true;contentDescription="${HubData.channels[channel]}同步结果";setOnClickListener {showBindingResult(channel)}
            if(failed){val login=SyncStatusText.requiresLogin(prefs.getString("binding_sync_result_$channel","").orEmpty());val recovery=ui.row();recovery.addView(ui.text("已有记录保留",12,false,muted),LinearLayout.LayoutParams(0,-2,1f));recovery.addView(ui.button(if(login)"登录 / 恢复" else "重试同步"){if(login)startBindingLogin(channel) else sync(channels=setOf(channel))}.apply {textSize=13f;minimumHeight=dp(38);minHeight=dp(38)},LinearLayout.LayoutParams(-2,-2));addView(recovery,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})}
        }
    }
    private fun showHistoryExplanation() {
        val ui=HubSheets(this);val panel=ui.panel("为什么还需查询？")
        panel.content.addView(ui.text("当前先显示已保存的记录。所选范围还有日期未从平台读取，继续查询后会补充；已有包裹会保留。",14,false,muted))
        ui.gap(panel.content,16);panel.content.addView(ui.text("历史记录按平台签收或本机标记时间展示；抖音缺少签收时间时按下单日期展示并标注。平台读取范围仍受订单时间和官方可查范围约束。",12,false,muted))
        ui.fullButton(panel.footer,"继续查询"){panel.close();sync(historyDays,historyImport=true)}
        ui.fullButton(panel.footer,"知道了",false,true){panel.close()};panel.show()
    }
    private fun historyHasFailure():Boolean {
        val prefs=HubData.prefs(this)
        return HubData.channels.keys.any {channel->
            (filter=="全部平台" || HubData.channels[channel]==filter) && Store.accounts(this,channel).any {it.enabled} &&
                SyncStatusText.needsAttention(prefs.getString("binding_sync_brief_$channel","")) && prefs.getInt("binding_sync_days_$channel",0)==historyDays
        }
    }
    private fun renderHistoryTasks(parent:LinearLayout) {
        historyProgressDetail=null
        val failed=!working && bindingSyncQueue.pendingRange()==null && historyHasFailure()
        if(!working && bindingSyncQueue.pendingRange()==null && !failed)return
        val pending=bindingSyncQueue.pendingRange()
        val progress=row().apply {tag="history-query-progress";setPadding(dp(12),dp(12),dp(12),dp(12));background=OfficialFlowUi.rounded(this@HubActivity,if(failed)Color.rgb(255,245,230) else Color.rgb(224,240,255),12);minimumHeight=dp(64)}
        progress.addView(if(failed)ImageView(this).apply {setImageDrawable(HubIcon("info",Color.rgb(207,121,0),dp(20)))} else OfficialFlowUi.spinner(this),LinearLayout.LayoutParams(dp(20),dp(20)).apply {marginEnd=dp(10)})
        val copy=column();copy.addView(label(if(failed)"部分平台未读完" else if(pending!=null)"正在切换至${historyRangeLabel(pending)}" else if(activeHistoryDays!=null)"正在查询${historyRangeLabel(activeHistoryDays!!)}" else "正在同步包裹",14,true).apply {setTextColor(accent)})
        historyProgressDetail=label(if(failed)"已有记录保留，可在详情中重试" else if(pending!=null)"正在结束上一次查询，随后自动继续" else loadingLabel.text.toString().ifBlank {"正在读取订单，已有记录先显示"},12).apply {tag="history-progress-detail";setTextColor(muted);setPadding(0,dp(3),0,0);maxLines=3}
        copy.addView(historyProgressDetail);progress.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
        progress.addView(label("详情 ›",12).apply {setTextColor(accent);setPadding(dp(6),0,0,0)})
        progress.isClickable=true;progress.isFocusable=true;progress.contentDescription="查询进度，点按查看同步详情";progress.setOnClickListener {showSyncDetails()}
        parent.addView(progress,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(12)})
    }
    private fun resultText(channel:String,fullDiagnostic:Boolean=false):String {
        val prefs=HubData.prefs(this);val parts=mutableListOf<String>()
        prefs.getString("binding_sync_result_$channel",null)?.let {parts.add("本次平台读取：$it")}
        when(channel) {
            "jd"->prefs.getString("jd_sync_diagnostic",null)?.let(parts::add)
            "pdd"->{
                val keys=if(fullDiagnostic)listOf("pdd_list_diagnostic","pdd_detail_diagnostic","pdd_location_diagnostic","pdd_pickup_diagnostic","pdd_login_diagnostic") else listOf("pdd_list_diagnostic","pdd_detail_diagnostic","pdd_pickup_diagnostic","pdd_login_diagnostic")
                keys.forEach {key->prefs.getString(key,null)?.let {text->parts.add(if(!fullDiagnostic && key=="pdd_detail_diagnostic")text.substringBefore("\nfix7：").substringBefore("\nfix8：").substringBefore("\nfix9：").substringBefore("\n优先直接") else text)}}
                parts.add(if(fullDiagnostic)PddDiagnostics.report(this) else PddDiagnostics.summary(this))
            }
            "douyin"->prefs.getString("douyin_list_diagnostic",null)?.let(parts::add)
            "taobao"->{prefs.getString("tb_list_diagnostic",null)?.let(parts::add);prefs.getString("pickup_diagnostic",null)?.let {parts.add("菜鸟取件码查询：$it")}}
        }
        return parts.joinToString("\n\n").ifBlank {"尚未进行${HubData.channels[channel]}同步"}
    }
    private fun recordSyncUiResults(report:SyncEngine.Report,days:Int,startedAt:Long) {
        val prefs=HubData.prefs(this);val readAt=System.currentTimeMillis();val items=HubData.items(this)
        report.statuses.filter {it.channel in HubData.channels}.forEach {entry->
            val accounts=Store.accounts(this,entry.channel).filter {it.enabled && (entry.channel!="taobao" || it.taobaoOrdersEnabled)}
            val owners=accounts.mapTo(linkedSetOf()){it.id}
            val boundary=accounts.isNotEmpty() && accounts.all {SyncLedger.completedRange(this,entry.channel,it.id,days,startedAt)}
            val cacheEmpty=items.none {it.source==entry.channel && it.accountId in owners && (it.orderCreatedAt==0L || it.orderCreatedAt>=ParcelTime.cutoff(days))}
            val state=SyncResultUiState(entry.count,entry.error,days,readAt,boundary,cacheEmpty)
            prefs.edit().putInt("sync_ui_count_${entry.channel}",entry.count).putLong("sync_ui_at_${entry.channel}",readAt)
                .putBoolean("sync_ui_empty_${entry.channel}",state.emptyVerified).putStringSet("sync_ui_owners_${entry.channel}",owners).apply()
        }
    }
    private fun verifiedEmptyRange(channel:String):Boolean {
        val prefs=HubData.prefs(this);val owners=Store.accounts(this,channel).filter {it.enabled && (channel!="taobao" || it.taobaoOrdersEnabled)}.mapTo(linkedSetOf()){it.id}
        return owners.isNotEmpty() && prefs.getBoolean("sync_ui_empty_$channel",false) && prefs.getInt("sync_ui_count_$channel",-1)==0 &&
            prefs.getString("binding_sync_brief_$channel","")=="已更新" && prefs.getStringSet("sync_ui_owners_$channel",emptySet()).orEmpty()==owners &&
            prefs.getLong("sync_ui_at_$channel",0)>=prefs.getLong("binding_sync_at_$channel",0)
    }
    private fun showBindingVerification(data:Intent) {
        val channel=data.getStringExtra("bound_channel") ?: if(data.getBooleanExtra("cainiao_bound",false))"taobao" else return
        if(channel !in HubData.channels)return
        val accountId=data.getStringExtra("authorized_account_id").orEmpty()
        val account=accountId.takeIf {it.isNotBlank()}?.let {Store.accountById(this,channel,it)}
        if(accountId.isNotBlank() && account?.enabled!=true)return
        HubData.prefs(this).edit().putLong("binding_verification_at_$channel",System.currentTimeMillis()).putString("binding_verification_account_$channel",accountId).apply()
        settingsPlatform=channel;settingsPage=null;bindingChannel=null
        if(pendingLookup.isBlank()){tab=2;showHistory=false}
        renderBody()
    }
    private fun copyResult(text:String) {(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("同步结果",text));toast("已复制 ${text.length} 字符")}
    private fun shareDiagnostic(channel:String) {
        val text=resultText(channel,true)
        lifecycleScope.launch {
            runCatching {
                val file=withContext(Dispatchers.IO){DiagnosticShare.file(this@HubActivity,channel,text)}
                if(!isFinishing && !isDestroyed)DiagnosticShare.open(this@HubActivity,file)
            }.onFailure {toast("分享失败，可选择保存诊断")}
        }
    }
    private fun renderSettingsPage() {
        val channel=settingsPlatform;val title=HubData.channels[channel].orEmpty();val prefs=HubData.prefs(this)
        when(settingsPage) {
            "verification"->{
                val ui=HubSheets(this);val content=nestedContent("绑定${title}账号")
                val verifiedId=prefs.getString("binding_verification_account_$channel","").orEmpty()
                val account=verifiedId.takeIf {it.isNotBlank()}?.let {Store.accountById(this,channel,it)}
                val passed=prefs.getLong("binding_verification_at_$channel",0)>0 && (verifiedId.isBlank() || account?.enabled==true)
                ui.card(content,HubUi.paleBlue).apply {addView(ui.text("${title}官方登录与授权",17,true));ui.gap(this,8);addView(ui.text("登录与安全核验已在官方页面完成，以下显示该流程返回的验证结果。",14,false,muted));ui.gap(this,14);val brand=ui.row();brand.addView(ui.brand(channel,48),LinearLayout.LayoutParams(dp(48),dp(48)).apply {marginEnd=dp(12)});brand.addView(ui.column().apply {addView(ui.text(title,17,true));addView(ui.text("登录并授权",13,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(6)})});addView(brand)}
                content.addView(ui.text("验证结果",18,true),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10);bottomMargin=dp(12)})
                ui.card(content).apply {
                    val line=ui.row();line.addView(ui.icon(if(passed)"success" else "warning",if(passed)ui.green else ui.orange,44),LinearLayout.LayoutParams(dp(44),dp(44)).apply {marginEnd=dp(14)});line.addView(ui.column().apply {addView(ui.text(if(passed)"登录验证已通过" else "请重新检查登录",18,true));addView(ui.text(if(passed)"已收到官方登录验证流程的成功结果。" else "账号状态已变化，请恢复登录后再检查。",13,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7)})},LinearLayout.LayoutParams(0,-2,1f));addView(line);ui.gap(this,18)
                    val login=ui.row();login.addView(ui.text("${title}账号登录",14,false,muted),LinearLayout.LayoutParams(0,-2,1f));login.addView(ui.badge(if(passed)"已通过" else "待恢复",if(passed)ui.green else ui.orange));addView(login)
                    if(channel=="taobao"){
                        ui.gap(this,12);val order=ui.row();order.addView(ui.text("淘宝订单读取",14,false,muted),LinearLayout.LayoutParams(0,-2,1f));order.addView(ui.badge(if(account?.taobaoOrdersVerified==true)"已验证" else "待验证",if(account?.taobaoOrdersVerified==true)ui.green else ui.orange));addView(order)
                        ui.gap(this,18);addView(ui.text("菜鸟取件服务仍按独立授权结果显示。",13,false,muted).apply {setCompoundDrawablesRelativeWithIntrinsicBounds(HubIcon("info",accent,dp(18)),null,null,null);compoundDrawablePadding=dp(8)})
                    }
                    ui.fullButton(this,"返回绑定"){platformAccounts(channel)}
                }
            }
            "accounts"->{
                val ui=HubSheets(this);val content=nestedContent("${title}账号",brand=true)
                content.addView(ui.text("${title}账号",23,true),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8)})
                content.addView(ui.text("用于查询${title}快递与取件码",15,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8);bottomMargin=dp(18)})
                val accounts=Store.accounts(this,channel)
                if(accounts.isEmpty())ui.notice(content,"暂无已绑定账号","添加并验证官方账号后，按当前范围同步记录。")
                accounts.forEach {account->ui.card(content).apply {
                    val line=ui.row();line.addView(ui.brand(channel,46),LinearLayout.LayoutParams(dp(46),dp(46)).apply {marginEnd=dp(12)})
                    line.addView(ui.column().apply {addView(ui.text(account.label.ifBlank {"已绑定网站账号"},16,true));addView(ui.text("网站账号",12,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(6)})},LinearLayout.LayoutParams(0,-2,1f))
                    line.addView(ui.badge(if(account.enabled)"已启用" else "已暂停",if(account.enabled)ui.green else muted))
                    line.addView(ImageButton(this@HubActivity).apply {setImageDrawable(HubIcon("more",muted,dp(20)));background=null;setPadding(0,0,0,0);contentDescription="${account.label}账号操作";setOnClickListener {showAccountActions(channel,account.id)};post {TouchTargets.expand(this,48)}},LinearLayout.LayoutParams(dp(28),dp(28)).apply {marginStart=dp(8)});addView(line)
                    setOnClickListener {showAccountActions(channel,account.id)};isClickable=true;isFocusable=true;contentDescription="${account.label}，${if(account.enabled)"已启用" else "已暂停"}，账号操作"
                }}
                settingsFooter(ui,false) {footer->ui.fullButton(footer,"＋ 添加新账号"){startBindingLogin(channel,true)};ui.gap(footer);footer.addView(ui.text("可添加其他${title}账号，\n按所选账号及授权范围查询。",14,false,muted).apply {gravity=Gravity.CENTER})}
            }
            "results"->{
                val ui=HubSheets(this)
                val failed=HubData.channels.keys.count {SyncStatusText.needsAttention(prefs.getString("binding_sync_brief_$it",""))}
                val completed=HubData.channels.keys.count {prefs.getString("binding_sync_brief_$it","")=="已更新"}
                val content=nestedContent("同步结果",brand=failed==0)
                if(failed>0){
                    ui.notice(content,if(completed>0)"${completed}个平台读取完成，${failed}个平台未完成" else "平台读取尚未完成","最近读取部分完成，已有记录保留。","warning")
                    ui.card(content,HubUi.paleBlue).apply {ui.actionRow(this,"查询范围：${historyRangeLabel(HubData.historyRange(this@HubActivity))}（含今天）","已有记录保留，可在取件历史扩大查询范围。","clock"){tab=0;showHistory=true;renderBody()}}
                    listOf("jd","pdd","taobao","douyin").filter {it in HubData.channels}.forEach {platformResultCard(content,it)}
                    faq(content,"详细诊断信息（可展开）",HubData.channels.keys.joinToString("\n\n"){resultText(it)})
                    settingsFooter(ui){footer->ui.fullButton(footer,"完成"){backSettingsPage()}}
                }else ui.card(content).apply {
                    val heading=ui.row();heading.addView(ui.text("同步结果",18,true),LinearLayout.LayoutParams(0,-2,1f))
                    val at=HubData.channels.keys.maxOfOrNull {prefs.getLong("binding_sync_at_$it",0)} ?: 0L
                    if(at>0)heading.addView(ui.text(ParcelTime.format(at),12,false,muted));addView(heading);ui.gap(this,10)
                    listOf("jd","pdd","taobao","douyin").filter {it in HubData.channels}.forEach {key->val name=HubData.channels[key].orEmpty()
                        val brief=prefs.getString("binding_sync_brief_$key","尚未同步").orEmpty();val time=prefs.getLong("binding_sync_at_$key",0)
                        ui.divider(this)
                        ui.actionRow(this,name,listOf(prefs.getString("binding_sync_result_$key","").orEmpty(),if(time>0)ParcelTime.format(time) else "").filter {it.isNotBlank()}.joinToString("\n"),value=brief,tint=if(SyncStatusText.needsAttention(brief))ui.orange else if(brief=="已更新")ui.green else muted,channel=key){showBindingResult(key)}
                    }
                    ui.divider(this);ui.actionRow(this,"运单查询",prefs.getString("pickup_diagnostic","尚未进行查询").orEmpty().lineSequence().firstOrNull().orEmpty().take(100),"search") {
                        val panel=ui.panel("运单查码结果");ui.notice(panel.content,"最近查询",prefs.getString("pickup_diagnostic","尚未进行查询").orEmpty());ui.fullButton(panel.footer,"按单号查询"){panel.close();queryDialog()};panel.show()
                    }.apply {(getChildAt(0) as? ImageView)?.apply {setImageDrawable(HubIcon("search",Color.WHITE,dp(28)));background=ui.background(accent,10);setPadding(dp(7),dp(7),dp(7),dp(7));layoutParams=LinearLayout.LayoutParams(dp(42),dp(42)).apply {marginEnd=dp(12)}}}
                }
                if(failed==0){content.addView(ui.text("其他",17,true),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(6);bottomMargin=dp(12)})
                ui.card(content).apply {ui.actionRow(this,"使用帮助","各平台登录、查询与常见问题","help") {
                    val panel=ui.panel("使用帮助");HubData.channels.forEach {(key,name)->ui.actionRow(panel.content,"${name}登录与查询帮助",channel=key){panel.close();showBindingHelp(key)}};panel.show()
                }}}
            }
            "result"->{
                val ui=HubSheets(this);val content=nestedContent("${title}同步结果")
                val at=prefs.getLong("binding_sync_at_$channel",0);val result=resultText(channel)
                if(verifiedEmptyRange(channel)) {
                    ui.card(content,HubUi.paleBlue).apply {
                        val line=ui.row();line.addView(ui.icon("success",ui.green,44),LinearLayout.LayoutParams(dp(44),dp(44)).apply {marginEnd=dp(14)})
                        line.addView(ui.column().apply {addView(ui.text("所选范围暂无记录",18,true,ui.green));addView(ui.text("本次官方列表已成功读取并到达查询边界。",13,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8)})},LinearLayout.LayoutParams(0,-2,1f));addView(line);ui.gap(this,20)
                        addView(ui.text("查询时间：${ParcelTime.format(at)}",13,false,muted));ui.gap(this,8);addView(ui.text("查询范围：${historyRangeLabel(prefs.getInt("binding_sync_days_$channel",7))}",13,false,muted))
                    }
                    ui.card(content).apply {
                        addView(ui.text("当前账号在所选范围暂无记录",17,true));ui.gap(this,12);addView(ui.text("已有缓存和其他账号记录保留，可核对账号或扩大范围。",14,false,muted))
                        ui.fullButton(this,"核对账号"){manageBindingAccounts(channel)}
                        ui.fullButton(this,"取件历史范围",false){tab=0;showHistory=true;renderBody()}
                    }
                    ui.notice(content,"查询范围说明","在取件历史中选择更大范围后自动查询，已有记录先显示。")
                    settingsFooter(ui){footer->ui.fullButton(footer,"完成"){backSettingsPage()}};return
                }
                ui.card(content).apply {
                    val head=ui.row();head.addView(ui.brand(channel,48),LinearLayout.LayoutParams(dp(48),dp(48)).apply {marginEnd=dp(14)});head.addView(ui.text(title,19,true));addView(head);ui.gap(this,18)
                    ui.card(this,Color.rgb(248,251,255),12).apply {
                        addView(ui.text("本次读取：${prefs.getString("binding_sync_result_$channel","尚未同步")}",15,true));ui.gap(this,10)
                        addView(ui.text("查询范围：${historyRangeLabel(prefs.getInt("binding_sync_days_$channel",HubData.historyRange(this@HubActivity)))}（含今天）",14,false,muted))
                        if(at>0){ui.gap(this,10);addView(ui.text("查询时间：${ParcelTime.format(at)}",14,false,muted))}
                    }
                }
                val failed=SyncStatusText.needsAttention(prefs.getString("binding_sync_brief_$channel",""))
                ui.card(content).apply {addView(ui.text("同步状态",17,true));ui.gap(this,14);ui.notice(this,if(failed)"读取未完成" else if(at>0)"本次平台读取完成" else "尚未同步",if(failed)"已有记录保留，失败不表示所选范围没有订单。" else "物流详情将分批补充，是否取得取件码以官方返回为准。",if(failed)"warning" else if(at>0)"success" else "info")}
                faq(content,"查看详细诊断",result)
                if(channel=="pdd"){
                    ui.fullButton(content,"分享完整诊断（TXT）",false){shareDiagnostic(channel)}
                    ui.fullButton(content,"保存诊断文件",false){diagnosticExportText=resultText(channel,true);diagnosticExporter.launch("dora-inbox-pdd-diagnostic-${System.currentTimeMillis()}.txt")}
                }
                if(failed && SyncStatusText.requiresLogin(prefs.getString("binding_sync_result_$channel","").orEmpty()))ui.fullButton(content,"登录 / 恢复",false,true){startBindingLogin(channel)}
                settingsFooter(ui){footer->ui.buttons(footer,"复制结果","再次同步",{copyResult(result)},{sync(channels=setOf(channel))})}
            }
            "help"->{
                val content=nestedContent("${title}帮助")
                val ui=HubSheets(this)
                ui.card(content).apply {val line=ui.row();line.addView(ui.brand(channel,52),LinearLayout.LayoutParams(dp(52),dp(52)).apply {marginEnd=dp(14)});line.addView(ui.column().apply {addView(ui.text(when(channel){"pdd"->"拼多多扫码登录帮助";"taobao"->"淘宝登录与授权帮助";"douyin"->"抖音登录与查询帮助";else->"京东登录与查询帮助"},17,true));addView(ui.text(when(channel){"jd"->"登录后可查询本人收件的包裹";"pdd"->"通过扫码登录查询您的收件包裹";"douyin"->"登录后同步实物订单与运单";else->"可查询订单及菜鸟取件服务的包裹"},13,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7)})},LinearLayout.LayoutParams(0,-2,1f));addView(line)}
                when(channel) {
                    "jd"->{
                        helpSection(content,"登录与恢复","user","使用京东账号登录后，哆啦收件箱将查询本人及京东官网已验证的关联收件号码的包裹。若登录失效，可重新登录或恢复。","打开官网收件列表",true){openJdBindingPage(JdLogisticsSource.LIST_URL)}
                        helpSection(content,"关联手机号","phone","可查询本人及京东官网已验证的关联收件号码的收件包裹。添加和核验请在官网完成。","管理关联手机号",false){openJdBindingPage("https://kd.jd.com/express/family/account?source=wlgwj")}
                        helpSection(content,"查询范围","clock","默认近7天；在取件历史扩大范围后自动查询。最多180天，结果以官网可查记录为准。")
                        ui.notice(content,"查询范围说明","仅支持本人及京东官网已验证关联手机号的收件信息，近半年数据以官网可查记录为准。")
                    }
                    "pdd"->{
                        ui.card(content).apply {
                            listOf("保存二维码" to "点击下方按钮，保存官方登录二维码到本地相册。","打开拼多多 App 扫一扫" to "在拼多多 App 中点击「扫一扫」，选择相册并识别该二维码。","返回后检查登录" to "返回哆啦后检查订单会话，验证通过后才开始同步。").forEachIndexed {index,step->
                                val line=ui.row().apply {gravity=Gravity.TOP}
                                val marker=FrameLayout(this@HubActivity)
                                if(index<2)marker.addView(View(this@HubActivity).apply {setBackgroundColor(Color.rgb(151,196,255))},FrameLayout.LayoutParams(dp(1),-1,Gravity.CENTER_HORIZONTAL).apply {topMargin=dp(30)})
                                marker.addView(ui.text("${index+1}",15,true,Color.WHITE).apply {gravity=Gravity.CENTER;background=ui.background(accent,30)},FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP or Gravity.CENTER_HORIZONTAL))
                                val description=ui.column().apply {addView(ui.text(step.first,16,true));addView(ui.text(step.second,14,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7)});setPadding(0,0,0,dp(22))}
                                line.addView(marker,LinearLayout.LayoutParams(dp(34),if(index<2)dp(96) else dp(30)).apply {marginEnd=dp(12)});line.addView(description,LinearLayout.LayoutParams(0,-2,1f));addView(line)
                            };ui.fullButton(this,"重新扫码"){startBindingLogin("pdd")}.apply {icon=HubIcon("qr",Color.WHITE,dp(22));iconSize=dp(22)}
                        }
                        ui.card(content).apply {
                            addView(ui.text("常见问题",17,true));ui.gap(this,10)
                            faq(this,"二维码已过期怎么办？","重新获取二维码，再在原App相册中选择新图授权。不能重复使用过期图片。")
                            faq(this,"相册保存失败怎么办？","重试保存并查看实际错误；保存成功后再打开原App，不把打开App当作授权完成。")
                            faq(this,"提示安全验证怎么办？","按官方网页完成姓名、地址或安全核验，返回后点检查登录。")
                            faq(this,"提示账号错误怎么办？","核对网页登录账号和原App当前账号；需要更换时返回绑定页重新扫码。网页与原App会话可能不同。")
                        }
                    }
                    "douyin"->{
                        helpSection(content,"登录与验证","user","在抖音官方页面扫码或手机号登录；如需验证码或其他核验，请完成官方提示。返回后点检查登录，账号与商城订单均验证通过才保存绑定。","登录或恢复",true){startBindingLogin("douyin")}
                        helpSection(content,"查询范围","clock","默认近7天，最多180天，按下单日期查询（含今天）。电子券、充值等明确虚拟订单排除；履约类型未知的订单暂不导入。")
                        helpSection(content,"当前支持范围","parcel","可同步商品和真实运单号。交易完成不代表已签收；完整物流轨迹、取件码与签收时间暂未提供，可到抖音 App 查看。")
                        ui.notice(content,"扫码提示","可在官方网页用手机号登录，或用另一台设备的抖音相机扫描屏幕二维码。抖音暂不支持相册识别登录码，保存二维码和打开 App 不代表登录成功。")
                    }
                    else->{
                        val accounts=Store.accounts(this,"taobao")
                        ui.card(content).apply {
                            ui.actionRow(this,"淘宝商城订单","读取商城订单、商品及物流信息。","parcel",if(accounts.any {it.enabled && it.taobaoOrdersVerified})"已验证" else "待验证",channel="taobao"){startBindingLogin("taobao")}
                            ui.divider(this);ui.actionRow(this,"菜鸟取件服务","查询已授权包裹的取件信息；需按官方提示完成核验。","box",if(accounts.any {it.enabled && it.cainiaoVerified})"已授权" else "待核验",channel="cainiao"){openCainiao(manage=true)}
                            ui.notice(this,"两项能力分别核查","淘宝订单可读不代表目标包裹已获得菜鸟查码授权。")
                        }
                        ui.card(content).apply {
                            addView(ui.text("常见问题",17,true));ui.gap(this,10)
                            faq(this,"订单读取失败怎么办？","先登录或恢复，再按官方提示完成验证。已有包裹保留，失败不表示范围内没有订单。")
                            faq(this,"如何进行号码核验？","进入菜鸟官方查询页完成收件号码核验，返回后重新查询，不能只按返回就判授权成功。")
                            faq(this,"如何出示身份码？","在身份码页选择菜鸟，通过淘宝官方页面实时出示。未出码时可重新加载页面，也可使用官方页内刷新。")
                            ui.fullButton(this,"登录 / 恢复"){startBindingLogin("taobao")}
                        }
                    }
                }
            }
            "version"->{
                settingsPage=null;settingsParent=null;renderBody()
                showVersionPanel()
            }
        }
    }
    private fun showVersionPanel() {
        val ui=HubSheets(this);val panel=ui.panel("版本信息")
        val line=ui.row();line.addView(ImageView(this).apply {setImageResource(com.halo.expressassistant.R.drawable.dora_pocket);contentDescription="哆啦收件箱图标"},LinearLayout.LayoutParams(dp(54),dp(54)).apply {marginEnd=dp(12)})
        line.addView(ui.column().apply {addView(ui.text("哆啦收件箱",17,true));addView(ui.text("当前版本：${com.halo.expressassistant.BuildConfig.VERSION_NAME}",13,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(6)})});panel.content.addView(line)
        ui.actionRow(panel.content,"系统与设备信息","Android ${Build.VERSION.RELEASE}","info",value="查看") {
            val info=ui.panel("系统与设备信息");ui.notice(info.content,"${Build.MANUFACTURER} ${Build.MODEL}","Android ${Build.VERSION.RELEASE}\n应用版本 ${com.halo.expressassistant.BuildConfig.VERSION_NAME}");ui.fullButton(info.footer,"完成"){info.close()};info.show()
        }
        ui.actionRow(panel.content,"本机会话信息","登录信息加密保存在本机。","identity",value="加密保存",endIcon=null){}.apply {isClickable=false;isFocusable=false}
        AboutAuthorSection.add(this,panel.content)
        panel.show()
    }
    private fun faq(parent:LinearLayout,title:String,answer:String,expanded:Boolean=false) {
        val ui=HubSheets(this)
        val container=ui.column().apply {background=ui.background(Color.WHITE,12,Color.rgb(229,239,249));setPadding(dp(12),dp(2),dp(12),dp(10))}
        val answerView=ui.text(answer,14,false,muted).apply {visibility=if(expanded)View.VISIBLE else View.GONE}
        val arrow=ui.icon(if(expanded)"down" else "chevron",muted,18)
        val line=ui.row().apply {minimumHeight=dp(44);isClickable=true;isFocusable=true;contentDescription=title;setOnClickListener {val open=answerView.visibility!=View.VISIBLE;answerView.visibility=if(open)View.VISIBLE else View.GONE;arrow.setImageDrawable(HubIcon(if(open)"down" else "chevron",muted,dp(18)))}}
        line.addView(ui.text(title,14),LinearLayout.LayoutParams(0,-2,1f));line.addView(arrow,LinearLayout.LayoutParams(dp(18),dp(18)))
        container.addView(line);container.addView(answerView);parent.addView(container,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(8)})
    }
    private fun helpSection(parent:LinearLayout,title:String,icon:String,answer:String,buttonTitle:String?=null,primary:Boolean=true,action:(()->Unit)?=null) {
        val ui=HubSheets(this);ui.card(parent).apply {
            val line=ui.row();line.addView(ui.icon(icon,accent,24),LinearLayout.LayoutParams(dp(24),dp(24)).apply {marginEnd=dp(12)});line.addView(ui.text(title,17,true),LinearLayout.LayoutParams(0,-2,1f));val arrow=ui.icon("down",muted,18);line.addView(arrow,LinearLayout.LayoutParams(dp(18),dp(18)));addView(line)
            ui.gap(this,12);val answerView=ui.text(answer,14,false,muted);addView(answerView)
            val link=if(buttonTitle!=null && action!=null)ui.fullButton(this,buttonTitle,primary,!primary,action) else null
            link?.apply {setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,HubIcon("external",if(primary)Color.WHITE else accent,dp(18)),null);compoundDrawablePadding=dp(8)}
            line.minimumHeight=dp(44);line.isClickable=true;line.isFocusable=true;line.contentDescription=title
            line.setOnClickListener {val open=answerView.visibility!=View.VISIBLE;answerView.visibility=if(open)View.VISIBLE else View.GONE;link?.visibility=answerView.visibility;arrow.setImageDrawable(HubIcon(if(open)"down" else "chevron",muted,dp(18)))}
        }
    }
    private fun showAccountActions(channel:String,id:String) {
        val account=Store.accountById(this,channel,id) ?: return
        val ui=HubSheets(this);val panel=ui.panel("账号操作")
        val summary=ui.row();summary.addView(ui.brand(channel,46),LinearLayout.LayoutParams(dp(46),dp(46)).apply {marginEnd=dp(14)})
        summary.addView(ui.column().apply {addView(ui.text(account.label.ifBlank {"已绑定网站账号"},17,true));addView(ui.text("已绑定的网站账号",13,false,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(6)})},LinearLayout.LayoutParams(0,-2,1f));panel.content.addView(summary);ui.gap(panel.content)
        ui.card(panel.content,if(account.enabled)Color.rgb(255,241,241) else accent).apply {
            val action=ui.actionRow(this,if(account.enabled)"暂停此账号同步" else "启用此账号同步",if(account.enabled)"此账号暂不参与同步，可随时启用；不退出官方账号。" else "此账号将重新参与同步，已有记录保留。",if(account.enabled)"pause" else "play",tint=if(account.enabled)ui.red else Color.WHITE,endIcon=null) {
                Store.updateAccount(this@HubActivity,channel,account.copy(enabled=!account.enabled));panel.close();renderBody();toast(if(account.enabled)"已暂停同步，已有记录保留" else "已启用同步")
            }
            if(!account.enabled){val text=action.getChildAt(1) as LinearLayout;for(index in 0 until text.childCount)(text.getChildAt(index) as? TextView)?.setTextColor(Color.WHITE)}
        }
        ui.card(panel.content,ui.pale).apply {ui.actionRow(this,"重新检查登录","检查账号是否仍保持登录状态。","refresh",endIcon=null){panel.close();startBindingLogin(channel,false,id)}}
        ui.card(panel.content,Color.rgb(255,241,241)).apply {ui.actionRow(this,"移除本机账号","仅解除本机绑定，已有包裹记录保留。","trash",tint=ui.red,endIcon=null) {
            panel.close();val confirm=ui.panel("移除本机账号？")
            ui.notice(confirm.content,account.label,"解除这台设备的绑定，已有包裹记录保留，不注销平台账号。","warning")
            ui.fullButton(confirm.footer,"移除") {Store.removeAccount(this@HubActivity,channel,id);if(Store.accounts(this@HubActivity,channel).isEmpty()){SessionCookies.clearChannel(channel);Store.clearBrowserCookies(this@HubActivity,channel)};confirm.close();renderBody()}
            ui.fullButton(confirm.footer,"取消",false,true){confirm.close()};confirm.show()
        }.apply {((getChildAt(1) as LinearLayout).getChildAt(0) as TextView).setTextColor(ui.red)}}
        ui.fullButton(panel.footer,"取消",false,true){panel.close()};panel.show()
    }
    private fun toggle(title:String,value:Boolean,onChange:(Boolean)->Unit)=SwitchMaterial(this).apply {
        text=title;setTextColor(ink);textSize=16f;minHeight=dp(56);isChecked=value;setOnCheckedChangeListener{_,checked->onChange(checked)}
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun toast(value:String)=Toast.makeText(this,value,Toast.LENGTH_LONG).show()
}
