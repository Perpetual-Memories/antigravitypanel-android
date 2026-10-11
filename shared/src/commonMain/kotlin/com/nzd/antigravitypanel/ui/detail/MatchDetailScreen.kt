package com.nzd.antigravitypanel.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
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
import com.nzd.antigravitypanel.data.settings.UserSettings
import com.nzd.antigravitypanel.domain.ATTACK_TIER_MIN_COIN
import com.nzd.antigravitypanel.domain.CompositeScoreBreakdown
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.compositeScores
import com.nzd.antigravitypanel.domain.CompositeScoreInput
import com.nzd.antigravitypanel.domain.difficultyNameOf
import com.nzd.antigravitypanel.domain.mapNameOf
import com.nzd.antigravitypanel.domain.modeOf
import com.nzd.antigravitypanel.domain.partitionNameOf
import com.nzd.antigravitypanel.ui.component.BarBackdropContent
import com.nzd.antigravitypanel.ui.component.BarBlurHost
import com.nzd.antigravitypanel.ui.component.BlurredBar
import com.nzd.antigravitypanel.ui.component.CardCornerRadius
import com.nzd.antigravitypanel.ui.component.TopBarGlassAnchor
import com.nzd.antigravitypanel.ui.component.TopBarGlassBackButton
import com.nzd.antigravitypanel.ui.component.TopBarGlassPlaceholder
import com.nzd.antigravitypanel.ui.component.HintBlock
import com.nzd.antigravitypanel.ui.component.mapArtPainter
import com.nzd.antigravitypanel.ui.component.pluginPainter
import com.nzd.antigravitypanel.ui.component.weaponPainter
import com.nzd.antigravitypanel.ui.format.formatCoinPerMinute
import com.nzd.antigravitypanel.ui.format.formatCompact
import com.nzd.antigravitypanel.ui.format.formatCompositeScore
import com.nzd.antigravitypanel.ui.format.formatDamagePerWanCoin
import com.nzd.antigravitypanel.ui.format.formatDateShort
import com.nzd.antigravitypanel.ui.format.formatDecimal
import com.nzd.antigravitypanel.ui.format.formatDuration
import com.nzd.antigravitypanel.ui.format.formatScore
import com.nzd.antigravitypanel.ui.format.formatTimeShort
import com.nzd.antigravitypanel.util.percentDecoded
import kotlin.math.round
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 玩家卡高度，正反面（数据 / 配装）共用。
 *
 * 照官方 PC 端的 `h-[150px]`：切换显示时高度必须不动，否则列表整段会跳一下，
 * 而且连续点几张卡时根本看不清自己点的是哪张。两面都装进这个固定高度的盒子里。
 */
private val PlayerCardHeight = 150.dp

/**
 * 机甲战那张卡的高度。
 *
 * 机甲没有 Boss 伤害 / 小怪伤害 / 金币口径（服务端根本不下发），统计格只剩
 * 积分 / 击杀 / 死亡三项 —— 还是占 [PlayerCardHeight] 的话下面空掉一大截。
 * 按"名字一行 + 统计一行 + 上下留白"算出来是 112。
 */
private val MechaPlayerCardHeight = 112.dp

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
 * 一个容易漏的点：塔防 / 时空追猎 / 机甲战**都没有** Boss 伤害 / 小怪伤害 / 金币口径
 * （NZM 里 `showExtra = mode !== '塔防战' && mode !== '时空追猎'`，机甲是客户端补的），
 * 这几项只在该显示的时候显示，否则会满屏 0。机甲的卡还要矮一截（只有三项统计）。
 */
