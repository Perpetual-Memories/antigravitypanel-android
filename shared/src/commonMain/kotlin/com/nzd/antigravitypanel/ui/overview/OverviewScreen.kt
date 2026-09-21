package com.nzd.antigravitypanel.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.repo.OverviewMode
import com.nzd.antigravitypanel.data.repo.RecentFive
import com.nzd.antigravitypanel.data.repo.countOf
import com.nzd.antigravitypanel.data.repo.formatPlaytime
import com.nzd.antigravitypanel.domain.ActivityEvent
import com.nzd.antigravitypanel.domain.soonestActivities
import com.nzd.antigravitypanel.ui.component.ActivityRow
import com.nzd.antigravitypanel.ui.component.InfoText
import com.nzd.antigravitypanel.ui.component.StatCard
import com.nzd.antigravitypanel.ui.component.statusGlyphPainter
import com.nzd.antigravitypanel.ui.format.formatCompact
import com.nzd.antigravitypanel.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 概览页。布局照 HyperIsland 的 `OverviewPage`：
 * 顶部状态卡（左侧凭证状态 + 右侧两个数据卡）、下方指标卡、活动日历。
 *
 * 刷新按钮不在这里——它在宿主 Scaffold 的 TopAppBar 右侧，和 HyperIsland 一样
 * 是跟着"应用名"标题走的，见 `AppContent` 里 page==0 时的 actions。
 *
 * @param insets Scaffold 给的顶栏 / 底栏高度。列表**不裁掉**这块区域（内容要从
 *   顶栏底下穿过去给磨砂采样），而是靠 `contentPadding` 把首项顶到顶栏下面。
 */
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onOpenAccountDetail: () -> Unit,
    onOpenAllActivities: () -> Unit,
    modifier: Modifier = Modifier,
    insets: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.state.collectAsState()
    var selectedEvent by remember { mutableStateOf<ActivityEvent?>(null) }

    val recent = state.recent

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .overScrollVertical(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp + insets.calculateTopPadding(),
            end = 12.dp,
            bottom = 12.dp + insets.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "status") {
            StatusGrid(
                status = state.cookieStatus,
                mode = state.mode,
                modeCount = state.stats.countOf(state.mode),
                playtimeSec = state.stats.playtime,
                loading = state.loading,
                verifying = state.cookieVerifying,
                onOpenAccountDetail = onOpenAccountDetail,
                onModeChange = viewModel::setMode,
            )
        }

        if (state.error != null) {
            item(key = "error") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = state.error ?: "",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        item(key = "recent") {
            SmallTitle(text = "近五场数据统计")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (recent == null) {
                        Text(
                            text = when {
                                state.loading -> "正在统计…"
                                else -> "还没有可统计的猎场对局"
                            },
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        InfoGrid(items = recentMetrics(recent))
                    }
                }
            }
        }

        item(key = "calendar") {
            SmallTitle(text = "活动日历")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
            ) {
                // 概览只放快截止的三场，剩下的进「更多活动」那页
                val shown = soonestActivities(state.activities)
                if (state.activities.isEmpty()) {
                    // 解析失败是静默的（它不该让整页挂掉），但"静默"很容易变成
                    // "永远查不出为什么是空的"。登录了却一条都没有，说明多半是接口结构变了，
                    // 把话说出来，别让用户以为游戏真的没活动。
                    Text(
                        text = when {
                            state.localOnly -> "活动日历需要登录后才会拉取"
                            state.cookieStatus == CookieStatus.OK ->
                                "没能读到活动，可能是接口结构变了"
                            else -> "暂无进行中或即将开始的活动"
                        },
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    Column {
                        shown.forEach { event ->
                            ActivityRow(
                                event = event,
                                onClick = { selectedEvent = event },
                            )
                        }
                        // 还有更多就给一个跳转入口，概览不该为了塞下全部活动无限拉长。
                        // 「更多」紧贴在最后一条下面，中间不再插线。
                        if (state.activities.size > shown.size) {
                            MoreActivitiesRow(
                                total = state.activities.size,
                                onClick = onOpenAllActivities,
                            )
                        }
                    }
                }
            }
        }
    }

    selectedEvent?.let { event ->
        WindowDialog(
            show = true,
            title = event.title,
            onDismissRequest = { selectedEvent = null },
        ) {
            Column {
                Text(
                    text = event.periodText(),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Button(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    onClick = { selectedEvent = null },
                ) {
                    Text("好的")
                }
            }
        }
    }
}

