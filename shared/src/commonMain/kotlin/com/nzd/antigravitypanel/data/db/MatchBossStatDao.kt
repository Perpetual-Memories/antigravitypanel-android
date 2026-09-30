package com.nzd.antigravitypanel.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchBossStatDao {

    @Upsert
    suspend fun upsert(item: MatchBossStatEntity)

    /** 给历史页做标签用。表撑死跟对局数一样多，整表读进来比按 id 逐个查省事。 */
    @Query("SELECT * FROM match_boss_stats")
    fun observeAll(): Flow<List<MatchBossStatEntity>>

    /** 已经试过的 roomId（不区分成功还是失败），补拉前先读一遍去重。 */
    @Query("SELECT roomId FROM match_boss_stats")
    suspend fun knownRoomIds(): List<String>

    /**
     * 跟着对局表走：对局被保留期裁掉以后，这边的行也该删，否则会无限攒下去。
     *
     * `NOT IN` 而不是连表删除：Room 在 KMP 下对 `DELETE ... WHERE ... IN (SELECT ...)`
     * 的支持是够用的，写起来也最直白。
     */
    @Query("DELETE FROM match_boss_stats WHERE roomId NOT IN (SELECT roomId FROM matches)")
    suspend fun deleteOrphans(): Int

    @Query("DELETE FROM match_boss_stats")
    suspend fun clear()
}
