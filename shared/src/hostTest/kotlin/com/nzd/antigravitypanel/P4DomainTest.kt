package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.db.toEntity
import com.nzd.antigravitypanel.data.remote.dto.ConfigListResponseDto
import com.nzd.antigravitypanel.data.remote.dto.DifficultyDto
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.data.remote.dto.GameRecordDto
import com.nzd.antigravitypanel.data.remote.IdeJson
import com.nzd.antigravitypanel.data.remote.dto.MapInfoDto
import com.nzd.antigravitypanel.data.remote.dto.MapStatsDto
import com.nzd.antigravitypanel.data.repo.RecentFive
import com.nzd.antigravitypanel.data.repo.formatPlaytime
import com.nzd.antigravitypanel.data.repo.summarizeLocalRecent
import com.nzd.antigravitypanel.data.repo.summarizeRecent
import com.nzd.antigravitypanel.domain.ActivityEvent
import com.nzd.antigravitypanel.domain.DateRange
import com.nzd.antigravitypanel.domain.DifficultyCount
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.MapStatEntry
import com.nzd.antigravitypanel.domain.MatchFilter
import com.nzd.antigravitypanel.domain.Outcome
import com.nzd.antigravitypanel.domain.aggregateByMap
import com.nzd.antigravitypanel.domain.applyMatchFilter
import com.nzd.antigravitypanel.domain.availableMaps
import com.nzd.antigravitypanel.domain.buildOfficialMapStats
import com.nzd.antigravitypanel.domain.difficultyBucketOf
import com.nzd.antigravitypanel.domain.difficultyNameOf
import com.nzd.antigravitypanel.domain.difficultyOptions
import com.nzd.antigravitypanel.domain.difficultyOptionsForMode
import com.nzd.antigravitypanel.domain.difficultyRank
import com.nzd.antigravitypanel.domain.groupMapsBySeason
import com.nzd.antigravitypanel.domain.mergeMapStats
import com.nzd.antigravitypanel.domain.mapNameOf
import com.nzd.antigravitypanel.domain.modeOf
import com.nzd.antigravitypanel.domain.parseCalendar
import com.nzd.antigravitypanel.domain.upcomingActivities
import com.nzd.antigravitypanel.util.parseServerTime
import com.nzd.antigravitypanel.util.startOfServerDay
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P4 新增的领域逻辑：模式归属、五维筛选、地图聚合、活动解析、近五场聚合。
 *
 * 这些都是纯函数，不碰网络也不碰数据库，放在这里是为了让"筛出来的结果对不对"
 * 这种问题不用装到手机上点半天。
 */
class P4DomainTest {

    // ---------------- 模式归属 ----------------

    @Test
    fun modeOfRecognizesEveryKnownGroup() {
        assertEquals(GameMode.HUNT, modeOf(12))
        assertEquals(GameMode.HUNT, modeOf(115))
        assertEquals(GameMode.TOWER, modeOf(304))
        assertEquals(GameMode.TIME_HUNT, modeOf(322))
        assertEquals(GameMode.MECHA, modeOf(1000))
        assertEquals(GameMode.MECHA, modeOf(1234))
        assertEquals(GameMode.UNKNOWN, modeOf(0))
        assertEquals(GameMode.UNKNOWN, modeOf(999))
    }

    @Test
    fun mechaIsNotCountable() {
        // 机甲战不计入统计口径，但概览的下拉里要能切换到它
        assertEquals(false, GameMode.MECHA.countable)
        assertEquals(true, GameMode.HUNT.countable)
        assertEquals(true, GameMode.TOWER.countable)
        assertEquals(true, GameMode.TIME_HUNT.countable)
    }

    // ---------------- 名字解析 ----------------

    private val config = GameConfigDto(
        mapInfo = mapOf(
            "304" to MapInfoDto(id = 304, name = "黑暗复活节"),
            "12" to MapInfoDto(id = 12, name = "沙漠神殿"),
        ),
        difficultyInfo = mapOf(
            "3" to DifficultyDto(id = 3, name = "炼狱"),
            "5" to DifficultyDto(id = 5, name = "普通"),
        ),
    )

    @Test
    fun mapNameFallsBackToUnknown() {
        assertEquals("黑暗复活节", mapNameOf(304, config))
        assertEquals("未知(777)", mapNameOf(777, config))
    }

    @Test
    fun difficultyNameResolvesFromConfig() {
        assertEquals("炼狱", difficultyNameOf(3, config))
        assertNull(difficultyNameOf(99, config))
    }

