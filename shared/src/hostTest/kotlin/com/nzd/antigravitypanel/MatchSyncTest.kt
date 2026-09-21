package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.db.toEntity
import com.nzd.antigravitypanel.data.remote.IdeJson
import com.nzd.antigravitypanel.data.remote.dto.GameListPageDto
import com.nzd.antigravitypanel.data.remote.dto.GameRecordDto
import com.nzd.antigravitypanel.data.repo.MatchLocalSource
import com.nzd.antigravitypanel.data.repo.MatchRemoteSource
import com.nzd.antigravitypanel.data.repo.MatchSyncer
import com.nzd.antigravitypanel.data.repo.SYNC_MAP_MODES
import com.nzd.antigravitypanel.data.repo.SyncResult
import com.nzd.antigravitypanel.data.repo.planPage
import com.nzd.antigravitypanel.util.minusCalendarMonths
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 增量同步的刹车逻辑。
 *
 * 服务端只留滚动窗口（近 30 天 / 最近 100 场），老数据滚出就再也拿不回来，
 * 所以"什么时候停"必须是可验证的：停早了丢数据，停晚了每次全量重拉还撞上限。
 */
class MatchSyncTest {

    // ---------------- 单页决策 ----------------

    @Test
    fun 全新一页全部写入且不停止() {
        val records = (1..10).map { fakeRecord("n$it") }
        val plan = planPage(records, emptyMap())
        assertEquals(10, plan.toWrite.size)
        assertEquals(10, plan.newCount)
        assertEquals(0, plan.refreshedCount)
        assertFalse(plan.stop)
    }

    @Test
    fun 遇到已完成的已知记录就停() {
        val records = (1..6).map { fakeRecord("n$it") }
        // 第 4 条本地已有且已完成 → 只写前 3 条
        val known = mapOf("n4" to true, "n5" to true, "n6" to true)
        val plan = planPage(records, known)
        assertEquals(listOf("n1", "n2", "n3"), plan.toWrite.map { it.DsRoomId })
        assertEquals(3, plan.newCount)
        assertTrue(plan.stop)
    }

    @Test
    fun 第一条就已知时一条都不写() {
        val records = listOf(fakeRecord("known"), fakeRecord("n2"), fakeRecord("n3"))
        val plan = planPage(records, mapOf("known" to true))
        assertTrue(plan.toWrite.isEmpty())
        assertEquals(0, plan.newCount)
        assertTrue(plan.stop)
    }

    @Test
    fun 已知但未完成的记录要刷新而不是跳过() {
        // 这就是 unfinished 对局的正确处理方式：第一次拉到时 iFinTime 是空串
        val records = listOf(
            fakeRecord("unfinished", finTime = 0),
            fakeRecord("done", finTime = 100),
            fakeRecord("n3"),
        )
        val plan = planPage(records, mapOf("unfinished" to false, "done" to true))
        assertEquals(listOf("unfinished"), plan.toWrite.map { it.DsRoomId })
        assertEquals(0, plan.newCount)
        assertEquals(1, plan.refreshedCount)
        assertTrue(plan.stop)
    }

    @Test
    fun 未知记录即使排在已知未完成之后也照写() {
        val records = listOf(fakeRecord("stale", finTime = 0), fakeRecord("fresh"))
        val plan = planPage(records, mapOf("stale" to false))
        assertEquals(listOf("stale", "fresh"), plan.toWrite.map { it.DsRoomId })
        assertEquals(2, plan.toWrite.size)
        assertFalse(plan.stop)
    }

    // ---------------- 整轮同步 ----------------

    @Test
    fun 命中已知记录后不再往下翻() {
        val local = FakeLocal()
        local.seed("k1")
        val remote = FakeRemote(
            1 to listOf(fakeRecord("a1"), fakeRecord("a2"), fakeRecord("a3"), fakeRecord("a4")),
            2 to listOf(fakeRecord("b1"), fakeRecord("b2"), fakeRecord("k1"), fakeRecord("b3")),
        )
        val result = sync(remote, local, pageSize = 4)

        assertEquals(2, result.pages)
        assertEquals(6, result.newCount) // a1..a4 + b1,b2
        assertEquals(8, result.fetchedCount)
        // b3 在 k1 后面，不该被拉下来
        assertFalse(local.rows.containsKey("b3"))
        assertEquals(listOf("猎场#1", "猎场#2"), remote.requested)
    }

