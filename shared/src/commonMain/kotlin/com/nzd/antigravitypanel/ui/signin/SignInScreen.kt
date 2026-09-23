package com.nzd.antigravitypanel.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 签到明细页。概览那张卡点开进来。
 *
 * 骨架照「活动日历」那页：`BarBlurHost` → `Scaffold(BlurredBar + SmallTopAppBar)`
 * → `BarBackdropContent` → `PullToRefresh` → `LazyColumn`。
 *
 * 三块签到各占一组（`SmallTitle` + `Card`）：小程序活动中心签到 / QQ游戏中心周签到礼包 /
 * 悦享卡每日礼包。后两块的**凭证和开关收在卡片点开的弹层里**，页面上只留状态与数字。
 *
 * 手动签到按钮放在这里而不是卡片上：绝大多数情况下打开 app 那一下就自动签掉了，
 * 卡片上的按钮反而是个误导（点了也是"今天已经签到过了"）。只有自动签到因为网络
 * 失败漏掉时，用户才需要来这里补一下。
 */
@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    qqViewModel: QqGiftViewModel,
    xinyueViewModel: XinyueViewModel,
    autoClaimQqGift: Boolean,
    autoClaimXinyueGift: Boolean,
    cookie: MiniProgramCredential?,
    onBack: () -> Unit,
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

    BarBlurHost(enabled = liquidGlassEnabled) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                BlurredBar(topGradient = true) {
                    SmallTopAppBar(
                        title = "每日签到",
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = "返回",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        },
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
                        viewModel.refreshBoardOnly(cookie)
                        // 只刷状态、不顺手领：下拉是"刷新"，不是"领取"
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
                                onSign = { viewModel.signNow(cookie) },
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
                                onConsumeNotice = xinyueViewModel::consumeNotice,
                            )
                        }

                    }
                }
            }
        }
    }
}

/**
 * 「小程序活动中心签到」那张卡。
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
    onSign: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
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