    @Test
    fun difficultyNameFallsBackToOfficialTableWithoutConfig() {
        // 这一条锁的是真实线上 bug：`center.config.list` 只在**有凭证**时拉得到，
        // 只导入 JSON 没登录的用户难度表是空的，于是每张图的难度全部塌成一个「未知 n」
        // （n 恰好等于总场次，因为所有对局都进了同一个桶）。没有内置兜底表就是这个结果。
        assertEquals("英雄", difficultyNameOf(4))
        assertEquals("炼狱", difficultyNameOf(5))
        assertEquals("折磨I", difficultyNameOf(6))
        assertEquals("折磨VI", difficultyNameOf(11))
        assertEquals("挑战模式", difficultyNameOf(12))
        assertEquals("超限", difficultyNameOf(33))
        // 配置优先：官方哪天改了名字，以服务端为准
        assertEquals("普通", difficultyNameOf(5, config))
    }

    @Test
    fun aggregateByMapDoesNotCollapseIntoUnknownWithoutConfig() {
        // 同上，但走完整聚合链路：难度必须分成两列，而不是合成一列「未知 2」
        val matches = listOf(
            match("a", 12, "2026-09-09 10:00:00", subModeType = 4),
            match("b", 12, "2026-09-08 10:00:00", subModeType = 4),
            match("c", 12, "2026-09-07 10:00:00", subModeType = 5),
        )
        val entry = aggregateByMap(matches, GameConfigDto(), mode = GameMode.HUNT).single()
        assertEquals(3, entry.total)
        assertEquals(2, entry.byDifficulty.size)
        assertEquals(setOf("英雄", "炼狱"), entry.byDifficulty.map { it.name }.toSet())
        assertTrue(entry.byDifficulty.none { it.name == "未知" })
    }

    @Test
    fun difficultyRankHandlesOfficialSplitNames() {
        // 官方的折磨分级（折磨I ~ 折磨VI）、挑战叫「挑战模式」，
        // 拿原名查权重表会落空、被当成未知难度排到最后。
        // 权重越大越难，卡片上按 rank 升序排就是「简单 -> 难」。
        assertTrue(difficultyRank("折磨III") > difficultyRank("炼狱"))
        assertTrue(difficultyRank("挑战模式") > difficultyRank("折磨I"))
        assertTrue(difficultyRank("超限") > difficultyRank("挑战模式"))
        assertTrue(difficultyRank("普通") < difficultyRank("英雄"))
        // 同一档里的分级不该互相插队
        assertEquals(difficultyRank("折磨I"), difficultyRank("折磨VI"))
    }

    @Test
    fun difficultyFilterMatchesAcrossTiers() {
        // 下拉框在没有配置时给的是内置那七个（「折磨」），数据里的名字却是「折磨I」——
        // 按名字逐字判等的话，选「折磨」一条都筛不出来。
        val matches = listOf(
            match("a", 12, "2026-09-09 10:00:00", subModeType = 6),
            match("b", 12, "2026-09-08 10:00:00", subModeType = 11),
            match("c", 12, "2026-09-07 10:00:00", subModeType = 4),
        )
        assertEquals(
            listOf("a", "b"),
            applyMatchFilter(matches, MatchFilter(difficulty = "折磨"), GameConfigDto(), nowSec = nowSec)
                .map { it.roomId },
        )
        // 配置拉到、下拉框里就是分级名时，筛选仍然精确
        assertEquals(
            listOf("a"),
            applyMatchFilter(matches, MatchFilter(difficulty = "折磨I"), GameConfigDto(), nowSec = nowSec)
                .map { it.roomId },
        )
    }

    @Test
    fun difficultyOptionsComeFromConfigOrderedById() {
        assertEquals(listOf("炼狱", "普通"), difficultyOptions(config))
        // 配置还没拉到时用内置顺序兜底
        assertTrue(difficultyOptions(GameConfigDto()).contains("超限"))
    }

    @Test
    fun difficultyNameCoversTowerAndTimeHuntBands() {
        // 这一条锁的是真实线上 bug：iSubModeType 不是全局连续的，塔防和时空追猎用的
        // 是 66-69 / 95 / 130-133 / 160 这几个号段，而服务端下发的 difficultyInfo
        // 里根本没有它们。只内置猎场那半张表的话，这两个模式的难度全部解析不出来，
        // 地图分布页就只剩一列「未知 n」（n = 总场次）。
        assertEquals("普通", difficultyNameOf(66))
        assertEquals("困难", difficultyNameOf(67))
        assertEquals("英雄", difficultyNameOf(68))
        assertEquals("炼狱", difficultyNameOf(69))
        assertEquals("挑战", difficultyNameOf(95))
        assertEquals("普通", difficultyNameOf(130))
        assertEquals("困难", difficultyNameOf(131))
        assertEquals("英雄", difficultyNameOf(132))
        assertEquals("炼狱", difficultyNameOf(133))
        assertEquals("训练场", difficultyNameOf(160))
        assertEquals("挑战", difficultyNameOf(31))
        // 配置里查不到 66 之类时**必须**落到内置表，而不是返回 null
        assertEquals("英雄", difficultyNameOf(132, config))
    }

