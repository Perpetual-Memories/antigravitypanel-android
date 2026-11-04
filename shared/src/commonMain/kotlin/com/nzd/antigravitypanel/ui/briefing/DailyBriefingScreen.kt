package com.nzd.antigravitypanel.ui.briefing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.domain.DailyBriefing
import com.nzd.antigravitypanel.domain.MatchTag
import com.nzd.antigravitypanel.domain.briefingDays
import com.nzd.antigravitypanel.domain.buildDailyBriefing
import com.nzd.antigravitypanel.domain.difficultyNameOf
import com.nzd.antigravitypanel.domain.mapNameOf
import com.nzd.antigravitypanel.domain.modeOf
import com.nzd.antigravitypanel.ui.component.HintBlock
import com.nzd.antigravitypanel.ui.component.InfoGrid
import com.nzd.antigravitypanel.ui.component.StatCard
import com.nzd.antigravitypanel.ui.component.mapArtPainter
import com.nzd.antigravitypanel.ui.format.formatBriefingPlaytime
import com.nzd.antigravitypanel.ui.format.formatDuration
import com.nzd.antigravitypanel.ui.format.formatHourRange
import com.nzd.antigravitypanel.ui.format.formatPercent
import com.nzd.antigravitypanel.ui.format.formatScore
import com.nzd.antigravitypanel.ui.format.formatTimeShort
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Snackbar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 每日简报。版式照 PC 端 v2.0.0 的「每日简报」页：
 *
 * ```
 * [今天][昨天][11月2日] … 7 天（Miuix TabRow，横向滚动，不可选日历）
 * ┌ 场次 ┐ ┌ 最活跃时段 ┐
 * └ 时长 ┘ └ 模式分布   ┘
 * ┌ 对局时间线 ─────────────────────┐
 * │ 06:22 ● ▮ 精绝古城 [折磨]  胜利 │
 * │        僵尸猎场 · 用时 19:34 1090万│
 * └────────────────────────────────┘
 * ```
 *
 * 四张卡都能点：场次 / 活跃时段 / 模式分布开底部弹层，游戏时长弹一条 Snackbar。
 * 弹层里的数字全部来自 [buildDailyBriefing] 这一份汇总，UI 不另算一遍。
 *
 * 数据来自本地库（同步链路已经在冷启动 / 手动刷新时把三个模式灌进去了），
 * 所以切日期是纯内存计算，不转圈、不联网。
 *
 * @param matchesFlow 本地全量对局。传 Flow 而不是 List：这个页面在 Pager 的
 *   内容 lambda 里，外面读好再传进来会钉在组合时那一帧（见 AppContent 里同款注释）。
 * @param tagsFlow 每局的实力局 / 带飞局标签。这两个判据要看全队 Boss 伤害，
 *   只有详情缓存里才有，所以是"已缓存的那部分"的计数。
 * @param insets 顶栏 + 悬浮底栏高度，只进 contentPadding。
 */
