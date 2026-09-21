package com.nzd.antigravitypanel.domain

import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.data.remote.dto.MapStatsDto

/**
 * 单张地图的统计。地图分布页的一张卡片就是一个实例。
 *
 * 两种口径，看 [official]：
 * - **官方**（true）：来自 `center.user.map.stats`，数是**通关**，服务端口径，
 *   和官方小程序 / PC 端显示得一模一样。
 * - **本地**（false）：来自本地库，数是**场次**（含没打完、输了的）。
 *   没登录 / 只有 JSON 导入时只能用它。
 */
data class MapStatEntry(
    val mapId: Int,
    val name: String,
    val total: Int,
    /** 通关数。**只有本地口径有值**，官方口径下是 0（服务端不细分这个）。 */
    val win: Int,
    /** 难度 -> 场次，按场次降序。key 可能为 null（难度表里查不到）。 */
    val byDifficulty: List<DifficultyCount>,
    /** true 表示 [total] 与 [byDifficulty] 走的是官方 `center.user.map.stats`。 */
    val official: Boolean = false,
    /**
     * 只有官方口径才填：本地算出来的通关率（0~100）。
     *
     * 官方口径下 [total] 是"通关数"，拿它当分母算通关率必然是 100%，
     * 所以那条进度得用本地的 win/plays 另算一个。
     */
    val localWinRate: Int = 0,
) {
    /** 通关率，0~100。没有打完的局时返回 0，别显示 NaN。 */
    val winRate: Int get() = when {
        total == 0 -> 0
        official -> localWinRate
        else -> win * 100 / total
    }
}

data class DifficultyCount(
    val name: String,
    val total: Int,
    val win: Int,
)

/**
 * 按地图聚合。
 *
 * 数据源是**本地库**而不是 `center.user.map.stats`：后者要按模式传中文参数、
 * 且只覆盖服务端滚动窗口内的对局，而本地库是唯一完整的历史来源（P2 的全部意义就在这）。
 *
 * 机甲排位（[GameMode.MECHA]）默认排除，与 NZM 的统计口径一致。
 */
fun aggregateByMap(
    matches: List<MatchEntity>,
    config: GameConfigDto = GameConfigDto(),
    mode: GameMode? = GameMode.HUNT,
    includeMecha: Boolean = false,
): List<MapStatEntry> {
    val grouped = LinkedHashMap<Int, MutableList<MatchEntity>>()
    for (m in matches) {
        val resolved = modeOf(m.mapId)
        if (!includeMecha && resolved == GameMode.MECHA) continue
        if (mode != null && resolved != mode) continue
        grouped.getOrPut(m.mapId) { ArrayList() }.add(m)
    }

    return grouped.entries
        .map { (mapId, list) ->
            val mapMode = modeOf(mapId)
            // 分组键用**档位名**（difficultyBucketOf）而不是原始难度名：
            // 官方把 折磨I~VI 合成一列「折磨」、把塔防的「默认」认成「挑战」。
            // 用原始名分组的话，塔防和时空追猎会横着排出六列折磨，而且塔防多一列「默认」。
            // 解析不出来时**带上编号**（官方是 `难度${iSubModeType}`），不要糊成一个「未知」桶：
            // 几个不同编号会并成一列，既看不出有几个难度，也丢了"到底哪个号没覆盖到"这条线索。
            val byDifficulty = list
                .groupBy { difficultyBucketOf(it.subModeType, mapMode, config) ?: "难度${it.subModeType}" }
                .map { (name, sub) ->
                    DifficultyCount(
                        name = name,
                        total = sub.size,
                        win = sub.count { it.finished && it.isWin },
                    )
                }
                .sortedWith(compareByDescending<DifficultyCount> { it.total }.thenBy { it.name })
            MapStatEntry(
                mapId = mapId,
                name = mapNameOf(mapId, config),
                total = list.size,
                win = list.count { it.finished && it.isWin },
                byDifficulty = byDifficulty,
            )
        }
        .sortedWith(compareByDescending<MapStatEntry> { it.total }.thenBy { it.mapId })
}