/**
 * 近五场显示哪几格，取决于手上有什么数据。
 *
 * 登录态下四项都有；本地库（导入的 JSON）里没有 Boss 伤害和金币，
 * 那两格整个不出现 —— 补成 0 会让人误读，宁可少两格。
 * 击杀 / 胜场反过来只在本地统计里有，所以顺序也跟着变。
 */
private fun recentMetrics(recent: RecentFive): List<Pair<String, String>> = buildList {
    recent.avgBossDamage?.let { add("场均 Boss 伤害" to formatCompact(it)) }
    add("MVP 次数" to "${recent.mvpCount} 次")
    add("场均评分" to formatCompact(recent.avgScore))
    recent.avgCoin?.let { add("场均金币" to formatCompact(it)) }
    recent.avgKills?.let { add("场均击杀" to "$it") }
    recent.winCount?.let { add("胜场" to "$it / ${recent.sampleCount}") }
}

/**
 * 两列指标网格。四行竖排在手机上会顶出一屏，两列刚好。
 *
 * 行距只加在**行与行之间**：最后一行再垫一份 bottom，卡片底部就会多出一整块空白
 * （原来每格自己还带 16dp，等于末行下面堆了 28dp），看着像"多出来一块白色区域"。
 */
@Composable
private fun InfoGrid(items: List<Pair<String, String>>) {
    val rows = items.chunked(2)
    Column {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = if (index != rows.lastIndex) {
                    Modifier.padding(bottom = 12.dp)
                } else {
                    Modifier
                },
            ) {
                for ((title, value) in row) {
                    InfoText(
                        title = title,
                        content = value,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 第三条活动下面那条「更多」。 */
@Composable
private fun MoreActivitiesRow(
    total: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = Color.Transparent),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "更多活动",
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary,
            )
            Text(
                text = "共 $total 场",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 6.dp).size(16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 顶部状态区。窄屏：左侧凭证卡占方形，右侧两个数据卡等分；
 * 宽屏（>=600dp）三张卡横排等高。
 */
@Composable
private fun StatusGrid(
    status: CookieStatus,
    mode: OverviewMode,
    modeCount: Int,
    playtimeSec: Long,
    loading: Boolean,
    verifying: Boolean,
    onOpenAccountDetail: () -> Unit,
    onModeChange: (OverviewMode) -> Unit,
) {
    var modeMenu by remember { mutableStateOf(false) }
    val modeEntries = remember(mode, onModeChange) {
        listOf(
            DropdownEntry(
                items = OverviewMode.entries.map { candidate ->
                    DropdownItem(
                        text = candidate.label,
                        selected = candidate == mode,
                        onClick = {
                            onModeChange(candidate)
                            modeMenu = false
                        },
                    )
                },
            ),
        )
    }

    val height = 168.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CookieStatusCard(
            status = status,
            loading = loading,
            verifying = verifying,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onClick = onOpenAccountDetail,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 下拉要锚在卡片上，所以套一层 Box：Miuix 的 popup 是拿"调用处的父布局"
            // 当锚点的，直接写在 Column 里会锚到整个右半栏。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                StatCard(
                    title = "${mode.label}总局数",
                    value = "$modeCount 场",
                    modifier = Modifier.fillMaxSize(),
                    onClick = { modeMenu = true },
                )
                OverlayDropdownPopup(
                    entries = modeEntries,
                    show = modeMenu,
                    onDismiss = { modeMenu = false },
                    onDismissFinished = { modeMenu = false },
                    maxHeight = 320.dp,
                    dropdownColors = DropdownDefaults.dropdownColors(),
                    renderInRootScaffold = true,
                )
            }
            StatCard(
                title = "在线时长",
                value = formatPlaytime(playtimeSec),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

/**
 * 凭证状态卡。版式照 HyperIsland 概览页的 `StatusCard`：
 * 左上大号加粗状态文案 + 下方一行说明小字，右下角压一个被裁掉一角的半透明状态符号。
 *
 * 文案顺序和 HyperIsland 相反（它下面是版本号，我们下面是"Cookie 状态"）——
 * 一眼要看的是"现在能不能用"，所以状态文案拿最大字号。
 */
@Composable
private fun CookieStatusCard(
    status: CookieStatus,
    loading: Boolean,
    verifying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (foreground, background, textColor) = cookieStatusColors(status)
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = background),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 大号符号故意超出卡片边界：只露出右下角一块，和 HyperIsland 一样
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(110.dp),
                    painter = statusGlyphPainter(status.glyph),
                    contentDescription = null,
                    tint = foreground.copy(alpha = 0.78f),
                )
            }
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = if (loading && status == CookieStatus.OK) "同步中…" else status.label,
                    // 「已导入json」六个字在半屏宽的卡里 24sp 会顶出去，长文案降一档
                    fontSize = if (status.label.length >= 6) 22.sp else 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                Text(
                    text = "Cookie 状态",
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                )
            }
            if (verifying) {
                SyncingScrim()
            }
        }
    }
}

