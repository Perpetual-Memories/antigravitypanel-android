package com.nzd.antigravitypanel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.nzd.antigravitypanel.data.config.ConfigRepository
import com.nzd.antigravitypanel.data.config.GameConfigRepository
import com.nzd.antigravitypanel.data.config.RemoteConfig
import com.nzd.antigravitypanel.data.backup.BackupExporter
import com.nzd.antigravitypanel.data.backup.BackupImporter
import com.nzd.antigravitypanel.data.backup.BackupImportResult
import com.nzd.antigravitypanel.data.backup.looksLikeZip
import com.nzd.antigravitypanel.data.credential.CredentialSession
import com.nzd.antigravitypanel.data.build.BuildPlan
import com.nzd.antigravitypanel.data.build.BuildPlanStore
import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
import com.nzd.antigravitypanel.data.credential.partitionLabelOf
import com.nzd.antigravitypanel.data.credential.scanMiniProgramCookie
import com.nzd.antigravitypanel.data.db.DatabaseProvider
import com.nzd.antigravitypanel.data.imports.JsonImportState
import com.nzd.antigravitypanel.data.export.MatchExportFormat
import com.nzd.antigravitypanel.data.export.MatchExporter
import com.nzd.antigravitypanel.data.imports.JsonMatchImporter
import com.nzd.antigravitypanel.data.qrlogin.QrLoginPayload
import com.nzd.antigravitypanel.data.qrlogin.parseQrLoginPayload
import com.nzd.antigravitypanel.data.yyb.GameCodeExchange
import com.nzd.antigravitypanel.data.yyb.YybLoginRepository
import com.nzd.antigravitypanel.data.yyb.createYybLoginEngine
import com.nzd.antigravitypanel.data.repo.MatchBossStats
import com.nzd.antigravitypanel.data.repo.MatchRepository
import com.nzd.antigravitypanel.data.repo.OverviewRepository
import com.nzd.antigravitypanel.data.settings.MapSeasonFilter
import com.nzd.antigravitypanel.data.settings.MatchMarks
import com.nzd.antigravitypanel.data.settings.ScoreUnitTarget
import com.nzd.antigravitypanel.data.settings.UserSettings
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.data.store.createKeyValueStore
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.ui.about.AboutScreen
import com.nzd.antigravitypanel.ui.activity.ActivityListScreen
import com.nzd.antigravitypanel.ui.briefing.DailyBriefingScreen
import com.nzd.antigravitypanel.ui.build.BuildPlanScreen
import com.nzd.antigravitypanel.ui.wiki.AutoStoriesIcon
import com.nzd.antigravitypanel.ui.wiki.WikiBackBridge
import com.nzd.antigravitypanel.ui.wiki.WikiScreen
import com.nzd.antigravitypanel.ui.component.BarBackdropContent
import com.nzd.antigravitypanel.ui.component.BarBlurHost
import com.nzd.antigravitypanel.ui.component.BlurredBar
import com.nzd.antigravitypanel.ui.component.LAYER_EXIT_DURATION
import com.nzd.antigravitypanel.ui.component.LocalBarBlurBackdrop
import com.nzd.antigravitypanel.ui.component.LocalBarBlurEnabled
import com.nzd.antigravitypanel.ui.component.LocalRootBottomBarPadding
import com.nzd.antigravitypanel.ui.component.LocalTopBarGlassProgress
import com.nzd.antigravitypanel.ui.component.MaterialGlyphs
import com.nzd.antigravitypanel.ui.component.PredictiveNavBackHandler
import com.nzd.antigravitypanel.ui.component.PredictiveNavBackdrop
import com.nzd.antigravitypanel.ui.component.PredictiveNavLayer
import com.nzd.antigravitypanel.ui.component.BackHandlerCompat
import com.nzd.antigravitypanel.ui.component.PredictiveNavLayerState
import com.nzd.antigravitypanel.ui.component.rememberPredictiveNavLayerState
import com.nzd.antigravitypanel.ui.component.requiresBackdropCapture
import com.nzd.antigravitypanel.ui.component.TOP_BAR_GLASS_GAP
import com.nzd.antigravitypanel.ui.component.TopBarGlassAnchor
import com.nzd.antigravitypanel.ui.component.TopBarGlassChip
import com.nzd.antigravitypanel.ui.component.TopBarGlassPlaceholder
import com.nzd.antigravitypanel.ui.component.firstWinChestPainter
import com.nzd.antigravitypanel.ui.component.appVersionName
import com.nzd.antigravitypanel.ui.component.liquid.IosLiquidGlassNavigationBar
import com.nzd.antigravitypanel.ui.component.rememberBinaryFilePicker
import com.nzd.antigravitypanel.ui.component.rememberTextFileSaver
import com.nzd.antigravitypanel.ui.component.rememberZipFileSaver
import com.nzd.antigravitypanel.ui.component.rememberTopBarGlassProgress
import com.nzd.antigravitypanel.ui.detail.MatchDetailScreen
import com.nzd.antigravitypanel.ui.detail.MatchDetailViewModel
import com.nzd.antigravitypanel.ui.history.HistoryScreen
import com.nzd.antigravitypanel.ui.history.HistoryViewModel
import com.nzd.antigravitypanel.ui.history.HistoryFilterMenu
import com.nzd.antigravitypanel.ui.home.HomeViewModel
import com.nzd.antigravitypanel.ui.mapdist.MapDistributionScreen
import com.nzd.antigravitypanel.ui.mapdist.MapDistributionViewModel
import com.nzd.antigravitypanel.ui.mapdist.MapSeasonFilterMenu
import com.nzd.antigravitypanel.ui.navigation.Navigator
import com.nzd.antigravitypanel.ui.navigation.Route
import com.nzd.antigravitypanel.ui.overview.AccountDetailSheet
import com.nzd.antigravitypanel.ui.overview.AccountTopBarChip
import com.nzd.antigravitypanel.ui.overview.CookieGuideDialog
import com.nzd.antigravitypanel.ui.overview.OverviewScreen
import com.nzd.antigravitypanel.ui.overview.OverviewViewModel
import com.nzd.antigravitypanel.ui.qrlogin.QrScannerScreen
import com.nzd.antigravitypanel.ui.qrlogin.QrShareScreen
import com.nzd.antigravitypanel.ui.qrlogin.YybQrLoginScreen
import com.nzd.antigravitypanel.ui.settings.SettingsScreen
import com.nzd.antigravitypanel.ui.settings.SettingsUiState
import com.nzd.antigravitypanel.ui.settings.ThemeSettingsScreen
import com.nzd.antigravitypanel.ui.signin.QqGiftViewModel
import com.nzd.antigravitypanel.ui.signin.SupercoreViewModel
import com.nzd.antigravitypanel.ui.signin.XinyueViewModel
import com.nzd.antigravitypanel.ui.signin.SignInScreen
import com.nzd.antigravitypanel.ui.signin.SignInViewModel
import com.nzd.antigravitypanel.ui.format.formatCountdownClock
import com.nzd.antigravitypanel.ui.theme.ColorMode
import com.nzd.antigravitypanel.util.currentEpochSeconds
import com.nzd.antigravitypanel.util.secondsUntilNextServerDayAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Snackbar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private object Tabs {
    const val OVERVIEW = 0
    const val HISTORY = 1
    const val MAPS = 2
    const val BRIEFING = 3
    const val SETTINGS = 4
    const val COUNT = 5

    val TITLES = listOf("概览", "历史战绩", "地图分布", "每日简报", "设置")

    /** 概览页顶栏显示应用名而不是"概览"，与 HyperIsland 的首页一致。 */
    const val APP_NAME = "反重力数据面板"
}

/**
 * 只有「停在目标页」或「二级页盖住底栏」时才不发起滚动。
 *
 * 不能用 settledPage 去重：跨页动画中它与 currentPage 可能不同，快速点回上一稳定页时
 * 会把必要的反向滚动过滤掉；动画中点击 currentPage 也必须受理，才能取消原目标并停在所点页。
 */
internal fun shouldRequestTabScroll(
    detailVisible: Boolean,
    targetIndex: Int,
    currentPage: Int,
    currentPageOffsetFraction: Float,
): Boolean = !detailVisible &&
    (targetIndex != currentPage || currentPageOffsetFraction != 0f)

/**
 * 给液态底栏**外投影**在上方留出的空间。
 *
 * 底栏那道 `dropShadow` 是画在胶囊边界之外的，而承载它的 Box 一旦因为 alpha < 1
 * （进出二级页的隐藏动画）启用离屏层，超出层边界的部分会被裁掉——
 * 顶部没有留白，就成了唯一被裁的一道，表现为"进入时先消失、返回时到位了才出现"。
 * 底部那道没事，是因为底栏下方本来就有 36+12dp 的 padding。
 *
 * 投影半径 10dp，留 16dp 足够容纳扩散。加了它 Box 会变高，
 * 所以 [LocalRootBottomBarPadding] 要把这部分减回去（见下方 Scaffold）。
 */
private val BottomBarShadowSpace = 16.dp

/**
 * 二级详情页。照 HyperIsland 的 `SettingsDetail`。
 *
 * 这里**不是**导航路由：二级页不进 NavDisplay 的 backStack，而是由
 * 「哪个页（[AppDetail]）」+「是否可见（detailShown）」两个 state 驱动一层
 * [com.nzd.antigravitypanel.ui.component.PredictiveNavLayer]。
 * 这样返回手势能带着页面跟手位移、背景同步缩放虚化；代价是父子关系得自己维护，
 * 所以打开时必须互斥（见 `openDetail` / `openMatch`）。
 */
private enum class AppDetail {
    /** 活动日历的全量列表。 */
    ACTIVITY_LIST,

    /** 签到明细。概览那张签到卡点开进来。 */
    SIGN_IN,

    /** 关于页。 */
    ABOUT,

    /** 外观 → 主题。照 HyperIsland，主题是二级页而不是一级页里的下拉。 */
    THEME,

    /** Build 计划：新赛季开始后统计"我要给哪把枪刷哪四个插件"。 */
    BUILD_PLAN,

    /**
     * wiki 浏览器：一整屏网页 + 顶部悬浮玻璃按钮条。
     *
     * 走二级页而不是外部浏览器，是为了让返回手势能把网页带着跟手位移回去；
     * 也因为它要跟「代理模式」那个开关配套 —— 用户得能在 App 内就把官方同步掐掉。
     */
    WIKI,
}

/**
 * 导出摘要的一句话。
 *
 * 用可变盒子而不是 `mutableStateOf`：SAF 的回调是在发起导出之后的某个时间点触发的，
 * 那次回调读到的是**注册时**那一帧的值，用 State 接有概率拿到空串。
 */
private class ExportSummary {
    var text: String = ""
}

/**
 * 数据包导入结果的那句话。
 *
 * 逐项报数而不是只说"导入成功"：用户要能一眼看出**凭证到底搬过来没有** ——
 * 那才是换机时最怕丢的东西。为 0 的项不显示，免得一行里挤五个 0。
 */
private fun backupResultText(result: BackupImportResult): String = buildString {
    append("导入完成：")
    append("${result.matchCount} 场对局")
    if (result.detailCount > 0) append("（其中 ${result.detailCount} 场带详情）")
    append("、${result.credentialCount} 项凭证")
    append("、${result.settingCount} 项设置")
    if (result.configCount > 0) append("、${result.configCount} 张配置表")
    append("。")
    if (result.credentialCount > 0) {
        append("已用包里的凭证重新同步，没有网络时也能先看到战绩。")
    }
}

