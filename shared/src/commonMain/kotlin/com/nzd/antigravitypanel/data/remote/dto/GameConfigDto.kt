package com.nzd.antigravitypanel.data.remote.dto

import com.nzd.antigravitypanel.data.remote.LenientInt
import com.nzd.antigravitypanel.data.remote.LenientLong
import com.nzd.antigravitypanel.data.remote.LenientString
import kotlinx.serialization.Serializable

/**
 * `center.config.list` 下发的游戏配置，**不要硬编码**。
 *
 * 三张表在 JSON 里都是对象（key 是 id 的字符串形式），不是数组，
 * 所以按 `Map<String, …>` 收。
 */
@Serializable
data class GameConfigDto(
    val difficultyInfo: Map<String, DifficultyDto> = emptyMap(),
    val huntingFieldPartitionArea: Map<String, String> = emptyMap(),
    val mapInfo: Map<String, MapInfoDto> = emptyMap(),
)

@Serializable
data class DifficultyDto(
    @Serializable(with = LenientInt::class)
    val id: Int = 0,
    val name: String = "",
)

/**
 * 单张地图。
 *
 * ⚠️ **统计口径看 `supportStatistics`，不要自己排除"机甲/PVP"**。
 * 早期开源版本是硬编码排除规则，官方后来加了这张表就是为了替代它。
 */
@Serializable
data class MapInfoDto(
    @Serializable(with = LenientInt::class)
    val id: Int = 0,
    val name: String = "",
    val mode: String = "",
    val icon: String = "",
    val supportStatistics: Boolean = false,
    /**
     * 这张图支持的副本 / 难度组合。
     *
     * **这是把 `center.user.map.stats` 的 `mapModeData` 翻译成难度名的唯一钥匙** ——
     * `mapModeData` 的 key 是副本 id（`2004085` 这种），不是 `iSubModeType`：
     *
     * ```
     * mapInfo[17].dungeonList = [{id:2004085, difficulty:6}, ...]
     * mapStats  [{map_id:17, total:500, mapModeData:{"2004085":270, ...}}]
     * ```
     *
     * 早期这里留成 `JsonElement` 是因为没见到样本，现在样本是齐的（见 `HarFixtures`）。
     * 没有副本表的图（新手关、`dungeonList: null`）拿不到难度分布，卡片就只有总数。
     */
    val dungeonList: List<DungeonDto>? = null,
)

/**
 * 一个副本（地图 × 难度的组合）。
 *
 * `id` 是服务端内部的副本 id，七个数字 + 一位序号，和 `iMapId` / `iSubModeType`
 * 都对不上，只能靠这张表翻译。`difficulty` 就是 `iSubModeType`。
 */
@Serializable
data class DungeonDto(
    @Serializable(with = LenientLong::class)
    val id: Long = 0,
    @Serializable(with = LenientInt::class)
    val difficulty: Int = 0,
)

/** `center.map.item.list`：地图掉落物，按 mapID 分组。 */
@Serializable
data class MapItemListDto(
    val itemMap: Map<String, List<MapItemDto>> = emptyMap(),
)

@Serializable
data class MapItemDto(
    @Serializable(with = LenientInt::class)
    val id: Int = 0,
    @Serializable(with = LenientString::class)
    val mapID: String = "",
    @Serializable(with = LenientString::class)
    val itemID: String = "",
    val name: String = "",
    @Serializable(with = LenientInt::class)
    val quality: Int = 0,
    val icon: String = "",
    @Serializable(with = LenientInt::class)
    val sort: Int = 0,
    val owned: Boolean = false,
)

/** `center.config.list` 的响应外层还包了一层 `config`。 */
@Serializable
data class ConfigListResponseDto(
    val config: GameConfigDto? = null,
)
