package com.nzd.antigravitypanel.data.repo

import com.nzd.antigravitypanel.data.db.MatchBossStatDao
import com.nzd.antigravitypanel.data.db.MatchBossStatEntity
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.remote.dto.GameDetailDto
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 每局 Boss 伤害口径的读写。历史页的「实力局 / 带飞局」两个标签靠它。
 *
 * 这些数字**只有** `center.game.detail` 会给，列表接口一个字都没有，所以整套逻辑
 * 就是"拉到一次落一次库"：
 * - 详情页打开某局时顺手记一笔（反正详情已经拉了，不额外花请求）
 * - 历史页在后台补拉还没有记录的局，补完的标签下次进来直接就有
 *
 * 补拉失败不是错误：老对局服务端可能已经没有了，那局就永远没有标签，
 * 写一条全 0 的行把它标记成"试过"，免得每次进页面都重试。
 */
class MatchBossStats(
    private val api: NzApi,
    private val dao: MatchBossStatDao,
) {

    /** `roomId -> 该局的 Boss 口径`。UI 直接拿它判断标签。 */
    fun observe(): Flow<Map<String, MatchBossStatEntity>> =
        dao.observeAll().map { rows -> rows.associateBy { it.roomId } }

    /** 没有凭证时补拉是无意义的（接口会直接抛缺凭证），调用方用它挡一道。 */
    fun hasCredential(): Boolean = api.hasCookie()

    /**
     * 拉一局详情并记进缓存。
     *
     * @return 写进去的内容；没凭证 / 接口挂了 / 详情里没有"自己"时返回 null（**不写库**，
     *   这样下次还能重试）。
     */
    suspend fun refresh(roomId: String): MatchBossStatEntity? {
        val detail = runCatching { api.gameDetail(roomId) }.getOrNull() ?: return null
        return record(roomId, detail)
    }

    /**
     * 把**已经拿到手**的详情写进缓存。详情页用它，不重复发请求。
     *
     * 详情里没有 `loginUserDetail`（自己不在名单里）时返回 null 且不写库 ——
     * 那不是"这局没有 Boss 数据"，而是"这份详情不完整"，写一条 0 会永久挡掉补拉。
     */
    suspend fun record(roomId: String, detail: GameDetailDto): MatchBossStatEntity? {
        val entity = bossStatOf(roomId, detail, currentEpochSeconds()) ?: return null
        runCatching { dao.upsert(entity) }
        return entity
    }

    /** 补拉前的去重基准：库里已经有的（含"试过但没数据"的）都列出来。 */
    suspend fun knownRoomIds(): List<String> = runCatching { dao.knownRoomIds() }.getOrDefault(emptyList())

    /** 清掉对局表已经没有的那些行。保留期裁剪之后调一次，别让它无限攒。 */
    suspend fun deleteOrphans() {
        runCatching { dao.deleteOrphans() }
    }

    /** 设置页「清空本地数据」用。 */
    suspend fun clear() {
        runCatching { dao.clear() }
    }
}

/**
 * 从单局详情里算出三个 Boss 伤害数。纯函数，方便单测。
 *
 * ⚠️ "谁是自己"优先认 `loginUserDetail`；认队友时按 `vOpenID` 排除自己，
 * 但 openId 是空串时（服务端偶尔这么给）退回到"排除同一个对象"——
 * 否则 `"" != ""` 会把所有人都当成自己，第二名永远是 0。
 */
fun bossStatOf(
    roomId: String,
    detail: GameDetailDto,
    nowSec: Long,
): MatchBossStatEntity? {
    val self = detail.loginUserDetail ?: return null
    val players = detail.list
    if (players.isEmpty()) return null

    val selfId = self.baseDetail?.vOpenID.orEmpty()
    val rivals = if (selfId.isBlank()) {
        players.filter { it !== self }
    } else {
        players.filter { it.baseDetail?.vOpenID.orEmpty() != selfId }
    }

    return MatchBossStatEntity(
        roomId = roomId,
        selfBossDamage = self.huntingDetails?.damageTotalOnBoss ?: 0L,
        teamBossDamage = players.sumOf { it.huntingDetails?.damageTotalOnBoss ?: 0L },
        rivalBossDamage = rivals.maxOfOrNull { it.huntingDetails?.damageTotalOnBoss ?: 0L } ?: 0L,
        updatedAtSec = nowSec,
    )
}