    @Test
    fun difficultyBucketCollapsesTortureTiers() {
        // 官方把 折磨I ~ 折磨VI 合成一列「折磨」：不合成的话时空追猎一张卡会横着排六列折磨
        assertEquals("折磨", difficultyBucketOf(6, GameMode.TIME_HUNT))
        assertEquals("折磨", difficultyBucketOf(11, GameMode.TIME_HUNT))
        assertEquals("折磨", difficultyBucketOf(7, GameMode.HUNT))
        // 挑战的两种写法归一
        assertEquals("挑战", difficultyBucketOf(12, GameMode.TOWER))
        assertEquals("挑战", difficultyBucketOf(95, GameMode.TOWER))
    }

    @Test
    fun difficultyBucketMapsTowerDefaultToChallenge() {
        // 塔防没有"默认难度"，服务端给 0 是因为它的挑战关走默认通道，官方特地改成「挑战」。
        // 但这条只对塔防成立，猎场 / 追猎真出现 0 就还是「默认」，别乱认。
        assertEquals("挑战", difficultyBucketOf(0, GameMode.TOWER))
        assertEquals("默认", difficultyBucketOf(0, GameMode.HUNT))
        assertEquals("默认", difficultyBucketOf(0, GameMode.TIME_HUNT))
    }

    @Test
    fun aggregateByMapCollapsesTortureForTimeHunt() {
        // 走完整聚合链路：时空追猎的 折磨I / 折磨VI 必须合成一列，而不是两列
        val matches = listOf(
            match("a", 323, "2026-09-09 10:00:00", subModeType = 6),
            match("b", 323, "2026-09-08 10:00:00", subModeType = 11),
            match("c", 323, "2026-09-07 10:00:00", subModeType = 130),
        )
        val entry = aggregateByMap(matches, GameConfigDto(), mode = GameMode.TIME_HUNT).single()
        assertEquals(3, entry.total)
        // 按场次降序，折磨（2 场）在前
        assertEquals(listOf("折磨", "普通"), entry.byDifficulty.map { it.name })
        assertEquals(2, entry.byDifficulty.first { it.name == "折磨" }.total)
    }

    @Test
    fun aggregateByMapKeepsUnknownSubModeTypeLabelled() {
        // 真遇到表里没有的号，带上编号（官方是 `难度${iSubModeType}`）而不是糊成「未知」——
        // 几个不同编号并成一列就丢了"到底哪个号没覆盖到"这条线索
        val matches = listOf(
            match("a", 310, "2026-09-09 10:00:00", subModeType = 77),
            match("b", 310, "2026-09-08 10:00:00", subModeType = 78),
        )
        val entry = aggregateByMap(matches, GameConfigDto(), mode = GameMode.TOWER).single()
        assertEquals(setOf("难度77", "难度78"), entry.byDifficulty.map { it.name }.toSet())
    }

    @Test
    fun difficultyOptionsArePerMode() {
        // 官方的 Gs：塔防没有超限、但有练习 / 新手关 / 训练场；猎场和追猎反过来
        val tower = difficultyOptionsForMode(GameMode.TOWER, GameConfigDto())
        val hunt = difficultyOptionsForMode(GameMode.HUNT, GameConfigDto())
        val timeHunt = difficultyOptionsForMode(GameMode.TIME_HUNT, GameConfigDto())
        assertTrue("练习" in tower)
        assertTrue("新手关" in tower)
        assertTrue("训练场" in tower)
        assertTrue("超限" !in tower)
        assertTrue("超限" in hunt)
        assertTrue("练习" !in hunt)
        assertTrue("超限" in timeHunt)
        // 不选模式时给并集，两个模式的档位都得能选到
        val all = difficultyOptionsForMode(null, GameConfigDto())
        assertTrue("超限" in all && "练习" in all)
        // 配置里有、表里没有的档位补在末尾，别让用户看得见却筛不出来
        assertTrue(difficultyOptionsForMode(GameMode.HUNT, config).containsAll(listOf("炼狱", "普通")))
    }

    // ---------------- 官方地图统计（center.user.map.stats） ----------------

    private fun officialConfig(): GameConfigDto =
        IdeJson.decodeFromString<ConfigListResponseDto>(HarFixtures.PAYLOAD_CONFIG_LIST).config
            ?: GameConfigDto()

    private fun officialStats(): List<MapStatsDto> = IdeJson.decodeFromJsonElement(
        ListSerializer(MapStatsDto.serializer()),
        IdeJson.parseToJsonElement(HarFixtures.PAYLOAD_MAP_STATS),
    )