    @Test
    fun 第一条就命中时只翻一页() {
        val local = FakeLocal()
        local.seed("k1")
        val remote = FakeRemote(1 to listOf(fakeRecord("k1"), fakeRecord("a1")))
        val result = sync(remote, local, pageSize = 4)
        assertEquals(1, result.pages)
        assertEquals(0, result.newCount)
        assertEquals(listOf("猎场#1"), remote.requested)
    }

    @Test
    fun 空页代表服务端到头了() {
        val remote = FakeRemote(1 to emptyList())
        val result = sync(remote, FakeLocal(), pageSize = 4)
        assertEquals(0, result.pages)
        assertEquals(0, result.fetchedCount)
        assertTrue(result.reachedEnd)
    }

    @Test
    fun 不满一页也代表到头了() {
        val remote = FakeRemote(1 to listOf(fakeRecord("a1"), fakeRecord("a2")))
        val result = sync(remote, FakeLocal(), pageSize = 4)
        assertEquals(1, result.pages)
        assertEquals(2, result.newCount)
        assertTrue(result.reachedEnd)
    }

    @Test
    fun 全是新数据时被maxPages截断且标记没到头() {
        // 服务端真的会给满页（它本来就只留 100 场），这时候必须靠 maxPages 兜底
        val remote = EndlessRemote(pageSize = 4)
        val result = sync(remote, FakeLocal(), pageSize = 4, maxPages = 3)
        assertEquals(3, result.pages)
        assertEquals(12, result.newCount)
        assertFalse(result.reachedEnd)
    }

    @Test
    fun 三个模式各翻一遍且互不截断() {
        // 这一条锁的是真实线上 bug：`center.user.game.list` 必须带 `map_mode` 并按
        // 猎场 / 塔防 / 时空追猎 各翻一遍。不带的话服务端只给猎场那一份，
        // 另外两个模式在地图分布页上永远只剩零星几场。
        val remote = FakeRemote(1 to listOf(fakeRecord("a1")))
        val result = sync(remote, FakeLocal(), pageSize = 4, mapModes = SYNC_MAP_MODES)

        assertEquals(3, result.pages)
        assertEquals(
            listOf("猎场#1", "塔防#1", "时空追猎#1"),
            remote.requested,
        )
        assertTrue(result.reachedEnd)
    }

    @Test
    fun 任一模式被截断时整轮不算翻到头() {
        val remote = EndlessRemote(pageSize = 4)
        val result = sync(remote, FakeLocal(), pageSize = 4, maxPages = 2, mapModes = SYNC_MAP_MODES)
        // 2 页 × 3 个模式
        assertEquals(6, result.pages)
        assertFalse(result.reachedEnd)
    }

    @Test
    fun 未完成的对局会在下一次同步时被刷新() {
        val local = FakeLocal()
        val remote = FakeRemote(
            1 to listOf(fakeRecord("A", finTime = 0), fakeRecord("B", finTime = 100)),
        )
        val first = sync(remote, local, pageSize = 4)
        assertEquals(2, first.newCount)
        assertFalse(local.rows["A"]!!.finished)
        assertTrue(local.rows["B"]!!.finished)

        // A 打完了：这次应该刷新 A，然后撞上已完成的 B 停下
        remote.pages[1] = listOf(fakeRecord("A", finTime = 200), fakeRecord("B", finTime = 100))
        remote.requested.clear()
        val second = sync(remote, local, pageSize = 4)
        assertEquals(0, second.newCount)
        assertEquals(1, second.refreshedCount)
        assertTrue(local.rows["A"]!!.finished)
        assertEquals(200, local.rows["A"]!!.finTime)
    }

    // ---------------- 保留期 ----------------

    @Test
    fun 保留期默认裁剪掉过期对局() {
        val local = FakeLocal()
        val now = 1788531316L // 2026-09-04 22:15:16 UTC+8
        // 一条 3 个月前（应保留），一条 2 年前（应删掉）
        local.rows["recent"] = fakeRecord("recent").copy(dtEventTime = "2026-06-01 10:00:00").toEntity(now)
        local.rows["ancient"] = fakeRecord("ancient").copy(dtEventTime = "2024-06-01 10:00:00").toEntity(now)

        val remote = FakeRemote(1 to emptyList())
        val result = sync(remote, local, pageSize = 4, nowSec = now, retentionMonths = 6)

        assertEquals(minusCalendarMonths(now, 6), local.lastCutoffSec)
        assertTrue(local.rows.containsKey("recent"))
        assertFalse(local.rows.containsKey("ancient"))
        assertEquals(1, result.deletedCount)
    }

