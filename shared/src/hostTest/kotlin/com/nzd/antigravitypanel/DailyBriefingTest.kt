package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.domain.BRIEFING_DAY_COUNT
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.briefingDays
import com.nzd.antigravitypanel.domain.buildDailyBriefing
import com.nzd.antigravitypanel.domain.hourOf
import com.nzd.antigravitypanel.ui.format.formatBriefingPlaytime
import com.nzd.antigravitypanel.ui.format.formatHourRange
import com.nzd.antigravitypanel.util.parseServerTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 每日简报的口径。
 *
 * 期望值全部手算：日期用"2026-11-04"这种能一眼看出跨月的日子，
 * 时长和场次写成注释里的算式，实现改坏了能立刻对照出是哪儿偏了。
 */
class DailyBriefingTest {

    // ---- 日期条 ----

    @Test
    fun 日期条是今天往前数七天() {
        val now = parseServerTime("2026-11-04 14:00:00")!!
        val days = briefingDays(now)

        assertEquals(BRIEFING_DAY_COUNT, days.size)
        assertEquals("今天", days[0].label)
        assertEquals("昨天", days[1].label)
        // 11-04 往前：11-03(昨天) / 11-02 / 11-01 / 10-31 / 10-30 / 10-29
        assertEquals("11月2日", days[2].label)
        assertEquals("11月1日", days[3].label)
        assertEquals("10月31日", days[4].label)
        assertEquals("10月29日", days[6].label)
        // 标题一律是完整月日（今天也照实写），卡片标题不跟着日期变
        assertEquals("11月4日", days[0].title)
        assertEquals("10月29日", days[6].title)
    }

    @Test
    fun 日期按北京时间切而不是设备时区() {
        // UTC 2026-11-03 17:30 = 北京时间 11-04 01:30，应该算「今天 = 11-04」
        val now = parseServerTime("2026-11-04 01:30:00")!!
        val days = briefingDays(now)
        assertEquals("11月4日", days[0].title)
        // 相邻两天的起点正好差 86400 秒
        assertEquals(86_400L, days[0].startSec - days[1].startSec)
    }

    // ---- 一天的聚合 ----

    @Test
    fun 汇总场次时长时段与模式分布() {
        val day = parseServerTime("2026-11-04 00:00:00")!!
        val matches = listOf(
            match("a", "2026-11-04 06:22:00", mapId = 17, duration = 1174, win = true),
            match("b", "2026-11-04 06:54:00", mapId = 17, duration = 1292, win = true),
            match("c", "2026-11-04 08:19:00", mapId = 17, duration = 411, win = false),
            match("d", "2026-11-04 23:10:00", mapId = 300, duration = 600, win = true),
        )

        val briefing = buildDailyBriefing(matches, day)

        assertEquals(4, briefing.matchCount)
        // 1174 + 1292 + 411 + 600 = 3477 秒 = 57 分 57 秒
        assertEquals(3477, briefing.playtimeSec)
        // 6 点两场最多（8 点和 23 点各一场）
        assertEquals(6, briefing.activeHour)
        assertEquals(3, briefing.winCount)
        assertEquals(1, briefing.lossCount)
        assertEquals(0, briefing.unfinishedCount)

        // 顺序固定：猎场 / 塔防 / 时空追猎，0 场的也留着
        assertEquals(listOf(GameMode.HUNT, GameMode.TOWER, GameMode.TIME_HUNT), briefing.modeCounts.map { it.mode })
        assertEquals(listOf(3, 1, 0), briefing.modeCounts.map { it.count })
        assertEquals(GameMode.HUNT, briefing.topMode?.mode)

        // 24 格柱状图：6 点两场，8 点 / 23 点各一场，其余为 0
        assertEquals(24, briefing.hourCounts.size)
        assertEquals(2, briefing.hourCounts[6])
        assertEquals(1, briefing.hourCounts[8])
        assertEquals(1, briefing.hourCounts[23])
        assertEquals(0, briefing.hourCounts[7])

        // 胜胜负胜 ⇒ 最长连胜 2
        assertEquals(2, briefing.longestWinStreak)
        assertEquals(0, briefing.mvpCount)
        assertEquals(4, briefing.finishedCount)
    }

    @Test
    fun MVP与最长连胜按开赛顺序算() {
        val day = parseServerTime("2026-11-04 00:00:00")!!
        val briefing = buildDailyBriefing(
            listOf(
                match("a", "2026-11-04 08:00:00", mapId = 17, win = true, rank = 1),
                match("b", "2026-11-04 09:00:00", mapId = 17, win = true, rank = 2),
                // 中途退出：既不算赢也不算输，**不打断**连胜
                match("c", "2026-11-04 10:00:00", mapId = 17, win = false, finished = false),
                match("d", "2026-11-04 11:00:00", mapId = 17, win = true, rank = 1),
                match("e", "2026-11-04 12:00:00", mapId = 17, win = false),
            ),
            day,
        )
        assertEquals(2, briefing.mvpCount)
        // 赢赢（退出）赢 ⇒ 3 连胜，随后输了一局
        assertEquals(3, briefing.longestWinStreak)
        assertEquals(3, briefing.winCount)
        assertEquals(1, briefing.lossCount)
        assertEquals(1, briefing.unfinishedCount)
        assertEquals(4, briefing.finishedCount)
    }

