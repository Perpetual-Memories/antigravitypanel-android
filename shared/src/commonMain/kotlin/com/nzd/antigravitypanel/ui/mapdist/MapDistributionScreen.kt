package com.nzd.antigravitypanel.ui.mapdist

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nzd.antigravitypanel.data.remote.dto.MapItemDto
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.MapStatEntry
import com.nzd.antigravitypanel.domain.difficultyRank
import com.nzd.antigravitypanel.ui.component.CardCornerRadius
import com.nzd.antigravitypanel.ui.component.DropFrame
import com.nzd.antigravitypanel.ui.component.HintBlock
import com.nzd.antigravitypanel.ui.component.dropFramePainter
import com.nzd.antigravitypanel.ui.component.mapArtPainter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

private val MODE_TABS = listOf(GameMode.HUNT, GameMode.TOWER, GameMode.TIME_HUNT)

/**
 * 卡片高度。**正反面共用一个固定值**，翻转只换画哪一面，高度就不可能变。
 *
 * 180dp 是照着"一屏能整看到三张、第四张露个头"定的：手机上内容区大约 580~600dp，
 * 加 10dp 行距一行占 190dp，正好三行多一点。再矮下去，背面的四列掉落物会被挤成一条。
 */
private val MapCardHeight = 180.dp

/** 掉落物网格的列数。官方 PC 端也是四列。 */
private const val GRID_COLUMNS = 4

/** 卡片上的文字色。底图是彩色的，所以一律走白系 + 一层深色压暗，浅色主题下也读得清。 */
private val CardTextSecondary = Color.White.copy(alpha = 0.72f)
private val CardTextTertiary = Color.White.copy(alpha = 0.5f)
private val CardDivider = Color.White.copy(alpha = 0.18f)

/** 底图之上那层压暗。上浅下深，保证底部那几行小字始终压得住画面。 */
private val CardScrim = Brush.verticalGradient(
    listOf(Color.Black.copy(alpha = 0.40f), Color.Black.copy(alpha = 0.86f)),
)

/** 收集品集齐时的进度条颜色。官方 PC 端用的就是这个绿。 */
private val CollectionDone = Color(0xFF10B981)

/** 未解锁的掉落物图标去色用。提到顶层，免得每帧新建一个矩阵。 */
private val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * 地图分布。按赛季分节（猎场），每张卡正面是本图的场次与难度分布，
 * 点一下翻到背面的掉落物解锁 —— 版式照官方 PC 端「反重力数据面板」的地图分布页。
 *
 * 掉落物（[MapItemDto.owned]）是官方给的"我已解锁"标记，页面只做展示，
 * 不去猜解锁条件——那个规则从来没公开过。
 */
