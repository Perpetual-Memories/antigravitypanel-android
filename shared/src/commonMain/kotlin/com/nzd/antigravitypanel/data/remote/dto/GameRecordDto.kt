package com.nzd.antigravitypanel.data.remote.dto

import com.nzd.antigravitypanel.data.remote.LenientBoolean
import com.nzd.antigravitypanel.data.remote.LenientInt
import com.nzd.antigravitypanel.data.remote.LenientLong
import com.nzd.antigravitypanel.data.remote.LenientString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `center.user.game.list` 的单条记录，18 个字段全是字符串，且可能为空串。
 *
 * 两个容易踩的点：
 * - `DsRoomId` 是 17 位数字，不用 Long（存 String 更省心，也方便直接当主键）
 * - `iIsWin` 空串表示这局没打完，不是"输了"
 */
@Serializable
data class GameRecordDto(
    @Serializable(with = LenientString::class)
    val dtEventTime: String = "",
    @Serializable(with = LenientString::class)
    val dtGameStartTime: String = "",
    @Serializable(with = LenientString::class)
    val vOpenID: String = "",
    @Serializable(with = LenientString::class)
    val AreaID: String = "",
    @Serializable(with = LenientInt::class)
    val iFinTime: Int = 0,
    @Serializable(with = LenientInt::class)
    val iDuration: Int = 0,
    @Serializable(with = LenientString::class)
    val DsRoomId: String = "",
    @Serializable(with = LenientBoolean::class)
    val iIsWin: Boolean = false,
    @Serializable(with = LenientInt::class)
    val iGameMode: Int = 0,
    @Serializable(with = LenientInt::class)
    val iModeType: Int = 0,
    @Serializable(with = LenientInt::class)
    val iSubModeType: Int = 0,
    @Serializable(with = LenientInt::class)
    val iMapId: Int = 0,
    @Serializable(with = LenientLong::class)
    val iScore: Long = 0,
    @Serializable(with = LenientString::class)
    val SeasonId: String = "",
    @Serializable(with = LenientInt::class)
    val iKills: Int = 0,
    @Serializable(with = LenientInt::class)
    val iDeaths: Int = 0,
    @Serializable(with = LenientInt::class)
    val iAssists: Int = 0,
    @Serializable(with = LenientInt::class)
    val Rank: Int = 0,
) {
    /** 这局是否打完了。未完成的局 KDA 仍然有值，但胜率统计必须排除掉。 */
    val finished: Boolean get() = iFinTime > 0
}

@Serializable
data class GameListPageDto(
    @Serializable(with = LenientInt::class)
    val page: Int = 0,
    @Serializable(with = LenientInt::class)
    val limit: Int = 0,
    @SerialName("gameList")
    val gameList: List<GameRecordDto> = emptyList(),
)
