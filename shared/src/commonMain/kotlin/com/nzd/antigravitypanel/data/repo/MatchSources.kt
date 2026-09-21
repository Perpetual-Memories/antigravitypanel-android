package com.nzd.antigravitypanel.data.repo

import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.remote.dto.GameRecordDto

/** [MatchLocalSource] 的 Room 实现。 */
class RoomMatchLocalSource(
    private val dao: MatchDao,
) : MatchLocalSource {

    override suspend fun knownStates(roomIds: List<String>): Map<String, Boolean> {
        if (roomIds.isEmpty()) return emptyMap()
        return dao.knownStates(roomIds).associate { it.roomId to it.finished }
    }

    override suspend fun upsertAll(entities: List<MatchEntity>) {
        if (entities.isEmpty()) return
        dao.upsertAll(entities)
    }

    override suspend fun deleteOlderThan(cutoffSec: Long): Int = dao.deleteOlderThan(cutoffSec)
}

/**
 * [MatchRemoteSource] 的 Ktor 实现。
 *
 * 顺手把 roomId 为空的记录滤掉：拿空串当主键会污染整张表（所有空 roomId 会互相覆盖），
 * 宁可丢一条可疑数据也不能让它进来。
 */
class NzApiMatchRemoteSource(
    private val api: NzApi,
) : MatchRemoteSource {

    override suspend fun page(page: Int, limit: Int, mapMode: String): List<GameRecordDto> =
        api.gameList(page, limit, mapMode).gameList.filter { it.DsRoomId.isNotBlank() }
}