@Composable
fun MapDistributionScreen(
    viewModel: MapDistributionViewModel,
    modifier: Modifier = Modifier,
    /** Scaffold 给的顶栏 / 底栏高度，只进 contentPadding，不裁掉内容区域。 */
    insets: PaddingValues = PaddingValues(0.dp),
) {
    val entries by viewModel.entries.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val drops by viewModel.drops.collectAsState()
    val dropsLoaded by viewModel.dropsLoaded.collectAsState()
    val dropError by viewModel.dropError.collectAsState()
    val officialLoaded by viewModel.officialLoaded.collectAsState()
    val officialError by viewModel.officialError.collectAsState()
    val flipped = remember { mutableStateSetOf<Int>() }

    // 掉落物一次全拉回来。卡片正面要显示「核心收集进度 x / y」，
    // 那就得在渲染之前就知道每张图有几件收集品——按需拉的话没翻过的卡全是空的。
    LaunchedEffect(Unit) {
        viewModel.loadDrops()
        // 官方统计跟着模式走（每个模式一个请求），所以初始这一次要在页面里发起
        viewModel.loadOfficial()
    }

    // 难度列取**整个模式**出现过的难度并集，而不是每张卡各取各的：
    // 列数不一样的话同一张图在不同屏之间位置会变，横着扫一列也对比不了
    val difficultyColumns = remember(entries) {
        entries.asSequence()
            .flatMap { it.byDifficulty.asSequence() }
            .map { it.name }
            .distinct()
            .sortedWith(compareBy({ difficultyRank(it) }, { it }))
            .toList()
    }

    // 全是 0 场：本地库还是空的，先说一句，比让用户对着九张 0 猜好
    val blank = sections.all { section -> section.entries.all { it.total == 0 } }

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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "tabs") {
            // 和「设置 → 外观 → 主题」二级页顶部用的是同一个 TabRow，
            // 不是带描边的 TabRowWithContour：这个页面只有三个模式，不需要描边强调
            TabRow(
                tabs = MODE_TABS.map { it.label },
                selectedTabIndex = MODE_TABS.indexOf(mode).coerceAtLeast(0),
                onTabSelected = { index ->
                    MODE_TABS.getOrNull(index)?.let(viewModel::setMode)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 官方统计没拿到时**必须说清楚**：这一页的数以官方统计为准，
        // 退本地口径只是权宜，不明说的话用户会以为"总场次"就是最终答案。
        if (!officialLoaded) {
            item(key = "official-hint") {
                HintBlock(
                    text = officialError?.let { "官方统计没拿到：$it\n下面是本地场次，仅供参考" }
                        ?: "正在获取官方统计…",
                )
            }
        }

        if (blank) {
            item(key = "blank") {
                HintBlock(
                    text = if (officialLoaded) {
                        "这个模式还没有通关记录"
                    } else {
                        "本地还没有对局，先同步一次或导入 JSON"
                    },
                )
            }
        }

        sections.forEach { section ->
            // 塔防 / 时空追猎没有赛季数据，标题是空串 —— 那就不摆标题，
            // 硬写一个「塔防地图」在本来就叫「塔防」的 Tab 底下只会显得啰嗦
            if (section.title.isNotBlank()) {
                item(key = "season_${section.title}") {
                    SmallTitle(text = section.title)
                }
            }
            items(items = section.entries, key = { "map_${it.mapId}" }) { entry ->
                MapCard(
                    entry = entry,
                    difficultyColumns = difficultyColumns,
                    drops = drops[entry.mapId],
                    dropsLoaded = dropsLoaded,
                    dropError = dropError,
                    flipped = entry.mapId in flipped,
                    onFlip = {
                        if (entry.mapId in flipped) {
                            flipped.remove(entry.mapId)
                        } else {
                            flipped.add(entry.mapId)
                        }
                    },
                )
            }
        }
    }
}

/**
 * 一张地图卡：正面统计、背面掉落物，点一下绕 Y 轴翻转。
 *
 * 两面都画在**同一块 [MapCardHeight] 高的容器**里，翻转时只换画哪一面，
 * 所以高度不可能跳 —— 之前用 `Crossfade` 时两面各自撑开高度，翻一下卡片会抖一下。
 */
@Composable
private fun MapCard(
    entry: MapStatEntry,
    difficultyColumns: List<String>,
    drops: List<MapItemDto>?,
    dropsLoaded: Boolean,
    dropError: String?,
    flipped: Boolean,
    onFlip: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "mapCardFlip",
    )
    val density = LocalDensity.current.density

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(MapCardHeight)
            // 底图要贴到卡片的圆角上，得自己裁一次：
            // Miuix 的 Card 只负责画自己的背景，不保证把内容裁进圆角
            .clip(RoundedCornerShape(CardCornerRadius)),
        cornerRadius = CardCornerRadius,
        // 卡片不再有底色，整张脸就是底图 + 压暗层。
        // 给个表面色反而会把底图盖掉，看起来像"图没加载出来"。
        colors = CardDefaults.defaultColors(color = Color.Transparent),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = onFlip,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 只画朝向自己的那一面。Compose 没有 backface-visibility，
            // 两面都画的话背面内容会镜像透出来
            val showingFront = rotation <= 90f
            MapCardFace(
                mapId = entry.mapId,
                angle = if (showingFront) rotation else rotation - 180f,
                density = density,
                blurred = !showingFront,
            ) {
                if (showingFront) {
                    StatFace(
                        entry = entry,
                        difficultyColumns = difficultyColumns,
                        collection = drops,
                    )
                } else {
                    DropFace(
                        entry = entry,
                        drops = drops.orEmpty(),
                        dropsLoaded = dropsLoaded,
                        dropError = dropError,
                    )
                }
            }
        }
    }
}

