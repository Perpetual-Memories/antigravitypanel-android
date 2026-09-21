package com.nzd.antigravitypanel.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.data.remote.dto.PartitionDetailDto
import com.nzd.antigravitypanel.data.remote.dto.PlayerDetailDto
import com.nzd.antigravitypanel.data.remote.dto.nicknameDecoded
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.difficultyNameOf
import com.nzd.antigravitypanel.domain.mapNameOf
import com.nzd.antigravitypanel.domain.modeOf
import com.nzd.antigravitypanel.domain.partitionNameOf
import com.nzd.antigravitypanel.ui.component.BarBackdropContent
import com.nzd.antigravitypanel.ui.component.BarBlurHost
import com.nzd.antigravitypanel.ui.component.BlurredBar
import com.nzd.antigravitypanel.ui.component.CardCornerRadius
import com.nzd.antigravitypanel.ui.component.HintBlock
import com.nzd.antigravitypanel.ui.component.mapArtPainter
import com.nzd.antigravitypanel.ui.component.pluginPainter
import com.nzd.antigravitypanel.ui.component.weaponPainter
import com.nzd.antigravitypanel.ui.format.formatCompact
import com.nzd.antigravitypanel.ui.format.formatDateShort
import com.nzd.antigravitypanel.ui.format.formatDuration
import com.nzd.antigravitypanel.ui.format.formatScore
import com.nzd.antigravitypanel.ui.format.formatTimeShort
import com.nzd.antigravitypanel.util.percentDecoded
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 玩家卡高度，正反面（数据 / 配装）共用。
 *
 * 照官方 PC 端的 `h-[150px]`：切换显示时高度必须不动，否则列表整段会跳一下，
 * 而且连续点几张卡时根本看不清自己点的是哪张。两面都装进这个固定高度的盒子里。
 */
private val PlayerCardHeight = 150.dp

/** 「数据总览」左边那张地图缩略图，官方 PC 端是 76×46。 */
private val MapThumbWidth = 76.dp
private val MapThumbHeight = 46.dp

/** 「我」角标的蓝。写死：白字压在上面要保对比度，主题 primary 在深色下偏亮。 */
private val SelfBadgeBlue = Color(0xFF2F6BFF)

/**
 * 对局详情页。
 *
 * **骨架必须和一级页一致**——`BarBlurHost` → `Scaffold(BlurredBar + SmallTopAppBar)`
 * → `BarBackdropContent` → `LazyColumn`。少了 `BarBackdropContent` 那层，
 * 顶栏就采不到底下滚动的内容，渐进式模糊直接消失；少给 contentPadding 带上
 * `innerPadding`，内容又会顶到顶栏底下（表现就是"整体上移"）。
 * 关于页当初就是栽在这两处。
 *
 * 内容三段：数据总览 → 区域用时 → 玩家（点卡片在数据 / 配装间切换）。
 *
 * 一个容易漏的点：塔防和时空追猎**没有** Boss 伤害 / 小怪伤害 / 金币口径
 * （NZM 里 `showExtra = mode !== '塔防战' && mode !== '时空追猎'`），
 * 这几项只在该显示的时候显示，否则会满屏 0。
 */
