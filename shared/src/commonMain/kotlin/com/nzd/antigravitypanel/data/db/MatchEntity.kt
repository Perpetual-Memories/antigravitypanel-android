package com.nzd.antigravitypanel.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nzd.antigravitypanel.data.remote.dto.GameRecordDto
import com.nzd.antigravitypanel.util.parseServerTime

/**
 * 对局表。
 *
 * 存在的唯一理由：服务端只保留滚动窗口内的对局（近 30 天 / 最近 100 场，先到先算），
 * 老数据会永久滚出且没法再拉回来。所以每拉到一页就落库，本地才是唯一的历史来源。
 *
 * 两个命名上的坑：
 * - 列名 `rankValue` 而不是 `rank`：SQLite 3.25+ 里 RANK 是窗口函数名，别去赌它能不能当标识符
 * - `eventTimeSec` 是冗余列，专门给排序 / 保留期裁剪用的，别拿 `eventTime` 字符串去比大小
 */
@Entity(
    tableName = "matches",
    indices = [
        Index(value = ["eventTimeSec"]),
        Index(value = ["mapId"]),
        Index(value = ["openId"]),
    ],
)
data class MatchEntity(
    @PrimaryKey
    @ColumnInfo(name = "roomId")
    val roomId: String,
    @ColumnInfo(name = "openId")
    val openId: String,
    /** 原始时间串，形如 "2026-09-04 22:15:16"，只用于展示。 */
    @ColumnInfo(name = "eventTime")
    val eventTime: String,
    /** 解析后的 epoch 秒（按 UTC+8）。解析失败为 0，排序时自然沉底。 */
    @ColumnInfo(name = "eventTimeSec")
    val eventTimeSec: Long,
    @ColumnInfo(name = "startTime")
    val startTime: String,
    @ColumnInfo(name = "areaId")
    val areaId: String,
    @ColumnInfo(name = "finTime")
    val finTime: Int,
    @ColumnInfo(name = "duration")
    val duration: Int,
    @ColumnInfo(name = "isWin")
    val isWin: Boolean,
    @ColumnInfo(name = "gameMode")
    val gameMode: Int,
    @ColumnInfo(name = "modeType")
    val modeType: Int,
    @ColumnInfo(name = "subModeType")
    val subModeType: Int,
    @ColumnInfo(name = "mapId")
    val mapId: Int,
    @ColumnInfo(name = "score")
    val score: Long,
    @ColumnInfo(name = "seasonId")
    val seasonId: String,
    @ColumnInfo(name = "kills")
    val kills: Int,
    @ColumnInfo(name = "deaths")
    val deaths: Int,
    @ColumnInfo(name = "assists")
    val assists: Int,
    @ColumnInfo(name = "rankValue")
    val rankValue: Int,
    /** 冗余列，查询时可以直接 `WHERE finished = 1`，不用每次算 `finTime > 0`。 */
    @ColumnInfo(name = "finished")
    val finished: Boolean,
    /** 本地入库时间，用于排查"这条到底是哪次同步拉回来的"。 */
    @ColumnInfo(name = "syncedAtSec")
    val syncedAtSec: Long,
)

fun GameRecordDto.toEntity(syncedAtSec: Long = 0L): MatchEntity = MatchEntity(
    roomId = DsRoomId,
    openId = vOpenID,
    eventTime = dtEventTime,
    eventTimeSec = parseServerTime(dtEventTime) ?: 0L,
    startTime = dtGameStartTime,
    areaId = AreaID,
    finTime = iFinTime,
    duration = iDuration,
    isWin = iIsWin,
    gameMode = iGameMode,
    modeType = iModeType,
    subModeType = iSubModeType,
    mapId = iMapId,
    score = iScore,
    seasonId = SeasonId,
    kills = iKills,
    deaths = iDeaths,
    assists = iAssists,
    rankValue = Rank,
    finished = finished,
    syncedAtSec = syncedAtSec,
)