@Composable
fun DailyBriefingScreen(
    matchesFlow: StateFlow<List<MatchEntity>>,
    tagsFlow: StateFlow<Map<String, MatchTag>>,
    config: GameConfigDto,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    insets: PaddingValues = PaddingValues(0.dp),
) {
    val matches by matchesFlow.collectAsState()
    val tags by tagsFlow.collectAsState()

    // ⚠️ 只取一次"现在"：日期条在页面存活期间不该自己变。真跨过零点的话，
    // 用户下拉刷新 / 切回这一页会重新组合，届时拿到新的那一天。
    val days = remember { briefingDays(currentEpochSeconds()) }
    // 用 rememberSaveable 记住选的是哪一天：切去别的 Tab 再回来，
    // 停在用户刚才看的那天比跳回「今天」更符合预期。
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val day = days.getOrNull(selectedIndex) ?: days.firstOrNull() ?: return

    val briefing = remember(matches, day.startSec) {
        buildDailyBriefing(matches = matches, dayStartSec = day.startSec)
    }
    val strongCount = remember(briefing.matches, tags) {
        briefing.matches.count { tags[it.roomId] == MatchTag.STRONG }
    }
    val carryCount = remember(briefing.matches, tags) {
        briefing.matches.count { tags[it.roomId] == MatchTag.CARRY }
    }

    var sheet by remember { mutableStateOf(BriefingSheet.NONE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 12.dp + insets.calculateTopPadding(),
                end = 12.dp,
                bottom = 12.dp + insets.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "days") {
                TabRow(
                    tabs = days.map { it.label },
                    selectedTabIndex = selectedIndex,
                    onTabSelected = { selectedIndex = it },
                    // ⚠️ Miuix 的 TabRow 是**所有格子等宽**的，宽度只按 min/max 取，不会量文字。
                    // 默认 minWidth = 76dp，减去左右各 12dp 内边距只剩 52dp，
                    // 「10月29日」这种五个字（两个全角 + 数字）放不下 ⇒ 末尾出省略号。
                    // 给到 104dp 才有 80dp 的文字区，正常字号 / 轻微放大都不裁字；
                    // 七格总宽超过屏宽时组件自己横向滚动（和 PC 端的日期条一致）。
                    minWidth = 104.dp,
                    maxWidth = 104.dp,
                    itemSpacing = 6.dp,
                )
            }

            item(key = "stats") {
                BriefingStats(
                    dayTitle = day.title,
                    briefing = briefing,
                    onMatchCountClick = { sheet = BriefingSheet.RECORDS },
                    onPlaytimeClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (briefing.matchCount > 0) {
                                    // 场均按"当天所有对局"平均：中途退出的那几分钟也是玩掉的
                                    "场均时长 ${formatDuration(briefing.playtimeSec / briefing.matchCount)}"
                                } else {
                                    "这一天还没有对局"
                                },
                            )
                        }
                    },
                    onActiveHourClick = { sheet = BriefingSheet.ACTIVE_HOURS },
                    onModeClick = { sheet = BriefingSheet.MODES },
                )
            }

            item(key = "timeline") {
                TimelineCard(
                    briefing = briefing,
                    config = config,
                    // 整库为空 ≠ 这一天没打：前者要么没登录、要么还没同步，
                    // 给同一句"这一天没有对局"会让人以为数据丢了。
                    // 顺手把本地总场次写出来 —— 排查"整页全 0"时这一句就能分清
                    // 是"库里没有"还是"这一天的筛选把它漏了"。
                    emptyHint = if (matches.isEmpty()) {
                        "还没有对局数据，先在概览页登录或导入数据"
                    } else {
                        "这一天没有对局（本地共 ${matches.size} 场）"
                    },
                    onOpenDetail = onOpenDetail,
                )
            }
        }

        // 底栏是浮在内容上的，Snackbar 要抬到它上面。
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        ) { data -> Snackbar(data) }
    }

    RecordsSheet(
        show = sheet == BriefingSheet.RECORDS,
        dayTitle = day.title,
        briefing = briefing,
        strongCount = strongCount,
        carryCount = carryCount,
        onDismiss = { sheet = BriefingSheet.NONE },
    )
    ActiveHoursSheet(
        show = sheet == BriefingSheet.ACTIVE_HOURS,
        dayTitle = day.title,
        briefing = briefing,
        onDismiss = { sheet = BriefingSheet.NONE },
    )
    ModesSheet(
        show = sheet == BriefingSheet.MODES,
        dayTitle = day.title,
        briefing = briefing,
        onDismiss = { sheet = BriefingSheet.NONE },
    )
}

/** 当前打开的弹层。`NONE` 而不是 null：三个 `OverlayBottomSheet` 要各自拿到布尔值。 */
private enum class BriefingSheet { NONE, RECORDS, ACTIVE_HOURS, MODES }