@Composable
fun MatchDetailScreen(
    viewModel: MatchDetailViewModel,
    roomId: String,
    config: GameConfigDto,
    /** 开关源。传对象而不是读好的 Boolean：二级页图层里的 lambda 拿到快照就不会再变。 */
    userSettings: UserSettings,
    onBack: () -> Unit,
    liquidGlassEnabled: Boolean = true,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(roomId) { viewModel.load(roomId) }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }
    val compositeScoreEnabled by userSettings.compositeScore.collectAsState()
    /** 长按「综合分」那一格要摆出来的算式。null = 不弹。 */
    var explainedScore by remember { mutableStateOf<CompositeScoreBreakdown?>(null) }

    val detail = state.detail
    val match = state.match
    val previous = state.previous
    // ⚠️ 机甲局**本地库里通常没有记录**（它不进同步口径，只有 JSON 导入过的才有），
    // 所以 match 经常是 null。模式只在 match 上取的话会退化成 UNKNOWN，
    // showExtra 跟着变回 true，卡片上又摆满 0 —— 所以从详情里那个人的 iMapId 兜一层。
    val mapId = match?.mapId?.takeIf { it > 0 }
        ?: detail?.loginUserDetail?.baseDetail?.iMapId?.takeIf { it > 0 }
        ?: detail?.list?.firstOrNull()?.baseDetail?.iMapId?.takeIf { it > 0 }
        ?: 0
    val mode = modeOf(mapId)
    // 机甲和塔防 / 追猎一样**没有 Boss 伤害 / 小怪伤害 / 金币口径**：
    // 服务端对机甲局根本不下发 huntingDetails，摆上去就是满屏 0。
    // 所以这一格和分均经济、综合分一起收掉，只留积分 / 击杀 / 死亡。
    val showExtra = mode != GameMode.TOWER && mode != GameMode.TIME_HUNT && mode != GameMode.MECHA
    val cardHeight = if (mode == GameMode.MECHA) MechaPlayerCardHeight else PlayerCardHeight
    val scrollBehavior = MiuixScrollBehavior()
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
                        title = "对局详情",
                        // 透明是必须的：BlurredBar 已经在采样底下的内容做模糊了，
                        // 这里再铺一层 surface 就等于把糊完的结果盖死
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { TopBarGlassPlaceholder(backGlassAnchor) },
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
                        // ⚠️ 排序跟着**服务端给的 Rank**走，不再按积分自己排。
                        // 右上角那个 #N 显示的是官方名次，列表顺序却按积分排的话，
                        // 会出现"第 3 名排在"第 1 名"上面"这种自相矛盾的画面。
                        // Rank 缺失（0）的沉到最后，那一批内部再按积分降序。
                        val players = remember(detail) {
                            detail.list.sortedWith(
                                compareBy<PlayerDetailDto> {
                                    it.baseDetail?.Rank?.takeIf { rank -> rank > 0 } ?: Int.MAX_VALUE
                                }.thenByDescending { it.baseDetail?.iScore ?: 0L },
                            )
                        }
                        // 综合分必须**整局一起算**：占比的分母是全队 Boss 伤害、
                        // 效率的分母是全队最高效率，两个都只有拿到所有人之后才知道。
                        // 所以在这里一次算完，下面按下标取回，顺序和 players 对齐。
                        val breakdowns = remember(detail) {
                            compositeScores(
                                players.map { player ->
                                    CompositeScoreInput(
                                        bossDamage = player.huntingDetails?.damageTotalOnBoss ?: 0L,
                                        coin = player.huntingDetails?.totalCoin ?: 0L,
                                    )
                                },
                            )
                        }
                        val selfOpenId = detail.loginUserDetail?.baseDetail?.vOpenID.orEmpty()

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .overScrollVertical()
                                // 甩到列表顶 / 底时的一记轻振动（NexioSchedule 同款）
                                .scrollEndHaptic()
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
                                    // 官方名次，不是这一行在列表里的序号（序号会随排序口径变，
                                    // 而名次是服务端给的结论）。0 = 没给出来，卡片上就不画。
                                    rank = player.baseDetail?.Rank ?: 0,
                                    isSelf = player.baseDetail?.vOpenID == selfOpenId,
                                    showExtra = showExtra,
                                    cardHeight = cardHeight,
                                    // 分均经济要拿金币除以时长，时长是**整局**的，不是某个人的
                                    durationSec = match?.duration ?: 0,
                                    // 关掉开关就退回「经济转化」，综合分连算式一起不给
                                    composite = if (compositeScoreEnabled) {
                                        breakdowns.getOrNull(index)
                                    } else {
                                        null
                                    },
                                    onExplainComposite = { explainedScore = breakdowns.getOrNull(index) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    CompositeScoreDialog(
        breakdown = explainedScore,
        onDismiss = { explainedScore = null },
    )
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
 * 玩家卡。点一下在「通关数据」和「配装」之间切换，高度恒定（[cardHeight]）。
 *
 * 右上角是本局排名 `#N`（原来这里是「显示配装」的文字，而切换本来就靠点卡片，
 * 那行字既不是操作入口又占了排名该在的位置）。
 *
 * @param rank 服务端给的**真实名次**，不是列表序号。<= 0 时这一角不画 ——
 *   没给出来就画个 `#0`，那是在拿一个假数字冒充结论。
 */
@Composable
private fun PlayerCard(
    player: PlayerDetailDto,
    rank: Int,
    isSelf: Boolean,
    showExtra: Boolean,
    durationSec: Int,
    /**
     * 这一格的综合分算式。null = 开关关着，那一格照旧显示「经济转化」。
     */
    composite: CompositeScoreBreakdown?,
    onExplainComposite: () -> Unit,
    cardHeight: Dp = PlayerCardHeight,
) {
    var showEquipment by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight),
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
                        StatGrid(
                            player = player,
                            durationSec = durationSec,
                            showExtra = showExtra,
                            composite = composite,
                            onExplainComposite = onExplainComposite,
                            // 综合分那一格要接管点击事件（长按弹算式），
                            // 所以轻触得由它自己把"翻到配装"这件事做掉，否则点了没反应
                            onCellClick = { showEquipment = !showEquipment },
                        )
                    }
                }
            }

            // 排名压在右上角：它是这块卡片的"角标"，不参与上面那列的高度计算
            if (rank > 0) {
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

/** 一格统计：上数值下标签。 */
private data class StatItem(
    val value: String,
    val label: String,
    /** false = 这一项是 0 / 算不出来，压暗。 */
    val highlight: Boolean = true,
    /** 非空 = 长按这一格弹个说明（目前只有「综合分」用）。 */
    val onExplain: (() -> Unit)? = null,
)

/**
 * 通关数据的网格：**一行四格**，不够四格时按实际数量排（[columns]）。
 *
 * 原来是三列两行（上排积分/击杀/死亡、下排 Boss/小怪/金币）。加到八项之后三列要排三行，
 * 而卡片高度是写死的 [PlayerCardHeight]，多一行必然溢出，所以改成四列两行。
 *
 * **顺序是用户定的**（上排偏"打出去多少"、下排偏"花了多少 / 扛了多少"）：
 *
 * ```
 * 积分      击杀    Boss 伤害   综合分 / 经济转化
 * 分均经济   死亡    小怪伤害    金币
 * ```
 *
 * 只有上排的数值加粗：一屏十几张卡，加粗是"先看这里"的暗示，
 * 两排都粗等于没有加粗。非猎场（没有 Boss/金币口径）时只剩积分/击杀/死亡三项。
 *
 * @param composite 非空时第四格换成「综合分」，长按弹它的算式；为 null 时照旧显示
 *   「经济转化」。开关在设置 → 实验性功能。
 */
@Composable
private fun StatGrid(
    player: PlayerDetailDto,
    durationSec: Int,
    showExtra: Boolean,
    composite: CompositeScoreBreakdown?,
    onExplainComposite: () -> Unit,
    onCellClick: () -> Unit,
) {
    val hunting = player.huntingDetails
    val coin = hunting?.totalCoin ?: 0L
    val boss = hunting?.damageTotalOnBoss ?: 0L
    val mobs = hunting?.damageTotalOnMobs ?: 0L

    val items = buildList {
        add(StatItem(formatScore(player.baseDetail?.iScore ?: 0L), "积分"))
        add(StatItem((player.baseDetail?.iKills ?: 0).toString(), "击杀"))
        if (showExtra) {
            add(StatItem(formatCompact(boss), "Boss 伤害", boss > 0))
            if (composite != null) {
                add(
                    StatItem(
                        value = formatCompositeScore(composite.score),
                        label = "综合分",
                        highlight = composite.score != null,
                        onExplain = onExplainComposite,
                    ),
                )
            } else {
                add(
                    StatItem(
                        value = formatDamagePerWanCoin(boss, coin),
                        label = "经济转化",
                        highlight = boss > 0 && coin > 0,
                    ),
                )
            }
            add(
                StatItem(
                    value = formatCoinPerMinute(coin, durationSec),
                    // 单位是万：写在标签里而不是挂在数后面，否则这一列比别的长一截
                    label = "分均经济(万)",
                    highlight = coin > 0 && durationSec > 0,
                ),
            )
            add(StatItem((player.baseDetail?.iDeaths ?: 0).toString(), "死亡"))
            add(StatItem(formatCompact(mobs), "小怪伤害", mobs > 0))
            add(StatItem(formatCompact(coin), "金币", coin > 0))
        } else {
            add(StatItem((player.baseDetail?.iDeaths ?: 0).toString(), "死亡"))
        }
    }
    val columns = items.size.coerceAtMost(4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 6.dp),
        // 两排之间要留够间距：原来那版每格里上下两行中间隔着 10dp，
        // 拆成独立的两排之后不显式spacedBy就会挤在一起，看着像一行。
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        for ((index, row) in items.chunked(columns).withIndex()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (item in row) {
                    StatCell(
                        modifier = Modifier.weight(1f),
                        item = item,
                        bold = index == 0,
                        onCellClick = onCellClick,
                    )
                }
                // 末行不满要补同样 weight 的 Spacer：不然那几格会被拉宽，和上面对不齐
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatCell(
    modifier: Modifier,
    item: StatItem,
    bold: Boolean,
    onCellClick: () -> Unit,
) {
    // 只有挂了说明的那一格接管点击：接管之后外层卡片的 onClick 收不到这一块的按下，
    // 所以轻触要自己把"翻到配装"做掉，否则点综合分没反应。
    // ⚠️ indication 给 null：卡片自带 Sink 反馈，再叠一层涟漪会让人以为这是个按钮。
    val explain = item.onExplain
    val clickable = if (explain != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onCellClick,
            onLongClick = explain,
        )
    } else {
        Modifier
    }
    Column(
        modifier = modifier.then(clickable),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = item.value,
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            // 0 的项压暗：一屏十几张卡，只有真有数的才该跳出来
            color = if (item.highlight) {
                MiuixTheme.colorScheme.onSurface
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = item.label,
            modifier = Modifier.padding(top = 1.dp),
            fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
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

/**
 * 长按「综合分」那一格弹出来的算式。
 *
 * 只丢一个 0~1 的结果出去，用户没法判断它算得对不对，所以四步的中间值
 * **全部用这一局这个人的真实数字**摆出来 —— 他拿着金币和 Boss 伤害
 * 就能自己按一遍计算器对上。
 *
 * 算法本身在 `domain/CompositeScore.kt`，这里只负责把它翻译成人话。
 */
@Composable
private fun CompositeScoreDialog(
    breakdown: CompositeScoreBreakdown?,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        show = breakdown != null,
        title = "综合分",
        onDismissRequest = onDismiss,
    ) {
        if (breakdown != null) {
            // L 在式子里的写法：档位是百分数，除的时候用倍数（200% → 2）
            val tier = tierMultiplierText(breakdown.tierPercent)
            val share = formatDecimal(breakdown.damageShare, 3)
            val ratio = if (breakdown.maxEfficiency > 0.0) {
                formatDecimal(breakdown.efficiency / breakdown.maxEfficiency, 3)
            } else {
                "—"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "把「打得多」和「打得值」合成一个数，取值 0~1，越接近 1 越好。" +
                        "下面用的都是这一局这个人的数字。",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                ScoreStep(
                    index = "1",
                    title = "金币 → 攻击力档位 L",
                    body = "金币 ${formatScore(breakdown.coin)} → L = " +
                        "${breakdown.tierPercent}%（$tier 倍）",
                )
                ScoreStep(
                    index = "2",
                    title = "档位修正效率 E = B ÷ L",
                    body = "${formatScore(breakdown.bossDamage)} ÷ $tier = " +
                        formatScore(round(breakdown.efficiency).toLong()),
                )
                ScoreStep(
                    index = "3",
                    title = "伤害占比 P = B ÷ 全队 B",
                    body = "${formatScore(breakdown.bossDamage)} ÷ " +
                        "${formatScore(breakdown.teamBossDamage)} = " +
                        "${formatDecimal(breakdown.damageShare * 100.0, 1)}%",
                )
                ScoreStep(
                    index = "4",
                    title = "综合分 S = √(P × E ÷ 全队最高 E)",
                    body = "√($share × $ratio) = ${formatCompositeScore(breakdown.score)}",
                )
                Text(
                    text = "全队最高 E = ${formatScore(round(breakdown.maxEfficiency).toLong())}" +
                        if (breakdown.coin < ATTACK_TIER_MIN_COIN) {
                            "；金币不满 $ATTACK_TIER_MIN_COIN（表里最低一档）按 25% 算"
                        } else {
                            ""
                        },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("知道了")
                }
            }
        }
    }
}

@Composable
private fun ScoreStep(index: String, title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = index,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.primary,
            )
            Text(
                text = title,
                modifier = Modifier.padding(start = 6.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = body,
            modifier = Modifier.padding(start = 18.dp, top = 2.dp),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/**
 * 档位百分数写成倍数：`200` -> `2`，`25` -> `0.25`，`1_500` -> `15`。
 *
 * 弹窗里 L 是当除数用的，写 `÷ 200%` 读起来别扭，写 `÷ 2` 才像一句话。
 */
private fun tierMultiplierText(percent: Int): String {
    val whole = percent / 100
    val rest = percent % 100
    if (rest == 0) return whole.toString()
    return "$whole.${rest.toString().padStart(2, '0')}".trimEnd('0')
}