/**
 * **官方口径**：把 `center.user.map.stats` 的结果按地图、按难度摊开。
 *
 * 这就是官方小程序 / PC 端显示的那套数，算法照官方前端的 `GC()` 逐行搬：
 *
 * ```js
 * let s = Object.values(mapInfo).filter(e => e.mode === mode);
 * for (let e of s) {
 *     let n = e.name;
 *     if (n.includes('新手关') || n.includes('竞速') || n === '月海火线') continue;
 *     let a = stats.find(x => String(x.map_id) === String(e.id));
 *     let o = a ? a.total : 0;
 *     let d = {};
 *     if (e.dungeonList) for (let t of e.dungeonList) {
 *         let cnt = a?.mapModeData?.[t.id] || 0;
 *         let name = difficultyInfo[t.difficulty]?.name || '未知';
 *         name.includes('折磨') && (name = '折磨');
 *         (name === '挑战模式' || name === '默认' || name === '挑战') && (name = '挑战');
 *         d[name] = (d[name] || 0) + cnt;
 *     }
 *     let p = Object.entries(d).map(([l,c]) => ({label:l, count:c}))
 *              .sort((x,y) => (Hs[x.label]||99) - (Hs[y.label]||99));
 * }
 * ```
 *
 * 三个容易做错的点：
 *
 * 1. **`mapModeData` 的 key 是副本 id，不是 `iSubModeType`**。必须拿地图自己的
 *    `dungeonList` 翻译（`dungeon.difficulty` 才是 `iSubModeType`）。直接拿
 *    `iSubModeType` 去查 `mapModeData` 一条都查不到。
 * 2. **这张图支持的难度即使 0 场也要摆出来**（官方就是 `d[name] = (d[name]||0) + cnt`）。
 *    一张图到底支持哪些难度本身就是信息，按实际场次过滤的话每张卡的列数都不一样。
 * 3. **跳过新手关 / 竞速 / 月海火线**，官方是这么过滤的（等价于 `supportStatistics=false`）。
 *
 * @param localWins 本地算出来的 `mapId -> 通关率`，只用来给卡片底部那条进度提供分母。
 */
fun buildOfficialMapStats(
    stats: List<MapStatsDto>,
    config: GameConfigDto = GameConfigDto(),
    mode: GameMode = GameMode.HUNT,
    localWins: Map<Int, Int> = emptyMap(),
): List<MapStatEntry> {
    val serverMode = mode.serverMode
    val byMapId = stats.associateBy { it.map_id }

    return config.mapInfo.values
        .asSequence()
        .filter { it.mode == serverMode }
        // 官方按名字排除这三类（等价于 supportStatistics = false）
        .filterNot {
            it.name.contains("新手关") || it.name.contains("竞速") || it.name == "月海火线"
        }
        .map { info ->
            val hit = byMapId[info.id]
            val counts = LinkedHashMap<String, Int>()
            for (dungeon in info.dungeonList.orEmpty()) {
                val name = difficultyBucketOf(dungeon.difficulty, mode, config) ?: continue
                val count = hit?.mapModeData?.get(dungeon.id.toString()) ?: 0
                counts[name] = (counts[name] ?: 0) + count
            }
            MapStatEntry(
                mapId = info.id,
                // 名字用 mapNameOf 兜一层：配置了不法时是官方名，
                // 配置里这张图压根没下发时（概率极低但发生过）退到内置表
                name = mapNameOf(info.id, config),
                total = hit?.total ?: 0,
                win = 0,
                byDifficulty = counts
                    .map { (name, count) -> DifficultyCount(name, count, 0) }
                    .sortedWith(compareBy<DifficultyCount> { difficultyRank(it.name) }.thenBy { it.name }),
                official = true,
                localWinRate = localWins[info.id] ?: 0,
            )
        }
        .toList()
}
/**
 * 猎场的赛季归属，新的在前 —— 官方前端的展示顺序就是从 S3 一路排到 S0。
 *
 * 按 **mapId** 分组而不是按地图名：名字会随 `center.config.list` 下发的内容变
 * （时空追猎那几张在配置里叫「根除异变」，我们的兜底表里叫「根除变异」），
 * 拿名字当 key 迟早在某个模式上对不上。id 是稳定的。
 *
 * 塔防 / 时空追猎**没有**赛季划分：官方没下发这张表，而 `SeasonId` 字段实测在真实对局
 * 里恒为空串（`HarFixtures` 里的 10 条样例全是 `""`），拿不到就别编。
 */
