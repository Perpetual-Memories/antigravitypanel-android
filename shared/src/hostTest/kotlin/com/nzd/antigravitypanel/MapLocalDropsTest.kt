package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.remote.dto.CollectionItemDto
import com.nzd.antigravitypanel.data.remote.dto.MapItemDto
import com.nzd.antigravitypanel.domain.DifficultyCount
import com.nzd.antigravitypanel.domain.MapSection
import com.nzd.antigravitypanel.domain.MapStatEntry
import com.nzd.antigravitypanel.domain.allDropsOwned
import com.nzd.antigravitypanel.domain.filterCompletedSections
import com.nzd.antigravitypanel.domain.mergeLocalDrops
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 地图分布页那三件「官方接口不下发」的隐藏掉落物，以及「未毕业」筛选。
 *
 * 口径全部照官方 PC 端 2.0.0 的 `mapCollection` 组装函数：
 * 19 补 AI精灵-晶耀、17 补 白蚁背包-幻巢、13 补 AI精灵-鎏光，
 * 名字 / 图标 / 是否解锁都从 `collection.pendant.list` 里按名字捞。
 */
class MapLocalDropsTest {

    private fun drop(name: String, owned: Boolean = false, itemID: String = name) = MapItemDto(
        itemID = itemID,
        name = name,
        quality = 2,
        owned = owned,
    )

    private fun pendant(name: String, owned: Boolean = false) = CollectionItemDto(
        pendantID = 1,
        name = name,
        icon = "icon://$name",
        owned = owned,
    )

    private fun apiDrops(): Map<Int, List<MapItemDto>> = mapOf(
        17 to listOf(drop("胡八一"), drop("紫背包")),
        19 to listOf(drop("樱之泪")),
        13 to emptyList(),
    )

    /** 三张图各补一件，插在最前面，品质是传说（4）。 */
    @Test
    fun addsHiddenDropToEachMap() {
        val merged = mergeLocalDrops(
            drops = apiDrops(),
            pendants = listOf(
                pendant("白蚁背包·幻巢", owned = true),
                pendant("AI精灵•晶耀"),
                pendant("AI精灵-鎏光"),
            ),
        )

        val jingjue = merged.getValue(17)
        assertEquals("白蚁背包-幻巢", jingjue.first().name)
        assertEquals(4, jingjue.first().quality)
        assertTrue(jingjue.first().owned)
        assertEquals("icon://白蚁背包·幻巢", jingjue.first().icon)
        // 原来的两件还在后面，顺序不动
        assertEquals(listOf("白蚁背包-幻巢", "胡八一", "紫背包"), jingjue.map { it.name })

        assertEquals("AI精灵-晶耀", merged.getValue(19).first().name)
        assertEquals("AI精灵-鎏光", merged.getValue(13).first().name)
    }

    /** 名字两边的间隔号可能一个 `·` 一个 `•`，比对前统一成 `-`。 */
    @Test
    fun matchesNamesAcrossSeparators() {
        val merged = mergeLocalDrops(
            drops = mapOf(17 to listOf(drop("白蚁背包·幻巢"))),
            pendants = listOf(pendant("白蚁背包•幻巢")),
        )
        // 官方已经下发了这件，不该再补一格
        assertEquals(1, merged.getValue(17).size)
    }

    /** 官方哪天开始自己下发这件了：靠名字判重，不能靠 itemID（id 是服务端分配的）。 */
    @Test
    fun doesNotDuplicateWhenServerAlreadySendsIt() {
        val merged = mergeLocalDrops(
            drops = mapOf(19 to listOf(drop("AI精灵-晶耀", itemID = "server-id"))),
            pendants = listOf(pendant("AI精灵-晶耀")),
        )
        assertEquals(1, merged.getValue(19).size)
    }

    /** 官方这份里没有这张图 = 这张图没有收集品数据，别硬造一格。 */
    @Test
    fun leavesUnknownMapsAlone() {
        val merged = mergeLocalDrops(drops = mapOf(17 to listOf(drop("胡八一"))), pendants = emptyList())
        assertFalse(merged.containsKey(19))
        assertFalse(merged.containsKey(13))
    }

    /**
     * 挂件图鉴那发失败、也没有上次的缓存：整条跳过。
     * 凭空报一件"未解锁"会让「未毕业」筛选永远剔不掉这张图。
     */
    @Test
    fun skipsWhenPendantsUnavailable() {
        val merged = mergeLocalDrops(
            drops = apiDrops(),
            pendants = emptyList(),
            pendantsLoaded = false,
        )
        assertEquals(listOf("胡八一", "紫背包"), merged.getValue(17).map { it.name })
    }

    /** 挂件图鉴失败但上次补过：沿用上次的图标和进度。 */
    @Test
    fun fallsBackToPreviousCopy() {
        val previous = mapOf(17 to listOf(drop("白蚁背包-幻巢", owned = true, itemID = "local_baiyibeibao")))
        val merged = mergeLocalDrops(
            drops = apiDrops(),
            pendants = emptyList(),
            previous = previous,
            pendantsLoaded = false,
        )
        val item = merged.getValue(17).first()
        assertEquals("白蚁背包-幻巢", item.name)
        assertTrue(item.owned)
    }

    /** 挂件图鉴拉到了、但这三件不在表里：照 PC 补一个空图标的格子（未解锁）。 */
    @Test
    fun addsBareSlotWhenPendantIsMissing() {
        val merged = mergeLocalDrops(drops = apiDrops(), pendants = emptyList(), pendantsLoaded = true)
        val item = merged.getValue(17).first()
        assertEquals("白蚁背包-幻巢", item.name)
        assertEquals("", item.icon)
        assertFalse(item.owned)
    }

    /** 有数据且全解锁 = 毕业；没有数据 = 判不了（null），不能当成已毕业。 */
    @Test
    fun completionNeedsData() {
        val drops = mapOf(
            17 to listOf(drop("a", owned = true), drop("b", owned = true)),
            19 to listOf(drop("a", owned = true), drop("b", owned = false)),
        )
        assertTrue(drops.allDropsOwned(17) == true)
        assertFalse(drops.allDropsOwned(19) == true)
        assertNull(drops.allDropsOwned(13))
        assertNull(mapOf<Int, List<MapItemDto>>(13 to emptyList()).allDropsOwned(13))
    }

    /** 整节都毕业了就连小标题一起不摆，别留一个空标题。 */
    @Test
    fun filtersCompletedSectionsAndDropsEmptyOnes() {
        val sections = listOf(
            MapSection("S1 赛季", listOf(entry(17), entry(16))),
            MapSection("S2 赛季", listOf(entry(19))),
        )
        val drops = mapOf(
            17 to listOf(drop("a", owned = true)),
            16 to listOf(drop("a", owned = false)),
            19 to listOf(drop("a", owned = true)),
        )
        val kept = filterCompletedSections(sections, drops)
        assertEquals(listOf("S1 赛季"), kept.map { it.title })
        assertEquals(listOf(16), kept.first().entries.map { it.mapId })
    }

    private fun entry(mapId: Int) = MapStatEntry(
        mapId = mapId,
        name = "地图$mapId",
        total = 0,
        win = 0,
        byDifficulty = emptyList<DifficultyCount>(),
    )
}
