package com.nzd.antigravitypanel.domain

import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.util.SERVER_UTC_OFFSET_SECONDS
import com.nzd.antigravitypanel.util.civilFromDays
import com.nzd.antigravitypanel.util.startOfServerDay

/**
 * 每日简报：把"某一天打了什么"压成四张数字卡 + 一条时间线。
 *
 * 口径全部来自**本地库**（`matches` 表），不额外打接口。理由和历史页一致：
 * 同步链路每次冷启动 / 手动刷新都会把三个模式的列表落库，本地那份就是最新的；
 * 而按天切数据这件事，本地 SQL 全表扫一遍比"给每一天都去翻服务端分页"更稳
 * （服务端只保留近 30 天 / 最近 100 场，翻不到的日子会静默变成 0 场）。
 *
 * 这里全是纯函数，`nowSec` 由调用方传入 —— 跨天、时区这类事必须可测。
 */

/** 简报能回看几天（今天 + 前 6 天）。和 PC 端的日期条一致。 */
const val BRIEFING_DAY_COUNT = 7

private const val SECONDS_PER_DAY = 24L * 3600L

/**
 * 日期条上的一格。
 *
 * @param startSec 这一天的零点（北京时间，epoch 秒）。
 * @param label 日期条上的短标签：今天 / 昨天 / `M月d日`。
 * @param title 卡片标题用的完整日期：`M月d日`。今天也照实写日期 ——
 *   「今天场次」在第二天回看时会变成一句谎话，而卡片标题一刷新就换掉又显得跳。
 */
data class BriefingDay(
    val startSec: Long,
    val label: String,
    val title: String,
)

/**
 * 最近 [count] 天，**从今天往前**排。
 *
 * 日期按北京时间切（[startOfServerDay]）：服务端按东八区出数，用设备时区的话
 * 改了系统时区就会把同一场对局算到别的日子上。
 */
fun briefingDays(nowSec: Long, count: Int = BRIEFING_DAY_COUNT): List<BriefingDay> {
    if (count <= 0) return emptyList()
    val today = startOfServerDay(nowSec)
    return (0 until count).map { offset ->
        val startSec = today - offset * SECONDS_PER_DAY
        // startSec 已经是"北京时间当天零点"，加回偏移后正好落在整天的边界上
        val (_, month, day) = civilFromDays((startSec + SERVER_UTC_OFFSET_SECONDS) / SECONDS_PER_DAY)
        BriefingDay(
            startSec = startSec,
            label = when (offset) {
                0 -> "今天"
                1 -> "昨天"
                else -> "${month}月${day}日"
            },
            title = "${month}月${day}日",
        )
    }
}

/** 模式分布里的一行。顺序固定按 [COUNTABLE_MODES]，0 场的模式也留着。 */
data class BriefingModeCount(
    val mode: GameMode,
    val count: Int,
)

/**
 * 一天的简报。
 *
 * @param matches 当天的对局，**按时间升序**（时间线从上往下读就是发生顺序）。
 * @param activeHour 场次最多的那个小时（0~23）。没对局时为 null。
 * @param playtimeSec 当天所有对局 `iDuration` 之和。
 * @param hourCounts 24 个整点各开了几局。活跃时段弹层里的柱状图直接用它。
 * @param mvpCount 拿过 MVP（`Rank == 1`）的局数。
 * @param longestWinStreak 当天最长连胜（按开赛时间顺序，中途退出不打断，输了才断）。
 * @param topMapId 当天打得最多的地图。并列时取**最早出现**的那张（时间线从上往下扫到的第一张）。
 */
data class DailyBriefing(
    val dayStartSec: Long,
    val matches: List<MatchEntity>,
    val winCount: Int,
    val lossCount: Int,
    val unfinishedCount: Int,
    val playtimeSec: Int,
    val activeHour: Int?,
    val modeCounts: List<BriefingModeCount>,
    val hourCounts: List<Int>,
    val mvpCount: Int,
    val longestWinStreak: Int,
    val topMapId: Int?,
) {
    val matchCount: Int get() = matches.size

    /** 分出了胜负的局数。通关率的分母用它 —— 中途退出的局既不是通关也不是失败。 */
    val finishedCount: Int get() = winCount + lossCount

    /** 场次最多的模式。并列时按 [COUNTABLE_MODES] 的顺序取第一个；一局没有则为 null。 */
    val topMode: BriefingModeCount?
        get() {
            val max = modeCounts.maxOfOrNull { it.count } ?: 0
            if (max <= 0) return null
            return modeCounts.firstOrNull { it.count == max }
        }
}