    @Test
    fun officialMapStatsMatchTheMiniProgram() {
        // 这一条锁的是真实线上 bug：地图分布页原来拿**本地库**算数，而小程序显示的是
        // `center.user.map.stats`（服务端终身账本）。同一个账号同一张图，
        // 本地 75 场 vs 官方 77 通关、折磨 20 vs 28 —— 对不上是必然的，
        // 本地库既受保留期裁剪，口径也还是"场次"而不是"通关"。
        //
        // 销金之城（18）的真实抓包：total=77，六列加起来正好也是 77。
        val entry = buildOfficialMapStats(officialStats(), officialConfig(), GameMode.HUNT)
            .first { it.mapId == 18 }

        assertTrue(entry.official)
        assertEquals(77, entry.total)
        assertEquals(
            listOf("普通" to 1, "困难" to 1, "英雄" to 6, "炼狱" to 15, "折磨" to 28, "超限" to 26),
            entry.byDifficulty.map { it.name to it.total },
        )
    }

    @Test
    fun officialMapStatsTranslatesDungeonIdIntoDifficulty() {
        // `mapModeData` 的 key 是**副本 id**（`2006091`），不是 `iSubModeType`。
        // 得拿地图自己的 `dungeonList` 翻译 —— dungeonList 里 2006091 对应难度 33（超限）。
        // 直接拿 iSubModeType 当 key 去查的话，超限这一列会变成 0。
        val entry = buildOfficialMapStats(officialStats(), officialConfig(), GameMode.HUNT)
            .first { it.mapId == 18 }
        assertEquals(26, entry.byDifficulty.first { it.name == "超限" }.total)
        // 折磨I ~ 折磨VI 合成一列「折磨」，和小程序的口径一致
        assertEquals(28, entry.byDifficulty.first { it.name == "折磨" }.total)
        assertTrue(entry.byDifficulty.none { it.name.startsWith("折磨I") })
    }

    @Test
    fun officialMapStatsKeepsZeroCountDifficulties() {
        // 官方是 `d[name] = (d[name] || 0) + cnt`：这张图**支持**的难度就算 0 场也要摆出来。
        // 按实际场次过滤的话，每张卡的列数都不一样，横着扫一列根本比不了。
        val tower = buildOfficialMapStats(emptyList(), officialConfig(), GameMode.TOWER)
            .first { it.mapId == 310 }
        assertEquals(0, tower.total)
        // 失落游轮的 dungeonList 是 2,3,4,5,12 —— 5 列都在，且 12（挑战模式）归一成「挑战」
        assertEquals(
            listOf("普通", "困难", "英雄", "炼狱", "挑战"),
            tower.byDifficulty.map { it.name },
        )
        assertTrue(tower.byDifficulty.all { it.total == 0 })
    }

    @Test
    fun officialMapStatsSkipsBeginnerRushAndMoonSea() {
        // 官方跳过的三类：名字含「新手关」/「竞速」、以及「月海火线」。
        // 等价于 mapInfo 里 supportStatistics = false 的那几张，摆上去就是永远 0 场的卡。
        val ids = buildOfficialMapStats(officialStats(), officialConfig(), GameMode.TIME_HUNT)
            .map { it.mapId }
        assertTrue(setOf(321, 322, 323).all { it in ids })
        assertTrue(324 !in ids) // 追猎-新手关
        assertTrue(424 !in ids) // 月海火线
    }

    @Test
    fun mergeFallsBackToLocalPerMap() {
        // 真实线上 bug：地图分布页原来是"官方到位就整页用官方"。而 buildOfficialMapStats
        // 会给**配置里的每一张图**都生成条目，官方没下发的图填 0 ——
        // 于是 S4 新图（20 朔望计划 / 22 禁魔岛）在官方还没把它们纳进
        // `center.user.map.stats` 的那几天一直是 0，而本地库里明明有几场，
        // 历史战绩页也能看到具体对局。
        //
        // officialIds 里只有 18：官方只为 18 号图说了话
        val official = buildOfficialMapStats(officialStats(), officialConfig(), GameMode.HUNT)
        val local = listOf(
            localEntry(mapId = 20, name = "朔望计划", total = 4, win = 1),
        )
        val merged = mergeMapStats(official, local, officialIds = setOf(18))

        // 官方说了话的图：口径不变（通关 77），标签仍是「总通关」
        val hunt18 = merged.first { it.mapId == 18 }
        assertTrue(hunt18.official)
        assertEquals(77, hunt18.total)

        // 官方没下发的新图：退回本地口径，标签跟着变成「总场次」
        val fresh = merged.first { it.mapId == 20 }
        assertFalse(fresh.official)
        assertEquals(4, fresh.total)
    }

    @Test
    fun mergeAppendsMapsMissingFromConfig() {
        // 配置比客户端旧、或者某张图压根没下发时：本地打过，但 buildOfficialMapStats
        // 的配置循环里根本没有它。这类图必须追加进去，不能凭空消失。
        val official = buildOfficialMapStats(emptyList(), officialConfig(), GameMode.HUNT)
        val local = listOf(localEntry(mapId = 20, name = "朔望计划", total = 9, win = 3))
        val merged = mergeMapStats(official, local, officialIds = emptySet())
        assertTrue(merged.any { it.mapId == 20 && it.total == 9 })
    }