@Composable
fun AppContent(
    padding: PaddingValues,
    colorMode: Int,
    onColorModeChange: (Int) -> Unit,
) {
    val store = remember { createKeyValueStore() }
    val session = remember { CredentialSession(store) }
    val settings = remember { UserSettings(store) }
    val marks = remember { MatchMarks(store) }
    val api = remember { NzApi() }
    val database = remember { DatabaseProvider.get() }
    val repository = remember { MatchRepository(api, database.matchDao()) }
    // 每局的 Boss 伤害口径缓存（历史页两个标签的数据源）。
    // 详情打开时顺手写、历史页后台补拉，两边共用同一份。
    val bossStats = remember { MatchBossStats(api, database.matchBossStatDao()) }
    val configRepository = remember { GameConfigRepository(api, database.configCacheDao()) }
    val importState = remember { JsonImportState(store) }
    val importer = remember { JsonMatchImporter(database.matchDao()) }
    // 「导入数据」读的是本应用导出的 zip。和 importer 并存：老的 nzm_matches.json
    // 仍然能从同一个入口进来（见 pickBackupFile 里的分支）。
    val backupImporter = remember {
        BackupImporter(
            store,
            database.matchDao(),
            database.matchBossStatDao(),
            database.configCacheDao(),
        )
    }
    // 各项持久化恢复完成的标志。首刷必须等它：否则概览可能抢在 importState.restore()
    // 之前跑完，导入过 JSON 也会先显示成「未输入」。
    var restored by remember { mutableStateOf(false) }

    val homeViewModel = remember { HomeViewModel(repository, settings) }
    // 概览缓存的同步快照。必须在 remember（合成阶段）里取而不是放进 LaunchedEffect：
    // 那是异步的，等协程跑完第一帧早就用默认状态画好了，状态卡会先闪一帧「未输入」。
    // peek 能同步是因为宿活在 setContent 之前已经 primeKeyValueStore() 预热过存储。
    val overviewSeed = remember { runCatching { store.peek(StoreKey.OVERVIEW_CACHE) }.getOrNull() }
    val overviewViewModel = remember {
        OverviewViewModel(api, database.matchDao(), store, importState.imported, overviewSeed)
    }
    val historyViewModel = remember {
        HistoryViewModel(database.matchDao(), marks, configRepository.config, bossStats)
    }
    // 地图分布的官方统计 / 掉落物同样走"合成阶段同步 peek"：
    // 冷启动那一两秒里这一页要先摆上一次确认过的数字，
    // 否则会先摆一份本地场次（和小程序对不上的那个数）再跳回来
    val mapStatsSeed = remember { runCatching { store.peek(StoreKey.MAP_STATS_CACHE) }.getOrNull() }
    val mapDropsSeed = remember { runCatching { store.peek(StoreKey.MAP_DROPS_CACHE) }.getOrNull() }
    val mapViewModel = remember {
        MapDistributionViewModel(
            api,
            database.matchDao(),
            configRepository.config,
            MapSeasonFilter(store),
            store,
            mapStatsSeed,
            mapDropsSeed,
        )
    }
    // 签到的冷启动缓存走和概览同一条路：宿主预热存储后同步 peek，
    // 否则签到卡会先闪一帧「未登录」再跳成真实数字
    val signInSeed = remember { runCatching { store.peek(SignInViewModel.CACHE_KEY) }.getOrNull() }
    val signInViewModel = remember { SignInViewModel(api, store, signInSeed) }
    // Build 计划。同样走"合成阶段同步 peek"：概览那张卡要在第一帧就画出在养的武器，
    // 放进 LaunchedEffect 的话它会先空一拍，看着像计划丢了
    val buildSeed = remember { runCatching { store.peek(StoreKey.BUILD_PLAN) }.getOrNull() }
    val buildPlanStore = remember { BuildPlanStore(store, buildSeed) }
    // 游戏中心周签到：另一套凭证（QQ 登录 cookie），和小程序的签到互不相干。
    // ⚠️ 凭证原文也要同步 peek 一份：缓存里只有状态数字、没有"绑没绑凭证"，
    // 少了它冷启动第一帧会先摆「需要 QQ 登录凭证」，等协程读完才跳成缓存里的数字。
    val qqSeed = remember { runCatching { store.peek(QqGiftViewModel.CACHE_KEY) }.getOrNull() }
    val qqCredentialSeed = remember {
        runCatching { store.peek(StoreKey.QQ_COOKIE_RAW) }.getOrNull()
    }
    val qqGiftViewModel = remember {
        QqGiftViewModel(store, cachedSeed = qqSeed, credentialSeed = qqCredentialSeed)
    }
    // 心悦悦享卡：第三套凭证（两个 T-* 请求头），和上面两块都不通用
    val xinyueSeed = remember { runCatching { store.peek(XinyueViewModel.CACHE_KEY) }.getOrNull() }
    val xinyueCredentialSeed = remember {
        runCatching { store.peek(StoreKey.XINYUE_CREDENTIAL_RAW) }.getOrNull()
    }
    val xinyueViewModel = remember {
        XinyueViewModel(store, cachedSeed = xinyueSeed, credentialSeed = xinyueCredentialSeed)
    }
    // 超核组队活动：第四套凭证（活动页的 T-OPENID / T-ACCESS-TOKEN / T-APPID）。
    // 和心悦那对 header 长得一样但不能通用 —— appid 不同，服务端会校验。
    val supercoreSeed = remember {
        runCatching { store.peek(StoreKey.SUPERCORE_CACHE) }.getOrNull()
    }
    val supercoreCredentialSeed = remember {
        runCatching { store.peek(StoreKey.SUPERCORE_CREDENTIAL_RAW) }.getOrNull()
    }
    val supercoreUrlSeed = remember {
        runCatching { store.peek(StoreKey.SUPERCORE_ACTIVITY_URL) }.getOrNull()
    }
    val supercoreViewModel = remember {
        SupercoreViewModel(
            store,
            cachedSeed = supercoreSeed,
            credentialSeed = supercoreCredentialSeed,
            urlSeed = supercoreUrlSeed,
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            homeViewModel.close()
            overviewViewModel.close()
            historyViewModel.close()
            mapViewModel.close()
            signInViewModel.close()
            qqGiftViewModel.close()
            xinyueViewModel.close()
            supercoreViewModel.close()
        }
    }

    LaunchedEffect(Unit) {
        session.restore()
        settings.restore()
        marks.restore()
        importState.restore()
        configRepository.restoreLocal()
        buildPlanStore.restore()
        // 同步快照没取到时（存储预热失败）的兜底。仍然要排在 restored 变 true 之前：
        // 那一刻下面那个观察 (restored, ready, cookie) 的 LaunchedEffect 才会启动首刷。
        overviewViewModel.restoreCached()
        signInViewModel.restoreCached()
        mapViewModel.restoreCached()
        qqGiftViewModel.restore()
        xinyueViewModel.restore()
        supercoreViewModel.restore()
        restored = true
        val config = runCatching { ConfigRepository().refresh() }.getOrDefault(RemoteConfig())
        repository.updateConfig(config)
        // 游戏配置不在这里拉：`center.config.list` 要凭证，而凭证可能是**之后**才填的。
        // 拉配置的时机挂在下面那个观察 cookie 的 LaunchedEffect 上。
    }

    val cookie by session.cookie.collectAsState()
    val ready by session.ready.collectAsState()
    val retentionMonths by settings.retentionMonths.collectAsState()
    val colorModeSetting by settings.colorMode.collectAsState()
    val autoRefreshMinutes by settings.autoRefreshMinutes.collectAsState()
    val predictiveBackTranslation by settings.predictiveBackTranslation.collectAsState()
    val autoSignIn by settings.autoSignIn.collectAsState()
    val autoClaimWelfareTask by settings.autoClaimWelfareTask.collectAsState()
    val autoClaimQqGift by settings.autoClaimQqGift.collectAsState()
    val autoClaimXinyueGift by settings.autoClaimXinyueGift.collectAsState()
    val autoSupercore by settings.autoSupercore.collectAsState()
    val topBarAccountEnabled by settings.topBarAccount.collectAsState()
    val compositeScoreEnabled by settings.compositeScore.collectAsState()
    val scoreWanTargets by settings.scoreWanTargets.collectAsState()
    // 「代理模式」：开着的时候所有官方请求全部掐断（读战绩 + 四块签到/领取）。
    // 由 UserSettings 统一算（开关状态 ∪ 本次冷启动被保护的那一轮），
    // 这里只订阅，别自己在 AppContent 里拼条件 —— 拼了就会漏掉某一路。
    val officialPaused by settings.officialNetworkPaused.collectAsState()
    // ⚠️ 开关的**显示值**另读一份：它是纯内存态，重启后必然是关。
    // 别拿 officialPaused 当开关状态显示 —— 冷启动被保护的那一轮它也 true，
    // 但那时候 UI 上必须显示"关"，否则用户会以为开关没生效。
    val proxyModeEnabled by settings.proxyMode.collectAsState()
    val gameConfig by configRepository.config.collectAsState()
    val pinned by marks.pinned.collectAsState()
    val favorite by marks.favorite.collectAsState()
    val buildPlan by buildPlanStore.plan.collectAsState()

    var liquidGlassEnabled by remember { mutableStateOf(true) }
    var guideSaving by remember { mutableStateOf(false) }
    var guideError by remember { mutableStateOf<String?>(null) }
    var accountSheet by remember { mutableStateOf(false) }
    /**
     * 全屏扫码页。它是**app 级浮层**，既不进路由也不走二级页图层：
     * 二级页图层（`AppDetail`）是挂在 MainScreen 里的，压不住同样 app 级的账号详情弹层，
     * 而扫码页必须盖在它上面全屏展开。
     */
    var scannerShown by remember { mutableStateOf(false) }
    /**
     * 「反重力登录」出码页。同样是 app 级浮层，而且必须排在扫码页**之后**渲染：
     * 它是从扫码页左下角进来的，退出时回扫码页，所以两者不能同时可见 ——
     * 打开时把 [scannerShown] 关掉，相机随之解绑（不透明页面底下留着相机只是白耗电）。
     */
    var qrShareShown by remember { mutableStateOf(false) }
    /** 应用宝渠道：本机出示微信授权二维码；与相机页互斥，避免相机空跑。 */
    var yybQrShown by remember { mutableStateOf(false) }
    /** 扫码扫到 Cookie，先摆在这儿等用户点头——扫错码直接顶掉当前账号的代价太大。 */
    var scanPending by remember { mutableStateOf<QrLoginPayload.CookieText?>(null) }
    /** 扫出来的东西用不了时的提示（不是登录码 / 是暂不支持的地址）。 */
    var scanMessage by remember { mutableStateOf<String?>(null) }
    /** 每日首胜宝箱的说明弹窗。 */
    var chestDialog by remember { mutableStateOf(false) }
    /** 没有凭证时点状态卡先弹的"怎么拿 Cookie"对话框。 */
    var cookieGuide by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    /** 导出结果。写文件的活在系统对话框里，所以只有等它回来才知道成功没有。 */
    var exportMessage by remember { mutableStateOf<String?>(null) }
    /**
     * 导出摘要：SAF 回调是在另一个时间点触发的，用可变盒子把这句话递过去，
     * 不然后回调里读 State 有可能拿到发起导出前那一帧的旧值。
     */
    val exportSummary = remember { ExportSummary() }
    /** 数据包的摘要，理由同上。 */
    val backupSummary = remember { ExportSummary() }
    val scope = rememberCoroutineScope()
    val yybLogin = remember {
        YybLoginRepository(
            engineFactory = ::createYybLoginEngine,
            exchange = GameCodeExchange(),
            configProvider = { repository.currentConfig() },
            scope = scope,
        )
    }
    val yybState by yybLogin.state.collectAsState()
    DisposableEffect(yybLogin) {
        onDispose { yybLogin.close() }
    }
    // ---- 二级详情页（HyperIsland 式图层）----
    // 「哪个页」和「是否可见」分开存：退出动画跑的那一瞬间页面还在画，
    // 立刻把 visibleDetail 清成 null 会让内容在动画结束前闪没。
    var visibleDetail by remember { mutableStateOf<AppDetail?>(null) }
    /** 战绩详情带的参数。带参数的页单独一个 state，和 HyperIsland 对 `visibleToastApp` 一样。 */
    var visibleMatch by remember { mutableStateOf<String?>(null) }
    var detailShown by remember { mutableStateOf(false) }
    val detailNavState = rememberPredictiveNavLayerState()

    /**
     * wiki 那一页递过来的「网页后退」能力。系统返回键 / 返回手势要靠它做到
     * 和左上角倒退按钮等价 —— 见 [WikiBackBridge]。
     */
    val wikiBack = remember { WikiBackBridge() }
    /**
     * 上一次「想在网页首页退出 wiki」的时刻（epoch 秒）。0 = 没有待确认的那一次。
     *
     * ⚠️ 走 [currentEpochSeconds] 而不是系统时钟：单测要能固定时间。
     */
    var wikiExitPromptSec by remember { mutableLongStateOf(0L) }
    /** 二级页那层之上唯一的 Snackbar 宿主（wiki 的「再按一次退出」用）。 */
    val detailSnackbarHost = remember { SnackbarHostState() }

    fun openDetail(detail: AppDetail) {
        // 两个 state 互斥：不清掉另一个，"上一个页面"会一直优先渲染出来
        visibleMatch = null
        visibleDetail = detail
        detailShown = true
        // 「再按一次退出」那一次确认不跨开关页：上一轮留着的时间戳会让刚打开的
        // wiki 第一次按返回就直接退出（提示压根没出现过）
        wikiExitPromptSec = 0L
    }

    fun openMatch(roomId: String) {
        visibleDetail = null
        visibleMatch = roomId
        detailShown = true
    }

    fun closeDetail() {
        // 只关可见性，内容留着——等退出动画跑完再换，不然会闪
        detailShown = false
    }

    /**
     * wiki 页上「返回」的统一入口（顶栏倒退按钮、系统返回键、返回手势都走这里）。
     *
     * 三条路必须**完全等价**，所以顺序照搬 [WikiScreen] 里那颗倒退按钮：
     * 1. 网页里还有历史 → **网页自己退一页**，这一页不关；
     * 2. 已经在网页首页 → 直接回概览页太突然（手势很容易误触），先弹一句提示，
     *    [WIKI_EXIT_PROMPT_WINDOW_SEC] 秒内再来一次才真退，同时把提示收掉。
     *
     * @return true = 这次没有退出这一页（调用方据此把这次返回吞掉）
     */
    fun confirmWikiExit(): Boolean {
        if (wikiBack.canGoBack) {
            // 顺手把上一轮"再按一次"的确认作废：那句话是针对"当时在首页"说的，
            // 中间网页又翻过页的话，留着它就会让回到首页的那一次直接退出、不再提示
            wikiExitPromptSec = 0L
            wikiBack.goBack()
            return true
        }
        val now = currentEpochSeconds()
        if (wikiExitPromptSec != 0L && now - wikiExitPromptSec < WIKI_EXIT_PROMPT_WINDOW_SEC) {
            wikiExitPromptSec = 0L
            // 真退之前先把提示撤掉，别让它跟着飘到概览页上
            scope.launch { detailSnackbarHost.newestSnackbarData()?.dismiss() }
            closeDetail()
            return false
        }
        wikiExitPromptSec = now
        scope.launch { detailSnackbarHost.showSnackbar(WIKI_EXIT_PROMPT) }
        return true
    }

    /**
     * 拉一轮全部数据。**冷启动那条 LaunchedEffect 和手动刷新都必须走这里**——
     * 之前两边各写一份，手动刷新那份少了 `updateCookie` 和游戏配置表，
     * 于是"点了刷新看着转了圈，地图分布还是旧的、凭证还是上一次的结论"。
     *
     * **签到与领任务奖励不在这里**——它们归下面那个专门的 effect，因为要不要做
     * 取决于两个开关，而开关一变就得立刻试一次，也不是"等下次冷启动"。
     * 手动刷新由 [signInAttempt] 把那个 effect 重新触发一遍（见它的注释）。
     */
    fun refreshAll(active: MiniProgramCredential?) {
        // 代理模式：一个官方请求都不发。直接 return，不去动 cookieStatus ——
        // 动了会把状态卡从「已识别」打回「校验中」，反而像数据丢了。
        // 概览 / 地图分布这时候显示的是落盘缓存里那一份。
        if (officialPaused) return
        if (active == null) {
            // 没凭证时也要刷一次概览：导入过 JSON 的话，状态卡要显示「已导入json」
            overviewViewModel.refresh(null)
            // 地图分布的官方口径同样用不了，别留着上一个号的统计
            mapViewModel.refresh(null)
            return
        }
        // 先把凭证挂到 api 上。地图分布页的官方统计（`center.user.map.stats`）
        // 是直接用这个 api 发的，不能依赖"概览先刷过一次"这种隐式先后关系。
        api.updateCookie(active)
        // 游戏配置（地图表 / 难度表）只在**有凭证**时才拉得到，而凭证可能是刚填的。
        // 带空判断是为了不在已经有配置时重复打接口（冷启动缓存命中就不必再拉）。
        if (configRepository.config.value.difficultyInfo.isEmpty()) {
            scope.launch { runCatching { configRepository.refresh(active) } }
        }
        homeViewModel.refresh(active)
        overviewViewModel.refresh(active)
        // 地图分布的两个请求（官方统计 / 掉落物）**只有这里发起**：
        // 它们都要凭证，而凭证是上面那行 `api.updateCookie` 才挂上的。
        // 放在这一页自己构造时抢跑，那一发必然是「还没有设置 cookie」，
        // 冷启动就会先摆一份本地场次（和小程序对不上）再跳回来。
        mapViewModel.refresh(active)
    }

    LaunchedEffect(colorModeSetting) {
        if (colorModeSetting != colorMode) onColorModeChange(colorModeSetting)
    }

    /**
     * 手动刷新（顶栏那个按钮）的次数。
     *
     * 它进下面那个 effect 的 key，是因为**cookie 没变时那个 effect 不会重跑**，
     * 而"点了刷新"正是要它再跑一遍：任务中心「每日完成1局」的奖励以前只在冷启动
     * 试过一次，白天打完那一局再点刷新也不会去领，非得杀掉 app 重开才行。
     */
    var signInAttempt by remember { mutableStateOf(0) }

    // 小程序活动中心的签到 + 任务中心自动领取。
    //
    // 独立于 cookie 那个 effect 的理由和下面两块一样：**两个开关要进 key**——
    // 用户刚把开关打开时应该立刻试一次，不用等下次冷启动。
    // 同一天重复跑不会有副作用：签到按「日期|openid」去重，任务按同一段前缀下的
    // taskId 集合去重（每日和每周不是同时达成的，所以必须记到任务粒度）；
    // 已经领过的任务服务端那边 `isawarded` 已经是 true，压根不会进领取请求。
    //
    // 冷启动 / 新登录 / 点刷新，三种时机都走这里，所以 refreshAll 里不再碰签到。
    LaunchedEffect(
        restored,
        ready,
        cookie,
        autoSignIn,
        autoClaimWelfareTask,
        officialPaused,
        signInAttempt,
    ) {
        if (!restored) return@LaunchedEffect
        // 代理模式下连「只读地查一下签到状态」都不发 —— 挂代理期间任何
        // 带凭证的官方请求都该由用户显式触发。
        if (officialPaused) return@LaunchedEffect
        signInViewModel.refresh(
            cookie = cookie,
            autoSign = autoSignIn,
            autoClaimTask = autoClaimWelfareTask,
        )
    }

    // 周签到礼包的自动领取。独立于 cookie 那个 effect：它用的是 QQ 凭证，
    // 和小程序那套没关系，不该被"小程序 cookie 变了"重新触发一遍。
    // 开关本身也进 key —— 用户刚打开时应该立刻试一次，不用等下次冷启动。
    // 同一天重复跑不会有副作用：仓库内部按「日期|uin」去重。
    LaunchedEffect(restored, autoClaimQqGift, officialPaused) {
        if (!restored) return@LaunchedEffect
        if (officialPaused) return@LaunchedEffect
        qqGiftViewModel.refresh(autoClaim = autoClaimQqGift)
    }

    // 悦享卡的自动领取。同理独立于 cookie 那个 effect：它看的是心悦凭证。
    // 开关进 key 是为了用户刚打开时立刻试一次，不用等下次冷启动。
    // 同一天重复跑不会有副作用：仓库内部按「日期|openid」去重。
    LaunchedEffect(restored, autoClaimXinyueGift, officialPaused) {
        if (!restored) return@LaunchedEffect
        if (officialPaused) return@LaunchedEffect
        xinyueViewModel.refresh(autoClaim = autoClaimXinyueGift)
    }

    // 超核组队活动。同理独立：第四套凭证，和 cookie 无关。
    // [signInAttempt] 也进 key —— 顶栏那颗刷新按钮要能重新领一次积分（白天打完一局才到账）。
    LaunchedEffect(restored, autoSupercore, officialPaused, signInAttempt) {
        if (!restored) return@LaunchedEffect
        if (officialPaused) return@LaunchedEffect
        supercoreViewModel.refresh(autoRun = autoSupercore)
    }

    LaunchedEffect(restored, ready, cookie) {
        if (!restored || !ready) return@LaunchedEffect
        // 接一个局部变量：cookie 是委托属性，编译器不做智能转换
        val active = cookie
        if (active != null) {
            refreshAll(active)
        } else {
            refreshAll(null)
            // 游戏中心那块和小程序的凭证无关，它只看自己有没有存过 QQ cookie。
            // 但自动领取要不要开，得等设置读完才知道，所以放在下面那个 effect。
            qqGiftViewModel.refresh(autoClaim = false)
        }
    }

    // 「导入数据」：优先当数据包（zip）处理，不是 zip 再按早期的对局 JSON 处理。
    // 两条路分开是因为写入语义不同：
    // - zip 是整机迁移，除对局外还要恢复凭证 / 设置 / 缓存；
    // - 早期 JSON 只有对局，恢复完重刷一次概览，让凭证卡从「未输入」变「已导入json」。
    val pickBackupFile = rememberBinaryFilePicker { bytes ->
        if (bytes == null) return@rememberBinaryFilePicker
        scope.launch {
            if (looksLikeZip(bytes)) {
                // 导入前先记住这台机器原本有没有凭证：下面的"补读冷启动缓存"只在
                // **空机迁移**时才做 —— 已经有数据的机器上再拿老缓存去覆盖，
                // 会把本次刚同步到的数字换成上一台设备的旧数字（要等下次刷新才纠正）。
                val hadCookie = cookie != null
                importMessage = runCatching { backupImporter.import(bytes) }.fold(
                    onSuccess = { result ->
                        // 存储刚被整包换过，内存里这几份 StateFlow 还是旧值，必须逐个重读。
                        // ⚠️ 顺序有讲究：先 cookie —— 它一变，下面那个观察 cookie 的 effect
                        // 会自动发起一次同步（在线时等于"导入后立刻校验"）；再是设置 / 标记 / 缓存。
                        session.reload()
                        // ⚠️ Settings.restore() 会"消费"代理模式那个一次性标记
                        // （读出来就删）。导入不该顺手把用户刚开的代理保护取消掉，
                        // 所以原有的开关状态要在恢复之后再写回去一次。
                        val proxyWasOn = settings.proxyMode.value
                        settings.restore()
                        if (proxyWasOn) settings.setProxyMode(true)
                        marks.restore()
                        importState.restore()
                        buildPlanStore.restore()
                        // 这两块有"缓存齐了就直接返回"的守卫，restore() 不会读到新凭证，
                        // 所以单独给了只重读凭证的入口
                        qqGiftViewModel.reloadCredential()
                        xinyueViewModel.reloadCredential()
                        // 超核的 restore 每次都整份重读，不用额外开门
                        supercoreViewModel.restore()
                        // 冷启动缓存：新机上 constructor 那一路本来是空的，
                        // 这里补读一次，离线也能先把数字摆出来（不是覆盖已有数据）
                        if (!hadCookie) {
                            overviewViewModel.restoreCached()
                            signInViewModel.restoreCached()
                            mapViewModel.restoreCached()
                        }
                        backupResultText(result)
                    },
                    onFailure = { e ->
                        "导入失败：${e.message ?: "数据包读不出来或格式对不上"}"
                    },
                )
            } else {
                // 老格式：UTF-8 文本，去掉 BOM 再交给原来的对局导入器
                val text = bytes.decodeToString().removePrefix("\uFEFF")
                importMessage = runCatching { importer.import(text) }.fold(
                    onSuccess = { result ->
                        importState.mark("已导入 ${result.upserted} 场")
                        overviewViewModel.refresh(cookie)
                        "导入完成：解析 ${result.parsed} 条，去重后 ${result.upserted} 场写入本地库"
                    },
                    onFailure = { e ->
                        "导入失败：${e.message ?: "文件读不出来或对不上格式"}"
                    },
                )
            }
        }
    }

    // 「导出数据」：凭证 + 战绩（含详情）+ 地图分布 / 签到缓存 + 游戏配置表，打成一个 zip。
    // 全部读本地，不发请求 —— 这个功能的场景就是"旧机已经不打算再登录了"。
    val backupExporter = remember {
        BackupExporter(
            store,
            database.matchDao(),
            database.matchBossStatDao(),
            database.configCacheDao(),
        )
    }
    val saveBackupFile = rememberZipFileSaver { saved ->
        exportMessage = if (saved) backupSummary.text else null
    }

    // 实验性功能：把本机对局导出成 CSV / JSON。
    // 全部读本地库，不发请求 —— 详情接口一次只查一局，为了导出几千行去逐局现拉不现实。
    val exporter = remember { MatchExporter(database.matchDao(), bossStats) }
    val saveMatchFile = rememberTextFileSaver { saved ->
        exportMessage = if (saved) exportSummary.text else null
    }

    val serializersModule = remember {
        SerializersModule {
            polymorphic(NavKey::class) {
                // 只剩一级页：二级详情页走图层，不再进 backStack
                subclass(Route.Main::class)
            }
        }
    }
    val savedStateConfig = remember(serializersModule) {
        SavedStateConfiguration { this.serializersModule = serializersModule }
    }
    val backStack = rememberNavBackStack(configuration = savedStateConfig, Route.Main)
    val navigator = remember { Navigator(backStack) }

    // 首胜宝箱数量跟着概览走：它和总览数字是同一次刷拉取回来的
    val overviewState by overviewViewModel.state.collectAsState()
    // ⚠️ 顶栏那颗账号胶囊要的东西**故意不在这里算**，要在 MainScreen 里面 collect：
    // 一级页的内容 lambda 是 `remember(backStack)` 存下来的，
    // 在外面先把 State 读成普通值再传进去，那一层不跟着重组的话值就钉死在第一帧了。
    // 传进 MainScreen 的也得是 ViewModel / UserSettings 这种稳定对象，传值就等于传快照。

    val entryProvider = remember(backStack) {
        entryProvider<NavKey> {
            entry<Route.Main> {
                MainScreen(
                    padding = padding,
                    // Cookie 的唯一入口：有凭证就直接看账号，没有就先问一句怎么拿
                    onOpenAccountDetail = {
                        if (cookie == null) cookieGuide = true else accountSheet = true
                    },
                    onOpenChestDialog = { chestDialog = true },
                    onOpenAllActivities = { openDetail(AppDetail.ACTIVITY_LIST) },
                    onOpenSignIn = { openDetail(AppDetail.SIGN_IN) },
                    onOpenMatchDetail = ::openMatch,
                    onOpenAbout = { openDetail(AppDetail.ABOUT) },
                    onOpenTheme = { openDetail(AppDetail.THEME) },
                    onOpenBuildPlan = { openDetail(AppDetail.BUILD_PLAN) },
                    onOpenWiki = { openDetail(AppDetail.WIKI) },
                    buildPlan = buildPlan,
                    firstWinCount = overviewState.firstWinCount,
                    overviewViewModel = overviewViewModel,
                    signInViewModel = signInViewModel,
                    qqGiftViewModel = qqGiftViewModel,
                    xinyueViewModel = xinyueViewModel,
                    supercoreViewModel = supercoreViewModel,
                    historyViewModel = historyViewModel,
                    mapViewModel = mapViewModel,
                    gameConfig = gameConfig,
                    pinned = pinned,
                    favorite = favorite,
                    cookie = cookie,
                    // 顶栏账号胶囊的输入源是**稳定对象**（ViewModel / UserSettings），
                    // 让 MainScreen 自己 collect —— 传值进去等于传一张快照
                    userSettings = settings,
                    liquidGlassEnabled = liquidGlassEnabled,
                    predictiveBackTranslation = predictiveBackTranslation,
                    // 手动刷新。内容和冷启动那条路径**逐条对齐**（下面每项注释照抄冷启动那边）：
                    // 之前这里只刷了三个 ViewModel，少了 updateCookie、配置表和 qq/心悦那两块，
                    // 结果就是"点了刷新但地图分布还是旧名 / 凭证还是上一次的结论"。
                    // 抽成 [refreshAll] 是为了两处不可能再分叉——各写一份迟早会漏一项。
                    // ⚠️ 签到那一路不在这里：它认 [signInAttempt] 那个 key，
                    // 光调 refreshAll 的话 cookie 没变、effect 不会重跑，
                    // 「每日完成1局」的奖励就只在冷启动领过一次。
                    onRefresh = {
                        signInAttempt++
                        refreshAll(cookie)
                    },
                    settings = SettingsUiState(
                        retentionMonths = retentionMonths,
                        autoRefreshMinutes = autoRefreshMinutes,
                        topBarAccountEnabled = topBarAccountEnabled,
                        scoreWanTargets = scoreWanTargets,
                        compositeScoreEnabled = compositeScoreEnabled,
                        proxyModeEnabled = proxyModeEnabled,
                    ),
                    onAutoRefreshChange = { scope.launch { settings.setAutoRefreshMinutes(it) } },
                    onRetentionMonthsChange = { scope.launch { settings.setRetentionMonths(it) } },
                    onTopBarAccountChange = { scope.launch { settings.setTopBarAccount(it) } },
                    onScoreUnitToggle = { target ->
                        scope.launch {
                            // 读**当前**值再翻：弹层条目是组合期快照，连点两项时
                            // 拿它算出来的整份集合可能已经过期
                            val current = settings.scoreWanTargets.value
                            settings.setScoreWanTargets(
                                if (target in current) current - target else current + target,
                            )
                        }
                    },
                    onCompositeScoreChange = { scope.launch { settings.setCompositeScore(it) } },
                    onProxyModeChange = { scope.launch { settings.setProxyMode(it) } },
                    onClearLocalData = {
                        scope.launch {
                            repository.clearLocal()
                            // Boss 口径缓存是挂在对局上的，对局都清了它也得跟着清，
                            // 不然下次同步回同样的局时会带着上一次的结论
                            bossStats.clear()
                            // 库都空了，凭证卡不该还挂着「已导入json」
                            importState.clear()
                            overviewViewModel.refresh(cookie)
                        }
                    },
                    onImportData = { pickBackupFile() },
                    onExportData = {
                        scope.launch {
                            runCatching {
                                // appVersion 只进清单，方便以后排查"这份包是哪版写的"
                                backupExporter.build(currentEpochSeconds(), appVersionName())
                            }.onSuccess { file ->
                                backupSummary.text = "已导出 ${file.matchCount} 场对局" +
                                    "（其中 ${file.detailCount} 场带详情）"
                                saveBackupFile(file.fileName, file.bytes)
                            }.onFailure { e ->
                                exportMessage = "导出失败：${e.message ?: "读写异常"}"
                            }
                        }
                    },
                    onExportMatches = { format ->
                        scope.launch {
                            val file = exporter.build(format, currentEpochSeconds())
                            if (file.matchCount == 0) {
                                exportMessage = "本机还没有对局记录，先同步一次再导出"
                                return@launch
                            }
                            exportSummary.text = "已导出 ${file.matchCount} 场" +
                                "（其中 ${file.detailCount} 场带单局详情）"
                            saveMatchFile(file.fileName, file.mimeType, file.content)
                        }
                    },
                    // ---- 二级详情页：图层内容 ----
                    // 挂在 MainScreen 内部（BarBlurHost 里）而不是这里，
                    // 因为预测性返回的虚化层要读同一个 backdrop。
                    detailVisible = detailShown,
                    // 只有 wiki 那一页装的是 WebView：其余二级页照旧走 graphicsLayer
                    detailTransformInLayer = visibleDetail != AppDetail.WIKI,
                    // 同上：wiki 关掉预测性返回手势（跟手会让整颗 WebView 每帧重新合成）
                    detailPredictiveBack = visibleDetail != AppDetail.WIKI,
    detailNavState = detailNavState,
    // 返回之前先过的一道闸。只有 wiki 要它（在网页首页上先「再按一次退出」），
    // 别的二级页按返回就该直接关 —— 所以判据放在这里，不塞进图层里。
    onConfirmDetailBack = {
        visibleDetail == AppDetail.WIKI && confirmWikiExit()
    },
    detailSnackbarHost = detailSnackbarHost,
    onDismissDetail = ::closeDetail,
                    detailContent = {
                        val roomId = visibleMatch
                        if (roomId != null) {
                            val detailViewModel = remember(roomId) {
                                MatchDetailViewModel(api, database.matchDao(), bossStats)
                            }
                            MatchDetailScreen(
                                viewModel = detailViewModel,
                                roomId = roomId,
                                config = gameConfig,
                                // 传对象而不是传读好的 Boolean：二级页图层里的 lambda
                                // 拿到的是快照的话，开关在别处改了这里不会跟着变
                                userSettings = settings,
                                onBack = ::closeDetail,
                            )
                        } else {
                            when (visibleDetail) {
                                AppDetail.ACTIVITY_LIST -> {
                                    val overview = overviewViewModel.state.collectAsState().value
                                    ActivityListScreen(
                                        activities = overview.activities,
                                        // 下拉刷新跑的就是概览那次拉取，进度直接接它的 loading
                                        refreshing = overview.loading,
                                        onRefresh = { overviewViewModel.refresh(cookie) },
                                        onBack = ::closeDetail,
                                        liquidGlassEnabled = liquidGlassEnabled,
                                    )
                                }

                                AppDetail.SIGN_IN -> SignInScreen(
                                    viewModel = signInViewModel,
                                    qqViewModel = qqGiftViewModel,
                                    xinyueViewModel = xinyueViewModel,
                                    supercoreViewModel = supercoreViewModel,
                                    autoSign = autoSignIn,
                                    autoClaimTask = autoClaimWelfareTask,
                                    autoClaimQqGift = autoClaimQqGift,
                                    autoClaimXinyueGift = autoClaimXinyueGift,
                                    autoSupercore = autoSupercore,
                                    cookie = cookie,
                                    onBack = ::closeDetail,
                                    liquidGlassEnabled = liquidGlassEnabled,
                                    // 四个开关都在二级页各自那块卡点开的弹层里：
                                    // 开关和它管的东西是一对，分开放在设置页的话用户得先想起来
                                    // "哦这功能我配过吗"才知道那个开关管的是什么。
                                    onAutoSignChange = {
                                        scope.launch { settings.setAutoSignIn(it) }
                                    },
                                    onAutoClaimTaskChange = {
                                        scope.launch { settings.setAutoClaimWelfareTask(it) }
                                    },
                                    onAutoClaimQqGiftChange = {
                                        scope.launch { settings.setAutoClaimQqGift(it) }
                                    },
                                    onAutoClaimXinyueGiftChange = {
                                        scope.launch { settings.setAutoClaimXinyueGift(it) }
                                    },
                                    onAutoSupercoreChange = {
                                        scope.launch { settings.setAutoSupercore(it) }
                                    },
                                )

                                AppDetail.ABOUT -> AboutScreen(
                                    onBack = ::closeDetail,
                                    liquidGlassEnabled = liquidGlassEnabled,
                                    predictiveBackTranslation = predictiveBackTranslation,
                                )

                                AppDetail.BUILD_PLAN -> BuildPlanScreen(
                                    plan = buildPlan,
                                    onBack = ::closeDetail,
                                    onAddWeapon = { scope.launch { buildPlanStore.addWeapon(it) } },
                                    onSetPerk = { name, index, perk ->
                                        scope.launch { buildPlanStore.setPerk(name, index, perk) }
                                    },
                                    onToggleObtained = { name, index ->
                                        scope.launch { buildPlanStore.toggleObtained(name, index) }
                                    },
                                    onRemoveWeapon = {
                                        scope.launch { buildPlanStore.removeWeapon(it) }
                                    },
                                    liquidGlassEnabled = liquidGlassEnabled,
                                )

                                AppDetail.WIKI -> WikiScreen(
                                    // 走「再按一次退出」而不是直接关：[confirmWikiExit]
                                    // 里面已经判过网页历史，能退就退、不能退才拦。
                                    onBack = { confirmWikiExit() },
                                    backBridge = wikiBack,
                                )

                                AppDetail.THEME -> ThemeSettingsScreen(
                                    colorMode = colorModeSetting,
                                    liquidGlassEnabled = liquidGlassEnabled,
                                    predictiveBackTranslation = predictiveBackTranslation,
                                    onColorModeChange = {
                                        scope.launch { settings.setColorMode(it) }
                                    },
                                    onLiquidGlassChange = { liquidGlassEnabled = it },
                                    onPredictiveBackTranslationChange = {
                                        scope.launch { settings.setPredictiveBackTranslation(it) }
                                    },
                                    onBack = ::closeDetail,
                                )

                                null -> Unit
                            }
                        }
                    },
                )
            }
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider,
    )

    NavDisplay(
        entries = entries,
        onBack = { navigator.pop() },
    )

    FirstWinChestDialog(
        show = chestDialog,
        count = overviewState.firstWinCount,
        onDismiss = { chestDialog = false },
    )

    CookieGuideDialog(
        show = cookieGuide,
        onGoGet = { cookieGuide = false },
        onAlreadyHave = {
            cookieGuide = false
            accountSheet = true
        },
    )

    // 保存凭证：手填和「导入抓包」两条路共用一段。抽出来是因为存完之后那串动作
    // （关弹层 + refreshAll）漏一步就会留下"登上了但数据还是上一个号的"这种毛病。
    val applyCredential: suspend (String) -> Unit = { raw ->
        guideSaving = true
        guideError = null
        val result = runCatching { session.save(raw) }
        guideSaving = false
        result.onSuccess { saved ->
            accountSheet = false
            // 和扫码登录、冷启动共用 refreshAll：以前这里只刷 home / overview / signIn
            // 三个 ViewModel，漏了 api.updateCookie() 和游戏配置表 —— 前者是
            // 「凭证靠时序副作用写进单例」那个坑，地图分布页会拿着上一个账号的凭证请求。
            // 刚填的凭证要立刻走一次自动签到，不用等下次冷启动
            refreshAll(saved)
        }.onFailure { e ->
            guideError = e.message ?: "保存失败"
        }
    }

    LaunchedEffect(yybState.resultCookie) {
        val raw = yybState.resultCookie ?: return@LaunchedEffect
        runCatching { session.save(raw) }
            .onSuccess { saved ->
                refreshAll(saved)
                yybLogin.close()
                yybQrShown = false
            }
            .onFailure { error ->
                yybLogin.close()
                yybQrShown = false
                scanMessage = "微信登录凭证校验失败：${error.message ?: "格式不对"}"
            }
    }

    AccountDetailSheet(
        show = accountSheet,
        cookie = cookie,
        saving = guideSaving,
        error = guideError,
        onSave = { raw -> scope.launch { applyCredential(raw) } },
        onLogout = {
            scope.launch {
                session.clear()
                // 签到的缓存和「今天已试过」的标记都要跟着清：换一个号登录时不该跳过自动签到
                signInViewModel.clear()
                // 弹层**故意不关**：清掉凭证后 OPENID 与 Cookie 两个框都会清空，
                // 用户得重新粘一条 Cookie 才能保存——和官方 PC 端退出后的状态一致。
                // 状态卡退回「未输入」/「已导入json」，关掉弹层再点卡会重新走引导。
                overviewViewModel.refresh(null)
            }
        },
        onDismiss = { accountSheet = false },
        onScanClick = {
            // 先收掉弹层：扫码页是全屏的，底下留一层弹层纯属浪费，
            // 而且扫完的目的是回到概览看数据，不是回到这张卡
            accountSheet = false
            scannerShown = true
        },
        // 左上角「导入抓包」：从 HAR 里认出 openid / access_token 那条 cookie 直接登。
        // 认出来就直接存——用户点"导入文件"就是要登录，再让他点一次 ✓ 是多余的。
        // 解析放 Default 线程：一份抓包动辄几 MB，主线程 Jsons 一下能卡掉两帧。
        onImportHar = { text ->
            scope.launch {
                val scan = withContext(Dispatchers.Default) { scanMiniProgramCookie(text) }
                val raw = scan.cookie
                if (raw != null) {
                    applyCredential(raw)
                } else {
                    guideError = if (scan.looksLikeHar) {
                        "这份 HAR 里没有带 openid / access_token 的请求，" +
                            "要在微信小程序「逆战未来工具箱」里抓一次"
                    } else {
                        "这个文件读不出 HAR 结构，换一份抓包文件试试"
                    }
                }
            }
        },
    )

    // 扫码页。盖在 NavDisplay 之后（也就是所有页面和上面那些弹层之上），
    // 黑底全屏，进出各给一个淡入淡出——它没有背景虚化那套，硬切一下会很突兀。
    AnimatedVisibility(
        visible = scannerShown,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        QrScannerScreen(
            onScanned = { text ->
                scannerShown = false
                // 认领扫码内容是 QrLoginPayload 的事，这里只管分派。
                // PC 端协议以后改成"给地址让手机去 POST 凭证"，改那边的分支就好
                when (val payload = parseQrLoginPayload(text)) {
                    is QrLoginPayload.CookieText -> scanPending = payload

                    is QrLoginPayload.Endpoint -> scanMessage =
                        "这是一个地址（${payload.url}），当前版本还不能用它登录。" +
                            "先让 PC 端出示 Cookie 二维码，或者在上面手动粘贴 Cookie。"

                    is QrLoginPayload.Unknown -> scanMessage =
                        "这个二维码不是本应用的登录码，换一张试试。"
                }
            },
            onDismiss = { scannerShown = false },
            onShowYybQr = {
                scannerShown = false
                yybQrShown = true
                yybLogin.start()
            },
            onShowShareQr = {
                // 先收相机页再出码：两个都是 app 级浮层，同时开着时相机还在底下跑
                scannerShown = false
                qrShareShown = true
            },
        )
    }
    // 扫码页开着的时候，返回键归它。不接的话按返回会直接把整个 app 退出去
    BackHandlerCompat(enabled = scannerShown) { scannerShown = false }

    AnimatedVisibility(
        visible = yybQrShown,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        YybQrLoginScreen(
            state = yybState,
            onBack = {
                yybLogin.close()
                yybQrShown = false
                scannerShown = true
            },
            onRetry = { yybLogin.start() },
        )
    }
    BackHandlerCompat(enabled = yybQrShown) {
        yybLogin.close()
        yybQrShown = false
        scannerShown = true
    }

    // 「反重力登录」出码页。排在扫码页之后 = 盖在它上面；进出各给一个淡入淡出，
    // 因为相机页是黑的、这页是主题背景色，硬切会像闪了一下。
    AnimatedVisibility(
        visible = qrShareShown,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        QrShareScreen(
            credential = cookie,
            onBack = {
                qrShareShown = false
                // 回扫码页而不是直接盖回主界面：用户是从那儿点进来的
                scannerShown = true
            },
        )
    }
    BackHandlerCompat(enabled = qrShareShown) {
        qrShareShown = false
        scannerShown = true
    }

    scanPending?.let { pending ->
        WindowDialog(
            show = true,
            title = "扫码登录",
            onDismissRequest = { scanPending = null },
        ) {
            Column {
                Text(
                    text = buildString {
                        append("已识别到账号")
                        if (!pending.openid.isNullOrBlank()) append("：${pending.openid}")
                        append("。确认后本机会改用这个账号，现有凭证会被替换。")
                    },
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                // 左右两个小按钮，靠右排：换账号是**破坏性**操作（现有凭证会被替掉），
                // 两个都撑满整行的话，一不留神就容易点到"确认登录"那一整条。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { scanPending = null },
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        onClick = {
                            val raw = pending.raw
                            scanPending = null
                            scope.launch {
                                runCatching { session.save(raw) }
                                    .onSuccess { saved ->
                                        // 走 refreshAll 而不是挨个刷三个 ViewModel：
                                        // 它里面还有 api.updateCookie 和游戏配置表，
                                        // 少了前者，地图分布页会拿着上一个号的凭证去请求
                                        refreshAll(saved)
                                    }
                                    .onFailure { e ->
                                        scanMessage =
                                            "这个二维码里的凭证用不了：${e.message ?: "格式不对"}"
                                    }
                            }
                        },
                    ) {
                        Text("确认登录")
                    }
                }
            }
        }
    }

    scanMessage?.let { message ->
        WindowDialog(
            show = true,
            title = "扫码结果",
            onDismissRequest = { scanMessage = null },
        ) {
            Column {
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Button(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    onClick = { scanMessage = null },
                ) {
                    Text("好的")
                }
            }
        }
    }

    importMessage?.let { message ->
        WindowDialog(
            show = true,
            title = "导入结果",
            onDismissRequest = { importMessage = null },
        ) {
            Column {
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Button(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    onClick = { importMessage = null },
                ) {
                    Text("好的")
                }
            }
        }
    }

    exportMessage?.let { message ->
        WindowDialog(
            show = true,
            title = "导出结果",
            onDismissRequest = { exportMessage = null },
        ) {
            Column {
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Button(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    onClick = { exportMessage = null },
                ) {
                    Text("好的")
                }
            }
        }
    }

    // Miuix 的 dialog / bottomsheet 只是往 `LocalDialogStates` 里注册一个 state，
    // 真正画出来要靠 `MiuixPopupHost` —— 而 Scaffold 内部才会自带这个 host。
    // 上面这几个弹层都在 Scaffold **外面**（它们是 app 级的，不属于某一页），
    // 没有 host 就等于注册进一个没人读的列表：点了完全没反应。
    // 所以要在这里手动补一个，并且放在最后，保证它盖在页面之上。
    MiuixPopupUtils.MiuixPopupHost()
}

