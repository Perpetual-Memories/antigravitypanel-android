package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.remote.dto.MapItemDto
import com.nzd.antigravitypanel.data.remote.dto.MapStatsDto
import com.nzd.antigravitypanel.data.repo.MapDropsCache
import com.nzd.antigravitypanel.data.repo.MapDropsCacheCodec
import com.nzd.antigravitypanel.data.repo.MapStatsCache
import com.nzd.antigravitypanel.data.repo.MapStatsCacheCodec
import com.nzd.antigravitypanel.data.repo.toDropsCache
import com.nzd.antigravitypanel.data.repo.toIntMap
import com.nzd.antigravitypanel.data.repo.toModeMap
import com.nzd.antigravitypanel.data.repo.toStatsCache
import com.nzd.antigravitypanel.domain.GameMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 地图分布页那两份缓存。
 *
 * 它们存在的唯一理由就是"冷启动那一两秒别摆错的数"，所以最该锁住的是
 * **往返不丢**：模式名转不回枚举、mapID 转不回 int，缓存就等于白存。
 */
class MapStatsCacheTest {

    private fun stats() = mapOf(
        GameMode.HUNT to listOf(
            MapStatsDto(map_id = 17, total = 515, mapModeData = mapOf("2004081" to 4)),
            MapStatsDto(map_id = 18, total = 77),
        ),
        GameMode.TOWER to listOf(MapStatsDto(map_id = 304, total = 12)),
    )

    private fun drops() = mapOf(
        17 to listOf(MapItemDto(id = 1, mapID = "17", name = "核心", owned = true)),
        18 to listOf(MapItemDto(id = 2, mapID = "18", name = "碎片")),
    )

    @Test
    fun 官方统计按模式名往返不丢() {
        val raw = MapStatsCacheCodec.encode(stats().toStatsCache(1_700_000_000))
        val decoded = MapStatsCacheCodec.decode(raw)?.toModeMap()
        assertEquals(stats(), decoded)
        assertEquals(2, decoded?.get(GameMode.HUNT)?.size)
        assertEquals(515, decoded?.get(GameMode.HUNT)?.first()?.total)
        // 难度号段是 mapModeData 里的东西，丢了整张卡的难度列就是空的
        assertEquals(4, decoded?.get(GameMode.HUNT)?.first()?.mapModeData?.get("2004081"))
    }

    @Test
    fun 认不出的模式名被丢掉而不是把整份缓存作废() {
        val cache = MapStatsCache(
            byMode = mapOf("HUNT" to stats().getValue(GameMode.HUNT), "FUTURE_MODE" to emptyList()),
        )
        val decoded = MapStatsCacheCodec.decode(MapStatsCacheCodec.encode(cache))?.toModeMap()
        assertEquals(setOf(GameMode.HUNT), decoded?.keys)
    }

    @Test
    fun 掉落物按mapID字符串往返不丢() {
        val decoded = MapDropsCacheCodec.decode(
            MapDropsCacheCodec.encode(drops().toDropsCache(1_700_000_000)),
        )?.toIntMap()
        assertEquals(drops(), decoded)
        // owned 是卡片背面"我已解锁"的唯一依据
        assertEquals(true, decoded?.get(17)?.first()?.owned)
        assertEquals(false, decoded?.get(18)?.first()?.owned)
    }

    @Test
    fun 转不出int的mapID被丢掉() {
        val cache = MapDropsCache(
            byMap = mapOf("17" to drops().getValue(17), "not-a-number" to emptyList()),
        )
        assertEquals(setOf(17), MapDropsCacheCodec.decode(MapDropsCacheCodec.encode(cache))?.toIntMap()?.keys)
    }

    @Test
    fun 坏掉的缓存返回null而不是抛异常() {
        assertNull(MapStatsCacheCodec.decode(""))
        assertNull(MapStatsCacheCodec.decode("{ 这不是 json"))
        assertNull(MapDropsCacheCodec.decode("""{"byMap":{"17":[{"id":1,"nam"""))
    }

    @Test
    fun 空缓存是空表而不是null() {
        // 冷启动时没有缓存要能走到"本地兜底"，不能因为解码出 null 崩在别处
        val decoded = MapStatsCacheCodec.decode(MapStatsCacheCodec.encode(MapStatsCache()))
        assertTrue(decoded?.toModeMap().isNullOrEmpty())
        val decodedDrops = MapDropsCacheCodec.decode(MapDropsCacheCodec.encode(MapDropsCache()))
        assertTrue(decodedDrops?.toIntMap().isNullOrEmpty())
    }
}