/** 四张 1×2 的卡：左列场次 / 时长，右列最活跃时段 / 模式分布。 */
@Composable
private fun BriefingStats(
    dayTitle: String,
    briefing: DailyBriefing,
    onMatchCountClick: () -> Unit,
    onPlaytimeClick: () -> Unit,
    onActiveHourClick: () -> Unit,
    onModeClick: () -> Unit,
) {
    // 高度和概览页的状态区一致（168dp = 两行 78 + 12 间距），
    // 让两页的卡片在同一台机器上量出来一样高。
    val height = 168.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                title = "${dayTitle}场次",
                value = "${briefing.matchCount} 场",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onClick = onMatchCountClick,
            )
            StatCard(
                title = "游戏时长",
                value = formatBriefingPlaytime(briefing.playtimeSec),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onClick = onPlaytimeClick,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                title = "最活跃时段",
                value = formatHourRange(briefing.activeHour),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onClick = onActiveHourClick,
            )
            StatCard(
                title = "模式分布",
                // 卡片只放得下一行，给场次最多的那个模式；三个模式各几场在弹层里。
                value = briefing.topMode?.let { "${it.mode.label} · ${it.count}场" } ?: "—",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onClick = onModeClick,
            )
        }
    }
}

/**
 * 对局时间线。一整张卡片包住所有行，行与行之间靠左侧那条时间轴串起来。
 *
 * 视觉照 PC：时间 → 圆点（绿=胜、红=负）→ 缩略图 → 地图名+难度 → 模式/用时 →
 * 右侧胜负标签与积分。窄屏摆不下 PC 那种横向长条，所以积分和标签改成右侧上下两行，
 * 但阅读顺序不变（先看哪张图、再看结果、最后看分）。
 */