    /** 一条本地口径的地图统计，merge 的两个测试用例用。 */
    private fun localEntry(mapId: Int, name: String, total: Int, win: Int) = MapStatEntry(
        mapId = mapId,
        name = name,
        total = total,
        win = win,
        byDifficulty = emptyList(),
    )

    @Test
    fun officialEntryKeepsLocalWinRateForTheProgressBar() {
        // 官方口径的 total 是"通关数"，拿它当分母算通关率永远是 100%。
        // 那条进度条要的是本地的 win/plays，得单独带进来。
        val entry = buildOfficialMapStats(
            officialStats(), officialConfig(), GameMode.HUNT, localWins = mapOf(18 to 62),
        ).first { it.mapId == 18 }
        assertEquals(62, entry.winRate)
        assertEquals(0, entry.win) // 官方口径不打通关数
    }

    // ---------------- 筛选 ----------------

    private fun match(
        roomId: String,
        mapId: Int,
        time: String,
        win: Boolean = true,
        finished: Boolean = true,
        subModeType: Int = 0,
    ): MatchEntity = GameRecordDto(
        DsRoomId = roomId,
        dtEventTime = time,
        iMapId = mapId,
        iIsWin = win,
        iFinTime = if (finished) 1 else 0,
        iSubModeType = subModeType,
        iScore = 100,
    ).toEntity()

    // 2026-09-10 20:00:00 (UTC+8)
    private val nowSec = parseServerTime("2026-09-10 20:00:00")!!

    private val sample = listOf(
        match("a", 304, "2026-09-10 19:00:00", win = true, subModeType = 3),
        match("b", 12, "2026-09-09 10:00:00", win = false, subModeType = 5),
        match("c", 304, "2026-08-01 10:00:00", win = true),
        match("d", 1000, "2026-09-10 18:00:00", win = true),
        match("e", 12, "2026-09-05 10:00:00", win = false, finished = false),
    )

    @Test
    fun emptyFilterKeepsEverything() {
        assertEquals(5, applyMatchFilter(sample, MatchFilter(), config, nowSec = nowSec).size)
    }

    @Test
    fun modeFilterRespectsMapIdGroups() {
        val tower = applyMatchFilter(
            sample,
            MatchFilter(mode = GameMode.TOWER),
            config,
            nowSec = nowSec,
        )
        assertEquals(listOf("a", "c"), tower.map { it.roomId })

        val hunt = applyMatchFilter(
            sample,
            MatchFilter(mode = GameMode.HUNT),
            config,
            nowSec = nowSec,
        )
        assertEquals(listOf("b", "e"), hunt.map { it.roomId })
    }

    @Test
    fun mapFilterPicksSingleMap() {
        val result = applyMatchFilter(sample, MatchFilter(mapId = 304), config, nowSec = nowSec)
        assertEquals(listOf("a", "c"), result.map { it.roomId })
    }

    @Test
    fun difficultyFilterMatchesResolvedName() {
        val result = applyMatchFilter(
            sample,
            MatchFilter(difficulty = "普通"),
            config,
            nowSec = nowSec,
        )
        assertEquals(listOf("b"), result.map { it.roomId })
    }

    @Test
    fun todayRangeUsesServerMidnight() {
        val midnight = startOfServerDay(nowSec)
        assertEquals(parseServerTime("2026-09-10 00:00:00")!!, midnight)

        val result = applyMatchFilter(
            sample,
            MatchFilter(range = DateRange.TODAY),
            config,
            nowSec = nowSec,
        )
        assertEquals(listOf("a", "d"), result.map { it.roomId })
    }

    @Test
    fun last3DaysRangeIsInclusive() {
        val result = applyMatchFilter(
            sample,
            MatchFilter(range = DateRange.LAST_3_DAYS),
            config,
            nowSec = nowSec,
        )
        // 09-10 / 09-09 都算，08-01 和 09-05 不算；结果按时间倒序
        assertEquals(listOf("a", "d", "b"), result.map { it.roomId })
    }

    @Test
    fun outcomeFiltersIgnoreUnfinishedMatches() {
        val win = applyMatchFilter(
            sample,
            MatchFilter(outcome = Outcome.WIN),
            config,
            nowSec = nowSec,
        )
        assertEquals(listOf("a", "d", "c"), win.map { it.roomId })

        // e 是未完成局，iIsWin=false，但不能算"失败"
        val loss = applyMatchFilter(
            sample,
            MatchFilter(outcome = Outcome.LOSS),
            config,
            nowSec = nowSec,
        )
        assertEquals(listOf("b"), loss.map { it.roomId })
    }

    @Test
    fun favoriteFilterUsesExternalSet() {
        val result = applyMatchFilter(
            sample,
            MatchFilter(outcome = Outcome.FAVORITE),
            config,
            favorites = setOf("c", "d"),
            nowSec = nowSec,
        )
        assertEquals(listOf("d", "c"), result.map { it.roomId })
    }

