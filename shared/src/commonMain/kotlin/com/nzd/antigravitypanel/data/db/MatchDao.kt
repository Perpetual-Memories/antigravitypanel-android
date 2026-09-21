package com.nzd.antigravitypanel.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {

    /**
     * 增量同步的主力：整页写入。
     *
     * 用 Upsert 而不是 IGNORE——未完成对局的字段（iFinTime / iIsWin / iScore）会在
     * 打完之后变，IGNORE 会让这些记录永远停在第一次拉到的样子。
     */
    @Upsert
    suspend fun upsertAll(entities: List<MatchEntity>)

    /**
     * 增量同步的刹车：查这一页里哪些 roomId 库里已经有了，以及它们的完成状态。
     *
     * 一次查完而不是逐条查，翻一页只多一次往返。SQLite 的 `IN` 上限 999，
     * 而 page_size 才 10，不会碰到。
     */
    @Query("SELECT roomId, finished FROM matches WHERE roomId IN (:roomIds)")
    suspend fun knownStates(roomIds: List<String>): List<MatchKnownRow>

    @Query("SELECT * FROM matches WHERE roomId = :roomId")
    suspend fun find(roomId: String): MatchEntity?

    /**
     * 同地图 / 同难度的**上一局**，用来在详情页做「更快 / 更慢」对比。
     *
     * 只比本地库里已有的：为了拿上局的时长再去打一次详情接口不划算
     * （那个接口按 roomId 查，一次只能查一局，而且配额有限）。
     * `eventTimeSec > 0` 是排除解析失败沉底的那批。
     */
    @Query(
        "SELECT * FROM matches WHERE mapId = :mapId AND subModeType = :subModeType " +
            "AND eventTimeSec > 0 AND eventTimeSec < :beforeSec " +
            "ORDER BY eventTimeSec DESC, roomId DESC LIMIT 1",
    )
    suspend fun previousOf(mapId: Int, subModeType: Int, beforeSec: Long): MatchEntity?

    /** 同一秒开两局理论上是可能的（不同区），加 roomId 兜底保证翻页不重不漏。 */
    @Query("SELECT * FROM matches ORDER BY eventTimeSec DESC, roomId DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<MatchEntity>

    @Query("SELECT * FROM matches ORDER BY eventTimeSec DESC, roomId DESC")
    fun observeAll(): Flow<List<MatchEntity>>

    @Query("SELECT COUNT(*) FROM matches")
    fun observeCount(): Flow<Long>

    @Query("SELECT COUNT(*) FROM matches")
    suspend fun count(): Long

    @Query("SELECT MIN(eventTimeSec) FROM matches")
    suspend fun oldestTimeSec(): Long?

    @Query("SELECT MAX(eventTimeSec) FROM matches")
    suspend fun newestTimeSec(): Long?

    /**
     * 保留期裁剪。传 0 表示不裁剪（"永久保留"）。
     * `eventTimeSec = 0` 的时间解析失败记录不参与——宁可多留，也不能误删。
     */
    @Query("DELETE FROM matches WHERE eventTimeSec > 0 AND eventTimeSec < :cutoffSec")
    suspend fun deleteOlderThan(cutoffSec: Long): Int

    @Query("DELETE FROM matches")
    suspend fun clear()
}

/** [MatchDao.knownStates] 的返回行。列名必须和 SELECT 的别名对上。 */
data class MatchKnownRow(
    val roomId: String,
    val finished: Boolean,
)
