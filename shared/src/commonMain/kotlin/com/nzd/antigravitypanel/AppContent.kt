package com.nzd.antigravitypanel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.nzd.antigravitypanel.data.credential.CredentialSession
import com.nzd.antigravitypanel.data.build.BuildPlan
import com.nzd.antigravitypanel.data.build.BuildPlanStore
import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.db.DatabaseProvider
import com.nzd.antigravitypanel.data.imports.JsonImportState
import com.nzd.antigravitypanel.data.imports.JsonMatchImporter
import com.nzd.antigravitypanel.data.qrlogin.QrLoginPayload
import com.nzd.antigravitypanel.data.qrlogin.parseQrLoginPayload
import com.nzd.antigravitypanel.data.repo.MatchRepository
import com.nzd.antigravitypanel.data.repo.OverviewRepository
import com.nzd.antigravitypanel.data.settings.MatchMarks
import com.nzd.antigravitypanel.data.settings.UserSettings
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.data.store.createKeyValueStore
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.ui.about.AboutScreen
import com.nzd.antigravitypanel.ui.activity.ActivityListScreen
import com.nzd.antigravitypanel.ui.build.BuildPlanScreen
import com.nzd.antigravitypanel.ui.component.BarBackdropContent
import com.nzd.antigravitypanel.ui.component.BarBlurHost
import com.nzd.antigravitypanel.ui.component.BlurredBar
import com.nzd.antigravitypanel.ui.component.LocalBarBlurBackdrop
import com.nzd.antigravitypanel.ui.component.PredictiveNavBackHandler
import com.nzd.antigravitypanel.ui.component.PredictiveNavBackdrop
import com.nzd.antigravitypanel.ui.component.PredictiveNavLayer
import com.nzd.antigravitypanel.ui.component.BackHandlerCompat
import com.nzd.antigravitypanel.ui.component.PredictiveNavLayerState
import com.nzd.antigravitypanel.ui.component.rememberPredictiveNavLayerState
import com.nzd.antigravitypanel.ui.component.requiresBackdropCapture
import com.nzd.antigravitypanel.ui.component.liquid.IosLiquidGlassNavigationBar
import com.nzd.antigravitypanel.ui.component.rememberJsonFilePicker
import com.nzd.antigravitypanel.ui.detail.MatchDetailScreen
import com.nzd.antigravitypanel.ui.detail.MatchDetailViewModel
import com.nzd.antigravitypanel.ui.history.HistoryScreen
import com.nzd.antigravitypanel.ui.history.HistoryViewModel
import com.nzd.antigravitypanel.ui.history.HistoryFilterMenu
import com.nzd.antigravitypanel.ui.home.HomeViewModel
import com.nzd.antigravitypanel.ui.mapdist.MapDistributionScreen
import com.nzd.antigravitypanel.ui.mapdist.MapDistributionViewModel
import com.nzd.antigravitypanel.ui.navigation.Navigator
import com.nzd.antigravitypanel.ui.navigation.Route
import com.nzd.antigravitypanel.ui.overview.AccountDetailSheet
import com.nzd.antigravitypanel.ui.overview.CookieGuideDialog
import com.nzd.antigravitypanel.ui.overview.OverviewScreen
import com.nzd.antigravitypanel.ui.overview.OverviewViewModel
import com.nzd.antigravitypanel.ui.qrlogin.QrScannerScreen
import com.nzd.antigravitypanel.ui.settings.SettingsScreen
import com.nzd.antigravitypanel.ui.settings.SettingsUiState
import com.nzd.antigravitypanel.ui.settings.ThemeSettingsScreen
import com.nzd.antigravitypanel.ui.signin.QqGiftViewModel
import com.nzd.antigravitypanel.ui.signin.XinyueViewModel
import com.nzd.antigravitypanel.ui.signin.SignInScreen
import com.nzd.antigravitypanel.ui.signin.SignInViewModel
import com.nzd.antigravitypanel.ui.theme.ColorMode
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private object Tabs {
    const val OVERVIEW = 0
    const val HISTORY = 1
    const val MAPS = 2
    const val SETTINGS = 3
    const val COUNT = 4

    val TITLES = listOf("概览", "历史战绩", "地图分布", "设置")

    /** 概览页顶栏显示应用名而不是"概览"，与 HyperIsland 的首页一致。 */
    const val APP_NAME = "反重力数据面板"
}

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
    val configRepository = remember { GameConfigRepository(api, database.configCacheDao()) }
    val importState = remember { JsonImportState(store) }
    val importer = remember { JsonMatchImporter(database.matchDao()) }
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
        HistoryViewModel(database.matchDao(), marks, configRepository.config)
    }
    val mapViewModel = remember {
        MapDistributionViewModel(api, database.matchDao(), configRepository.config)
    }
    // 签到的冷启动缓存走和概览同一条路：宿主预热存储后同步 peek，
    // 否则签到卡会先闪一帧「未登录」再跳成真实数字
    val signInSeed = remember { runCatching { store.peek(SignInViewModel.CACHE_KEY) }.getOrNull() }
    val signInViewModel = remember { SignInViewModel(api, store, signInSeed) }
    // Build 计划。同样走"合成阶段同步 peek"：概览那张卡要在第一帧就画出在养的武器，
    // 放进 LaunchedEffect 的话它会先空一拍，看着像计划丢了
    val buildSeed = remember { runCatching { store.peek(StoreKey.BUILD_PLAN) }.getOrNull() }
    val buildPlanStore = remember { BuildPlanStore(store, buildSeed) }
    // 游戏中心周签到：另一套凭证（QQ 登录 cookie），和小程序的签到互不相干
    val qqSeed = remember { runCatching { store.peek(QqGiftViewModel.CACHE_KEY) }.getOrNull() }
    val qqGiftViewModel = remember { QqGiftViewModel(store, cachedSeed = qqSeed) }
    // 心悦悦享卡：第三套凭证（两个 T-* 请求头），和上面两块都不通用
    val xinyueSeed = remember { runCatching { store.peek(XinyueViewModel.CACHE_KEY) }.getOrNull() }
    val xinyueViewModel = remember { XinyueViewModel(store, cachedSeed = xinyueSeed) }
    DisposableEffect(Unit) {
        onDispose {
            homeViewModel.close()
            overviewViewModel.close()
            historyViewModel.close()
            mapViewModel.close()
            signInViewModel.close()
            qqGiftViewModel.close()
            xinyueViewModel.close()
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
        qqGiftViewModel.restore()
        xinyueViewModel.restore()
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
    val autoClaimQqGift by settings.autoClaimQqGift.collectAsState()
    val autoClaimXinyueGift by settings.autoClaimXinyueGift.collectAsState()
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
    /** 扫码扫到 Cookie，先摆在这儿等用户点头——扫错码直接顶掉当前账号的代价太大。 */
    var scanPending by remember { mutableStateOf<QrLoginPayload.CookieText?>(null) }
    /** 扫出来的东西用不了时的提示（不是登录码 / 是暂不支持的地址）。 */
    var scanMessage by remember { mutableStateOf<String?>(null) }
    /** 每日首胜宝箱的说明弹窗。 */
    var chestDialog by remember { mutableStateOf(false) }
    /** 没有凭证时点状态卡先弹的"怎么拿 Cookie"对话框。 */
    var cookieGuide by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // ---- 二级详情页（HyperIsland 式图层）----
    // 「哪个页」和「是否可见」分开存：退出动画跑的那一瞬间页面还在画，
    // 立刻把 visibleDetail 清成 null 会让内容在动画结束前闪没。
    var visibleDetail by remember { mutableStateOf<AppDetail?>(null) }
    /** 战绩详情带的参数。带参数的页单独一个 state，和 HyperIsland 对 `visibleToastApp` 一样。 */
    var visibleMatch by remember { mutableStateOf<String?>(null) }
    var detailShown by remember { mutableStateOf(false) }
    val detailNavState = rememberPredictiveNavLayerState()

    fun openDetail(detail: AppDetail) {
        // 两个 state 互斥：不清掉另一个，"上一个页面"会一直优先渲染出来
        visibleMatch = null
        visibleDetail = detail
        detailShown = true
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
     * 拉一轮全部数据。**冷启动那条 LaunchedEffect 和手动刷新都必须走这里**——
     * 之前两边各写一份，手动刷新那份少了 `updateCookie` 和游戏配置表，
     * 于是"点了刷新看着转了圈，地图分布还是旧的、凭证还是上一次的结论"。
     *
     * @param autoSign true 顺手签到（冷启动），false 只刷签到看板（手动刷新）。
     *   手动刷新不该顺便把签到做了——那是"打开 app"那一下的事；但数字要跟着更新，
     *   否则看着像没刷。
     */
    fun refreshAll(active: NzCookie?, autoSign: Boolean = false) {
        if (active == null) {
            // 没凭证时也要刷一次概览：导入过 JSON 的话，状态卡要显示「已导入json」
            overviewViewModel.refresh(null)
            // 顺带把签到卡收起来，别留着上一个号的签到状态
            signInViewModel.refresh(null)
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
        if (autoSign) signInViewModel.refresh(active) else signInViewModel.refreshBoardOnly(active)
    }

    LaunchedEffect(colorModeSetting) {
        if (colorModeSetting != colorMode) onColorModeChange(colorModeSetting)
    }

    // 周签到礼包的自动领取。独立于 cookie 那个 effect：它用的是 QQ 凭证，
    // 和小程序那套没关系，不该被"小程序 cookie 变了"重新触发一遍。
    // 开关本身也进 key —— 用户刚打开时应该立刻试一次，不用等下次冷启动。
    // 同一天重复跑不会有副作用：仓库内部按「日期|uin」去重。
    LaunchedEffect(restored, autoClaimQqGift) {
        if (!restored) return@LaunchedEffect
        qqGiftViewModel.refresh(autoClaim = autoClaimQqGift)
    }

    // 悦享卡的自动领取。同理独立于 cookie 那个 effect：它看的是心悦凭证。
    // 开关进 key 是为了用户刚打开时立刻试一次，不用等下次冷启动。
    // 同一天重复跑不会有副作用：仓库内部按「日期|openid」去重。
    LaunchedEffect(restored, autoClaimXinyueGift) {
        if (!restored) return@LaunchedEffect
        xinyueViewModel.refresh(autoClaim = autoClaimXinyueGift)
    }

    LaunchedEffect(restored, ready, cookie) {
        if (!restored || !ready) return@LaunchedEffect
        // 接一个局部变量：cookie 是委托属性，编译器不做智能转换
        val active = cookie
        if (active != null) {
            refreshAll(active, autoSign = true)
        } else {
            refreshAll(null)
            // 游戏中心那块和小程序的凭证无关，它只看自己有没有存过 QQ cookie。
            // 但自动领取要不要开，得等设置读完才知道，所以放在下面那个 effect。
            qqGiftViewModel.refresh(autoClaim = false)
        }
    }

    // 实验性功能：选一个 nzm_matches.json 写进本地库。
    // 导入完立刻重刷概览，让凭证卡从「未输入」变「已导入json」。
    val pickJsonFile = rememberJsonFilePicker { content ->
        if (content == null) return@rememberJsonFilePicker
        scope.launch {
            importMessage = runCatching { importer.import(content) }.fold(
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
                    buildPlan = buildPlan,
                    firstWinCount = overviewState.firstWinCount,
                    overviewViewModel = overviewViewModel,
                    signInViewModel = signInViewModel,
                    qqGiftViewModel = qqGiftViewModel,
                    xinyueViewModel = xinyueViewModel,
                    historyViewModel = historyViewModel,
                    mapViewModel = mapViewModel,
                    gameConfig = gameConfig,
                    pinned = pinned,
                    favorite = favorite,
                    cookie = cookie,
                    liquidGlassEnabled = liquidGlassEnabled,
                    predictiveBackTranslation = predictiveBackTranslation,
                    // 手动刷新。内容和冷启动那条路径**逐条对齐**（下面每项注释照抄冷启动那边）：
                    // 之前这里只刷了三个 ViewModel，少了 updateCookie、配置表和 qq/心悦那两块，
                    // 结果就是"点了刷新但地图分布还是旧名 / 凭证还是上一次的结论"。
                    // 抽成 [refreshAll] 是为了两处不可能再分叉——各写一份迟早会漏一项。
                    onRefresh = { refreshAll(cookie) },
                    settings = SettingsUiState(
                        retentionMonths = retentionMonths,
                        autoRefreshMinutes = autoRefreshMinutes,
                    ),
                    onAutoRefreshChange = { scope.launch { settings.setAutoRefreshMinutes(it) } },
                    onRetentionMonthsChange = { scope.launch { settings.setRetentionMonths(it) } },
                    onClearLocalData = {
                        scope.launch {
                            repository.clearLocal()
                            // 库都空了，凭证卡不该还挂着「已导入json」
                            importState.clear()
                            overviewViewModel.refresh(cookie)
                        }
                    },
                    onImportJson = { pickJsonFile() },
                    // ---- 二级详情页：图层内容 ----
                    // 挂在 MainScreen 内部（BarBlurHost 里）而不是这里，
                    // 因为预测性返回的虚化层要读同一个 backdrop。
                    detailVisible = detailShown,
                    detailNavState = detailNavState,
                    onDismissDetail = ::closeDetail,
                    detailContent = {
                        val roomId = visibleMatch
                        if (roomId != null) {
                            val detailViewModel = remember(roomId) {
                                MatchDetailViewModel(api, database.matchDao())
                            }
                            MatchDetailScreen(
                                viewModel = detailViewModel,
                                roomId = roomId,
                                config = gameConfig,
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
                                    autoClaimQqGift = autoClaimQqGift,
                                    autoClaimXinyueGift = autoClaimXinyueGift,
                                    cookie = cookie,
                                    onBack = ::closeDetail,
                                    liquidGlassEnabled = liquidGlassEnabled,
                                    // 两个自动领取开关跟各自的凭证一起放在二级页的弹层里：
                                    // 开关和凭证是一对，分开放在设置页的话用户得先想起来
                                    // "哦这功能我配过吗"才知道那个开关管的是什么。
                                    onAutoClaimQqGiftChange = {
                                        scope.launch { settings.setAutoClaimQqGift(it) }
                                    },
                                    onAutoClaimXinyueGiftChange = {
                                        scope.launch { settings.setAutoClaimXinyueGift(it) }
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

    AccountDetailSheet(
        show = accountSheet,
        cookie = cookie,
        saving = guideSaving,
        error = guideError,
        onSave = { raw ->
            scope.launch {
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
                    refreshAll(saved, autoSign = true)
                }.onFailure { e ->
                    guideError = e.message ?: "保存失败"
                }
            }
        },
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
        )
    }
    // 扫码页开着的时候，返回键归它。不接的话按返回会直接把整个 app 退出去
    BackHandlerCompat(enabled = scannerShown) { scannerShown = false }

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
                                        refreshAll(saved, autoSign = true)
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
    buildPlan: BuildPlan,
    detailVisible: Boolean,
    detailNavState: PredictiveNavLayerState,
    onDismissDetail: () -> Unit,
    detailContent: @Composable () -> Unit,
    firstWinCount: Int?,
    overviewViewModel: OverviewViewModel,
    signInViewModel: SignInViewModel,
    qqGiftViewModel: QqGiftViewModel,
    xinyueViewModel: XinyueViewModel,
    historyViewModel: HistoryViewModel,
    mapViewModel: MapDistributionViewModel,
    gameConfig: com.nzd.antigravitypanel.data.remote.dto.GameConfigDto,
    pinned: Set<String>,
    favorite: Set<String>,
    cookie: NzCookie?,
    liquidGlassEnabled: Boolean,
    predictiveBackTranslation: Int,
    onRefresh: () -> Unit,
    settings: SettingsUiState,
    onAutoRefreshChange: (Int) -> Unit,
    onRetentionMonthsChange: (Int) -> Unit,
    onClearLocalData: () -> Unit,
    onImportJson: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { Tabs.COUNT })
    val coroutineScope = rememberCoroutineScope()

    // 顶栏的筛选菜单要用，跟 HistoryScreen 里那份是同一个 StateFlow，重复 collect 没有副作用
    val historyFilter by historyViewModel.filter.collectAsState()
    val historyAllMatches by historyViewModel.allMatches.collectAsState()

    val navigationItems = remember {
        listOf(
            NavigationItem(Tabs.TITLES[Tabs.OVERVIEW], MiuixIcons.Home),
            NavigationItem(Tabs.TITLES[Tabs.HISTORY], MiuixIcons.ListView),
            NavigationItem(Tabs.TITLES[Tabs.MAPS], MiuixIcons.MapAlbum),
            NavigationItem(Tabs.TITLES[Tabs.SETTINGS], MiuixIcons.Settings),
        )
    }

    // 每页一份滚动行为：切页时顶栏读的是当前页那份，
    // 不会出现"在战绩页滑到底、切到概览顶栏还是折叠的"这种错位。
    val overviewBehavior = MiuixScrollBehavior()
    val historyBehavior = MiuixScrollBehavior()
    val mapsBehavior = MiuixScrollBehavior()
    val settingsBehavior = MiuixScrollBehavior()
    val behaviors = remember(
        overviewBehavior,
        historyBehavior,
        mapsBehavior,
        settingsBehavior,
    ) {
        listOf(overviewBehavior, historyBehavior, mapsBehavior, settingsBehavior)
    }

    // 磨砂的宿主必须包在 Scaffold 外面：它负责开离屏 layer，
    // 顶栏和页面内容都从同一块 layer 上取像素。
    BarBlurHost(
        enabled = liquidGlassEnabled,
        // 返回手势期间要虚化背景，那一层得采样当前屏幕内容。
        // 就算用户关了磨砂，这里也照样得开离屏 layer，否则虚化退化成一层纯色。
        captureForEffects = detailNavState.requiresBackdropCapture(detailVisible),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                // 顶栏读 settledPage 而不是 currentPage：currentPage 在跨页动画
                // **走到一半**时就翻到相邻那页了，顶栏会跟着重建一次。
                // 历史战绩那个筛选菜单不便宜（要按全量对局算地图下拉），滚一趟重建两遍
                // 没必要；而且标题中途闪一下"历史战绩"再变"地图分布"也难看。
                // settledPage 只在停稳后才变，正好是"该换标题"的时刻。
                val page = pagerState.settledPage
                val title = if (page == Tabs.OVERVIEW) Tabs.APP_NAME else Tabs.TITLES[page]
                BlurredBar(topGradient = true) {
                    // TopAppBar（不是 SmallTopAppBar）才有大标题：页面在顶部时标题单独占一行、
                    // 不和右侧按钮挤在一起，下滑后才收进工具条。
                    TopAppBar(
                        title = title,
                        largeTitle = title,
                        color = Color.Transparent,
                        scrollBehavior = behaviors[page],
                        actions = {
                            when (page) {
                                Tabs.OVERVIEW -> {
                                    // 首胜宝箱在刷新按钮**左边**：它是"看一眼"的信息，
                                    // 刷新是要点的操作，主操作放最右更符合右手习惯
                                    FirstWinChestButton(
                                        count = firstWinCount,
                                        onClick = onOpenChestDialog,
                                    )
                                    IconButton(onClick = onRefresh) {
                                        Icon(
                                            imageVector = MiuixIcons.Refresh,
                                            contentDescription = "刷新数据",
                                            tint = MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                }

                                // 筛选改成级联下拉菜单，五个维度各一个子菜单。
                                // 展开状态由组件自己管，不用再在外面存一个 "filterSheet" 布尔值。
                                Tabs.HISTORY -> HistoryFilterMenu(
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
                        },
                    )
                }
            },
            bottomBar = {
                LiquidNavigationBar(
                    items = navigationItems,
                    // 底栏反过来要读 currentPage：指示器得**跟着滑**才有手感。
                    // 用 settledPage 的话，跨页动画跑完之前指示器一直钉在旧的那一格，
                    // 看着像点了没反应。currentPage 中途翻到相邻页只是让指示器多走一段，
                    // 不会再引发切页——底栏组件里那条"回灌即回调"的路已经拿掉了。
                    selectedIndex = pagerState.currentPage,
                    onItemClick = { index ->
                        // 已经在这一页就别再滚一次：既省一次动画，也避免打断正在跑的那次
                        if (index != pagerState.settledPage) {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        }
                    },
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                )
            },
        ) { innerPadding ->
            // 关键：这一层铺满整屏且**不裁掉顶栏那一条**，内容滚动时会从顶栏底下穿过去，
            // 顶栏的 progressiveTextureBlur 才有东西可糊。页面自己用 contentPadding 躲开顶栏。
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // 与 HyperIsland 一致：多留一页在视口外，切过去时不用临时现组
                    beyondViewportPageCount = 1,
                ) { page ->
                    // 滚动行为挂在页面外层：LazyColumn 的滚动增量会沿嵌套滚动链冒泡到这里，
                    // 顶栏据此决定大标题收不收、磨砂出不出。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(behaviors[page].nestedScrollConnection),
                    ) {
                        when (page) {
                            Tabs.OVERVIEW -> OverviewScreen(
                                viewModel = overviewViewModel,
                                signInViewModel = signInViewModel,
                                qqGiftViewModel = qqGiftViewModel,
                                xinyueViewModel = xinyueViewModel,
                                onOpenSignIn = onOpenSignIn,
                                onOpenAccountDetail = onOpenAccountDetail,
                                onOpenAllActivities = {
                                    onOpenAllActivities()
                                },
                                onOpenBuildPlan = onOpenBuildPlan,
                                plan = buildPlan,
                                insets = innerPadding,
                            )

                            Tabs.HISTORY -> HistoryScreen(
                                viewModel = historyViewModel,
                                config = gameConfig,
                                pinned = pinned,
                                favorite = favorite,
                                insets = innerPadding,
                                onOpenDetail = { roomId ->
                                    onOpenMatchDetail(roomId)
                                },
                            )

                            Tabs.MAPS -> MapDistributionScreen(
                                viewModel = mapViewModel,
                                insets = innerPadding,
                            )

                            Tabs.SETTINGS -> SettingsScreen(
                                state = settings,
                                onAutoRefreshChange = onAutoRefreshChange,
                                onRetentionMonthsChange = onRetentionMonthsChange,
                                onClearLocalData = onClearLocalData,
                                onOpenAbout = onOpenAbout,
                                onOpenTheme = onOpenTheme,
                                onImportJson = onImportJson,
                                insets = innerPadding,
                            )
                        }
                    }
                }
            }
        }

        // ---- 二级详情页图层 ----
        // 必须放在 Scaffold **之后**：底栏是浮在上面的，写进 Scaffold 的 content
        // 里会被底栏压住，二级页右下角就露出一条导航条。
        // 虚化层夹在中间（一级内容之上、二级页之下），手势返回时才能看到背景糊掉。
        PredictiveNavBackdrop(
            state = detailNavState,
            modifier = Modifier.fillMaxSize(),
        )
        PredictiveNavLayer(
            visible = detailVisible,
            state = detailNavState,
            // 最大横移距离可调（设置 → 外观 → 主题 → 预测返回距离）
            maxTranslationPercent = predictiveBackTranslation.toLong(),
        ) {
            detailContent()
        }
        PredictiveNavBackHandler(
            visible = detailVisible,
            enabled = detailVisible,
            state = detailNavState,
            onDismiss = onDismissDetail,
        )
    }
}

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
                imageVector = MiuixIcons.TopDownloads,
                contentDescription = "每日首胜宝箱",
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

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
        Column {
            val body = when {
                count == null -> "登录后才能看到今天还能领几个。"
                count >= OverviewRepository.FIRST_WIN_CHEST_LIMIT ->
                    "次数已满：$count / ${OverviewRepository.FIRST_WIN_CHEST_LIMIT}"

                count > 0 -> "还能领 $count / ${OverviewRepository.FIRST_WIN_CHEST_LIMIT} 个。"
                else -> "今天的首胜宝箱已经领完了（0 / " +
                    "${OverviewRepository.FIRST_WIN_CHEST_LIMIT}）。"
            }
            Text(
                text = body,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "数量来自官方的每日首胜接口，每个模式每天首次通关会补充，攒到 " +
                    "${OverviewRepository.FIRST_WIN_CHEST_LIMIT} 个就不再增加。",
                modifier = Modifier.padding(top = 12.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
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