/**
 * 「同步中」遮罩：半透明中性灰铺满整张卡，中间竖排一行文字 + 一个转圈。
 *
 * 用在冷启动那一下——卡片显示的是**上次缓存下来的状态**（比如「已识别」），
 * 但这一轮的凭证还没被服务端确认过，直接显示等于拿旧结论冒充新结论。
 * 盖一层灰 + 转圈，确认完了（成功或失败都算）由 ViewModel 把
 * `cookieVerifying` 置回 false，遮罩自己消失，状态该改成什么就改成什么。
 *
 * 用中性灰而不是主题色：卡片本身可能是绿 / 红 / 黄任意一种，
 * 只有不含色相的灰才能在任何一张上都读作"暂时不可用"。
 */
@Composable
private fun SyncingScrim() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SYNCING_SCRIM_COLOR),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "同步中",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            CircularProgressIndicator(
                modifier = Modifier.padding(top = 10.dp),
                // 不传 progress：不确定进度的转圈，同步多久是服务端说了算的
                progress = null,
                size = 26.dp,
                strokeWidth = 3.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = Color.White,
                    backgroundColor = Color.White.copy(alpha = 0.28f),
                ),
            )
        }
    }
}

/** 遮罩底色：80% 不透明的中性灰。再淡就压不住红卡，再浓就看不见底下是什么状态了。 */
private val SYNCING_SCRIM_COLOR = Color(0xCC545454)

/**
 * 凭证状态配色。取自 HyperIsland 概览页的同一组值：
 * 绿=正常、红=未输入、浅黄=过期、琥珀黄=已导入json，深色模式下换一组更沉的底色。
 *
 * 「已导入json」用琥珀而不是和「过期」共用浅黄：两者都表示"拿不到服务端数据"，
 * 但过期是要用户去重新抓 cookie 的告警，导入是用户自己选的状态，不该长得一样。
 *
 * 第三项是卡片文案色。HyperIsland 在浅色底上直接用 `0xFF101010` 纯黑，
 * 但我们深色模式的卡片底色是压暗的，黑字会糊成一片，所以深色下退成状态色本身。
 */
@Composable
private fun cookieStatusColors(status: CookieStatus): Triple<Color, Color, Color> {
    val dark = isInDarkTheme()
    val (foreground, background) = when (status) {
        CookieStatus.OK -> if (dark) {
            Color(0xFF36D167) to Color(0xFF14331F)
        } else {
            Color(0xFF0E7A33) to Color(0xFFDFFAE4)
        }

        CookieStatus.EXPIRED -> if (dark) {
            Color(0xFFFFD978) to Color(0xFF3A2D12)
        } else {
            Color(0xFF704D00) to Color(0xFFFFF3D6)
        }

        CookieStatus.IMPORTED_JSON -> if (dark) {
            Color(0xFFFFB13D) to Color(0xFF3A2708)
        } else {
            Color(0xFF8A5300) to Color(0xFFFFEDCE)
        }

        CookieStatus.MISSING -> if (dark) {
            Color(0xFFFF5A52) to Color(0xFF3A1A18)
        } else {
            Color(0xFFB3261E) to Color(0xFFFFE5E3)
        }
    }
    return Triple(foreground, background, if (dark) foreground else Color(0xFF101010))
}
