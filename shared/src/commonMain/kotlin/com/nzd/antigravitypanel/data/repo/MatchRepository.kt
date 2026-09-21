package com.nzd.antigravitypanel.data.repo

import com.nzd.antigravitypanel.data.config.RemoteConfig
import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.NzApi
import kotlinx.coroutines.flow.Flow

/**
 * 战绩数据的唯一入口：远端走 [NzApi]，本地走 [MatchDao]，两边由 [MatchSyncer] 缝合。
 *
 * 之所以等到 P3 才写这个类：P2 结束时还没有任何消费者，提前加就是没人用的死代码。
 */
class MatchRepository(
    private val api: NzApi,
    private val dao: MatchDao,
    config: RemoteConfig = RemoteConfig(),
) {
    private var currentConfig: RemoteConfig = config

    private var syncer: MatchSyncer = buildSyncer(config)

    fun observeAll(): Flow<List<MatchEntity>> = dao.observeAll()

    fun observeCount(): Flow<Long> = dao.observeCount()

    suspend fun count(): Long = dao.count()

    suspend fun page(limit: Int, offset: Int): List<MatchEntity> = dao.page(limit, offset)

    /**
     * 拉一次增量。[cookie] 每次都传，因为用户可能刚换过凭证。
     *
     * @param nowSec 当前时间，交给调用方注入以便测试 & 保证与保留期裁剪用同一个基准
     */
    suspend fun sync(
        cookie: NzCookie,
        nowSec: Long,
        retentionMonths: Int = MatchSyncer.DEFAULT_RETENTION_MONTHS,
    ): SyncResult {
        api.updateCookie(cookie)
        return syncer.sync(nowSec, retentionMonths)
    }

    /** 远程配置到位后调一次。pageSize / maxPages 变了要重建同步器。 */
    fun updateConfig(value: RemoteConfig) {
        currentConfig = value
        api.updateConfig(value)
        syncer = buildSyncer(value)
    }

    fun currentConfig(): RemoteConfig = currentConfig

    /** 清除本地对局（保留凭证）。设置页"清空本地数据"用。 */
    suspend fun clearLocal() = dao.clear()

    private fun buildSyncer(config: RemoteConfig): MatchSyncer = MatchSyncer(
        remote = NzApiMatchRemoteSource(api),
        local = RoomMatchLocalSource(dao),
        pageSize = config.pageSize,
        maxPages = config.maxPages,
    )
}