/**
 * 一个绕 Y 轴转了 [angle] 度的面：底图 + 压暗层 + [content]。
 *
 * [content] 是 `ColumnScope` 的，因为它拿到的就是铺满整张脸那一列。
 */
@Composable
private fun MapCardFace(
    mapId: Int,
    angle: Float,
    density: Float,
    blurred: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = angle
                // 不给相机距离就是正交投影，翻转看起来像"被压扁"而不是转过去。
                // 乘 density 是因为这个值按 px 算，不乘的话在 3x 屏上会贴到脸上
                cameraDistance = 14f * density
            },
    ) {
        val art = mapArtPainter(mapId)
        if (art != null) {
            Image(
                painter = art,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurred) Modifier.blur(3.dp) else Modifier),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CardScrim),
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}

// ---------------- 正面：统计卡片 ----------------

@Composable
private fun ColumnScope.StatFace(
    entry: MapStatEntry,
    difficultyColumns: List<String>,
    collection: List<MapItemDto>?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = entry.name,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${entry.total}",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text(
                    // 官方口径下这个数是服务端的**通关**数（各难度一列排下来加起来正好等于它），
                    // 和小程序 / PC 端显示的是同一个；本地兜底时是本地库里的**场次**
                    // （含没打完和输了的），两个口径差得远，标签必须跟着变。
                    text = if (entry.official) "总通关" else "总场次",
                    modifier = Modifier.padding(top = 1.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CardTextSecondary,
                )
            }
        }

        // 难度一排在中间那段自由高度里居中，卡片多高都不会歪
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                difficultyColumns.forEachIndexed { index, column ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .width(1.dp)
                                .height(18.dp)
                                .background(CardDivider),
                        )
                    }
                    DifficultyCell(
                        name = column,
                        count = entry.byDifficulty.firstOrNull { it.name == column }?.total ?: 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        CollectionBlock(entry = entry, collection = collection)
    }
}

/** 一列难度：上面名字，下面场次。 */
@Composable
private fun DifficultyCell(
    name: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = name,
            modifier = Modifier.fillMaxWidth(),
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = CardTextSecondary,
        )
        Text(
            text = "$count",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 19.sp, stepSize = 0.5.sp),
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Black,
            // 0 场压暗：一屏九张图，只有真打过的难度才该跳出来
            color = if (count > 0) MiuixTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
        )
    }
}

/**
 * 卡片底部那条进度。
 *
 * 有收集品数据时是「核心收集进度 owned / total」，没有时退回「通关率」——
 * 未登录拉不到 `center.map.item.list`，与其把这块留空，不如换个这一页本来就有的指标。
 */