@Composable
private fun MainScreen(
    padding: PaddingValues,
    onOpenAccountDetail: () -> Unit,
    onOpenChestDialog: () -> Unit,
    onOpenAllActivities: () -> Unit,
    onOpenSignIn: () -> Unit,
    onOpenMatchDetail: (String) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenBuildPlan: () -> Unit,
    onOpenWiki: () -> Unit,
    onProxyModeChange: (Boolean) -> Unit,
    buildPlan: BuildPlan,
    detailVisible: Boolean,
    /** 见 [PredictiveNavLayer] 的同名参数：内容里有 WebView 的页（wiki）传 false。 */
    detailTransformInLayer: Boolean,
    /**
     * 见 [PredictiveNavBackHandler] 的同名参数：wiki 传 false（不要跟手返回）。
     *
     * ⚠️ 和 [detailTransformInLayer] 目前是**同一个判据**，但含义不同：一个管"位移
     * 走哪条渲染路径"，一个管"要不要跟手"。别图省事合并成一个参数——
     * 哪天某个页面没有 WebView 但也想关掉跟手，合并了就改不动了。
     */
    detailPredictiveBack: Boolean,
    detailNavState: PredictiveNavLayerState,
    /** 见 [PredictiveNavBackHandler] 的同名参数：返回 true = 这次返回被拦下。 */
    onConfirmDetailBack: () -> Boolean,
    /** 二级页那层的 Snackbar 宿主。必须挂在这一层**之上**，否则会被铺满整屏的二级页盖住。 */
    detailSnackbarHost: SnackbarHostState,
    onDismissDetail: () -> Unit,
    detailContent: @Composable () -> Unit,
    firstWinCount: Int?,
    overviewViewModel: OverviewViewModel,
    signInViewModel: SignInViewModel,
    qqGiftViewModel: QqGiftViewModel,
    xinyueViewModel: XinyueViewModel,
    supercoreViewModel: SupercoreViewModel,
    historyViewModel: HistoryViewModel,
    mapViewModel: MapDistributionViewModel,
    gameConfig: com.nzd.antigravitypanel.data.remote.dto.GameConfigDto,
    pinned: Set<String>,
    favorite: Set<String>,
    cookie: MiniProgramCredential?,
    /**
     * 顶栏账号胶囊要读的两个设置源。传对象而不是传值：一级页的内容 lambda 是
     * `remember(backStack)` 存下来的，在外面先把 State 读成普通值再传进来，
     * 那一层不跟着重组的话值就永远钉在第一帧（也就是"明明拿到了却不显示"）。
     */
    userSettings: UserSettings,
    liquidGlassEnabled: Boolean,
    predictiveBackTranslation: Int,
    onRefresh: () -> Unit,
    settings: SettingsUiState,
    onAutoRefreshChange: (Int) -> Unit,
    onRetentionMonthsChange: (Int) -> Unit,
    onTopBarAccountChange: (Boolean) -> Unit,
    onScoreUnitToggle: (ScoreUnitTarget) -> Unit,
    onCompositeScoreChange: (Boolean) -> Unit,
    onClearLocalData: () -> Unit,
    onImportData: () -> Unit,
    onExportData: () -> Unit,
    onExportMatches: (MatchExportFormat) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { Tabs.COUNT })
    val coroutineScope = rememberCoroutineScope()

    // 顶栏的筛选菜单要用，跟 HistoryScreen 里那份是同一个 StateFlow，重复 collect 没有副作用
    val historyFilter by historyViewModel.filter.collectAsState()
    val historyAllMatches by historyViewModel.allMatches.collectAsState()

    // 地图分布页顶栏的赛季筛选同理：选项跟着当前 Tab（猎场 / 塔防各一份），
    // selected 为 null 时菜单里五个都画成勾着（第一次用 = 全选）
    val mapSeasonOptions by mapViewModel.seasonOptions.collectAsState()
    val mapSeasonSelected by mapViewModel.seasonSelection.collectAsState()

    // 顶栏账号胶囊：开关和名片都在**这里** collect，不接外层传进来的快照。
    // 两道闸：开关开着 → （没登录，摆占位）或者（有凭证且这张名片属于当前这个号）。
    // 名片那道还要比对 openid：换号后缓存里还留着旧名字，不比对就会先顶一会儿上一个号的昵称。
    val topBarAccountEnabled by userSettings.topBarAccount.collectAsState()
    val overviewState by overviewViewModel.state.collectAsState()
    val topBarAccount = overviewState.account
        ?.takeIf { it.openid.isBlank() || it.openid == cookie?.openid }
        ?.takeIf { it.nickname.isNotBlank() || it.openid.isNotBlank() }
    // ⚠️ 没有凭证时**也要摆**（profile 传 null 就是未登录态）：默认开关是开的，
    // 首屏左侧空一块的话用户既看不到登录入口、也发现不了这里有东西。
    val showTopBarAccount = topBarAccountEnabled && (cookie == null || topBarAccount != null)

    val navigationItems = remember {
        listOf(
            NavigationItem(Tabs.TITLES[Tabs.OVERVIEW], MiuixIcons.Home),
            NavigationItem(Tabs.TITLES[Tabs.HISTORY], MiuixIcons.ListView),
            NavigationItem(Tabs.TITLES[Tabs.MAPS], MiuixIcons.MapAlbum),
            // 每日简报排在「地图分布」之后、设置之前：前三个是"看数据"，
            // 这一页也是，设置是"改配置" —— 别把设置挤到中间去。
            NavigationItem(Tabs.TITLES[Tabs.BRIEFING], MiuixIcons.Notes),
            NavigationItem(Tabs.TITLES[Tabs.SETTINGS], MiuixIcons.Settings),
        )
    }

    // 悬浮底栏的出入场进度：0 = 稳在原位，1 = 完全滑出屏幕（同时淡透）。
    //
    // 直接 `AnimatedVisibility` 掉会让底栏"啪"地消失 / "啪"地出现，和二级页那一段
    // 横向滑入对不上。照 HyperIsland 的 AppShell：底栏永远在场，靠 translationY + alpha
    // 走完，跟页面同一段时间轴。
    val bottomBarProgress = remember { Animatable(0f) }

    LaunchedEffect(detailVisible, detailNavState.isBackActive) {
        // 预测返回手势期间**不插手**：那一段位移交给 backgroundDepth 走跟手分支，
        // 这里插一脚的话，手势刚起手底栏就自己弹回来了。
        if (detailNavState.isBackActive) return@LaunchedEffect
        bottomBarProgress.animateTo(
            targetValue = if (detailVisible) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (detailVisible) {
                    LAYER_EXIT_DURATION
                } else {
                    BOTTOM_BAR_ENTER_DURATION
                },
                easing = FastOutSlowInEasing,
            ),
        )
    }

    // 手势返回期间**跟手**：`backgroundDepth` 就是"下层还被盖住多少"（1 = 完全盖住）。
    // 拿它当底栏的隐藏进度，手指拖多少底栏就回来多少；取消时它退回 1（底栏缩回去），
    // 提交时 commitBack 把它送到 0（底栏归位），两种结局都不用额外接。
    // 手势结束那一刻 isBackActive 转假，[bottomBarProgress] 已经被
    // additionalCommitAnimation 送到 0 了，两条支路在这里是连续的，不会跳。
    //
    // ⚠️⚠️ 这份"隐藏进度"**不在 composition 里读成 val**。那两个动画值跟手时每帧都在变，
    // 读成 val 就等于让 MainScreen 每帧重组一次 —— 二级页（wiki 那颗 WebView）也会被
    // 一起拖着重组。挪到下面 graphicsLayer 的闭包里读：Compose 只把这一层标成"要重画"。

    // 磨砂的宿主必须包在 Scaffold 外面：它负责开离屏 layer，
    // 顶栏和页面内容都从同一块 layer 上取像素。
    BarBlurHost(
        enabled = liquidGlassEnabled,
        // 返回手势期间要虚化背景，那一层得采样当前屏幕内容。
        // 就算用户关了磨砂，这里也照样得开离屏 layer，否则虚化退化成一层纯色。
        //
        // ⚠️ 磨砂开着时短路掉后半段：[enabled] 那一支路已经保证有 backdrop 了，再判一遍
        // 纯属多余 —— 而 [requiresBackdropCapture] 读的是 backdropIntensity 的动画值，
        // 跟手时每帧都在变，等于让 MainScreen 每帧重组一次。磨砂关着时行为与原来一致。
        captureForEffects = !liquidGlassEnabled &&
            detailNavState.requiresBackdropCapture(detailVisible),
    ) {
        // 顶栏**不在这里**。它归每个一级页自己（见 [RootPage]），这样切页时顶栏是跟着
        // 页面一起横向滑进来的，而不是原地换一行标题 —— 而且各页的折叠状态互不干扰：
        // 概览滑到底把大标题收起来，切到设置看到的仍然是没滚过的完整顶栏。
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                // 底栏留在 Scaffold 的 bottomBar 槽里（而不是像 HyperIsland 那样另外铺一层
                // overlay）：一级页里的弹层默认注册到**根** Scaffold 的 popup host，
                // 那层画在 bottomBar 之后，刚好压住底栏；换成 overlay 就会被底栏反过来压住。
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            // 见上面那段的说明：动画值在**绘制期**读，不在组合期读。
                            val hidden = if (detailNavState.isBackActive) {
                                detailNavState.backgroundDepth.value.coerceIn(0f, 1f)
                            } else {
                                bottomBarProgress.value
                            }
                            translationY = size.height * hidden
                            alpha = 1f - hidden
                        }
                        // 上方留出[BottomBarShadowSpace]给外投影：alpha < 1 会开离屏层，
                        // 层外（胶囊边界之外）画的东西会被裁掉，不留白顶部那道就没了。
                        // ⚠️ padding 必须写在 graphicsLayer **之后**（内层）：离屏层的尺寸等于
                        // graphicsLayer 量到的 child 尺寸，写在外层的话层还是只有胶囊那么大，
                        // 留出来的 16dp 落在层外，阴影照样被裁。
                        .padding(top = BottomBarShadowSpace),
                ) {
                    LiquidNavigationBar(
                        items = navigationItems,
                        // 底栏反过来要读 currentPage：指示器得**跟着滑**才有手感。
                        // 用 settledPage 的话，跨页动画跑完之前指示器一直钉在旧的那一格，
                        // 看着像点了没反应。currentPage 中途翻到相邻页只是让指示器多走一段，
                        // 不会再引发切页——底栏组件里那条"回灌即回调"的路已经拿掉了。
                        selectedIndex = pagerState.currentPage,
                        onItemClick = { index ->
                            // 不能用 settledPage 去重：A → B 的动画中途 currentPage 已到 B、
                            // settledPage 仍是 A，这时点回 A 若被过滤掉，底栏已乐观选中 A，
                            // Pager 却继续停在 B，两边就会一直对不上。连续滚动由 Pager 的
                            // 滚动互斥取消旧动画，保证最后一次点击的目标生效。
                            if (shouldRequestTabScroll(
                                    detailVisible = detailVisible,
                                    targetIndex = index,
                                    currentPage = pagerState.currentPage,
                                    currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                                )
                            ) {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    )
                }
            },
        ) { innerPadding ->
            // 关键：这一层铺满整屏且**不裁掉顶栏那一条**，内容滚动时会从顶栏底下穿过去，
            // 顶栏的 progressiveTextureBlur 才有东西可糊。页面自己用 contentPadding 躲开顶栏。
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    // bottomBar 那道 16dp 只是给投影留的空，不是底栏占位，
                    // 得减回去，否则每个一级页底部都会凭空多出一截。
                    LocalRootBottomBarPadding provides (
                        innerPadding.calculateBottomPadding() - BottomBarShadowSpace
                        ).coerceAtLeast(0.dp),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        // 与 HyperIsland 一致：多留一页在视口外，切过去时不用临时现组
                        beyondViewportPageCount = 1,
                    ) { page ->
                        val pageTitle = if (page == Tabs.OVERVIEW) {
                            Tabs.APP_NAME
                        } else {
                            Tabs.TITLES[page]
                        }
                        // 只有概览页有账号胶囊：它显示的是**当前登录的这个游戏账号**，
                        // 摆在历史 / 地图分布那些页上不合适。没登录时也占着这一格（profile 为 null），
                        // 靠 [chipVisible] 而不是 [chipProfile] 判断要不要摘掉标题 ——
                        // 未登录态下 profile 本来就是 null，拿它判断会把占位胶囊一起吞掉。
                        val chipVisible = page == Tabs.OVERVIEW && showTopBarAccount
                        // ⚠️ 没凭证时**必须**把名片丢掉，哪怕缓存里还留着上一个号的昵称：
                        // 退出登录后概览缓存可能还挂着旧 account，照抄就会顶着别人的名字说"未登录"，
                        // 红点也跟着跑到一个看起来登着的头像上。
                        val chipProfile = if (chipVisible && cookie != null) topBarAccount else null
                        // 账号胶囊的玻璃底和右侧按钮同源，都画在顶栏外面那层不被裁的 overlay 上。
                        // 锚点在这里建：它和这一页的顶栏槽位是同一份生命周期。
                        val chipGlassAnchor = remember { TopBarGlassAnchor() }
                        // 显式标出 @Composable 函数类型：写成 `if (...) { { ... } } else null`
                        // 让编译器从参数类型反推也行，但那样可读性差，而且以后拆成变量容易漏类型。
                        val chipGlass: (@Composable () -> Unit)? = if (chipVisible) {
                            {
                                AccountTopBarChip(
                                    profile = chipProfile,
                                    partition = partitionLabelOf(cookie),
                                    // 未登录才亮红点：它就是"这里还差一步"的提示，
                                    // 登录成功后 cookie 一有值自然就不画了
                                    showLoginDot = cookie == null,
                                    onClick = onOpenAccountDetail,
                                )
                            }
                        } else {
                            null
                        }
                        RootPage(
                            title = pageTitle,
                            // 摆了账号胶囊就把应用名从折叠后的工具条里摘掉：
                            // 一行中间一个应用名、左边一个头像昵称，读起来像两个标题在打架。
                            // 同时把大标题也整块去掉，改由概览页画在列表里（跟着内容滚），
                            // 否则它会被顶栏收走 —— 用户要的是"像状态卡一样跟着页面移动"。
                            collapsedTitle = if (chipVisible) "" else pageTitle,
                            largeTitle = if (chipVisible) null else pageTitle,
                            navigationIcon = {
                                // 真身已经搬到 overlay 上了，槽位里只留一个同高（42dp）的占位：
                                // 它既撑住 Miuix 的 navigationIcon 内边距，又把坐标报给锚点。
                                // 标题在这页是空串，所以占位宽度只影响内边距、不影响排版。
                                if (chipVisible) {
                                    TopBarGlassPlaceholder(chipGlassAnchor)
                                }
                            },
                            startGlassAnchor = if (chipVisible) chipGlassAnchor else null,
                            startGlass = chipGlass,
                            actions = {
                                when (page) {
                                    Tabs.OVERVIEW -> {
                                        // 首胜宝箱在刷新按钮**左边**：它是"看一眼"的信息，
                                        // 刷新是要点的操作，主操作放最右更符合右手习惯。
                                        // 两颗玻璃之间留 [TOP_BAR_GLASS_GAP]：贴着放时
                                        // 两个圆相切，读起来像一颗连体按钮。
                                        Row(horizontalArrangement = Arrangement.spacedBy(TOP_BAR_GLASS_GAP)) {
                                            // wiki 入口放在**最左**：它跟数据无关，是个"去别处查资料"的出口，
                                            // 紧挨着刷新会让人以为点了它就会重新拉数据。
                                            TopBarGlassChip {
                                                IconButton(onClick = onOpenWiki) {
                                                    Icon(
                                                        imageVector = AutoStoriesIcon,
                                                        contentDescription = "查询 Wiki",
                                                        tint = MiuixTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            }
                                            TopBarGlassChip {
                                                FirstWinChestButton(
                                                    count = firstWinCount,
                                                    onClick = onOpenChestDialog,
                                                )
                                            }
                                            TopBarGlassChip {
                                                IconButton(onClick = onRefresh) {
                                                    Icon(
                                                        imageVector = MiuixIcons.Refresh,
                                                        contentDescription = "刷新数据",
                                                        tint = MiuixTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 地图分布的赛季筛选（猎场 / 塔防各有自己的一份勾选）。
                                    // 时空追猎没有赛季表，seasonOptions 是空的 —— 那时整个入口不摆，
                                    // 摆一个点开什么都没有的图标比没有更让人困惑。
                                    Tabs.MAPS -> if (mapSeasonOptions.isNotEmpty()) {
                                        TopBarGlassChip {
                                            MapSeasonFilterMenu(
                                                seasons = mapSeasonOptions,
                                                selected = mapSeasonSelected,
                                                onToggle = mapViewModel::toggleSeason,
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Tune,
                                                    contentDescription = "按赛季筛选",
                                                    tint = MiuixTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }

                                    // 筛选改成级联下拉菜单，五个维度各一个子菜单。
                                    // 展开状态由组件自己管，不用再在外面存一个 "filterSheet" 布尔值。
                                    Tabs.HISTORY -> TopBarGlassChip {
                                        HistoryFilterMenu(
                                            filter = historyFilter,
                                            allMatches = historyAllMatches,
                                            config = gameConfig,
                                            onFilterChange = { historyViewModel.setFilter(it) },
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Tune,
                                                contentDescription = "筛选",
                                                tint = MiuixTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }

                                    // 每日简报读的是本地库，而"今天"要靠一轮同步才齐。
                                    // 刷新入口和概览页是同一个（refreshAll），
                                    // 不要在这里单拉接口 —— 那会把凭证 / 签到 / 缓存的口径拆成两套。
                                    Tabs.BRIEFING -> TopBarGlassChip {
                                        IconButton(onClick = onRefresh) {
                                            Icon(
                                                imageVector = MiuixIcons.Refresh,
                                                contentDescription = "刷新数据",
                                                tint = MiuixTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            },
                        ) { insets ->
                            when (page) {
                                Tabs.OVERVIEW -> OverviewScreen(
                                    viewModel = overviewViewModel,
                                    signInViewModel = signInViewModel,
                                    qqGiftViewModel = qqGiftViewModel,
                                    xinyueViewModel = xinyueViewModel,
                                    supercoreViewModel = supercoreViewModel,
                                    // 账号模式下顶栏没有大标题，那一句交给页面自己画在列表首项
                                    inlineTitle = if (chipVisible) pageTitle else null,
                                    onOpenSignIn = onOpenSignIn,
                                    onOpenAccountDetail = onOpenAccountDetail,
                                    onOpenAllActivities = {
                                        onOpenAllActivities()
                                    },
                                    onOpenBuildPlan = onOpenBuildPlan,
                                    plan = buildPlan,
                                    // 积分单位要跟着设置变，所以传对象而不是读好的 Boolean
                                    userSettings = userSettings,
                                    insets = insets,
                                )

                                Tabs.HISTORY -> HistoryScreen(
                                    viewModel = historyViewModel,
                                    config = gameConfig,
                                    pinned = pinned,
                                    favorite = favorite,
                                    userSettings = userSettings,
                                    insets = insets,
                                    onOpenDetail = { roomId ->
                                        onOpenMatchDetail(roomId)
                                    },
                                )

                                Tabs.MAPS -> MapDistributionScreen(
                                    viewModel = mapViewModel,
                                    insets = insets,
                                )

                                Tabs.BRIEFING -> DailyBriefingScreen(
                                    matchesFlow = historyViewModel.allMatches,
                                    config = gameConfig,
                                    // 换号后库里还留着上一个号的对局，按 openid 收一道；
                                    // 没登录（离线导入）时传 null，不做账号过滤。
                                    openId = cookie?.openid,
                                    onOpenDetail = { roomId -> onOpenMatchDetail(roomId) },
                                    insets = insets,
                                )

                                Tabs.SETTINGS -> SettingsScreen(
                                    state = settings,
                                    onAutoRefreshChange = onAutoRefreshChange,
                                    onRetentionMonthsChange = onRetentionMonthsChange,
                                    onTopBarAccountChange = onTopBarAccountChange,
                                    onScoreUnitToggle = onScoreUnitToggle,
                                    onCompositeScoreChange = onCompositeScoreChange,
                                    onProxyModeChange = onProxyModeChange,
                                    onClearLocalData = onClearLocalData,
                                    onOpenAbout = onOpenAbout,
                                    onOpenTheme = onOpenTheme,
                                    onImportData = onImportData,
                                    onExportData = onExportData,
                                    onExportMatches = onExportMatches,
                                    insets = insets,
                                )
                            }
                        }
                    }
                }
            }
            // 虚化层必须放在 Scaffold 的 **content 里**，不能放到 Scaffold 之后：
            // 它是一张铺满整屏的模糊位图（不透明），放到外面会把悬浮底栏连它那段
            // 下滑/上浮动画一起盖住 —— 表现就是"底栏直接消失 / 直接出现"。
            // 放在 content 里正好夹在一级内容之上、底栏之下，和 HyperIsland 一致
            // （它把底栏铺在所有图层之后，这里用 content 内层达到同样的 z 序，
            //  好处是一级页的 Snackbar 和弹层仍然压得住底栏）。
            PredictiveNavBackdrop(
                state = detailNavState,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ---- 二级详情页图层 ----
        // 必须放在 Scaffold **之后**：底栏是浮在上面的，写进 Scaffold 的 content
        // 里会被底栏压住，二级页右下角就露出一条导航条。
        PredictiveNavLayer(
            visible = detailVisible,
            state = detailNavState,
            // 最大横移距离可调（设置 → 外观 → 主题 → 预测返回距离）
            maxTranslationPercent = predictiveBackTranslation.toLong(),
            // wiki 那一页里是整颗 WebView，跟手位移不能走 graphicsLayer（会开离屏层）
            transformInLayer = detailTransformInLayer,
        ) {
            detailContent()
        }
        PredictiveNavBackHandler(
            visible = detailVisible,
            enabled = detailVisible,
            state = detailNavState,
            maxTranslationPercent = predictiveBackTranslation.toLong(),
            predictive = detailPredictiveBack,
            confirmBack = onConfirmDetailBack,
            onDismiss = onDismissDetail,
            // 手势提交时底栏跟着页面一起归位（同一组 duration / easing）
            additionalCommitAnimation = { durationMillis, easing ->
                bottomBarProgress.animateTo(0f, tween(durationMillis, easing = easing))
            },
        )

        // 「再按一次退出」那句提示。
        // ⚠️⚠️ 必须排在所有图层**之后**：二级页那层是铺满整屏的，写在它前面会被整个挡掉。
        //
        // ⚠️ 外面这层 Box 是必需的：BarBlurHost 的 content 不是 BoxScope，拿不到 `align`；
        // 而 SnackbarHost 自己只是 `contentAlignment = BottomCenter`（尺寸由父布局定），
        // 不给 align 的话整块会被摆在左上角。
        // 只按位置传前两个参数、snackbar 内容走尾随 lambda：Miuix 的 SnackbarHost
        // 参数名在不同版本间改过，逐个写名字反而容易被坑。
        Box(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(
                detailSnackbarHost,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
            ) { data -> Snackbar(data) }
        }
    }
}

/**
 * 一级页的壳：自带一条顶栏 + 自己的滚动行为。照 HyperIsland 的 `CollapsingPage`。
 *
 * 顶栏**必须长在页面里**而不是宿主 Scaffold 上，两个原因：
 * 1. 切页时它跟着页面一起横向滑进来，能看出"换了一页"；
 *    挂在宿主上就是原地换一行标题，动画全靠猜。
 * 2. 折叠状态天然按页隔离。共用一份 TopAppBar 时，在概览滑到底把大标题收起来、
 *    再切到设置，顶栏会在切页那一帧突然弹回全高 —— 用户看到的就是"顶栏闪现变高"。
 *
 * @param insets 顶栏高度 + 悬浮底栏占掉的高度。列表**不裁掉**这两块（内容要从顶栏
 *   底下穿过去给磨砂采样），而是靠自己的 `contentPadding` 躲开。
 */
@Composable
private fun RootPage(
    title: String,
    /**
     * 工具条（折叠后那一条）里显示的标题，默认是 [title]。
     *
     * 页面在顶部时工具条里其实没有东西：标题那时候是下面那个大标题，
     * 工具条要下滑把大标题收起来之后才显示。所以这个参数和 [title] 分开，
     * 是为了让"左边摆了账号胶囊的那一类页面"能把工具条里的标题摘掉
     * ——一行里同时出现应用名和账号名，读起来是两个标题在打架。
     */
    collapsedTitle: String = title,
    /**
     * 页面顶部那个大标题。**传 null 表示这一页不要大标题**。
     *
     * 和 [collapsedTitle] 传空串是两件事：空串只是"工具条里不写字"，滑上去时
     * 大标题照样会被顶栏收掉（淡出并上移）。传 null 才是把它整块去掉 ——
     * 摆了账号胶囊的页面要的是"应用名像状态卡一样跟着内容滚"，
     * 那一句得由页面自己画在列表里（[OverviewScreen] 的 `inlineTitle`）。
     *
     * 传 null 时顶栏走 [SmallTopAppBar]（只有工具条），不再是大标题被收起来的 TopAppBar。
     */
    largeTitle: String? = title,
    navigationIcon: @Composable () -> Unit = {},
    /**
     * 顶栏**左侧**那组玻璃（概览页的账号胶囊）的定位锚点。
     *
     * 和右侧 `endGlassAnchor` 同一套机制：Miuix 顶栏自带 `clipToBounds()`，
     * 玻璃阴影溢出到胶囊外面会被切成直边，所以真身要画在 `BlurredBar` 那层
     * 不被裁的 overlay 上；顶栏槽位里只留 [TopBarGlassPlaceholder] 上报坐标。
     *
     * 传 null = 这一页左侧没有玻璃（历史 / 地图分布这些不摆账号胶囊的页面）。
     */
    startGlassAnchor: TopBarGlassAnchor? = null,
    /** 顶栏左侧那组玻璃本身。和 [startGlassAnchor] 成对出现。 */
    startGlass: (@Composable () -> Unit)? = null,
    // RowScope：TopAppBar 的 actions 就是一行，跟着它走才能在里面用 Modifier.weight 之类的
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurEnabled = LocalBarBlurEnabled.current
    // 顶栏按钮那层玻璃跟着这份滚动行为显隐（见 ScrollDrivenGlass.kt）。
    // 放在这里不会有性能负担：它内部是一条 snapshotFlow，只有阴影/玻璃自己在动。
    val glassProgress = rememberTopBarGlassProgress(scrollBehavior)
    // 顶栏那几颗玻璃画在**顶栏外面**那层不被裁的 overlay 上（Miuix 顶栏自带
    // clipToBounds，会把溢出到 42dp 之外的阴影切成一条直边），槽位里只留一个
    // 同尺寸的占位撑住标题，位置由它上报给锚点。
    val endGlassAnchor = remember { TopBarGlassAnchor() }
    BarBlurHost(enabled = blurEnabled) {
        // 必须罩住 Scaffold：Actions 是在它的 topBar 槽位里组合的，Provide 只包到
        // topBar 内部那一层就漏了。受影响的就是顶栏这几个按钮 ——
        // 页面内容里的按钮各自有卡片当背景，不需要跟着滚。
        CompositionLocalProvider(LocalTopBarGlassProgress provides glassProgress) {
            Scaffold(
                topBar = {
                    BlurredBar(
                        topGradient = true,
                        startGlassAnchor = startGlassAnchor,
                        startGlass = startGlass,
                        endGlassAnchor = endGlassAnchor,
                        endGlass = actions,
                    ) {
                        if (largeTitle == null) {
                            // ⚠️ 不要大标题的页面必须用 SmallTopAppBar，不能拿
                            // TopAppBar(largeTitle = "") 顶替：大标题在 Miuix 里是
                            // 「padding(top = CollapsedHeight) + Text」的一个 Box，
                            // **传空串也照样量出一行的高度**（顶栏高度按它算），
                            // 于是工具条下面白白空出一行 —— 用户看到的就是
                            // "应用名和顶栏之间多了一块"。SmallTopAppBar 只有工具条那一条，
                            // 顶栏高度 = 状态栏 + CollapsedHeight，正好是应用名该贴的位置。
                            // 副作用也是想要的：它会把 scrollBehavior 钉住，顶栏不再折叠，
                            // 那一句标题由页面自己画在列表里跟着内容滚。
                            SmallTopAppBar(
                                title = collapsedTitle,
                                color = Color.Transparent,
                                scrollBehavior = scrollBehavior,
                                navigationIcon = navigationIcon,
                                actions = { TopBarGlassPlaceholder(endGlassAnchor) },
                            )
                        } else {
                            // TopAppBar（不是 SmallTopAppBar）才有大标题：页面在顶部时标题单独占一行、
                            // 不和右侧按钮挤在一起，下滑后才收进工具条。
                            TopAppBar(
                                title = collapsedTitle,
                                largeTitle = largeTitle,
                                color = Color.Transparent,
                                scrollBehavior = scrollBehavior,
                                // 左侧账号胶囊（只有概览页会往这里摆东西）。它整块可点，
                                // 点了就弹「账号详细信息」 —— 和点概览里的状态卡走同一条路
                                navigationIcon = navigationIcon,
                                actions = { TopBarGlassPlaceholder(endGlassAnchor) },
                            )
                        }
                    }
                },
            ) { padding ->
                BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                    // 滚动行为挂在页面外层：LazyColumn 的滚动增量会沿嵌套滚动链冒泡到这里，
                    // 顶栏据此决定大标题收不收、磨砂出不出。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    ) {
                        content(
                            PaddingValues(
                                top = padding.calculateTopPadding(),
                                bottom = LocalRootBottomBarPadding.current,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 悬浮底栏回到原位的时长。比二级页滑出(380ms)短一点，视觉上是"页面先把路让开"。 */
private const val BOTTOM_BAR_ENTER_DURATION = 300

/** wiki 在网页首页上「再按一次退出」的有效期（秒）。过了就当没这回事，重新提示。 */
private const val WIKI_EXIT_PROMPT_WINDOW_SEC = 5L

/** 那句提示的文案。 */
private const val WIKI_EXIT_PROMPT = "已经在 Wiki 首页了，再按一次返回退出"

/**
 * 每日首胜宝箱按钮。图标右上角挂一个小红点，红点里是**还能领几个**。
 *
 * 数量用官方的 `center.user.day` → `firstWinLeftCount`，上限 7（官方前端写死 `/ 7`）。
 * 几个边界要说清楚：
 * - `count == null`：没拉到（未登录 / 接口失败）。按钮还在，但不画红点——
 *   画一个"0"会被读成"今天领完了"，那是另一件事
 * - `count == 0`：能拉到但领完了，同样不画红点，点开弹窗里说明
 */
@Composable
private fun FirstWinChestButton(
    count: Int?,
    onClick: () -> Unit,
) {
    BadgedBox(
        badge = {
            if (count != null && count > 0) {
                Badge(containerColor = MiuixTheme.colorScheme.error) {
                    Text(
                        text = count.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) {
        IconButton(onClick = onClick) {
            Icon(
                // 公文包（Material `business_center`）：这颗按钮是"每日首胜宝箱"，
                // 论形状说的是奖励/福利，不是"下载量"——之前那颗 TopDownloads 容易被读成下载。
                imageVector = MaterialGlyphs.BusinessCenter,
                contentDescription = "每日首胜宝箱",
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 每日首胜宝箱的补充倒计时（到北京时间下一个 05:00）。
 *
 * 官方 PC 端是按**设备本地时间** `setHours(5,0,0,0)` 算的，这里按北京时间算同一个点 ——
 * 服务端只认北京时间，跟着被改过的系统时区走，倒计时会在真正刷新之前就归零。
 */
private const val CHEST_REFILL_HOUR = 5

/** 弹窗里那张箱子插画的边长。原图 1024px，这里按 3x 屏倒推绰绰有余。 */
private val CHEST_ART_SIZE = 72.dp

private fun chestRefillCountdown(nowSec: Long): String =
    formatCountdownClock(secondsUntilNextServerDayAt(nowSec, CHEST_REFILL_HOUR))

@Composable
private fun FirstWinChestDialog(
    show: Boolean,
    count: Int?,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "每日首胜宝箱",
        onDismissRequest = onDismiss,
    ) {
        // 第一帧就得是有内容的（不能等 LaunchedEffect 跑一圈才填），所以初值在组合算一次；
        // 之后靠下面的轮询刷新。弹窗收起时不转：看不见还在 tick 纯属浪费。
        var countdown by remember { mutableStateOf(chestRefillCountdown(currentEpochSeconds())) }
        LaunchedEffect(show) {
            if (!show) return@LaunchedEffect
            while (isActive) {
                val next = chestRefillCountdown(currentEpochSeconds())
                if (next != countdown) countdown = next
                // 每 0.5 秒刷一次而不是 1 秒：让这次 tick 的累积误差在半秒内被下一次修正，
                // 否则秒位会偶尔一次跳两格
                delay(500)
            }
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = firstWinChestPainter(),
                    contentDescription = null,
                    modifier = Modifier.size(CHEST_ART_SIZE),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        // 拉不到数量时是 null 而不是 0 —— 和顶栏小红点同一个规矩：
                        // null 是"我不知道"，不能画成"今天领完了"
                        text = "${count?.toString() ?: "—"}/" +
                            OverviewRepository.FIRST_WIN_CHEST_LIMIT,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "下次补充 $countdown",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Button(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(),
                onClick = onDismiss,
            ) {
                Text("好的")
            }
        }
    }
}

@Composable
private fun LiquidNavigationBar(
    items: List<NavigationItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // backdrop 由 BarBlurHost 统一提供，底栏和顶栏采样同一块离屏 layer。
    // API < 33 时它是 null，组件自动退化为不透明胶囊。
    val backdrop = LocalBarBlurBackdrop.current
    IosLiquidGlassNavigationBar(
        items = items,
        selectedIndex = selectedIndex,
        onItemClick = onItemClick,
        backdrop = backdrop,
        isBlurActive = backdrop != null && isRuntimeShaderSupported(),
        modifier = modifier.padding(bottom = 12.dp),
    )
}