    @Test
    fun pinnedMatchesSortFirst() {
        val result = applyMatchFilter(
            sample,
            MatchFilter(),
            config,
            pinned = setOf("c"),
            nowSec = nowSec,
        )
        assertEquals("c", result.first().roomId)
    }

    @Test
    fun availableMapsSortedByCount() {
        // 场次相同（304 和 12 都是 2 场）时按 mapId 升序，保证顺序稳定
        val maps = availableMaps(sample, config)
        // 1000 是机甲图，官方表里叫「风暴峡谷」，不再落到「未知(id)」
        assertEquals(listOf(12 to "沙漠神殿", 304 to "黑暗复活节", 1000 to "风暴峡谷"), maps)
    }

    @Test
    fun mapNamesCoverIdsThatUsedToFallBackToUnknown() {
        // 这几个 id 在官方前端的表里存在，早期按 NZM 整理的那版漏了，
        // 本地库里出现时会被判成 UNKNOWN / 未知(id)。锁住回归。
        assertEquals("樱之城", mapNameOf(13))
        assertEquals("丛林魅影", mapNameOf(15))
        assertEquals("销金之城", mapNameOf(18))
        assertEquals("樱之渊", mapNameOf(19))
        assertEquals("蔷薇庄园", mapNameOf(309))
        assertEquals("失落游轮", mapNameOf(310))
        assertEquals("月海火线", mapNameOf(424))
        assertEquals(GameMode.HUNT, modeOf(13))
        assertEquals(GameMode.HUNT, modeOf(19))
        assertEquals(GameMode.TOWER, modeOf(309))
        assertEquals(GameMode.TOWER, modeOf(310))
        assertEquals(GameMode.TIME_HUNT, modeOf(424))
    }

    @Test
    fun s4MapsAreKnown() {
        // S4·朔望计划（2026-09-22）新增的三张图。不进内置表的话会被判成
        // UNKNOWN / 未知(id)，地图分布页连卡都不会有。
        assertEquals("朔望计划", mapNameOf(20))
        assertEquals("禁魔岛", mapNameOf(22))
        assertEquals("银河战舰", mapNameOf(311))
        assertEquals(GameMode.HUNT, modeOf(20))
        assertEquals(GameMode.HUNT, modeOf(22))
        assertEquals(GameMode.TOWER, modeOf(311))
    }

    // ---------------- 地图聚合 ----------------

    @Test
    fun aggregateByMapExcludesMechaByDefault() {
        val result = aggregateByMap(sample, config, mode = null)
        assertEquals(listOf(12, 304), result.map { it.mapId })
    }

    @Test
    fun aggregateByMapCanIncludeMecha() {
        val result = aggregateByMap(sample, config, mode = null, includeMecha = true)
        assertEquals(listOf(12, 304, 1000), result.map { it.mapId })
    }

    @Test
    fun aggregateByMapComputesWinRateAndDifficultyBreakdown() {
        val result = aggregateByMap(sample, config, mode = GameMode.HUNT)
        assertEquals(1, result.size)
        val entry = result.single()
        assertEquals(
            MapStatEntry(
                mapId = 12,
                name = "沙漠神殿",
                total = 2,
                win = 0,
                byDifficulty = listOf(
                    DifficultyCount("普通", 1, 0),
                    // 第二条 `iSubModeType = 0`，配置里没有这一项 —— 走内置兜底表叫「默认」，
                    // 不再是「未知」
                    DifficultyCount("默认", 1, 0),
                ),
            ),
            entry,
        )
        assertEquals(0, entry.winRate)
    }

    @Test
    fun winRateIgnoresUnfinished() {
        val matches = listOf(
            match("x", 304, "2026-09-10 19:00:00", win = true),
            match("y", 304, "2026-09-09 19:00:00", win = false, finished = false),
        )
        val entry = aggregateByMap(matches, config, mode = GameMode.TOWER).single()
        assertEquals(2, entry.total)
        assertEquals(1, entry.win)
        assertEquals(50, entry.winRate)
    }

    // ---------------- 赛季分组 ----------------

    /** 只填分组看得见的那几列。 */
    private fun stat(mapId: Int, total: Int, name: String = mapNameOf(mapId)) =
        MapStatEntry(mapId = mapId, name = name, total = total, win = total, byDifficulty = emptyList())

    @Test
    fun huntIsSplitIntoSeasonsNewestFirst() {
        val sections = groupMapsBySeason(GameMode.HUNT, emptyList())
        assertEquals(
            listOf("S4 赛季", "S3 赛季", "S2 赛季", "S1 赛季", "S0 赛季"),
            sections.map { it.title },
        )
        // S4·朔望计划（2026-09-22 上线）：朔望计划 + 禁魔岛
        assertEquals(listOf(20, 22), sections[0].entries.map { it.mapId })
        assertEquals(listOf(18, 15), sections[1].entries.map { it.mapId })
        // 官方次序是樱之渊在前（19 然后 13）
        assertEquals(listOf(19, 13), sections[2].entries.map { it.mapId })
        assertEquals(listOf(16, 17), sections[3].entries.map { it.mapId })
        assertEquals(listOf(12, 14, 21), sections[4].entries.map { it.mapId })
        // 没打过的图也要出现，数字是 0
        assertTrue(sections.all { section -> section.entries.all { it.total == 0 } })
    }