private val HUNT_SEASONS: List<Pair<String, List<Int>>> = listOf(
    "S3 赛季" to listOf(18, 15),
    "S2 赛季" to listOf(13, 19),
    "S1 赛季" to listOf(16, 17),
    "S0 赛季" to listOf(12, 14, 21),
)

/**
 * 展示用的地图 id（含**本地没打过**的），次序照官方前端。
 *
 * 只有塔防和时空追猎在这里 —— 猎场的次序在 [HUNT_SEASONS] 里，那张表本身就已经
 * 按赛季把九张图排完了，再留一份只会两边不同步。
 *
 * 不含：
 * - 新手关（30 / 308 / 324）—— 不是正经地图
 * - 猎场竞速的旧 id（112 / 114 / 115）—— 名字和 12 / 14 / 21 完全一样，
 *   一起列会在「黑暗复活节」下面出现两张一模一样的卡
 *
 * 这两类只要本地真有对局，仍会被补到最后一节里（见 [groupMapsBySeason]），不丢数据。
 *
 * 月海火线（424）不在里面的原因见 [buildOfficialMapStats]：官方明确跳过它，
 * 摆上去就是一张永远 0 场的卡。
 */
private val DISPLAY_MAP_IDS: Map<GameMode, List<Int>> = mapOf(
    GameMode.TOWER to listOf(310, 309, 304, 306, 300),
    GameMode.TIME_HUNT to listOf(323, 322, 321),
)

/** 表外地名（打过但不在 [DISPLAY_MAP_IDS] 里）归到这一节。 */
private const val LEFTOVER_SECTION = "其他地图"

/** 地图分布页的一节：一个赛季（或一个模式的全部地图）+ 它下面的卡片。 */
data class MapSection(
    /**
     * 节标题。**空串表示不摆标题** —— 塔防和时空追猎没有赛季数据，
     * 硬安一个「塔防地图」当标题，在已经叫「塔防」的那个 Tab 底下只会显得啰嗦。
     */
    val title: String,
    val entries: List<MapStatEntry>,
)

/**
 * 按赛季（猎场）/ 模式（塔防、时空追猎）分节，并把没打过的地图补成 0 场。
 *
 * **补零是有意的**：分节之后"这个赛季有哪几张图"本身就是信息。只列打过的那几张，
 * S0 会只剩一张卡，看不出这个赛季一共几张图。官方 PC 端也是这么做的 ——
 * 它遍历 `config.mapInfo` 里该模式的全部地图，没有统计的填 0。
 *
 * 节内次序用官方次序而**不是**场次：卡片按赛季固定之后，位置就该是稳定的，
 * 否则"销金之城在哪一节第几张"每次同步完都变。
 */
fun groupMapsBySeason(
    mode: GameMode,
    stats: List<MapStatEntry>,
    config: GameConfigDto = GameConfigDto(),
): List<MapSection> {
    val byId = stats.associateBy { it.mapId }
    val sections = when (mode) {
        // 猎场的次序就是赛季表的次序：S3 -> S2 -> S1 -> S0
        GameMode.HUNT -> HUNT_SEASONS
        // 没有赛季数据：整个模式一节，且不摆标题（见 [MapSection.title]）
        GameMode.TOWER, GameMode.TIME_HUNT -> listOf("" to DISPLAY_MAP_IDS[mode].orEmpty())
        else -> emptyList()
    }

    val placed = sections.flatMapTo(mutableSetOf()) { it.second }
    val result = sections
        .map { (title, ids) -> MapSection(title, ids.map { entryOf(it, byId, config) }) }
        // 表里一张图都没有时（配置还没拉到、表也没覆盖），别留一个空标题在那儿
        .filter { it.entries.isNotEmpty() }

    val leftovers = stats.map { it.mapId }.filter { it !in placed }
    return if (leftovers.isEmpty()) {
        result
    } else {
        result + MapSection(LEFTOVER_SECTION, leftovers.map { entryOf(it, byId, config) })
    }
}

/** 没打过这张图时补一个全 0 的条目，卡片照画，只是数字都是 0。 */
private fun entryOf(
    mapId: Int,
    byId: Map<Int, MapStatEntry>,
    config: GameConfigDto,
): MapStatEntry = byId[mapId] ?: MapStatEntry(
    mapId = mapId,
    name = mapNameOf(mapId, config),
    total = 0,
    win = 0,
    byDifficulty = emptyList(),
)
