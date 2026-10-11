package com.nzd.antigravitypanel.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
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
import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
import com.nzd.antigravitypanel.data.signin.SignInStatus
import com.nzd.antigravitypanel.ui.component.BarBackdropContent
import com.nzd.antigravitypanel.ui.component.BarBlurHost
import com.nzd.antigravitypanel.ui.component.BlurredBar
import com.nzd.antigravitypanel.ui.component.InfoGrid
import com.nzd.antigravitypanel.ui.component.TopBarGlassAnchor
import com.nzd.antigravitypanel.ui.component.TopBarGlassBackButton
import com.nzd.antigravitypanel.ui.component.TopBarGlassPlaceholder
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 签到明细页。概览那张卡点开进来。
 *
 * 骨架照「活动日历」那页：`BarBlurHost` → `Scaffold(BlurredBar + SmallTopAppBar)`
 * → `BarBackdropContent` → `PullToRefresh` → `LazyColumn`。
 *
 * 三块签到各占一组（`SmallTitle` + `Card`）：小程序活动中心签到 / QQ游戏中心周签到礼包 /
 * 悦享卡每日礼包。**三块的开关都收在各自卡片点开的弹层里**，页面上只留状态与数字。
 *
 * 手动签到按钮放在这里而不是概览那张卡上：绝大多数情况下打开 app 那一下就自动签掉了，
 * 卡片上的按钮反而是个误导（点了也是"今天已经签到过了"）。只有自动签到因为网络
 * 失败漏掉时，用户才需要来这里补一下。
 */
@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    qqViewModel: QqGiftViewModel,
    xinyueViewModel: XinyueViewModel,
    autoSign: Boolean,
    autoClaimTask: Boolean,
    autoClaimQqGift: Boolean,
    autoClaimXinyueGift: Boolean,
    cookie: MiniProgramCredential?,
    onBack: () -> Unit,
    onAutoSignChange: (Boolean) -> Unit,
    onAutoClaimTaskChange: (Boolean) -> Unit,
    onAutoClaimQqGiftChange: (Boolean) -> Unit,
    onAutoClaimXinyueGiftChange: (Boolean) -> Unit,
    liquidGlassEnabled: Boolean = true,
) {
    val state by viewModel.state.collectAsState()
    val qqState by qqViewModel.state.collectAsState()
    val xinyueState by xinyueViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    var welfareSheet by remember { mutableStateOf(false) }
    // 返回键玻璃的定位锚点：位置由顶栏槽位里那个占位上报。
    // 玻璃必须画在顶栏**外面**那层不被裁的 overlay 上 —— Miuix 顶栏自带
    // clipToBounds，溢出到 42dp 之外的阴影会被切成一条直边。
    val backGlassAnchor = remember { TopBarGlassAnchor() }

    BarBlurHost(enabled = liquidGlassEnabled) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                BlurredBar(
                    topGradient = true,
                    startGlassAnchor = backGlassAnchor,
                    startGlass = {
                        TopBarGlassBackButton(
                            onBack = onBack,
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) {
                    SmallTopAppBar(
                        title = "每日签到",
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { TopBarGlassPlaceholder(backGlassAnchor) },
                    )
                }
            },
        ) { innerPadding ->
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                val contentTop = 12.dp + innerPadding.calculateTopPadding()
                val contentBottom = 12.dp + innerPadding.calculateBottomPadding()
                val contentPadding = remember(contentTop, contentBottom) {
                    PaddingValues(
                        start = 12.dp,
                        top = contentTop,
                        end = 12.dp,
                        bottom = contentBottom,
                    )
                }
                PullToRefresh(
                    isRefreshing = state.loading || qqState.loading || xinyueState.loading,
                    onRefresh = {
                        // 领取时机是明确的：**冷启动和每一次刷新都要试**。
                        // 「每日完成1局」白天才达成，下拉刷新不试的话只能杀掉 app 重开。
                        // 已经领完的压根不会发领取请求（服务端 isawarded 已经是 true），
                        // 所以这里带上两个开关没有副作用。
                        viewModel.refresh(
                            cookie = cookie,
                            autoSign = autoSign,
                            autoClaimTask = autoClaimTask,
                        )
                        // 下面两块是另两套凭证，用户没要求它们跟着下拉一起领，保持只刷状态
                        qqViewModel.refresh(autoClaim = false)
                        xinyueViewModel.refresh(autoClaim = false)
                    },
                    pullToRefreshState = pullToRefreshState,
                    topAppBarScrollBehavior = scrollBehavior,
                    contentPadding = contentPadding,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            // 甩到列表顶 / 底时的一记轻振动（NexioSchedule 同款）
                            .scrollEndHaptic()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        // contentPadding 必须也传给 LazyColumn：PullToRefresh 拿它算下拉的起点，
                        // 列表自己这份才是真正把首项顶到顶栏下面的
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            SmallTitle(text = "小程序活动中心签到")
                            TodayCard(
                                status = state.status,
                                available = state.available,
                                loading = state.loading,
                                signing = state.signing,
                                hasCookie = cookie != null,
                                notice = state.notice,
                                onSign = { viewModel.signNow(cookie) },
                                onOpenSettings = { welfareSheet = true },
                            )
                        }

                        if (state.error != null && !state.available) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.defaultColors(
                                        color = MiuixTheme.colorScheme.surfaceContainer,
                                    ),
                                ) {
                                    Text(
                                        text = state.error ?: "",
                                        modifier = Modifier.padding(16.dp),
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.error,
                                    )
                                }
                            }
                        }

                        item {
                            QqGiftSection(
                                status = qqState.status,
                                bound = qqState.bound,
                                available = qqState.available,
                                loading = qqState.loading,
                                claiming = qqState.claiming,
                                error = qqState.error,
                                notice = qqState.notice,
                                credentialRaw = qqState.credentialRaw,
                                autoClaim = autoClaimQqGift,
                                onClaim = qqViewModel::claim,
                                onSaveCredential = { raw ->
                                    scope.launch { qqViewModel.saveCredential(raw) }
                                },
                                onAutoClaimChange = {
                                    scope.launch { onAutoClaimQqGiftChange(it) }
                                },
                                onClearCredential = {
                                    scope.launch { qqViewModel.clearCredential() }
                                },
                                onImportHar = { text ->
                                    scope.launch { qqViewModel.importFromHar(text) }
                                },
                                onConsumeNotice = qqViewModel::consumeNotice,
                            )
                        }

                        item {
                            XinyueSection(
                                status = xinyueState.status,
                                bound = xinyueState.bound,
                                available = xinyueState.available,
                                loading = xinyueState.loading,
                                claiming = xinyueState.claiming,
                                error = xinyueState.error,
                                notice = xinyueState.notice,
                                credentialRaw = xinyueState.credentialRaw,
                                autoClaim = autoClaimXinyueGift,
                                onClaim = xinyueViewModel::claim,
                                onSaveCredential = { raw ->
                                    scope.launch { xinyueViewModel.saveCredential(raw) }
                                },
                                onAutoClaimChange = {
                                    scope.launch { onAutoClaimXinyueGiftChange(it) }
                                },
                                onClearCredential = {
                                    scope.launch { xinyueViewModel.clearCredential() }
                                },
                                onImportHar = { text ->
                                    scope.launch { xinyueViewModel.importFromHar(text) }
                                },
                                onConsumeNotice = xinyueViewModel::consumeNotice,
                            )
                        }

                    }
                }
            }
        }
    }

    WelfareAutoSheet(
        show = welfareSheet,
        autoSign = autoSign,
        autoClaimTask = autoClaimTask,
        onAutoSignChange = { scope.launch { onAutoSignChange(it) } },
        onAutoClaimTaskChange = { scope.launch { onAutoClaimTaskChange(it) } },
        onDismiss = { welfareSheet = false },
    )
}

