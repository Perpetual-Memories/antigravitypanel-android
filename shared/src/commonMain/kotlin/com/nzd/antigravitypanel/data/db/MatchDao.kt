package com.nzd.antigravitypanel.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

    /** 只查当前凭证 owner，别的账号即使有相同 roomId 也不能参与增量同步判断。 */
    @Query("SELECT roomId, finished FROM matches WHERE ownerOpenId = :ownerOpenId AND roomId IN (:roomIds)")
    suspend fun knownStates(ownerOpenId: String, roomIds: List<String>): List<MatchKnownRow>

    @Query("SELECT * FROM matches WHERE ownerOpenId = :ownerOpenId AND roomId = :roomId")
    suspend fun find(ownerOpenId: String, roomId: String): MatchEntity?

    /** 同账号、同地图 / 同难度的上一局。 */
    @Query(
        "SELECT * FROM matches WHERE ownerOpenId = :ownerOpenId " +
            "AND mapId = :mapId AND subModeType = :subModeType " +
            "AND eventTimeSec > 0 AND eventTimeSec < :beforeSec " +
            "ORDER BY eventTimeSec DESC, roomId DESC LIMIT 1",
    )
    suspend fun previousOf(
        ownerOpenId: String,
        mapId: Int,
        subModeType: Int,
        beforeSec: Long,
    ): MatchEntity?

    /** 同一秒开两局理论上是可能的（不同区），加 roomId 兜底保证翻页不重不漏。 */
    @Query(
        "SELECT * FROM matches WHERE ownerOpenId = :ownerOpenId " +
            "ORDER BY eventTimeSec DESC, roomId DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun page(ownerOpenId: String, limit: Int, offset: Int): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE ownerOpenId = :ownerOpenId ORDER BY eventTimeSec DESC, roomId DESC")
    fun observeAll(ownerOpenId: String): Flow<List<MatchEntity>>

    /** 当前账号的导出快照，绝不能退化成整表读取。 */
    @Query("SELECT * FROM matches WHERE ownerOpenId = :ownerOpenId ORDER BY eventTimeSec DESC, roomId DESC")
    suspend fun all(ownerOpenId: String): List<MatchEntity>

    @Query("SELECT COUNT(*) FROM matches WHERE ownerOpenId = :ownerOpenId")
    fun observeCount(ownerOpenId: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM matches WHERE ownerOpenId = :ownerOpenId")
    suspend fun count(ownerOpenId: String): Long

    @Query("SELECT MIN(eventTimeSec) FROM matches WHERE ownerOpenId = :ownerOpenId")
    suspend fun oldestTimeSec(ownerOpenId: String): Long?

    @Query("SELECT MAX(eventTimeSec) FROM matches WHERE ownerOpenId = :ownerOpenId")
    suspend fun newestTimeSec(ownerOpenId: String): Long?

    /** 保留期裁剪只作用当前账号；eventTimeSec = 0 的解析失败记录继续保留。 */
    @Query(
        "DELETE FROM matches WHERE ownerOpenId = :ownerOpenId " +
            "AND eventTimeSec > 0 AND eventTimeSec < :cutoffSec",
    )
    suspend fun deleteOlderThan(ownerOpenId: String, cutoffSec: Long): Int

    @Query("DELETE FROM matches WHERE ownerOpenId = :ownerOpenId")
    suspend fun clear(ownerOpenId: String)

    /**
     * 把迁移前（ownerOpenId = ''）的对局和详情认领给首个已迁移账号。
     *
     * `UPDATE OR IGNORE` 遇到同账号已有同 roomId 时保留现有行；随后清掉认领后仍空 owner
     * 的旧行（也就是这些冲突行），因此重复调用不会产生副本，也不会覆盖新同步的数据。
     */
    @Transaction
    suspend fun claimLegacyData(ownerOpenId: String) {
        if (ownerOpenId.isBlank()) return
        claimLegacyMatches(ownerOpenId)
        claimLegacyBossStats(ownerOpenId)
        deleteUnclaimedBossStats()
        deleteUnclaimedMatches()
    }

    @Query("UPDATE OR IGNORE matches SET ownerOpenId = :ownerOpenId WHERE ownerOpenId = ''")
    suspend fun claimLegacyMatches(ownerOpenId: String): Int

    @Query("UPDATE OR IGNORE match_boss_stats SET ownerOpenId = :ownerOpenId WHERE ownerOpenId = ''")
    suspend fun claimLegacyBossStats(ownerOpenId: String): Int

    @Query("DELETE FROM matches WHERE ownerOpenId = ''")
    suspend fun deleteUnclaimedMatches(): Int

    @Query("DELETE FROM match_boss_stats WHERE ownerOpenId = ''")
    suspend fun deleteUnclaimedBossStats(): Int
}

/** [MatchDao.knownStates] 的返回行。列名必须和 SELECT 的别名对上。 */
data class MatchKnownRow(
    val roomId: String,
    val finished: Boolean,
)
