package com.nzd.antigravitypanel.data.config

import com.nzd.antigravitypanel.data.db.ConfigCacheDao
import com.nzd.antigravitypanel.data.db.ConfigCacheEntity
import com.nzd.antigravitypanel.data.db.ConfigCacheKey
import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.remote.IdeJson
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 游戏配置（地图表 / 难度表 / 猎场分区表）的持有者。
 *
 * UI 到处要用它把 id 翻译成名字（"304" -> "黑暗复活节"），但它只在有凭证之后
 * 才拉得到，所以做成 StateFlow：先给一份空/缓存的，拉到之后再推送。
 *
 * 落库是**缓存**而不是主数据源：拉失败就用上一次的，冷启动不至于满屏"未知(id)"。
 */
class GameConfigRepository(
    private val api: NzApi,
    private val cache: ConfigCacheDao,
) {
    private val _config = MutableStateFlow(GameConfigDto())
    val config: StateFlow<GameConfigDto> = _config.asStateFlow()

    /** 冷启动先读本地快照，别等到网络回来才显示地图名。 */
    suspend fun restoreLocal() {
        val raw = runCatching { cache.get(ConfigCacheKey.GAME_CONFIG)?.json }.getOrNull()
        if (raw.isNullOrBlank()) return
        runCatching { IdeJson.decodeFromString<GameConfigDto>(raw) }
            .onSuccess { _config.value = it }
    }

    /**
     * 拉一次远程配置。失败时保留旧值并把结果放进 Result，
     * 让调用方自己决定要不要提示——概览页不该因为配置没拉到就报错。
     *
     * @param cookie **必须显式传**。这个仓库自己不持有凭证，而 `NzApi` 上的 cookie
     *   是靠别处 `updateCookie()` 设的 —— 排在别人后面调用就会拿到"还没设置 cookie"。
     *   实测踩过：`AppContent` 里先 `configRepository.refresh()` 再 `overview.refresh(cookie)`，
     *   第一步永远失败，而 cookie 之后不再变化，就再也没有重试的机会了。
     */
    suspend fun refresh(cookie: NzCookie): Result<GameConfigDto> = runCatching {
        api.updateCookie(cookie)
        val fetched = api.gameConfig()
        _config.value = fetched
        runCatching {
            cache.put(
                ConfigCacheEntity(
                    cacheKey = ConfigCacheKey.GAME_CONFIG,
                    json = IdeJson.encodeToString(fetched),
                    updatedAtSec = currentEpochSeconds(),
                ),
            )
        }
        fetched
    }
}
