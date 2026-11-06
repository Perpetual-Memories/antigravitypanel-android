package com.nzd.antigravitypanel.data.repo

import com.nzd.antigravitypanel.data.config.RemoteConfig
import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.NzApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** 战绩数据的唯一入口：远端走 [NzApi]，本地走 [MatchDao]，两边由 [MatchSyncer] 缝合。 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchRepository(
    private val api: NzApi,
    private val dao: MatchDao,
    config: RemoteConfig = RemoteConfig(),
    /** 当前凭证 owner。空账号只观察空数据，不能退回整表或空 owner 旧行。 */
    private val ownerOpenId: StateFlow<String?> = MutableStateFlow(null),
) {
    private var currentConfig: RemoteConfig = config

    private var syncer: MatchSyncer = buildSyncer(config)

    fun observeAll(): Flow<List<MatchEntity>> = ownerOpenId.flatMapLatest { owner ->
        if (owner.isNullOrBlank()) flowOf(emptyList()) else dao.observeAll(owner)
    }

    fun observeCount(): Flow<Long> = ownerOpenId.flatMapLatest { owner ->
        if (owner.isNullOrBlank()) flowOf(0L) else dao.observeCount(owner)
    }

    suspend fun count(): Long = ownerOpenId.value
        ?.takeIf { it.isNotBlank() }
        ?.let { dao.count(it) }
        ?: 0L

    suspend fun page(limit: Int, offset: Int): List<MatchEntity> = ownerOpenId.value
        ?.takeIf { it.isNotBlank() }
        ?.let { dao.page(it, limit, offset) }
        .orEmpty()

    /**
     * 拉一次增量。所有本地读写都显式使用凭证 openid，不能使用战绩 DTO 的 vOpenID。
     *
     * @param nowSec 当前时间，交给调用方注入以便测试 & 保证与保留期裁剪用同一个基准
     */
    suspend fun sync(
        cookie: MiniProgramCredential,
        nowSec: Long,
        retentionMonths: Int = MatchSyncer.DEFAULT_RETENTION_MONTHS,
    ): SyncResult {
        require(cookie.openid.isNotBlank()) { "同步对局需要凭证 openid" }
        api.updateCookie(cookie)
        return syncer.sync(cookie.openid, nowSec, retentionMonths)
    }

    /** 远程配置到位后调一次。pageSize / maxPages 变了要重建同步器。 */
    fun updateConfig(value: RemoteConfig) {
        currentConfig = value
        api.updateConfig(value)
        syncer = buildSyncer(value)
    }

    fun currentConfig(): RemoteConfig = currentConfig

    /**
     * 认领数据库升级前空 owner 的对局及详情。
     *
     * 宿主应在数据库完成迁移、首个有效凭证恢复后调用；重复调用安全，同 roomId 冲突时保留已归属行。
     */
    suspend fun claimLegacyData(ownerOpenId: String) = dao.claimLegacyData(ownerOpenId)

    /** 清除当前账号本地对局（保留凭证）；空 owner 只表示兼容清理未认领旧数据。 */
    suspend fun clearLocal(ownerOpenId: String = this.ownerOpenId.value.orEmpty()) = dao.clear(ownerOpenId)

    private fun buildSyncer(config: RemoteConfig): MatchSyncer = MatchSyncer(
        remote = NzApiMatchRemoteSource(api),
        local = RoomMatchLocalSource(dao),
        pageSize = config.pageSize,
        maxPages = config.maxPages,
    )
}
