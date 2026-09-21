package com.nzd.antigravitypanel.data.imports

import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.util.parseServerTime
import kotlinx.serialization.Serializable

/**
 * 反重力数据面板导出的单条对局（`nzm_matches.json` 里的一个元素）。
 *
 * 和列表接口的 [com.nzd.antigravitypanel.data.remote.dto.GameRecordDto] 有三处不一样，
 * 照搬字段名会直接踩进去：
 *
 * - 这边是真数字（`DsRoomId: Long` / `AreaID: Int`），列表接口那边全是字符串；
 *   入库统一转成 String，因为 [MatchEntity.roomId] 是 String 主键
 * - `iIsWin` 是**三态**的：0=未完成/异常、1=胜、2=负。列表接口里它是 Boolean，
 *   空串表示没打完。所以这里不能直接 `iIsWin == 1` 之外就当负，要看 `iFinTime`
 * - 导出文件里多了 `mapName` / `modeName` / `diffName` / `icon` 四个展示用的冗余字段，
 *   而且只有部分记录有（实测 2226 条里 428 条带）。声明出来只是为了不用
 *   `ignoreUnknownKeys` 兜底，入库时一个都不用
 */
@Serializable
data class ImportedMatchDto(
    val DsRoomId: Long = 0,
    val dtEventTime: String = "",
    val iMapId: Int = 0,
    /** 0=未完成/异常，1=胜利，2=失败。 */
    val iIsWin: Int = 0,
    val iScore: Long = 0,
    val iSubModeType: Int = 0,
    /** 32 位十六进制的 openid，和 [vOpenID] 是同一个人，只是进制不同。 */
    val openid: String = "",
    val vOpenID: Long? = null,
    val iKills: Int = 0,
    val iDeaths: Int = 0,
    val iAssists: Int = 0,
    val iDuration: Int = 0,
    val Rank: Int? = null,
    val iFinTime: Int? = null,
    val iGameMode: Int = 0,
    val iModeType: Int = 0,
    val AreaID: Int = 0,
    val SeasonId: String = "",
    val dtGameStartTime: String = "",
    val mapName: String? = null,
    val modeName: String? = null,
    val diffName: String? = null,
    val icon: String? = null,
) {

    /** 三态里的"胜利"。 */
    val isWin: Boolean get() = iIsWin == WIN

    companion object {
        const val INCOMPLETE = 0
        const val WIN = 1
        const val LOSE = 2
    }
}

/**
 * 导出记录 → 本地库实体。
 *
 * `finished` 只认 `iFinTime`：`iIsWin == 0` 与 `iFinTime` 为空在实测数据里是
 * 完全重合的两件事（2226 条里各 185 条），但"打完了吗"这件事本身该由完赛时间说话，
 * 胜平负是另一个维度。
 */
fun ImportedMatchDto.toEntity(syncedAtSec: Long = 0L): MatchEntity = MatchEntity(
    roomId = DsRoomId.toString(),
    openId = openid,
    eventTime = dtEventTime,
    eventTimeSec = parseServerTime(dtEventTime) ?: 0L,
    startTime = dtGameStartTime,
    areaId = AreaID.toString(),
    finTime = iFinTime ?: 0,
    duration = iDuration,
    isWin = isWin,
    gameMode = iGameMode,
    modeType = iModeType,
    subModeType = iSubModeType,
    mapId = iMapId,
    score = iScore,
    seasonId = SeasonId,
    kills = iKills,
    deaths = iDeaths,
    assists = iAssists,
    rankValue = Rank ?: 0,
    finished = (iFinTime ?: 0) > 0,
    syncedAtSec = syncedAtSec,
)