    @Test
    fun seasonKeepsStatsOnTheRightCard() {
        val sections = groupMapsBySeason(GameMode.HUNT, listOf(stat(16, 752), stat(21, 92)))
        val flat = sections.flatMap { it.entries }.associateBy { it.mapId }
        assertEquals(752, flat[16]?.total)
        assertEquals(92, flat[21]?.total)
        // 次序是官方次序，不是场次降序：S0 里 21 排在 12/14 后面
        assertEquals(listOf(12, 14, 21), sections[4].entries.map { it.mapId })
    }

    @Test
    fun mapsOutsideTheSeasonTableGoLast() {
        // 112 是「猎场竞速」版的黑暗复活节：和 12 同名，塞进 S0 会多出一张一模一样的卡
        val sections = groupMapsBySeason(GameMode.HUNT, listOf(stat(112, 7)))
        assertEquals("其他地图", sections.last().title)
        assertEquals(listOf(112), sections.last().entries.map { it.mapId })
        // 名次不变，S0 还是三张
        assertEquals(listOf(12, 14, 21), sections[4].entries.map { it.mapId })
    }

    @Test
    fun towerAndTimeHuntGetOneUntitledSection() {
        val tower = groupMapsBySeason(GameMode.TOWER, emptyList())
        assertEquals(1, tower.size)
        assertEquals("", tower.single().title)
        // 311 银河战舰是 S4 新增的塔防图，官方把它排在最前
        assertEquals(listOf(311, 310, 309, 304, 306, 300), tower.single().entries.map { it.mapId })

        val timeHunt = groupMapsBySeason(GameMode.TIME_HUNT, emptyList())
        // 月海火线（424）不在列表里：官方明确跳过它（`supportStatistics = false`），
        // 摆上去就是一张永远 0 场的卡。本地真有它的对局时会落到「其他地图」那一节。
        assertEquals(listOf(323, 322, 321), timeHunt.single().entries.map { it.mapId })
    }

    @Test
    fun difficultyRankOrdersEasiestFirst() {
        assertEquals(listOf("普通", "困难", "英雄", "炼狱", "折磨", "超限"), listOf(
            "超限", "折磨", "普通", "炼狱", "英雄", "困难",
        ).sortedWith(compareBy({ difficultyRank(it) }, { it })))
        // 表里没有的名字排最后，而不是插在中间
        assertTrue(difficultyRank("某个新难度") > difficultyRank("超限"))
    }

    // ---------------- 活动解析 ----------------

    @Test
    fun parseCalendarReadsOfficialRilipeizhi() {
        // 官方 PC 端 fetchCalendar 拿到的形状：content 是 JSON 字符串，
        // 活动数组在 rilipeizhi.data 下，每条只有 title / extParam / desc
        val json = """
            {"rilipeizhi":{"data":[
              {"id":"1","title":"双倍掉落","extParam":"2026.09.10-2026.09.24","desc":"猎场掉落翻倍"},
              {"id":"2","title":"新版本预热","extParam":"2026.10.01-2026.10.07","desc":""}
            ]}}
        """.trimIndent()
        val events = parseCalendar(json)
        assertEquals(2, events.size)
        assertEquals("双倍掉落", events[0].title)
        assertEquals("猎场掉落翻倍", events[0].description)
        assertEquals("09-10 ~ 09-24", events[0].periodText())
    }

    @Test
    fun parseCalendarFallsBackToLooseShapes() {
        val json = """
            {"list":[
              {"title":"双倍掉落","startTime":"2026-09-01 00:00:00","endTime":"2026-09-30 23:59:59"},
              {"name":"新版本预热","start":"2026-10-01 00:00:00","end":"2026-10-07 00:00:00"}
            ]}
        """.trimIndent()
        val events = parseCalendar(json)
        assertEquals(2, events.size)
        assertEquals("双倍掉落", events[0].title)
        assertEquals("09-01 ~ 09-30", events[0].periodText())
    }

    @Test
    fun parseCalendarKeepsServerTextWhenPeriodIsUnparsable() {
        // 时间文案的格式没有样本，解析不出来时原样展示，不能把活动丢掉
        val json = """
            {"rilipeizhi":{"data":[{"title":"赛季冲刺","extParam":"限时开启","desc":""}]}}
        """.trimIndent()
        val events = parseCalendar(json)
        assertEquals(1, events.size)
        assertEquals("限时开启", events[0].periodText())
    }

