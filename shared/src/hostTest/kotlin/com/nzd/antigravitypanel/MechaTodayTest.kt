package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.remote.dto.GameRecordDto
import com.nzd.antigravitypanel.data.repo.MECHA_TODAY_GOAL
import com.nzd.antigravitypanel.data.repo.OverviewRepository.Companion.MECHA_TODAY_PAGE_SIZE
import com.nzd.antigravitypanel.data.repo.countMechaTodayGames
import com.nzd.antigravitypanel.util.parseServerTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 「今日机甲战」那个 n 的算法。
 *
 * 一天只打几局，但边界（北京时间 00:00）差一秒就会把昨天那局算进来或漏掉，
 * 所以钉死在这里，不靠真机数。
 */
class MechaTodayTest {

    /**
     * 夹具默认**打完了的机甲局**。
     *
     * ⚠️ 两个默认值都得给真的，否则断言会集体变成 0 却仍然"通过"：
     * - `iFinTime`：中途退出的局服务端一样会返回、但不计入；
     * - `iMapId`：不填就是 0（认不出模式），而这一页数的是机甲。
     *
     * 1000 是机甲图（官方表里叫「风暴峡谷」），12 是猎场图（「黑暗复活节」）。
     */
    private fun record(
        time: String,
        finished: Boolean = true,
        mapId: Int = 1000,
        gameMode: Int = 0,
    ) = GameRecordDto(
        dtEventTime = time,
        iFinTime = if (finished) 1 else 0,
        iMapId = mapId,
        iGameMode = gameMode,
    )

    /** 以 `2026-10-09 12:00`（北京时间）为"现在"。 */
    private val now = parseServerTime("2026-10-09 12:00:00")!!

    @Test
    fun countsOnlyGamesAfterServerMidnight() {
        val records = listOf(
            record("2026-10-09 11:30:00"),
            record("2026-10-09 00:00:00"),
            // 昨天的最后一秒不算：分界是自然日 00:00
            record("2026-10-08 23:59:59"),
            record("2026-10-08 10:00:00"),
        )
        assertEquals(2, countMechaTodayGames(records, now))
    }

    /** 一整页都是今天的，全算；服务端倒序，翻页逻辑靠"条数 == 今天数"判断要不要继续。 */
    @Test
    fun countsWholePageWhenAllAreToday() {
        val records = listOf(
            record("2026-10-09 23:59:59"),
            record("2026-10-09 12:00:00"),
            record("2026-10-09 00:00:01"),
        )
        assertEquals(3, countMechaTodayGames(records, now))
    }

    /** 时间解析不出来（未完成的局给空串）就跳过，不能因为一条脏数据把整页算废。 */
    @Test
    fun skipsUnparsableTimestamps() {
        val records = listOf(
            record("2026-10-09 10:00:00"),
            record(""),
            record("2026-10-08 10:00:00"),
        )
        assertEquals(1, countMechaTodayGames(records, now))
    }

    /**
     * 中途退出的不算。
     *
     * 玩家要看的是"今天打完了几局 / 达标场次" —— 退掉的局服务端照样返回，
     * 但计进去会让人以为快满了，其实一局都没成。
     */
    @Test
    fun skipsUnfinishedGames() {
        val records = listOf(
            record("2026-10-09 11:00:00"),
            record("2026-10-09 10:00:00", finished = false),
            record("2026-10-09 09:00:00", finished = false),
            record("2026-10-09 08:00:00"),
        )
        assertEquals(2, countMechaTodayGames(records, now))
    }

    /** 全是中途退出就是 0，不是"有几条记录就算几局"。 */
    @Test
    fun allUnfinishedCountsZero() {
        val records = listOf(
            record("2026-10-09 11:00:00", finished = false),
            record("2026-10-09 10:00:00", finished = false),
        )
        assertEquals(0, countMechaTodayGames(records, now))
    }

    @Test
    fun emptyPageCountsZero() {
        assertEquals(0, countMechaTodayGames(emptyList(), now))
    }

