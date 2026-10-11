package com.nzd.antigravitypanel.data.repo

import com.nzd.antigravitypanel.data.remote.dto.MapItemDto
import com.nzd.antigravitypanel.data.remote.dto.MapStatsDto
import com.nzd.antigravitypanel.domain.GameMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 地图分布页「官方统计」（`center.user.map.stats`）的落盘缓存。
 *
 * 存在的理由：**冷启动那一两秒里这一页不该摆错的数**。
 * 官方统计要凭证，而凭证是宿主在 `refreshAll` 里才挂到 api 上的；
 * 没有缓存时这一页只能先用本地库的场次顶上，而本地口径是「场次」、
 * 官方是「通关」，两个数天然对不上 —— 表现就是"开 app 时地图分布显示 A，
 * 同步完跳成 B，其中 B 才是小程序里那个数"。
 * 摆一份上次的官方统计至少是"上次确认过的真值"，比半截的本地库靠谱。
 *
 * 存的是**原始值**（按模式分组的 `MapStatsDto` 列表），不是算好的卡片条目：
 * 游戏配置（`center.config.list`）可能比它晚到，留原始值才能让配置一到就重算。
 *
 * @param byMode key 是 [GameMode] 的枚举名。用名字而不是枚举本身，
 *   是为了让这个纯数据类不因为以后调整枚举顺序而读不出来。
 */
@Serializable
data class MapStatsCache(
    val byMode: Map<String, List<MapStatsDto>> = emptyMap(),
    val savedAtSec: Long = 0,
)

/**
 * 地图掉落物（`center.map.item.list`）的落盘缓存。
 *
 * 和官方统计同一条理由：它也要凭证，冷启动没缓存时卡片背面会先写
 * 「还没有设置 cookie」，等同步完才换成正经的收集进度。
 *
 * @param byMap key 是 mapID 的**字符串**形式（服务端本来就是这么下发的），
 *   原样存省得在缓存这一层再来回转类型。
 */
@Serializable
data class MapDropsCache(
    val byMap: Map<String, List<MapItemDto>> = emptyMap(),
    val savedAtSec: Long = 0,
)

/** 缓存 → 运行时用的那份（按模式枚举索引）。认不出的模式名直接丢。 */
fun MapStatsCache.toModeMap(): Map<GameMode, List<MapStatsDto>> =
    byMode.entries.mapNotNull { (key, list) ->
        val mode = GameMode.entries.firstOrNull { it.name == key } ?: return@mapNotNull null
        mode to list
    }.toMap()

fun Map<GameMode, List<MapStatsDto>>.toStatsCache(nowSec: Long): MapStatsCache =
    MapStatsCache(
        byMode = entries.associate { (mode, list) -> mode.name to list },
        savedAtSec = nowSec,
    )

/** 缓存 → 运行时用的那份（按 mapID 索引）。转不出 int 的 key 直接丢。 */
fun MapDropsCache.toIntMap(): Map<Int, List<MapItemDto>> =
    byMap.entries.mapNotNull { (key, list) ->
        val mapId = key.toIntOrNull() ?: return@mapNotNull null
        mapId to list
    }.toMap()

fun Map<Int, List<MapItemDto>>.toDropsCache(nowSec: Long): MapDropsCache =
    MapDropsCache(
        byMap = entries.associate { (mapId, list) -> mapId.toString() to list },
        savedAtSec = nowSec,
    )

/**
 * 缓存的编解码。
 *
 * 和 [OverviewCacheCodec] 一样是自己写自己读，只开 `ignoreUnknownKeys`：
 * 以后加字段时老缓存不至于把 app 搞崩，少字段则走默认值。
 * 解析不出来返回 null，调用方当"没有缓存"处理。
 */
object MapStatsCacheCodec {
    private val Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun encode(cache: MapStatsCache): String = Json.encodeToString(cache)

    fun decode(raw: String): MapStatsCache? =
        runCatching { Json.decodeFromString<MapStatsCache>(raw) }.getOrNull()
}

object MapDropsCacheCodec {
    private val Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun encode(cache: MapDropsCache): String = Json.encodeToString(cache)

    fun decode(raw: String): MapDropsCache? =
        runCatching { Json.decodeFromString<MapDropsCache>(raw) }.getOrNull()
}