/**
 * 汇总某一天。
 *
 * 只算三个**计入统计**的模式（猎场 / 塔防 / 时空追猎），机甲战刻意排除：
 * 它没有胜负、没有 Boss 口径，混进来会让"场次"和"模式分布"两栏都读不懂。
 * 与地图分布页、概览的模式切换同一个口径。
 *
 * ⚠️ **不按 openid 过滤**（0.2.5 第一版就是这么写的，表现是整页每天全 0）。
 * 对局表里的 `vOpenID` 与凭证里的 `openid` **不保证是同一个值**（微信区的凭证走
 * `ieg_ams_*`，openid 很可能不是游戏账号 id），拿它们判等会把整个库筛成空。
 * 历史页同样不筛账号，这里跟它保持一致：设备上本来就只有自己的数据，
 * 换号残留的对局也一直留在库里。
 */
fun buildDailyBriefing(
    matches: List<MatchEntity>,
    dayStartSec: Long,
): DailyBriefing {
    val dayEnd = dayStartSec + SECONDS_PER_DAY
    val dayMatches = matches.asSequence()
        .filter { it.eventTimeSec in dayStartSec until dayEnd }
        .filter { modeOf(it.mapId).countable }
        .sortedBy { it.eventTimeSec }
        .toList()

    val hourHistogram = IntArray(24)
    var playtime = 0
    var win = 0
    var loss = 0
    var unfinished = 0
    var mvp = 0
    var streak = 0
    var longestStreak = 0
    val modeCounts = COUNTABLE_MODES.associateWith { 0 }.toMutableMap()
    // LinkedHashMap：插入顺序就是开赛顺序，maxByOrNull 取到第一个最大值，
    // 于是"并列最多"时落在最早打的那张图上。
    val mapCounts = LinkedHashMap<Int, Int>()

    for (match in dayMatches) {
        if (match.duration > 0) playtime += match.duration
        hourHistogram[hourOf(match.eventTimeSec)]++
        modeOf(match.mapId).let { mode -> modeCounts[mode] = (modeCounts[mode] ?: 0) + 1 }
        mapCounts[match.mapId] = (mapCounts[match.mapId] ?: 0) + 1
        if (match.finished && match.rankValue == 1) mvp++
        // 连胜：按升序扫一遍，赢 +1、输清零；中途退出的局既不算赢也不算输，
        // **不打断**连胜（它是"没打完"，不是"输了"）。
        if (match.finished) {
            if (match.isWin) {
                streak++
                if (streak > longestStreak) longestStreak = streak
            } else {
                streak = 0
            }
        }
        when {
            !match.finished -> unfinished++
            match.isWin -> win++
            else -> loss++
        }
    }

    val peak = hourHistogram.maxOrNull() ?: 0
    // 并列时取最早的那个小时：PC 端的高峰提示也是这么给的，
    // 而且"今天从 6 点开始连打"比"12 点也打了几局"更贴近用户对"最活跃"的直觉。
    val activeHour = if (peak > 0) hourHistogram.indexOfFirst { it == peak } else null

    return DailyBriefing(
        dayStartSec = dayStartSec,
        matches = dayMatches,
        winCount = win,
        lossCount = loss,
        unfinishedCount = unfinished,
        playtimeSec = playtime,
        activeHour = activeHour,
        modeCounts = COUNTABLE_MODES.map { BriefingModeCount(it, modeCounts[it] ?: 0) },
        hourCounts = hourHistogram.toList(),
        mvpCount = mvp,
        longestWinStreak = longestStreak,
        topMapId = mapCounts.entries.maxByOrNull { it.value }?.key,
    )
}

/** 对局开始时刻落在北京时间哪个整点。 */
fun hourOf(epochSec: Long): Int {
    val local = epochSec + SERVER_UTC_OFFSET_SECONDS
    val timeOfDay = ((local % SECONDS_PER_DAY) + SECONDS_PER_DAY) % SECONDS_PER_DAY
    return (timeOfDay / 3600).toInt()
}