    @Test
    fun parseCalendarUnwrapsStringEncodedJson() {
        // 运营位的 content 有时是"字符串包着的 JSON"，剥一层才能读到 rilipeizhi
        val inner = """{"rilipeizhi":{"data":[{"title":"雷渊之击","extParam":"8月13日-9月21日"}]}}"""
        val wrapped = IdeJson.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            kotlinx.serialization.json.JsonPrimitive(inner),
        )
        val events = parseCalendar(wrapped)
        assertEquals(1, events.size)
        assertEquals("雷渊之击", events[0].title)
    }

    @Test
    fun parseCalendarNeverThrowsOnGarbage() {
        assertTrue(parseCalendar("").isEmpty())
        assertTrue(parseCalendar("not json at all").isEmpty())
        assertTrue(parseCalendar("{\"a\":1}").isEmpty())
        assertTrue(parseCalendar("[[1,2,3]]").isEmpty())
    }

    @Test
    fun summarizeLocalRecentUsesStoredColumns() {
        val matches = listOf(
            storedMatch(roomId = "1", score = 100, kills = 20, rank = 1, win = true),
            storedMatch(roomId = "2", score = 50, kills = 10, rank = 3, win = false),
        )
        val result = summarizeLocalRecent(matches)
        assertEquals(2, result?.sampleCount)
        assertEquals(75, result?.avgScore)
        assertEquals(15, result?.avgKills)
        assertEquals(1, result?.mvpCount)
        assertEquals(1, result?.winCount)
        // Boss 伤害 / 金币本地没有，必须是 null 而不是 0
        assertNull(result?.avgBossDamage)
        assertNull(result?.avgCoin)
        assertTrue(result?.localOnly == true)
    }

    @Test
    fun upcomingActivitiesDropsExpired() {
        val events = listOf(
            ActivityEvent("已结束", 1_000L, 2_000L),
            ActivityEvent("进行中", 1_000L, nowSec + 100),
            ActivityEvent("未开始", nowSec + 100, nowSec + 200),
        )
        val result = upcomingActivities(events, nowSec)
        assertEquals(listOf("进行中", "未开始"), result.map { it.title })
        assertTrue(result[0].isOngoing(nowSec))
        assertTrue(result[1].isUpcoming(nowSec))
    }

    // ---------------- 近五场 ----------------

    @Test
    fun summarizeRecentAveragesAndCountsMvp() {
        val details = listOf(
            detail(selfScore = 100, others = listOf(80, 60), boss = 1000, coin = 200),
            detail(selfScore = 50, others = listOf(90), boss = 500, coin = 100),
        )
        val result = summarizeRecent(details)
        assertEquals(
            RecentFive(sampleCount = 2, avgBossDamage = 750, mvpCount = 1, avgScore = 75, avgCoin = 150),
            result,
        )
    }

    @Test
    fun summarizeRecentReturnsNullWithoutSelfDetail() {
        assertNull(summarizeRecent(emptyList()))
    }

    @Test
    fun formatPlaytimeHandlesBothUnits() {
        assertEquals("0 小时", formatPlaytime(0))
        assertEquals("30 分", formatPlaytime(1_800))
        assertEquals("2 小时 5 分", formatPlaytime(7_500))
        assertEquals("10 小时 0 分", formatPlaytime(36_000))
    }

    private fun detail(
        selfScore: Long,
        others: List<Long>,
        boss: Long,
        coin: Long,
    ): com.nzd.antigravitypanel.data.remote.dto.GameDetailDto {
        fun player(score: Long) = com.nzd.antigravitypanel.data.remote.dto.PlayerDetailDto(
            baseDetail = GameRecordDto(iScore = score),
            huntingDetails = com.nzd.antigravitypanel.data.remote.dto.HuntingDetailsDto(
                damageTotalOnBoss = boss,
                totalCoin = coin,
            ),
        )
        return com.nzd.antigravitypanel.data.remote.dto.GameDetailDto(
            loginUserDetail = player(selfScore),
            list = listOf(player(selfScore)) + others.map(::player),
        )
    }

    /** 本地库里的一行对局。只填 `summarizeLocalRecent` 看得见的那几列。 */
    private fun storedMatch(
        roomId: String,
        score: Long,
        kills: Int,
        rank: Int,
        win: Boolean,
    ): MatchEntity = MatchEntity(
        roomId = roomId,
        openId = "137700727749128",
        eventTime = "2026-09-10 20:00:00",
        eventTimeSec = nowSec,
        startTime = "2026-09-10 19:40:00",
        areaId = "1",
        finTime = 1200,
        duration = 1200,
        isWin = win,
        gameMode = 3,
        modeType = 134,
        subModeType = 6,
        mapId = 16,
        score = score,
        seasonId = "",
        kills = kills,
        deaths = 1,
        assists = 0,
        rankValue = rank,
        finished = true,
        syncedAtSec = nowSec,
    )
}