    /**
     * **别的模式一律不算** —— 这一条锁的是真实线上 bug：
     * 早上打两局猎场，卡片上「今日机甲战」就显示「2 / 达标场次」。
     *
     * 根因是 `map_mode=机甲非对称` 服务端不认，回过来的就是猎场那一份，
     * 而旧的算法只按时间和"已完成"过滤，压根没看这条记录是什么模式。
     * ⇒ 请求里的 `map_mode` 只能当"请服务端先滤一道"，判据必须是记录自己。
     */
    @Test
    fun skipsGamesFromOtherModes() {
        val records = listOf(
            record("2026-10-09 11:00:00", mapId = 12), // 猎场：黑暗复活节
            record("2026-10-09 10:00:00", mapId = 300), // 塔防：空间站
            record("2026-10-09 09:00:00", mapId = 321), // 时空追猎：根除异变
            record("2026-10-09 08:00:00"), // 机甲
        )
        assertEquals(1, countMechaTodayGames(records, now))
    }

    /** 一整页都是别的模式时就是 0，不是"返回了几条就算几局"。 */
    @Test
    fun allOtherModesCountsZero() {
        val records = listOf(
            record("2026-10-09 11:00:00", mapId = 12),
            record("2026-10-09 10:00:00", mapId = 12),
        )
        assertEquals(0, countMechaTodayGames(records, now))
    }

    /**
     * **不看 `iGameMode`** —— 这一条锁的是我自己写错过的一次：以为机甲是 `iGameMode == 6`。
     *
     * 2026-10-10 抓包实测的编号正好相反：机甲局 `iGameMode=2 / iModeType=65`，
     * 猎场局 `iGameMode=3 / iModeType=134`。拿一个猜来的编号去判模式，
     * 轻则该认的不认，重则把别的模式算成机甲。判据只能是地图 id。
     */
    @Test
    fun ignoresGameModeNumber() {
        val records = listOf(
            record("2026-10-09 11:00:00", mapId = 1001, gameMode = 2), // 机甲（实测编号）
            record("2026-10-09 10:00:00", mapId = 17, gameMode = 3), // 猎场
            record("2026-10-09 09:00:00", mapId = 0, gameMode = 6), // 编号 6：别的模式，不是机甲
        )
        assertEquals(1, countMechaTodayGames(records, now))
    }

    /** 抓包里出现的三张机甲图都要认：1001 / 1002 / 1003（1003 是官方表里还没有的新图）。 */
    @Test
    fun acceptsEveryMechaMapId() {
        val records = listOf(
            record("2026-10-09 11:00:00", mapId = 1001),
            record("2026-10-09 10:00:00", mapId = 1002),
            record("2026-10-09 09:00:00", mapId = 1003),
        )
        assertEquals(3, countMechaTodayGames(records, now))
    }

    /**
     * 页大小必须**等于服务端每页实际上限**，不是"我想要多少"（2026-10-10 抓包实测 10，
     * 官方小程序自己也是 `limit=10`，请求 20 服务端照样只给 10）。
     *
     * 翻页有一条终止判据是"这一页不满 = 翻到头了"，拿一个比服务端上限大的数去比，
     * 第一页就会被判成翻到头 —— 实际表现是今天打 25 局，卡片永远停在 10。
     */
    @Test
    fun pageSizeMatchesServerCap() {
        assertEquals(10, MECHA_TODAY_PAGE_SIZE)
    }

    /**
     * 达标线就是 20：它不是算出来的，是官方定的（2026-10-10 从 40 改成 20）。
     *
     * 钉在这里是因为卡片上的 `n / 20 场` 是拿这个常量拼的 —— 哪天官方再改，
     * 改常量就够了，不该有人在 UI 里另写一份数字。
     */
    @Test
    fun goalIsTwenty() {
        assertEquals(20, MECHA_TODAY_GOAL)
    }
}
