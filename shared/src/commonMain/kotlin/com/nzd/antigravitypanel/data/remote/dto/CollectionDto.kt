package com.nzd.antigravitypanel.data.remote.dto

import com.nzd.antigravitypanel.data.remote.LenientInt
import com.nzd.antigravitypanel.data.remote.LenientLong
import com.nzd.antigravitypanel.data.remote.LenientString
import kotlinx.serialization.Serializable

/**
 * 图鉴条目。
 *
 * 八种图鉴（武器 / 陷阱 / 插件 / 角色 / 挂饰 / 机甲 / 机甲皮肤 / 塔防皮肤）
 * 的 id 与名称字段名**各不相同**：`weaponID` / `itemID` / `skinID` / `roleID` /
 * `pendantID` …，名字字段也是 `weaponName` / `itemName` / `trapName` / `name` 混着来。
 *
 * 与其写八个 DTO，P1 阶段先用一个并集把它们都收下来，统一用
 * [displayName] / [displayIcon] / [anyId] 取用；真要分类建模留到 P4 做图鉴页时再拆。
 */
@Serializable
data class CollectionItemDto(
    // —— 各类型各自的 id ——
    @Serializable(with = LenientLong::class)
    val weaponID: Long = 0,
    @Serializable(with = LenientLong::class)
    val itemID: Long = 0,
    @Serializable(with = LenientLong::class)
    val skinID: Long = 0,
    @Serializable(with = LenientLong::class)
    val roleID: Long = 0,
    @Serializable(with = LenientLong::class)
    val pendantID: Long = 0,
    @Serializable(with = LenientLong::class)
    val towerItemID: Long = 0,
    @Serializable(with = LenientLong::class)
    val originMechaID: Long = 0,
    @Serializable(with = LenientLong::class)
    val originTrapID: Long = 0,
    @Serializable(with = LenientLong::class)
    val bindingSkin: Long = 0,
    @Serializable(with = LenientLong::class)
    val belongedRoleID: Long = 0,
    @Serializable(with = LenientInt::class)
    val id: Int = 0,

    // —— 各类型各自的名 ——
    val weaponName: String = "",
    val itemName: String = "",
    val trapName: String = "",
    val name: String = "",

    // —— 图片：有的用 pic，有的用 icon ——
    val pic: String = "",
    val icon: String = "",

    @Serializable(with = LenientInt::class)
    val quality: Int = 0,
    val ammoType: String = "",
    val description: String = "",
    val unitType: String = "",
    @Serializable(with = LenientInt::class)
    val trapType: Int = 0,
    @Serializable(with = LenientInt::class)
    val slotIndex: Int = 0,
    @Serializable(with = LenientInt::class)
    val sort: Int = 0,
    @Serializable(with = LenientInt::class)
    val wearPositionI: Int = 0,
    @Serializable(with = LenientInt::class)
    val wearPositionII: Int = 0,
    val wearPositionDesc: String = "",
    @Serializable(with = LenientString::class)
    val mapID: String = "",
    val owned: Boolean = false,
    val isDefault: Boolean = false,
    val isNewest: Boolean = false,
    val unlockTime: String = "",
    val itemProgress: ItemProgressDto? = null,
) {
    val anyId: Long
        get() = listOf(weaponID, itemID, skinID, roleID, pendantID, towerItemID, id.toLong())
            .firstOrNull { it != 0L } ?: 0L

    val displayName: String
        get() = weaponName.ifBlank { itemName.ifBlank { trapName.ifBlank { name } } }

    val displayIcon: String get() = pic.ifBlank { icon }
}

@Serializable
data class ItemProgressDto(
    @Serializable(with = LenientInt::class)
    val current: Int = 0,
    @Serializable(with = LenientInt::class)
    val required: Int = 0,
)

/** 八种图鉴列表的响应外层都是 `{ list: [...] }`。 */
@Serializable
data class CollectionListDto(
    val list: List<CollectionItemDto> = emptyList(),
)

/** `collection.home`：首页只展示前 N 件武器。 */
@Serializable
data class CollectionHomeDto(
    val weaponList: List<CollectionItemDto> = emptyList(),
)

/** `gear.config.list` / `dist.contents`：内容运营位，结构由服务端定，原样收。 */
@Serializable
data class RawConfigDto(
    val config: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class DistContentsDto(
    @Serializable(with = LenientInt::class)
    val configID: Int = 0,
    val content: String = "",
    val currentTime: String = "",
)

/**
 * 活动日历那支 `dist.contents` 的响应。
 *
 * 不能复用 [DistContentsDto]：那边 `content` 是 String，而这支下发的是**对象**
 * （官方前端直接 `content.rilipeizhi.data` 往下取）。用 String 去接会在反序列化那一步
 * 就抛异常，被上层 `runCatching` 吞掉后表现为"活动日历永远是空的"。
 * 所以这里原样收成 [JsonElement]，交给 `parseCalendar` 自己去剥。
 */
@Serializable
data class DistCalendarDto(
    @Serializable(with = LenientInt::class)
    val configID: Int = 0,
    val content: kotlinx.serialization.json.JsonElement? = null,
    val currentTime: String = "",
)

/** `thread.search`：攻略帖搜索。P4 之前用不到，先把壳留着。 */
@Serializable
data class ThreadSearchDto(
    val currentTime: String = "",
    val list: List<ThreadItemDto> = emptyList(),
    @Serializable(with = LenientInt::class)
    val total: Int = 0,
    val readCursorID: String = "",
    val hasMore: Boolean = false,
)

@Serializable
data class ThreadItemDto(
    val threadID: String = "",
    val title: String = "",
    val authorName: String = "",
)
