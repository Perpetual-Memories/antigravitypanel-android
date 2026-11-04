package com.nzd.antigravitypanel.data.remote.dto

import com.nzd.antigravitypanel.data.remote.LenientInt
import com.nzd.antigravitypanel.data.remote.LenientLong
import com.nzd.antigravitypanel.data.remote.LenientString
import kotlinx.serialization.Serializable

/** `center.user.stats`：赛季总览。playtime 单位是秒。 */
@Serializable
data class UserStatsDto(
    @Serializable(with = LenientLong::class)
    val playtime: Long = 0,
    @Serializable(with = LenientInt::class)
    val mechaGameCount: Int = 0,
    @Serializable(with = LenientInt::class)
    val towerGameCount: Int = 0,
    @Serializable(with = LenientInt::class)
    val huntGameCount: Int = 0,
    @Serializable(with = LenientInt::class)
    val timeHuntGameCount: Int = 0,
)

/**
 * `center.user.map.stats`：地图维度统计。
 *
 * `mapModeData` 的 key 是难度/子模式 id，value 是场次，形状像
 * `{"2004081": 4, "2004085": 270}`，所以按字符串收，别按数字。
 */
@Serializable
data class MapStatsDto(
    @Serializable(with = LenientInt::class)
    val map_id: Int = 0,
    @Serializable(with = LenientInt::class)
    val total: Int = 0,
    val mapModeData: Map<String, @Serializable(with = LenientInt::class) Int> = emptyMap(),
)

/** `center.user.day`：今日首胜等。 */
@Serializable
data class UserDayDto(
    val currentTime: String = "",
    @Serializable(with = LenientInt::class)
    val firstWinLeftCount: Int = 0,
)

/** `user.info`。实测 `loginStatus=2` 为正常登录。 */
@Serializable
data class UserInfoDto(
    @Serializable(with = LenientInt::class)
    val loginStatus: Int = 0,
)

/** `weekly.report.status`。 */
@Serializable
data class WeeklyReportDto(
    @Serializable(with = LenientInt::class)
    val is_online: Int = 0,
    @Serializable(with = LenientInt::class)
    val is_playgame: Int = 0,
)

/** `center.user.map.drop`：掉落记录，实测返回空数组。 */
typealias MapDropDto = List<MapDropItemDto>

@Serializable
data class MapDropItemDto(
    @Serializable(with = LenientString::class)
    val mapId: String = "",
    @Serializable(with = LenientString::class)
    val itemName: String = "",
)