@Composable
fun MatchDetailScreen(
    viewModel: MatchDetailViewModel,
    roomId: String,
    config: GameConfigDto,
    onBack: () -> Unit,
    liquidGlassEnabled: Boolean = true,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(roomId) { viewModel.load(roomId) }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }

    val detail = state.detail
    val match = state.match
    val previous = state.previous
    val mode = match?.let { modeOf(it.mapId) } ?: GameMode.UNKNOWN
    val showExtra = mode != GameMode.TOWER && mode != GameMode.TIME_HUNT
    val scrollBehavior = MiuixScrollBehavior()

    BarBlurHost(enabled = liquidGlassEnabled) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                BlurredBar(topGradient = true) {
                    SmallTopAppBar(
                        title = "对局详情",
                        // 透明是必须的：BlurredBar 已经在采样底下的内容做模糊了，
                        // 这里再铺一层 surface 就等于把糊完的结果盖死
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
            // 这一层铺满整屏且不裁掉顶栏那一带，列表滚动时才能从顶栏底下穿过去
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                val contentPadding = remember(innerPadding) {
                    PaddingValues(
                        start = 12.dp,
                        top = 12.dp + innerPadding.calculateTopPadding(),
                        end = 12.dp,
                        bottom = 12.dp + innerPadding.calculateBottomPadding(),
                    )
                }

                when {
                    state.loading -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        InfiniteProgressIndicator(size = 28.dp)
                    }

                    state.error != null -> HintBlock(
                        text = state.error ?: "",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    )

                    detail == null -> HintBlock(
                        text = "没有拿到这局的详情",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    )

                    else -> {
                        val players = remember(detail) {
                            detail.list.sortedByDescending { it.baseDetail?.iScore ?: 0L }
                        }
                        val selfOpenId = detail.loginUserDetail?.baseDetail?.vOpenID.orEmpty()

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = contentPadding,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "overview") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    SmallTitle(text = "数据总览")
                                    MatchOverviewCard(
                                        match = match,
                                        previous = previous,
                                        mode = mode,
                                        config = config,
                                        detailBossDamage = detail.loginUserDetail
                                            ?.huntingDetails?.damageTotalOnBoss ?: 0L,
                                        teamBossDamage = detail.list.sumOf {
                                            it.huntingDetails?.damageTotalOnBoss ?: 0L
                                        },
                                        showExtra = showExtra,
                                    )
                                }
                            }

                            val areas = detail.loginUserDetail?.huntingDetails?.partitionDetails
                                ?.filter { it.usedTime > 0 }
                                .orEmpty()
                            if (areas.isNotEmpty()) {
                                item(key = "areas") {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        SmallTitle(text = "区域用时")
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(items = areas, key = { it.areaId }) { area ->
                                                AreaChip(area = area, config = config)
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "players-title") { SmallTitle(text = "玩家") }

                            itemsIndexed(
                                items = players,
                                key = { _, player ->
                                    "${player.nickname}_${player.baseDetail?.vOpenID}"
                                },
                            ) { index, player ->
                                PlayerCard(
                                    player = player,
                                    rank = index + 1,
                                    isSelf = player.baseDetail?.vOpenID == selfOpenId,
                                    showExtra = showExtra,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「数据总览」：左边地图缩略图，中间三行（结果/模式、地图-难度、日期/时间），
 * 右边三行（积分、耗时 + 更快更慢、Boss 伤害 + 占全队比例）。
 *
 * 用**不传 onClick 的** [BasicComponent]：它不是可点条目，只是一块带留白和按压反馈
 * 规范的信息区，套在 Card 里正好（HyperIsland 设置页顶部那段提示文字就是这么写的）。
 */
@Composable
private fun MatchOverviewCard(
    match: MatchEntity?,
    previous: MatchEntity?,
    mode: GameMode,
    config: GameConfigDto,
    detailBossDamage: Long,
    teamBossDamage: Long,
    showExtra: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        BasicComponent(
            startAction = { MapThumbnail(mapId = match?.mapId) },
            endActions = {
                OverviewRightColumn(
                    match = match,
                    previous = previous,
                    bossDamage = detailBossDamage,
                    teamBossDamage = teamBossDamage,
                    showExtra = showExtra,
                )
            },
        ) {
            if (match == null) {
                Text(
                    text = "本地没有这局记录",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                return@BasicComponent
            }
            val win = match.isWin && match.finished
            val resultText = when {
                !match.finished -> "未通关"
                match.isWin -> "胜利"
                else -> "失败"
            }
            val resultColor = if (win) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.error
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = resultText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = resultColor,
                )
                Text(
                    text = mode.label,
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val difficulty = difficultyNameOf(match.subModeType, config)
            Text(
                text = mapNameOf(match.mapId, config) +
                    (difficulty?.let { "-$it" } ?: ""),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = formatDateShort(match.eventTime),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = formatTimeShort(match.eventTime),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 左边那张地图小图。包里没有这张图时退化成一块表面色，不留空白。 */
@Composable
private fun MapThumbnail(mapId: Int?) {
    val painter = mapId?.let { mapArtPainter(it) }
    Box(
        modifier = Modifier
            .size(width = MapThumbWidth, height = MapThumbHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** 「数据总览」右侧三行：积分 / 耗时（带与上局对比）/ Boss 伤害（带占全队比例）。 */
@Composable
private fun OverviewRightColumn(
    match: MatchEntity?,
    previous: MatchEntity?,
    bossDamage: Long,
    teamBossDamage: Long,
    showExtra: Boolean,
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = formatScore(match?.score ?: 0L),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(match?.duration ?: 0),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
            durationDelta(current = match?.duration ?: 0, previous = previous?.duration)
                ?.let { delta ->
                    Text(
                        text = delta.text,
                        modifier = Modifier.padding(start = 4.dp),
                        fontSize = 11.sp,
                        color = delta.color,
                    )
                }
        }
        if (showExtra && bossDamage > 0) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatScore(bossDamage),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                // 官方 PC 端这里比的是"占全队伤害的比例"，我们没存上局的分人伤害，
                // 所以只给本局的占比 —— 比不了就别编一个数上去
                if (teamBossDamage > 0) {
                    Text(
                        text = "占${bossDamage * 100 / teamBossDamage}%",
                        modifier = Modifier.padding(start = 4.dp),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

private class Delta(val text: String, val color: Color)

/** 与上局耗时的对比。上局不存在（这是该图该难度的第一局）时返回 null。 */
@Composable
private fun durationDelta(current: Int, previous: Int?): Delta? {
    if (previous == null || previous <= 0 || current <= 0) return null
    val diff = current - previous
    if (diff == 0) return null
    return if (diff < 0) {
        Delta("更快${formatDuration(-diff)}", MiuixTheme.colorScheme.primary)
    } else {
        Delta("更慢${formatDuration(diff)}", MiuixTheme.colorScheme.error)
    }
}

/**
 * 区域用时的一格。
 *
 * 排序照官方 PC 端：按 `areaId` 升序就是"这一局打过去的顺序"，
 * 唯一的例外是 40847（"关卡3"），它得排在 40843 和 40844 中间，
 * 官方那边是直接给它一个 40843.5 的排序键。
 */
@Composable
private fun AreaChip(
    area: PartitionDetailDto,
    config: GameConfigDto,
) {
    Card(
        cornerRadius = CardCornerRadius,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = partitionNameOf(area.areaId, config),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = formatDuration(area.usedTime),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 玩家卡。点一下在「通关数据」和「配装」之间切换，高度恒定 [PlayerCardHeight]。
 *
 * 右上角是本局排名 `#N`（原来这里是「显示配装」的文字，而切换本来就靠点卡片，
 * 那行字既不是操作入口又占了排名该在的位置）。
 */
@Composable
private fun PlayerCard(
    player: PlayerDetailDto,
    rank: Int,
    isSelf: Boolean,
    showExtra: Boolean,
) {
    var showEquipment by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(PlayerCardHeight),
        cornerRadius = CardCornerRadius,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = { showEquipment = !showEquipment },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = player.nicknameDecoded.ifBlank { "未知玩家" },
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelf) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isSelf) {
                        SelfBadge(modifier = Modifier.padding(start = 6.dp))
                    }
                }

                // 两面都装进剩下的空间里，谁高谁矮都不会把卡片撑开
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (showEquipment) {
                        EquipmentRow(player = player)
                    } else {
                        StatGrid(player = player, showExtra = showExtra)
                    }
                }
            }

            // 排名压在右上角：它是这块卡片的"角标"，不参与上面那列的高度计算
            Text(
                text = "#$rank",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 14.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = rankColor(rank),
            )
        }
    }
}

/** 「我」：蓝色胶囊 + 白字。 */
@Composable
private fun SelfBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(SelfBadgeBlue)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "我",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

private val RankColors = listOf(
    Color(0xFFD4A84B),
    Color(0xFF9AA0A6),
    Color(0xFFB87333),
)

/** 名次颜色：前三名金 / 银 / 铜，其余用次要文字色。 */
@Composable
private fun rankColor(rank: Int): Color =
    RankColors.getOrNull(rank - 1) ?: MiuixTheme.colorScheme.onSurfaceVariantSummary

/**
 * 通关数据：三列居中，每列上数值下标签。
 *
 * 三列是照官方 PC 端的排布（积分/击杀/死亡 一行，Boss/小怪/金币 一行），
 * 但**竖着放**：原来一横排六个数挤在半张卡里，现在一列就是一个维度，扫起来不用对齐。
 */
@Composable
private fun StatGrid(
    player: PlayerDetailDto,
    showExtra: Boolean,
) {
    val hunting = player.huntingDetails
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp),
    ) {
        StatColumn(
            modifier = Modifier.weight(1f),
            top = formatScore(player.baseDetail?.iScore ?: 0L),
            topLabel = "积分",
            bottom = formatCompact(hunting?.damageTotalOnBoss ?: 0L),
            bottomLabel = "Boss 伤害",
            showBottom = showExtra,
            bottomHighlight = (hunting?.damageTotalOnBoss ?: 0L) > 0,
        )
        StatColumn(
            modifier = Modifier.weight(1f),
            top = (player.baseDetail?.iKills ?: 0).toString(),
            topLabel = "击杀",
            bottom = formatCompact(hunting?.damageTotalOnMobs ?: 0L),
            bottomLabel = "小怪伤害",
            showBottom = showExtra,
            bottomHighlight = (hunting?.damageTotalOnMobs ?: 0L) > 0,
        )
        StatColumn(
            modifier = Modifier.weight(1f),
            top = (player.baseDetail?.iDeaths ?: 0).toString(),
            topLabel = "死亡",
            bottom = formatCompact(hunting?.totalCoin ?: 0L),
            bottomLabel = "金币",
            showBottom = showExtra,
            bottomHighlight = (hunting?.totalCoin ?: 0L) > 0,
        )
    }
}

@Composable
private fun StatColumn(
    modifier: Modifier,
    top: String,
    topLabel: String,
    bottom: String,
    bottomLabel: String,
    showBottom: Boolean,
    bottomHighlight: Boolean,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = top,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = topLabel,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (showBottom) {
            Text(
                text = bottom,
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                // 0 的项压暗：一屏十几张卡，只有真有数的才该跳出来
                color = if (bottomHighlight) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = bottomLabel,
                modifier = Modifier.padding(top = 1.dp),
                fontSize = 10.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * 配装：横向滚动，一组方案里先武器再插件，每件都是图 + 名字。
 *
 * 图优先用本地打包的（[weaponPainter] / [pluginPainter]，按物品名查），
 * 查不到才退回服务端给的 `pic` —— 官方 PC 端也是本地图集优先，
 * `pic` 时有时无，全指望它会一半是裂图。
 */
@Composable
private fun EquipmentRow(player: PlayerDetailDto) {
    val schemes = player.equipmentScheme
    if (schemes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "这局没有记录配装",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        schemes.forEach { equipment ->
            EquipmentItem(
                name = equipment.weaponName,
                pic = equipment.pic,
                painter = weaponPainter(equipment.weaponName.percentDecoded()),
                width = 96.dp,
                height = 56.dp,
            )
            equipment.commonItems.forEach { item ->
                EquipmentItem(
                    name = item.itemName,
                    pic = item.pic,
                    painter = pluginPainter(item.itemName.percentDecoded()),
                    width = 42.dp,
                    height = 42.dp,
                )
            }
        }
    }
}

@Composable
private fun EquipmentItem(
    name: String,
    pic: String,
    painter: Painter?,
    width: Dp,
    height: Dp,
) {
    val label = name.percentDecoded().ifBlank { "未知" }
    Column(
        modifier = Modifier
            .padding(end = 10.dp)
            .width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = height)
                .clip(RoundedCornerShape(6.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else if (pic.isNotBlank()) {
                AsyncImage(
                    model = pic,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            text = label,
            modifier = Modifier
                .padding(top = 3.dp)
                .width(width),
            fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