    @Test
    fun 永久保留时不删任何东西() {
        val local = FakeLocal()
        val now = 1788531316L
        local.rows["ancient"] = fakeRecord("ancient").copy(dtEventTime = "2020-06-01 10:00:00").toEntity(now)

        sync(FakeRemote(1 to emptyList()), local, pageSize = 4, nowSec = now, retentionMonths = 0)

        assertNull(local.lastCutoffSec)
        assertTrue(local.rows.containsKey("ancient"))
    }

    // ---------------- 真实抓包数据 ----------------

    @Test
    fun 真实对局记录能转成实体() {
        val page: GameListPageDto = IdeJson.decodeFromJsonElement(
            IdeJson.parseToJsonElement(HarFixtures.PAYLOAD_GAME_LIST_P1),
        )
        val first = page.gameList.first().toEntity(syncedAtSec = 1788531316L)

        assertEquals("72075042511372298", first.roomId)
        assertEquals("2026-09-04 22:15:16", first.eventTime)
        assertEquals(1788531316L, first.eventTimeSec)
        assertEquals(1235, first.finTime)
        assertEquals(19665238L, first.score)
        assertEquals(475, first.kills)
        assertEquals(16, first.mapId)
        assertEquals(1, first.rankValue)
        assertTrue(first.isWin)
        assertTrue(first.finished)
    }

    @Test
    fun 时间字段为空的记录不会污染排序() {
        val page: GameListPageDto = IdeJson.decodeFromJsonElement(
            IdeJson.parseToJsonElement(HarFixtures.PAYLOAD_GAME_LIST_P1),
        )
        val empty = page.gameList.first().copy(dtEventTime = "").toEntity()
        assertEquals(0L, empty.eventTimeSec)
    }

    // ---------------- 辅助 ----------------

    private fun sync(
        remote: MatchRemoteSource,
        local: MatchLocalSource,
        pageSize: Int,
        maxPages: Int = 10,
        nowSec: Long = 1788531316L,
        retentionMonths: Int = 6,
        // 单模式是刻意选的默认：分页刹车的断言只在"一个模式内部按时间倒序"这个前提下成立
        mapModes: List<String> = listOf("猎场"),
    ): SyncResult = runBlocking {
        MatchSyncer(remote, local, pageSize, maxPages, mapModes).sync(nowSec, retentionMonths)
    }

    private class FakeLocal : MatchLocalSource {
        val rows = LinkedHashMap<String, MatchEntity>()
        var lastCutoffSec: Long? = null

        fun seed(vararg roomIds: String) {
            for (id in roomIds) rows[id] = fakeRecord(id).toEntity()
        }

        override suspend fun knownStates(roomIds: List<String>): Map<String, Boolean> =
            roomIds.mapNotNull { id -> rows[id]?.let { id to it.finished } }.toMap()

        override suspend fun upsertAll(entities: List<MatchEntity>) {
            entities.forEach { rows[it.roomId] = it }
        }

        override suspend fun deleteOlderThan(cutoffSec: Long): Int {
            lastCutoffSec = cutoffSec
            val stale = rows.filter { it.value.eventTimeSec > 0 && it.value.eventTimeSec < cutoffSec }
            stale.keys.forEach { rows.remove(it) }
            return stale.size
        }
    }

    private class FakeRemote(
        vararg pages: Pair<Int, List<GameRecordDto>>,
    ) : MatchRemoteSource {
        val pages: MutableMap<Int, List<GameRecordDto>> = pages.toMap().toMutableMap()
        /** 记成 `模式#页码`，三个模式的翻页是分开的，只记页码看不出问题。 */
        val requested = mutableListOf<String>()

        override suspend fun page(page: Int, limit: Int, mapMode: String): List<GameRecordDto> {
            requested += "$mapMode#$page"
            return pages[page] ?: emptyList()
        }
    }

    /** 每次都返回满页，用来验证 maxPages 兜底。 */
    private class EndlessRemote(private val pageSize: Int) : MatchRemoteSource {
        private var seq = 0

        override suspend fun page(page: Int, limit: Int, mapMode: String): List<GameRecordDto> =
            List(pageSize) { fakeRecord("gen-${seq++}") }
    }
}

/** 放在类外面：嵌套的非 inner 类（FakeLocal / EndlessRemote）也要用它造数据。 */
private fun fakeRecord(
    roomId: String,
    finTime: Int = 100,
    eventTime: String = "2026-09-04 22:15:16",
): GameRecordDto = GameRecordDto(
    DsRoomId = roomId,
    iFinTime = finTime,
    dtEventTime = eventTime,
    iDuration = 600,
    iScore = 12345L,
)