    @Test
    fun 时间线按时间升序() {
        val day = parseServerTime("2026-11-04 00:00:00")!!
        val matches = listOf(
            match("late", "2026-11-04 21:00:00", mapId = 17),
            match("early", "2026-11-04 06:00:00", mapId = 17),
            match("mid", "2026-11-04 12:00:00", mapId = 17),
        )

        val ids = buildDailyBriefing(matches, day).matches.map { it.roomId }
        assertEquals(listOf("early", "mid", "late"), ids)
    }

    @Test
    fun 只统计当天且排除机甲() {
        val day = parseServerTime("2026-11-04 00:00:00")!!
        val matches = listOf(
            match("today", "2026-11-04 10:00:00", mapId = 17),
            // 前一天的
            match("yesterday", "2026-11-03 23:59:00", mapId = 17),
            // 机甲战（iMapId >= 1000）：不进简报口径
            match("mecha", "2026-11-04 07:00:00", mapId = 1000),
        )

        val briefing = buildDailyBriefing(matches, day)
        assertEquals(listOf("today"), briefing.matches.map { it.roomId })
        assertEquals(1, briefing.matchCount)
    }

    @Test
    fun 未完成的对局不算胜负但仍然计时() {
        val day = parseServerTime("2026-11-04 00:00:00")!!
        val briefing = buildDailyBriefing(
            listOf(
                match("quit", "2026-11-04 09:00:00", mapId = 17, duration = 300, win = false, finished = false),
            ),
            day,
        )
        assertEquals(1, briefing.unfinishedCount)
        assertEquals(0, briefing.lossCount)
        assertEquals(0, briefing.winCount)
        assertEquals(300, briefing.playtimeSec)
    }

    @Test
    fun 不按账号过滤() {
        val day = parseServerTime("2026-11-04 00:00:00")!!
        val matches = listOf(
            match("mine", "2026-11-04 10:00:00", mapId = 17, openId = "openid-a"),
            // 微信区的凭证 openid 和战绩里的 vOpenID 不一定是同一个值；
            // 早期导入的数据更是十六进制 / 十进制两套写法。判等会把整库筛空，所以不筛。
            match("other-format", "2026-11-04 11:00:00", mapId = 17, openId = "openid-b"),
            match("legacy", "2026-11-04 12:00:00", mapId = 17, openId = ""),
        )

        assertEquals(3, buildDailyBriefing(matches, day).matchCount)
    }

    @Test
    fun 没有对局时各格都是空值() {
        val day = parseServerTime("2026-11-04 00:00:00")!!
        val briefing = buildDailyBriefing(emptyList(), day)

        assertEquals(0, briefing.matchCount)
        assertEquals(0, briefing.playtimeSec)
        assertNull(briefing.activeHour)
        assertNull(briefing.topMode)
        assertEquals(listOf(0, 0, 0), briefing.modeCounts.map { it.count })
    }

    @Test
    fun 最活跃时段并列时取最早的那个小时() {
        val day = parseServerTime("2026-11-04 00:00:00")!!
        val briefing = buildDailyBriefing(
            listOf(
                match("a", "2026-11-04 12:10:00", mapId = 17),
                match("b", "2026-11-04 20:10:00", mapId = 17),
                match("c", "2026-11-04 20:40:00", mapId = 17),
                match("d", "2026-11-04 12:50:00", mapId = 17),
            ),
            day,
        )
        // 12 点和 20 点各两场 —— 取最早的 12 点
        assertEquals(12, briefing.activeHour)
    }

    @Test
    fun 小时按北京时间取整() {
        assertEquals(6, hourOf(parseServerTime("2026-11-04 06:59:59")!!))
        assertEquals(0, hourOf(parseServerTime("2026-11-04 00:00:00")!!))
        assertEquals(23, hourOf(parseServerTime("2026-11-04 23:59:59")!!))
    }

    // ---- 展示格式 ----

    @Test
    fun 时长文案到小时就够() {
        assertEquals("0分", formatBriefingPlaytime(0))
        assertEquals("不足1分", formatBriefingPlaytime(40))
        assertEquals("57分", formatBriefingPlaytime(57 * 60 + 57))
        assertEquals("2小时16分", formatBriefingPlaytime(2 * 3600 + 16 * 60 + 20))
        // 正好一小时不写 0 分
        assertEquals("2小时", formatBriefingPlaytime(2 * 3600))
    }

    @Test
    fun 活跃时段文案() {
        assertEquals("—", formatHourRange(null))
        assertEquals("06:00 - 07:00", formatHourRange(6))
        assertEquals("00:00 - 01:00", formatHourRange(0))
        // 23 点收尾是次日 0 点，不写成 24:00
        assertEquals("23:00 - 00:00", formatHourRange(23))
    }

    // ---- 夹具 ----

    private fun match(
        roomId: String,
        eventTime: String,
        mapId: Int,
        duration: Int = 1200,
        win: Boolean = true,
        finished: Boolean = true,
        openId: String = "",
        rank: Int = 0,
    ): MatchEntity {
        val sec = assertNotNull(parseServerTime(eventTime), "测试数据里的时间必须能解析")
        return MatchEntity(
            roomId = roomId,
            openId = openId,
            eventTime = eventTime,
            eventTimeSec = sec,
            startTime = eventTime,
            areaId = "",
            finTime = if (finished) sec.toInt() else 0,
            duration = duration,
            isWin = win,
            gameMode = 0,
            modeType = 0,
            subModeType = 4,
            mapId = mapId,
            score = 0,
            seasonId = "4",
            kills = 0,
            deaths = 0,
            assists = 0,
            rankValue = rank,
            finished = finished,
            syncedAtSec = 0,
        )
    }
}
