package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.settings.MapSeasonFilter
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.domain.GameMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/**
 * 地图分布页「按赛季筛选」的勾选持久化。
 *
 * 这套逻辑里唯一容易写错的是 **null 和空集合的分工**：前者是"从没设过"（= 全选），
 * 后者是"用户一格都没勾"。混起来的话，用户全取消之后下次进来会莫名变回全选。
 */
class MapSeasonFilterTest {

    private class FakeStore : KeyValueStore {
        val values = HashMap<String, String>()
        override suspend fun read(key: String): String? = values[key]
        override suspend fun write(key: String, value: String) {
            values[key] = value
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }

        override fun peek(key: String): String? = values[key]
    }

    private fun filter() = MapSeasonFilter(FakeStore())

    /** 第一次用：没有任何记录，selectionOf 是 null（调用方按全选处理）。 */
    @Test
    fun firstUseHasNoSelection() = runBlocking {
        val filter = filter()
        filter.restore()
        assertNull(filter.selectionOf(GameMode.HUNT))
        assertNull(filter.selectionOf(GameMode.TOWER))
    }

    /**
     * 第一次点某个赛季：基准是**全选**。
     * 用户看到的明明是五个都勾着，点一下"S4"却变成"只剩 S4"会非常莫名其妙。
     */
    @Test
    fun firstToggleStartsFromEverything() = runBlocking {
        val filter = filter()
        filter.restore()

        filter.toggle(GameMode.HUNT, "S4")
        assertEquals(setOf("S0", "S1", "S2", "S3"), filter.selectionOf(GameMode.HUNT))
    }

    /** 全取消之后是**空集合**，不是 null —— 下次进来还得是"一个都没勾"。 */
    @Test
    fun deselectingEverythingKeepsAnEmptySet() = runBlocking {
        val store = FakeStore()
        val filter = MapSeasonFilter(store)
        filter.restore()

        for (key in listOf("S0", "S1", "S2", "S3", "S4")) {
            filter.toggle(GameMode.HUNT, key)
        }
        assertEquals(emptySet<String>(), filter.selectionOf(GameMode.HUNT))
        assertEquals("", store.values[StoreKey.MAP_SEASON_FILTER_HUNT])

        // 重新读一遍：不能因为"值是空串"就被当成没设过
        val restored = MapSeasonFilter(store)
        restored.restore()
        assertEquals(emptySet<String>(), restored.selectionOf(GameMode.HUNT))
    }

    /** 猎场和塔防各存一份，互不影响。 */
    @Test
    fun huntAndTowerAreStoredSeparately() = runBlocking {
        val store = FakeStore()
        val filter = MapSeasonFilter(store)
        filter.restore()

        filter.toggle(GameMode.HUNT, "S4")
        filter.toggle(GameMode.TOWER, "S0")

        assertEquals(setOf("S0", "S1", "S2", "S3"), filter.selectionOf(GameMode.HUNT))
        // 塔防第一次点走的是塔防自己的全选（同样 5 个赛季）
        assertEquals(setOf("S1", "S2", "S3", "S4"), filter.selectionOf(GameMode.TOWER))

        val restored = MapSeasonFilter(store)
        restored.restore()
        assertEquals(setOf("S0", "S1", "S2", "S3"), restored.selectionOf(GameMode.HUNT))
        assertEquals(setOf("S1", "S2", "S3", "S4"), restored.selectionOf(GameMode.TOWER))
    }

    /** 时空追猎没有赛季表，一律当"不筛"。 */
    @Test
    fun modesWithoutSeasonsAreNeverStored() = runBlocking {
        val filter = filter()
        filter.restore()
        assertNull(filter.selectionOf(GameMode.TIME_HUNT))
    }
}