@Composable
private fun ColumnScope.CollectionBlock(
    entry: MapStatEntry,
    collection: List<MapItemDto>?,
) {
    val total = collection?.size ?: 0
    val owned = collection?.count { it.owned } ?: 0
    val hasCollection = total > 0
    val done = hasCollection && owned == total
    val accent = if (done) CollectionDone else MiuixTheme.colorScheme.primary

    val target = if (hasCollection) owned.toFloat() / total else entry.winRate / 100f
    val progress by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "mapCollectionProgress",
    )
    val pulse = rememberPulseAlpha()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = if (hasCollection) "核心收集进度" else "通关率",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.82f),
            )
            if (hasCollection) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$owned",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = accent,
                    )
                    Text(
                        text = " / $total",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CardTextTertiary,
                    )
                }
            } else {
                Text(
                    text = "${entry.winRate}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(accent),
            )
        }
        Text(
            text = "点击查看地图收集品解锁情况",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                // alpha 走 graphicsLayer：呼吸值每帧都在变，
                // 在 composition 里读它等于每帧重组这一行，直接读 .value 就不会
                .graphicsLayer { alpha = pulse.value },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------- 背面：掉落物解锁 ----------------

@Composable
private fun ColumnScope.DropFace(
    entry: MapStatEntry,
    drops: List<MapItemDto>,
    dropsLoaded: Boolean,
    dropError: String?,
) {
    // 背面把底图再糊一层：正面要看清是哪张图，背面的主角是掉落物，
    // 底图糊一点才不会把图标的边缘吃掉
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.2f))
                .padding(start = 12.dp, top = 7.dp, end = 12.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = entry.name,
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = " 掉落物解锁",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                val pulse = rememberPulseAlpha()
                Text(
                    text = "点击返回统计卡片",
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .graphicsLayer { alpha = pulse.value },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            val owned = drops.count { it.owned }
            Row(
                modifier = Modifier.padding(start = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "$owned",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = if (drops.isNotEmpty() && owned == drops.size) {
                        CollectionDone
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                )
                Text(
                    text = "/${drops.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CardTextTertiary,
                )
            }
        }

        if (drops.isEmpty()) {
            // 三种"空"要分开说：没拉到（未登录 / 网络）、正在拉、以及这张图确实没有收集品
            val emptyText = when {
                !dropsLoaded && !dropError.isNullOrBlank() -> "掉落物没拉到：$dropError"
                !dropsLoaded -> "正在拉取掉落物…"
                else -> "这张图没有可解锁的收集品"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyText,
                    fontSize = 12.sp,
                    color = CardTextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // 四列网格。行数按实际件数来、每行每格都吃 weight，
            // 所以件数再多也只是格子变小，永远不会溢出到卡片外面去
            val rows = remember(drops) { drops.chunked(GRID_COLUMNS) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { item ->
                            DropItemCard(
                                item = item,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                        // 最后一行不足四件时把空位补出来，免得剩下几件被拉宽
                        repeat(GRID_COLUMNS - row.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** 一件掉落物：品质外框 + 图标 + 名字，未解锁再加一层锁和去色。 */
@Composable
private fun DropItemCard(
    item: MapItemDto,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            // 外框图本身是半透明的，底下得垫一层深色才压得住底图
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        Image(
            painter = dropFramePainter(
                if (item.quality >= 4) DropFrame.LEGENDARY else DropFrame.EPIC,
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        if (item.quality >= 3) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(0.6f)
                    .blur(12.dp)
                    .background(qualityColor(item.quality).copy(alpha = 0.15f)),
            )
        }
        if (item.icon.isNotBlank()) {
            AsyncImage(
                model = item.icon,
                contentDescription = item.name,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(0.85f),
                contentScale = ContentScale.Fit,
                colorFilter = if (item.owned) null else GrayscaleFilter,
            )
        }
        if (!item.owned) {
            Image(
                painter = dropFramePainter(DropFrame.LOCK),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                alpha = 0.65f,
            )
        }
        Text(
            text = item.name.ifBlank { "未知物品" },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 2.dp, bottom = 3.dp, end = 2.dp),
            // 官方那边是按字数算字号硬塞进一行；这里交给自适应字号，
            // 「白蚁背包-幻巢」六个字也不会被截成「白蚁背包-幻…」
            autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** 品质强调色，取自官方掉落物的品质色表（4 传说 / 3 史诗 / 2 稀有 / 其他灰）。 */
private fun qualityColor(quality: Int): Color = when (quality) {
    4 -> Color(0xFFFFBB00)
    3 -> Color(0xFFA335EE)
    2 -> Color(0xFF0070DD)
    else -> Color(0xFF9D9D9D)
}

/**
 * 卡片上那句"点击……"的呼吸透明度。
 *
 * 返回的是 `State` 本身而不是当帧的值：调用方要把它用在
 * `graphicsLayer { alpha = pulse.value }` 里 —— 在 composition 里读 `.value`
 * 会让这一行每帧重组一次，放在 lambda 里读就只影响绘制阶段。
 */
@Composable
private fun rememberPulseAlpha(): State<Float> {
    val transition = rememberInfiniteTransition(label = "mapCardHint")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mapCardHintAlpha",
    )
}
