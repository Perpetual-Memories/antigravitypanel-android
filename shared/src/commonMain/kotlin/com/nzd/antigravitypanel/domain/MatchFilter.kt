package com.nzd.antigravitypanel.domain

import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.util.startOfServerDay

/** 日期范围。`days` 为 null 表示不限。 */
enum class DateRange(val label: String, val days: Int?) {
    ALL("全部日期", null),
    TODAY("今天", 0),
    LAST_3_DAYS("最近三天", 3),
    LAST_WEEK("最近一周", 7),
    LAST_MONTH("最近一个月", 30),
}

enum class Outcome(val label: String) {
    ALL("全部场次"),
    LOSS("仅显示失败"),
    WIN("仅显示胜利"),
    FAVORITE("仅显示收藏"),
}

/**
 * 战绩筛选条件。五个维度都是单选，与需求里的 filter1~filter5 一一对应。
 *
 * 收藏集合 [favorites] 不放在 data class 里当构造参数，而是单独传给
 * [applyMatchFilter]：它变化频繁（长按一下就变），塞进 data class 会让
 * 每次点收藏都重建整个筛选描述对象，进而把已经加载好的列表判定成"条件变了"。
 */
data class MatchFilter(
    val mode: GameMode? = null,
    val mapId: Int? = null,
    val difficulty: String? = null,
    val range: DateRange = DateRange.ALL,
    val outcome: Outcome = Outcome.ALL,
) {
    /** 是否处于"没筛任何东西"的状态。UI 用它决定要不要显示"已筛选"角标。 */
    val isEmpty: Boolean
        get() = mode == null && mapId == null && difficulty == null &&
            range == DateRange.ALL && outcome == Outcome.ALL
}

private const val SECONDS_PER_DAY = 24L * 3600L

/**
 * 时间下界（含）。`days == 0` 表示当天零点而不是"最近 0 秒"。
 *
 * `eventTimeSec == 0` 是时间解析失败的记录，任何时间筛选都会把它们排除掉——
 * 宁可在"全部日期"里少显示几条，也不要让脏数据看起来像今天的对局。
 */
fun DateRange.cutoffSec(nowSec: Long): Long? = when (val d = days) {
    null -> null
    0 -> startOfServerDay(nowSec)
    else -> nowSec - d * SECONDS_PER_DAY
}

/**
 * 应用筛选 + 排序。纯函数，方便单测。
 *
 * 排序规则：置顶的排最前（置顶之间按时间倒序），其余按时间倒序、同秒用 roomId 兜底，
 * 与 DAO 里 `ORDER BY eventTimeSec DESC, roomId DESC` 保持一致，避免翻页时顺序跳变。
 */
fun applyMatchFilter(
    matches: List<MatchEntity>,
    filter: MatchFilter = MatchFilter(),
    config: GameConfigDto = GameConfigDto(),
    favorites: Set<String> = emptySet(),
    pinned: Set<String> = emptySet(),
    nowSec: Long = 0L,
): List<MatchEntity> {
    val cutoff = filter.range.cutoffSec(nowSec)
    return matches
        .filter { m ->
            if (filter.mode != null && modeOf(m.mapId) != filter.mode) return@filter false
            if (filter.mapId != null && m.mapId != filter.mapId) return@filter false
            // 按"难度档位"比而不是按名字逐字比：没拉到配置时下拉框给的是内置那七个
            // （「折磨」「挑战」），而数据里的名字是「折磨I」「挑战模式」——
            // 直接判等的话按「折磨」一栏筛下去永远一条都出不来。
            if (filter.difficulty != null &&
                !difficultyMatches(difficultyNameOf(m.subModeType, config), filter.difficulty)
            ) {
                return@filter false
            }
            if (cutoff != null && (m.eventTimeSec <= 0L || m.eventTimeSec < cutoff)) {
                return@filter false
            }
            when (filter.outcome) {
                Outcome.ALL -> Unit
                Outcome.WIN -> if (!m.finished || !m.isWin) return@filter false
                Outcome.LOSS -> if (!m.finished || m.isWin) return@filter false
                Outcome.FAVORITE -> if (m.roomId !in favorites) return@filter false
            }
            true
        }
        .sortedWith(
            compareByDescending<MatchEntity> { it.roomId in pinned }
                .thenByDescending { it.eventTimeSec }
                .thenByDescending { it.roomId },
        )
}

/** 当前数据里出现过的地图，按场次从多到少排，给"全部地图"下拉用。 */
fun availableMaps(
    matches: List<MatchEntity>,
    config: GameConfigDto = GameConfigDto(),
    mode: GameMode? = null,
): List<Pair<Int, String>> = matches
    .filter { mode == null || modeOf(it.mapId) == mode }
    .groupBy { it.mapId }
    .map { (mapId, list) -> mapId to Pair(list.size, mapNameOf(mapId, config)) }
    .sortedWith(compareByDescending<Pair<Int, Pair<Int, String>>> { it.second.first }
        .thenBy { it.first })
    .map { it.first to it.second.second }