@Composable
private fun TimelineCard(
    briefing: DailyBriefing,
    config: GameConfigDto,
    emptyHint: String,
    onOpenDetail: (String) -> Unit,
) {
    val cardColor = MiuixTheme.colorScheme.surfaceContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = cardColor),
    ) {
        Column {
            Text(
                text = "对局时间线",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )

            if (briefing.matches.isEmpty()) {
                HintBlock(text = emptyHint)
            } else {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    briefing.matches.forEachIndexed { index, match ->
                        TimelineRow(
                            match = match,
                            config = config,
                            cardColor = cardColor,
                            isFirst = index == 0,
                            isLast = index == briefing.matches.lastIndex,
                            onClick = { onOpenDetail(match.roomId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    match: MatchEntity,
    config: GameConfigDto,
    cardColor: Color,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val mode = modeOf(match.mapId)
    val difficulty = difficultyNameOf(match.subModeType, config)
    val result = when {
        !match.finished -> TimelineResult("未完成", MiuixTheme.colorScheme.onSurfaceVariantSummary)
        match.isWin -> TimelineResult("胜利", MiuixTheme.colorScheme.primary)
        else -> TimelineResult("失败", MiuixTheme.colorScheme.error)
    }
    val railColor = MiuixTheme.colorScheme.dividerLine

    // 单行卡片而不是整块列表：Miuix 的按压反馈（Sink）只有 Card 才带，
    // 直接用 Modifier.clickable 出来的是方形水波纹，和整站的圆角卡片不搭。
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = Color.Transparent),
        cornerRadius = 12.dp,
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 让时间轴那一列能撑满整行高度：圆点和连线要跨过上下两行的间距
                .height(IntrinsicSize.Min)
                // ⚠️ 只留横向内边距。竖向内边距如果留在这层，时间轴会被上下各切掉
                // 8dp，两行之间就断开成一段段短竖线了 —— 竖向留白改由每个内容自己加。
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = formatTimeShort(match.eventTime),
                modifier = Modifier
                    .width(38.dp)
                    // 和圆点对齐：8dp 行内留白 + 4dp，中心落在 18dp 左右
                    .padding(top = 12.dp),
                fontSize = 11.sp,
                maxLines = 1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            TimelineRail(
                color = railColor,
                dotColor = result.color,
                cardColor = cardColor,
                isFirst = isFirst,
                isLast = isLast,
            )

            Box(
                modifier = Modifier
                    .padding(top = 8.dp, start = 6.dp)
                    .size(width = 52.dp, height = 30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant),
            ) {
                val art = mapArtPainter(match.mapId)
                if (art != null) {
                    Image(
                        painter = art,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, top = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mapNameOf(match.mapId, config),
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    if (difficulty != null) {
                        DifficultyPill(
                            text = difficulty,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Text(
                    text = "${mode.label} · 用时 ${formatDuration(match.duration)}",
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            Column(
                modifier = Modifier
                    .padding(start = 8.dp, top = 8.dp)
                    .widthIn(min = 56.dp),
                horizontalAlignment = Alignment.End,
            ) {
                ResultBadge(text = result.label, color = result.color)
                Text(
                    // 积分是这一行"打得好不好"的唯一数字，保留千分位完整显示；
                    // 十位数字挤不下时让它自己缩到 9sp，而不是截断成省略号。
                    text = if (match.score > 0) formatScore(match.score) else "—",
                    modifier = Modifier.padding(top = 3.dp),
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 9.sp,
                        maxFontSize = 12.sp,
                        stepSize = 1.sp,
                    ),
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold,
                    color = if (match.score > 0) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}

/**
 * 时间轴那一列：一根竖线 + 一颗空心圆点。
 *
 * 空心（描边）而不是实心：一条时间线上有十几颗实心圆点时，视线会被圆点本身抓住，
 * 反而看不出"哪几局是赢的"。描边只给轮廓，颜色仍然一眼可辨。
 * 圆点内圈先铺一层卡片底色，否则下面那条竖线会从圆心穿出来。
 *
 * ⚠️ 这条线必须**从这一行的顶边画到底边**（所以行内不能有竖向 padding），
 * 否则两行之间会空出 16dp，看起来是一串断开的短竖线。
 */
@Composable
private fun TimelineRail(
    color: Color,
    dotColor: Color,
    cardColor: Color,
    isFirst: Boolean,
    isLast: Boolean,
) {
    Canvas(
        modifier = Modifier
            .width(12.dp)
            .fillMaxHeight(),
    ) {
        val cx = size.width / 2f
        val cy = 18.dp.toPx()
        val stroke = 1.dp.toPx()

        if (!isFirst) {
            drawLine(color, Offset(cx, 0f), Offset(cx, cy), stroke)
        }
        if (!isLast) {
            drawLine(color, Offset(cx, cy), Offset(cx, size.height), stroke)
        }
        drawCircle(cardColor, radius = 4.5.dp.toPx(), center = Offset(cx, cy))
        drawCircle(
            color = dotColor,
            radius = 3.5.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/** 难度小标签：描边胶囊，和 PC 端地图名后面那个一致。 */
@Composable
private fun DifficultyPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .border(
                width = 1.dp,
                color = MiuixTheme.colorScheme.dividerLine,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = 10.sp,
        maxLines = 1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** 行尾的胜负标签：淡色底 + 同色字，扫列表时不必逐字读。 */
@Composable
private fun ResultBadge(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        color = color,
    )
}

/**
 * ① 当日场次弹层：胜 / 负 / MVP / 实力 / 带飞 / 通关率 / 最长连胜。
 *
 * 实力局与带飞局要全队 Boss 伤害，只有**详情已缓存**的局才算得出来 ——
 * 和历史页同一份数据源。所以底部那句说明不是废话：它解释了"为什么这里比列表里少"。
 */
@Composable
private fun RecordsSheet(
    show: Boolean,
    dayTitle: String,
    briefing: DailyBriefing,
    strongCount: Int,
    carryCount: Int,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = "${dayTitle} · 场次",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            InfoGrid(
                items = listOf(
                    "胜场" to "${briefing.winCount} 场",
                    "负场" to "${briefing.lossCount} 场",
                    "MVP 次数" to "${briefing.mvpCount} 次",
                    "通关率" to formatPercent(briefing.winCount, briefing.finishedCount),
                    "最长连胜" to "${briefing.longestWinStreak} 场",
                    "实力局" to "$strongCount 局",
                    "带飞局" to "$carryCount 局",
                ),
                columns = 2,
            )
            if (briefing.unfinishedCount > 0) {
                Text(
                    text = "另有 ${briefing.unfinishedCount} 场中途退出，不计入胜负与通关率",
                    modifier = Modifier.padding(top = 14.dp),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = "实力局 / 带飞局只统计已缓存详情的对局",
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * ③ 活跃时段弹层：24 根柱子，和 PC 端那张图同一口径。
 *
 * 只给柱状图不标每个整点几场 —— 柱子高度本身就够读，逐个标数字会把 24 格挤成
 * 一片小字。高峰那一小时用实色，其余淡一档。
 */
@Composable
private fun ActiveHoursSheet(
    show: Boolean,
    dayTitle: String,
    briefing: DailyBriefing,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = "${dayTitle} · 活跃时段",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = if (briefing.activeHour != null) {
                    "高峰 ${formatHourRange(briefing.activeHour)}"
                } else {
                    "这一天没有对局"
                },
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            HourBarChart(
                counts = briefing.hourCounts,
                peakHour = briefing.activeHour,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("0", "6", "12", "18", "24").forEach { label ->
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/** 24 根柱子。高度按当小时场次占比，0 场画一条 2dp 的底槽，读起来像"这一格存在但是空"。 */
@Composable
private fun HourBarChart(
    counts: List<Int>,
    peakHour: Int?,
    modifier: Modifier = Modifier,
) {
    val max = counts.maxOrNull() ?: 0
    val accent = MiuixTheme.colorScheme.primary
    val track = MiuixTheme.colorScheme.dividerLine

    Row(
        modifier = modifier.height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        counts.forEachIndexed { hour, count ->
            val isPeak = count > 0 && count == max && hour == peakHour
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (count <= 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(track, RoundedCornerShape(1.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            // 至少 6%：只打了一局的那一格也要看得见，不然图会像漏了一格
                            .fillMaxWidth()
                            .fillMaxHeight((count.toFloat() / max).coerceAtLeast(0.06f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (isPeak) accent else accent.copy(alpha = 0.45f)),
                    )
                }
            }
        }
    }
}

/** ④ 模式分布弹层：三个模式的横向柱状图，长度按场次最多的那个归一。 */
@Composable
private fun ModesSheet(
    show: Boolean,
    dayTitle: String,
    briefing: DailyBriefing,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = "${dayTitle} · 模式分布",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (briefing.matchCount > 0) {
                    "共 ${briefing.matchCount} 场"
                } else {
                    "这一天没有对局"
                },
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            val max = briefing.modeCounts.maxOfOrNull { it.count } ?: 0
            briefing.modeCounts.forEach { row ->
                ModeBarRow(
                    label = row.mode.label,
                    count = row.count,
                    total = briefing.matchCount,
                    fraction = if (max > 0) row.count.toFloat() / max else 0f,
                )
            }
        }
    }
}

@Composable
private fun ModeBarRow(
    label: String,
    count: Int,
    total: Int,
    fraction: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(64.dp),
            fontSize = 13.sp,
            maxLines = 1,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MiuixTheme.colorScheme.dividerLine),
        ) {
            if (count > 0 && fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtMost(1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }
        Text(
            text = if (total > 0) {
                "$count 场 · ${formatPercent(count, total)}"
            } else {
                "$count 场"
            },
            modifier = Modifier
                .padding(start = 10.dp)
                .width(72.dp),
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.End,
            color = if (count > 0) {
                MiuixTheme.colorScheme.onSurface
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
    }
}

private data class TimelineResult(val label: String, val color: Color)
