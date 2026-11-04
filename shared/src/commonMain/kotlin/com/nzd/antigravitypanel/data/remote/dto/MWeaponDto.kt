package com.nzd.antigravitypanel.data.remote.dto

import com.nzd.antigravitypanel.data.remote.LenientInt
import com.nzd.antigravitypanel.data.remote.LenientLong
import kotlinx.serialization.Serializable

/**
 * `center.mweapon.list` 的响应。2026-11-04 抓包（S4）：
 *
 * ```json
 * {"totalCount":1,"list":[{"id":527613,"weaponID":20113000070,"seasonID":4,
 *   "level":148,"unlockTime":"2026-07-07 22:39:37","eventTime":"2026-11-03 07:14:19"}],
 *  "relateWeapon":{...}}
 * ```
 *
 * 只声明要用的那几列：这个接口还挂着整把武器的详情（技能、外观、被动），
 * 解析进来只会白占内存。`ignoreUnknownKeys` 会把其余字段丢掉。
 *
 * ⚠️ `level` 是**万象武器等级**，也就是账号的赛季等级（PC 端账号面板那个 `LV.n`）。
 * 它不是武器强化等级，别拿来做别的用途。
 */
@Serializable
data class MWeaponListDto(
    @Serializable(with = LenientInt::class)
    val totalCount: Int = 0,
    val list: List<MWeaponDto> = emptyList(),
) {
    /** 赛季等级：多把万象武器时取最高的那把。没有武器（新号 / 没打）时为 0。 */
    val seasonLevel: Int get() = list.maxOfOrNull { it.level } ?: 0
}

@Serializable
data class MWeaponDto(
    @Serializable(with = LenientLong::class)
    val id: Long = 0,
    @Serializable(with = LenientLong::class)
    val weaponID: Long = 0,
    @Serializable(with = LenientInt::class)
    val seasonID: Int = 0,
    /** 赛季等级。 */
    @Serializable(with = LenientInt::class)
    val level: Int = 0,
    val unlockTime: String = "",
    val eventTime: String = "",
)