/**
 * 「小程序活动中心签到」那张卡。点它打开「自动签到 / 自动领任务奖励」的弹层。
 *
 * 标题和四格指标**合并在一张卡里**：原来标题一张、指标一张，中间那道缝让人以为是
 * 两个互不相干的东西，而且两块卡各自的 16dp 内边距在接缝处堆出 32dp，看着像多空了一行。
 *
 * 标题下面**不加小字摘要**——下面四格已经把连续天数、累计天数、本月进度全说了，
 * 再来一行"连续 3 天 · 累计 28 天"是同一份信息说两遍。
 */
@Composable
private fun TodayCard(
    status: SignInStatus,
    available: Boolean,
    loading: Boolean,
    signing: Boolean,
    hasCookie: Boolean,
    notice: String?,
    onSign: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onOpenSettings,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        loading && !available -> "同步中…"
                        !available -> if (hasCookie) "还没拿到签到状态" else "未登录"
                        status.signedToday -> "今天已签到"
                        else -> "今天还没签到"
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (available && status.signedToday) {
                        signInAccentColor()
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                )
                // 当前积分总数。和「今天已签到」同一行、靠右、**黑体加粗**：
                // 它是这块唯一一个"越攒越多"的数，配得上和状态同级的视觉重量。
                // 拿不到（null）就整块不摆——显示一个 0 会被读成"一分没有"。
                if (status.totalScore != null) {
                    Text(
                        text = "${status.totalScore} 积分",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                if (signing) {
                    CircularProgressIndicator(
                        progress = null,
                        size = 22.dp,
                        strokeWidth = 3.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = MiuixTheme.colorScheme.primary,
                            backgroundColor = MiuixTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }

            // 自动领到东西了说一句：这件事没有按钮也没有入口，全是开 app 那一下做的，
            // 不说的话用户只看到积分涨了，不知道是从哪来的。
            if (notice != null) {
                Text(
                    text = notice,
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 13.sp,
                    color = signInAccentColor(),
                )
            }

            if (available && status.date.isNotBlank()) {
                InfoGrid(
                    items = buildList {
                        add("连续签到" to "${status.continuousDays} 天")
                        add(
                            "累计签到" to if (status.totalTarget > 0) {
                                "${status.totalDays} / ${status.totalTarget}"
                            } else {
                                "${status.totalDays} 天"
                            },
                        )
                        add(
                            "本月" to if (status.monthTarget > 0) {
                                "${status.monthDays} / ${status.monthTarget}"
                            } else {
                                "${status.monthDays} 天"
                            },
                        )
                        add(
                            "统计周期" to if (status.periodStart.isNotBlank()) {
                                "${status.periodStart} 起"
                            } else {
                                "—"
                            },
                        )
                    },
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            // 没签到才给按钮：已经签了还摆一个"签到"是纯误导
            if (available && hasCookie && !status.signedToday) {
                Button(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth(),
                    onClick = onSign,
                    enabled = !signing,
                ) {
                    Text("立即签到")
                }
            }
        }
    }
}
