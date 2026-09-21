package com.nzd.antigravitypanel.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.domain.difficultyNameOf
import com.nzd.antigravitypanel.domain.mapNameOf
import com.nzd.antigravitypanel.domain.modeOf
import com.nzd.antigravitypanel.ui.component.CardCornerRadius
import com.nzd.antigravitypanel.ui.format.formatDateTimeShort
import com.nzd.antigravitypanel.ui.format.formatDuration
import com.nzd.antigravitypanel.ui.format.formatScore
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
// 图标直接用 extended 包里的顶层属性：MiuixIcons 聚合对象只收了一部分常用图标，
// Pin / FavoritesFill 这些不在里面，写 MiuixIcons.Pin 会 unresolved。
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 历史战绩。单列列表 + 触底加载更多，滚动手感照 miuix uitest 的
 * `OverscrollLoadMorePage`：不额外挂 overScrollModifier，靠 Miuix 自带的
 * `LocalOverscrollFactory` 出回弹。
 *
 * 置顶 / 收藏走长按——列表行上再塞两个图标按钮会把行高顶起来，
 * 而且误触率高。
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    config: GameConfigDto,
    pinned: Set<String>,
    favorite: Set<String>,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Scaffold 给的顶栏 / 底栏高度，只进 contentPadding，不裁掉内容区域。 */
    insets: PaddingValues = PaddingValues(0.dp),
) {
    val items by viewModel.items.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val listState = rememberLazyListState()
    var longPressed by remember { mutableStateOf<MatchEntity?>(null) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= 0 && lastVisible >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd && hasMore) viewModel.loadMore() }
    }

    if (items.isEmpty()) {
        // 空态也要能吃掉外层给的 modifier（页面级 padding），否则会顶到顶栏下面
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            HistoryEmpty(
                filtered = !filter.isEmpty,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .overScrollVertical(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp + insets.calculateTopPadding(),
            end = 12.dp,
            bottom = 12.dp + insets.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = items, key = { it.roomId }) { match ->
            MatchRow(
                match = match,
                config = config,
                pinned = match.roomId in pinned,
                favorite = match.roomId in favorite,
                // 菜单挂在这一行内部：弹窗的锚点取的是它所在的那块布局，
                // 挂这里才会贴着这张卡展开，而不是飘到屏幕别处
                menuExpanded = longPressed?.roomId == match.roomId,
                onClick = { onOpenDetail(match.roomId) },
                onLongPress = { longPressed = match },
                onTogglePin = { viewModel.togglePin(match.roomId) },
                onToggleFavorite = { viewModel.toggleFavorite(match.roomId) },
                onDismissMenu = { longPressed = null },
            )
        }
        if (hasMore) {
            item(key = "loading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InfiniteProgressIndicator(size = 20.dp)
                    Text(
                        text = "加载更多…",
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }

}

/**
 * MVP 角标的红。写死不跟主题：这是"荣誉标记"而不是状态色，
 * 而且白字压在上面要保对比度——主题的 error 色在深色模式下会偏亮。
 */
private val MvpBadgeRed = Color(0xFFE23C3C)

/**
 * 单条战绩。三行：状态行（胜负 + 模式 + MVP 角标 + 置顶/收藏图标）、地图-难度、底部时间与积分。
 *
 * 置顶/收藏从文字角标改成图标：原来那两个 " 置顶" " 收藏" 是往同一行里塞字，
 * 图名一长就被挤到换行；图标不占宽度，靠行尾对齐，扫列表时反而更显眼。
 * 长按菜单仍然是唯一的开关入口（行内放按钮误触率高），图标只表示当前状态。
 */
@Composable
private fun MatchRow(
    match: MatchEntity,
    config: GameConfigDto,
    pinned: Boolean,
    favorite: Boolean,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismissMenu: () -> Unit,
) {
    val mode = modeOf(match.mapId)
    val winColor = if (match.isWin && match.finished) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.error
    }
    val resultText = when {
        !match.finished -> "未通关"
        match.isWin -> "胜利"
        else -> "失败"
    }
    val accent = MiuixTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
        onLongPress = onLongPress,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // 长按菜单：锚点是这一行，所以必须写在 Column 里面。
            // 只在**被长按的那一行**挂载——每行都挂一个就是 N 份菜单状态白白注册进
            // 弹层宿主。Miuix 这个弹层进出都是 EnterTransition.None / ExitTransition.None，
            // 本来就没有动画，卸载掉不会有视觉差别。
            // collapseOnSelection = false —— 置顶和收藏可以同时开，选一个就收起的话
            // 每次只能改一项；关掉靠点周围空白（enableWindowDim 默认开）。
            if (menuExpanded) {
                OverlayCascadingListPopup(
                    show = true,
                    entries = listOf(
                        DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = "置顶",
                                    summary = "置顶的对局排在列表最前",
                                    selected = pinned,
                                    onClick = onTogglePin,
                                ),
                                DropdownItem(
                                    text = "收藏",
                                    summary = "可用“仅显示收藏”快速筛出",
                                    selected = favorite,
                                    onClick = onToggleFavorite,
                                ),
                            ),
                        ),
                    ),
                    onDismissRequest = onDismissMenu,
                    collapseOnSelection = false,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 圆点 + 结果词：色块比纯文字更容易在长列表里定位胜负
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(winColor),
                )
                Text(
                    text = resultText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = winColor,
                )
                Text(
                    text = " · ${mode.label}",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Box(modifier = Modifier.weight(1f))
                if (pinned) {
                    Icon(
                        imageVector = MiuixIcons.Pin,
                        contentDescription = "已置顶",
                        tint = accent,
                    )
                }
                if (favorite) {
                    Icon(
                        imageVector = MiuixIcons.FavoritesFill,
                        contentDescription = "已收藏",
                        tint = accent,
                    )
                }
                // 官方的 Rank 字段就是名次，1 = 本局 MVP。
                // 没打完的局 Rank 是空串（解析成 0），所以要带上 finished 判断。
                if (match.finished && match.rankValue == 1) {
                    MvpBadge(modifier = Modifier.padding(start = 4.dp))
                }
            }

            val difficulty = difficultyNameOf(match.subModeType, config)
            Text(
                text = mapNameOf(match.mapId, config) +
                    (difficulty?.let { "-$it" } ?: ""),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDateTimeShort(match.eventTime),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = " · ${formatDuration(match.duration)}",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = formatScore(match.score),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * MVP 角标：红色胶囊 + 白字。
 *
 * 用 [CircleShape] 而不是圆角矩形：高度就一行字高，全圆角裁出来正好是胶囊形，
 * 不用去凑"半径 = 高度一半"这种会随字号漂移的数。
 */
@Composable
private fun MvpBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MvpBadgeRed)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "MVP",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/**
 * 空列表。比一行灰字多给一句"接下来该做什么"——
 * 只说"没有对局"的话，用户分不清是没同步还是筛选太窄。
 */
@Composable
private fun HistoryEmpty(
    filtered: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = MiuixIcons.ListView,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = if (filtered) "没有符合筛选条件的对局" else "本地还没有对局",
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = if (filtered) {
                    "放宽顶栏筛选，或点右上角图标重置条件"
                } else {
                    "填入 Cookie 后会自动累积"
                },
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
