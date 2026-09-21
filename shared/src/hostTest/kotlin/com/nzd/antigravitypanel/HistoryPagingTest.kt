package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.db.MatchKnownRow
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.data.settings.MatchMarks
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.MatchFilter
import com.nzd.antigravitypanel.ui.history.HistoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 历史战绩的"内存翻页"契约。
 *
 * 这页的分页是在内存里做的（本地库撑死几千行），所以真正要锁住的是三件事：
 * 一屏显示多少、加载更多是否会重置、换筛选是否回到第一页。
 * 这几条一旦破掉，表现是"划到底又跳回开头"或者"再也加载不出更多"，
 * 都属于看一眼数据看不出来、只有滑到列表底部才复现的问题。
 */
class HistoryPagingTest {

    private class FakeStore : KeyValueStore {
        override suspend fun read(key: String): String? = null
        override suspend fun write(key: String, value: String) = Unit
        override suspend fun remove(key: String) = Unit
        override fun peek(key: String): String? = null
    }

    private class FakeDao(matches: List<MatchEntity>) : MatchDao {
        val source = MutableStateFlow(matches)

        override suspend fun upsertAll(entities: List<MatchEntity>) = Unit
        override suspend fun knownStates(roomIds: List<String>): List<MatchKnownRow> = emptyList()
        override suspend fun find(roomId: String): MatchEntity? = null
        override suspend fun previousOf(
            mapId: Int,
            subModeType: Int,
            beforeSec: Long,
        ): MatchEntity? = null

        override suspend fun page(limit: Int, offset: Int): List<MatchEntity> = emptyList()
        override fun observeAll(): Flow<List<MatchEntity>> = source
        override fun observeCount(): Flow<Long> = emptyFlow()
        override suspend fun count(): Long = source.value.size.toLong()
        override suspend fun oldestTimeSec(): Long? = null
        override suspend fun newestTimeSec(): Long? = null
        override suspend fun deleteOlderThan(cutoffSec: Long): Int = 0
        override suspend fun clear() = Unit
    }

    private fun match(index: Int) = MatchEntity(
        roomId = "room-$index",
        openId = "open",
        eventTime = "2026-09-04 22:15:16",
        // 用 eventTimeSec 当排序键：applyMatchFilter 是按时间倒序排的，
        // 全填 0 的话顺序就是未定义的，"加载更多是追加"这条断言会变成随机成功
        eventTimeSec = index.toLong(),
        startTime = "",
        areaId = "",
        finTime = 1,
        duration = 600,
        isWin = true,
        gameMode = 1,
        modeType = 1,
        subModeType = 2,
        // 必须是猎场图（12 在 HUNT_MAP_IDS 里）："换筛选条件回到第一页"那条会按
        // 猎场筛一遍，填个机甲图（>=1000）的话筛完是空的，断言会直接超时
        mapId = 12,
        score = 100,
        seasonId = "3",
        kills = 1,
        deaths = 0,
        assists = 0,
        rankValue = 1,
        finished = true,
        syncedAtSec = 0L,
    )

    /**
     * 按页面的方式把 VM 跑起来：`items` / `hasMore` 都是 `stateIn(Lazily)`，
     * **没有订阅者就永远停在初值**，直接读 `.value` 会读到假的 false。
     * 所以这里先挂上收集器，再断言。
     *
     * 必须用**独立的 scope**：这两个收集器永不结束，挂进 `runBlocking` 的作用域
     * 会让 `runBlocking` 一直等它们，测试直接卡死。用完记得 `cancel()`。
     */
    private fun HistoryViewModel.warmUp(): CoroutineScope {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch { items.collect {} }
        scope.launch { hasMore.collect {} }
        return scope
    }

    private suspend fun HistoryViewModel.awaitSize(size: Int): List<MatchEntity> =
        withTimeout(2_000) { items.first { it.size == size } }

    /** 造一个 VM 并挂上订阅者，断言跑完自动收掉收集器。 */
    private fun withHistory(total: Int, body: suspend (HistoryViewModel) -> Unit) = runBlocking {
        val vm = HistoryViewModel(
            FakeDao((0 until total).map { match(it) }),
            MatchMarks(FakeStore()),
            MutableStateFlow(GameConfigDto()),
        )
        val scope = vm.warmUp()
        try {
            body(vm)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `第一次只显示一屏20条并且还有更多`() = withHistory(50) { vm ->
        assertEquals(20, vm.awaitSize(20).size)
        assertEquals(true, withTimeout(2_000) { vm.hasMore.first { it } })
    }

    @Test
    fun `加载更多是在已算好的结果上追加而不是重来`() = withHistory(50) { vm ->
        val first = vm.awaitSize(20)

        vm.loadMore()
        val second = vm.awaitSize(40)

        // 前面那 20 条必须原样还在——加载更多是追加，不是重来
        assertEquals(first.map { it.roomId }, second.take(20).map { it.roomId })
    }

    @Test
    fun `到底之后hasMore变false`() = withHistory(25) { vm ->
        assertEquals(20, vm.awaitSize(20).size)

        vm.loadMore()
        assertEquals(25, vm.awaitSize(25).size)
        assertEquals(false, withTimeout(2_000) { vm.hasMore.first { !it } })
    }

    @Test
    fun `换筛选条件回到第一页`() = withHistory(50) { vm ->
        vm.awaitSize(20)
        vm.loadMore()
        vm.awaitSize(40)

        // 换个筛选条件：页数得回到第一页，不然会带着上一页的 40 条继续往下叠
        vm.setFilter(MatchFilter(mode = GameMode.HUNT))
        assertEquals(20, vm.awaitSize(20).size)
    }
}
