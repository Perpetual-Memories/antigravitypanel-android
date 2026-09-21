package com.nzd.antigravitypanel.data.remote.dto

import com.nzd.antigravitypanel.data.remote.LenientBoolean
import com.nzd.antigravitypanel.data.remote.LenientInt
import com.nzd.antigravitypanel.data.remote.LenientLong
import com.nzd.antigravitypanel.data.remote.LenientString
import com.nzd.antigravitypanel.util.percentDecoded
import kotlinx.serialization.Serializable

/**
 * `center.game.detail` 的单局详情。
 *
 * ⚠️ `nickname` / `avatar` 是 URL 编码的，而且两处编码层数还不一样：
 * - `list[].avatar` 编了一层，解出来就是 `https://...`
 * - `loginUserDetail.avatar` 编了两层，解出来还是 `http%3A%2F%2F...`
 *
 * 所以解码要"解到解不动为止"，见 [percentDecoded]。
 */
@Serializable
data class GameDetailDto(
    val loginUserDetail: PlayerDetailDto? = null,
    @Serializable(with = LenientBoolean::class)
    val isRogueMatch: Boolean = false,
    val list: List<PlayerDetailDto> = emptyList(),
)

@Serializable
data class PlayerDetailDto(
    val nickname: String = "",
    val avatar: String = "",
    val baseDetail: GameRecordDto? = null,
    val huntingDetails: HuntingDetailsDto? = null,
    val equipmentScheme: List<EquipmentDto> = emptyList(),
)

val PlayerDetailDto.nicknameDecoded: String get() = nickname.percentDecoded()

val PlayerDetailDto.avatarDecoded: String get() = avatar.percentDecoded()

@Serializable
data class HuntingDetailsDto(
    @Serializable(with = LenientLong::class)
    val totalCoin: Long = 0,
    @Serializable(with = LenientLong::class)
    val damageTotalOnBoss: Long = 0,
    @Serializable(with = LenientLong::class)
    val damageTotalOnMobs: Long = 0,
    val partitionDetails: List<PartitionDetailDto> = emptyList(),
)

/**
 * 单个分区（Boss）的耗时。
 * `areaId` 要去 `center.config.list` 的 `huntingFieldPartitionArea` 里查名字。
 */
@Serializable
data class PartitionDetailDto(
    @Serializable(with = LenientString::class)
    val areaId: String = "",
    @Serializable(with = LenientInt::class)
    val usedTime: Int = 0,
)

@Serializable
data class EquipmentDto(
    @Serializable(with = LenientLong::class)
    val weaponID: Long = 0,
    val weaponName: String = "",
    @Serializable(with = LenientInt::class)
    val quality: Int = 0,
    val pic: String = "",
    val commonItems: List<CommonItemDto> = emptyList(),
)

@Serializable
data class CommonItemDto(
    @Serializable(with = LenientLong::class)
    val itemID: Long = 0,
    val itemName: String = "",
    @Serializable(with = LenientInt::class)
    val quality: Int = 0,
    val pic: String = "",
)
