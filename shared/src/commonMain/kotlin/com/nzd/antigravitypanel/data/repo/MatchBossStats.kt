package com.nzd.antigravitypanel.data.repo

import com.nzd.antigravitypanel.data.db.MatchBossStatDao
import com.nzd.antigravitypanel.data.db.MatchBossStatEntity
import com.nzd.antigravitypanel.data.db.cachedDetailOf
import com.nzd.antigravitypanel.data.db.encodeCachedDetail
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.remote.dto.GameDetailDto
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 每局 Boss 伤害口径的读写；所有缓存都以凭证 owner + roomId 为身份。 */
class MatchBossStats(
    private val api: NzApi,
    private val dao: MatchBossStatDao,
) {

    /** 当前账号的 `roomId -> 该局的 Boss 口径`。 */
    fun observe(ownerOpenId: String): Flow<Map<String, MatchBossStatEntity>> =
        dao.observeAll(ownerOpenId).map { rows -> rows.associateBy { it.roomId } }

    /** 当前账号的导出快照。空 owner 仅用于未认领旧数据的兼容导出。 */
    suspend fun snapshot(ownerOpenId: String = ""): Map<String, MatchBossStatEntity> =
        runCatching { dao.all(ownerOpenId) }.getOrDefault(emptyList()).associateBy { it.roomId }

    /** 没有凭证时补拉是无意义的（接口会直接抛缺凭证），调用方用它挡一道。 */
    fun hasCredential(): Boolean = api.hasCookie()

    /** 拉一局详情并记到 [ownerOpenId] 名下；空 owner 不写新缓存。 */
    suspend fun refresh(ownerOpenId: String, roomId: String): MatchBossStatEntity? {
        if (ownerOpenId.isBlank()) return null
        val detail = runCatching { api.gameDetail(roomId) }.getOrNull() ?: return null
        return record(ownerOpenId, roomId, detail)
    }

    /** 把已经拿到手的详情写进当前 owner 缓存，不重复发请求。 */
    suspend fun record(
        ownerOpenId: String,
        roomId: String,
        detail: GameDetailDto,
    ): MatchBossStatEntity? {
        if (ownerOpenId.isBlank()) return null
        val entity = bossStatOf(roomId, detail, currentEpochSeconds(), ownerOpenId) ?: return null
        val saved = entity.copy(payloadJson = cachedDetailOf(detail)?.let(::encodeCachedDetail))
        runCatching { dao.upsert(saved) }
        return saved
    }

    /** 当前账号补拉前的去重基准。 */
    suspend fun knownRoomIds(ownerOpenId: String): List<String> =
        runCatching { dao.knownRoomIds(ownerOpenId) }.getOrDefault(emptyList())

    /** 清掉当前账号对局表已经没有的那些行。 */
    suspend fun deleteOrphans(ownerOpenId: String) {
        runCatching { dao.deleteOrphans(ownerOpenId) }
    }

    /** 清空当前账号详情；空 owner 仅用于未认领旧数据的兼容清理。 */
    suspend fun clear(ownerOpenId: String = "") {
        runCatching { dao.clear(ownerOpenId) }
    }
}

/** 一局的 Boss 伤害三个口径：自己 / 全队 / 第二名（除自己外最高）。 */
data class BossDamageShare(
    val self: Long,
    val team: Long,
    val rival: Long,
)

fun bossDamageShareOf(detail: GameDetailDto): BossDamageShare? {
    val self = detail.loginUserDetail ?: return null
    val players = detail.list
    if (players.isEmpty()) return null

    // vOpenID 只用于识别这份详情里的队友，绝不能代替数据库的凭证 ownerOpenId。
    val selfId = self.baseDetail?.vOpenID.orEmpty()
    val rivals = if (selfId.isBlank()) {
        players.filter { it !== self }
    } else {
        players.filter { it.baseDetail?.vOpenID.orEmpty() != selfId }
    }

    return BossDamageShare(
        self = self.huntingDetails?.damageTotalOnBoss ?: 0L,
        team = players.sumOf { it.huntingDetails?.damageTotalOnBoss ?: 0L },
        rival = rivals.maxOfOrNull { it.huntingDetails?.damageTotalOnBoss ?: 0L } ?: 0L,
    )
}

fun bossStatOf(
    roomId: String,
    detail: GameDetailDto,
    nowSec: Long,
    ownerOpenId: String = "",
): MatchBossStatEntity? {
    val share = bossDamageShareOf(detail) ?: return null

    return MatchBossStatEntity(
        roomId = roomId,
        selfBossDamage = share.self,
        teamBossDamage = share.team,
        rivalBossDamage = share.rival,
        updatedAtSec = nowSec,
        ownerOpenId = ownerOpenId,
    )
}
