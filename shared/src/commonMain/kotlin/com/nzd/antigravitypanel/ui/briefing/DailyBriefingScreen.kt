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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.domain.DailyBriefing
import com.nzd.antigravitypanel.domain.briefingDays
import com.nzd.antigravitypanel.domain.buildDailyBriefing
import com.nzd.antigravitypanel.domain.difficultyNameOf
import com.nzd.antigravitypanel.domain.mapNameOf
import com.nzd.antigravitypanel.domain.modeOf
import com.nzd.antigravitypanel.ui.component.HintBlock
import com.nzd.antigravitypanel.ui.component.StatCard
import com.nzd.antigravitypanel.ui.component.mapArtPainter
import com.nzd.antigravitypanel.ui.format.formatBriefingPlaytime
import com.nzd.antigravitypanel.ui.format.formatDuration
import com.nzd.antigravitypanel.ui.format.formatHourRange
import com.nzd.antigravitypanel.ui.format.formatScore
import com.nzd.antigravitypanel.ui.format.formatTimeShort
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
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
 * 数据来自本地库（同步链路已经在冷启动 / 手动刷新时把三个模式灌进去了），
 * 所以切日期是纯内存计算，不转圈、不联网。四张卡片的数字口径见
 * [buildDailyBriefing] —— 那里是唯一的实现，UI 不另算一遍。
 *
 * @param matchesFlow 本地全量对局。传 Flow 而不是 List：这个页面在 Pager 的
 *   内容 lambda 里，外面读好再传进来会钉在组合时那一帧（见 AppContent 里同款注释）。
 * @param openId 当前账号的 openid。null / 空串时不做账号过滤（离线导入的场景）。
 * @param insets 顶栏 + 悬浮底栏高度，只进 contentPadding。
 */
@Composable
fun DailyBriefingScreen(
    matchesFlow: StateFlow<List<MatchEntity>>,
    config: GameConfigDto,
    openId: String?,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    insets: PaddingValues = PaddingValues(0.dp),
) {
    val matches by matchesFlow.collectAsState()

    // ⚠️ 只取一次"现在"：日期条在页面存活期间不该自己变。真跨过零点的话，
    // 用户下拉刷新 / 切回这一页会重新组合，届时拿到新的那一天。
    val days = remember { briefingDays(currentEpochSeconds()) }
    // 用 rememberSaveable 记住选的是哪一天：切去别的 Tab 再回来，
    // 停在用户刚才看的那天比跳回「今天」更符合预期。
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val day = days.getOrNull(selectedIndex) ?: days.firstOrNull() ?: return

    val briefing = remember(matches, day.startSec, openId) {
        buildDailyBriefing(matches = matches, dayStartSec = day.startSec, openId = openId)
    }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier
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
            )
        }

        item(key = "stats") {
            BriefingStats(dayTitle = day.title, briefing = briefing)
        }

        item(key = "timeline") {
            TimelineCard(
                briefing = briefing,
                config = config,
                // 整库为空 ≠ 这一天没打：前者要么没登录、要么还没同步，
                // 给同一句"这一天没有对局"会让人以为数据丢了。
                emptyHint = if (matches.isEmpty()) {
                    "还没有对局数据，先在概览页登录或导入数据"
                } else {
                    "这一天没有对局"
                },
                onOpenDetail = onOpenDetail,
            )
        }
    }
}

/** 四张 1×2 的卡：左列场次 / 时长，右列最活跃时段 / 模式分布。 */
@Composable
private fun BriefingStats(
    dayTitle: String,
    briefing: DailyBriefing,
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
            )
            StatCard(
                title = "游戏时长",
                value = formatBriefingPlaytime(briefing.playtimeSec),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
            )
            StatCard(
                title = "模式分布",
                // 卡片只放得下一行，给场次最多的那个模式；三个模式各几场在时间线下面
                // 那排标签里能看全（见模式分布说明），这里不重复堆。
                value = briefing.topMode?.let { "${it.mode.label} ${it.count} 场" } ?: "—",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "对局时间线",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = "按时间先后 · 点击查看详情",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = formatTimeShort(match.eventTime),
                modifier = Modifier
                    .width(38.dp)
                    // 和圆点对齐：11sp 的行高约 14dp，加 4dp 后中心落在 11dp
                    .padding(top = 4.dp),
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
                    .padding(start = 6.dp)
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
                    .padding(start = 10.dp, top = 1.dp),
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
                    .padding(start = 8.dp)
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
        val cy = 11.dp.toPx()
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

private data class TimelineResult(val label: String, val color: Color)
