package com.nzd.antigravitypanel.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchBossStatDao {

    @Upsert
    suspend fun upsert(item: MatchBossStatEntity)

    /** 导入数据包用：整批写入当前 owner 的详情。 */
    @Upsert
    suspend fun upsertAll(items: List<MatchBossStatEntity>)

    /** 历史页只订阅当前账号，避免相同 roomId 的标签互相串号。 */
    @Query("SELECT * FROM match_boss_stats WHERE ownerOpenId = :ownerOpenId")
    fun observeAll(ownerOpenId: String): Flow<List<MatchBossStatEntity>>

    /** 当前账号的导出快照。 */
    @Query("SELECT * FROM match_boss_stats WHERE ownerOpenId = :ownerOpenId")
    suspend fun all(ownerOpenId: String): List<MatchBossStatEntity>

    /** 当前账号已经试过的 roomId（不区分成功还是失败）。 */
    @Query("SELECT roomId FROM match_boss_stats WHERE ownerOpenId = :ownerOpenId")
    suspend fun knownRoomIds(ownerOpenId: String): List<String>

    /** 只清理当前账号的孤儿详情，另一账号的同 roomId 对局不能让这一行被保留或误删。 */
    @Query(
        "DELETE FROM match_boss_stats WHERE ownerOpenId = :ownerOpenId " +
            "AND roomId NOT IN (SELECT roomId FROM matches WHERE ownerOpenId = :ownerOpenId)",
    )
    suspend fun deleteOrphans(ownerOpenId: String): Int

    @Query("DELETE FROM match_boss_stats WHERE ownerOpenId = :ownerOpenId")
    suspend fun clear(ownerOpenId: String)
}
