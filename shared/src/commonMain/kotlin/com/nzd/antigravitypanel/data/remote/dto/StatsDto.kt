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
    /**
     * 赛季等级。PC 端 2.0.0 的账号面板显示的就是它（`LV.148`）。
     *
     * 服务端字段名在 PC 那边只露出过 `seasonLevel` / `seasonStartLevel` / `seasonEndLevel`
     * 三个（Rust 侧的结算结构里用的是后两个），所以这里三个都收、按优先级取第一个 >0 的：
     * 字段不存在时宽松解析会落 0，不会炸；真名换了也不会整块消失。
     */
    @Serializable(with = LenientInt::class)
    val seasonLevel: Int = 0,
    @Serializable(with = LenientInt::class)
    val seasonEndLevel: Int = 0,
    @Serializable(with = LenientInt::class)
    val seasonStartLevel: Int = 0,
) {
    /** 展示用的等级。零表示服务端没给（还没登录 / 老接口），UI 那边整段不摆。 */
    val level: Int
        get() = seasonLevel.takeIf { it > 0 }
            ?: seasonEndLevel.takeIf { it > 0 }
            ?: seasonStartLevel.takeIf { it > 0 }
            ?: 0
}

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
