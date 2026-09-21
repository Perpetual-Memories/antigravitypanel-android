package com.nzd.antigravitypanel.data.repo

import com.nzd.antigravitypanel.data.remote.dto.UserStatsDto
import com.nzd.antigravitypanel.domain.ActivityEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 概览页的落盘缓存。
 *
 * 存在的理由只有一个：**冷启动时概览页不该先空一拍**。
 * 近五场要逐局拉详情、活动日历要走一次 `dist.contents`，两个都是必失败于弱网的慢请求；
 * 不缓存的话每次开 app 都会先看到「正在统计…」和「暂无进行中的活动」，
 * 然后等一两秒才跳出来，像是数据丢了。
 *
 * 缓存只在下一次拉取**成功**时被覆盖：拉失败要留着上一份（见
 * [OverviewViewModel.refresh] 里的 `?: _state.value.recent`），
 * 否则一次网络抖动就把已经显示出来的内容打回空态，比慢更难受。
 *
 * @param cookieStatus 上次确认过的凭证状态名。存名字而不是枚举，是为了让这一层
 *   不反向依赖 `ui.overview.CookieStatus`；解析不出来就当没缓存。
 * @param savedAtSec 落盘时间戳。UI 不读它，纯粹是排查"这份缓存多老了"用。
 */
@Serializable
data class OverviewCache(
    val cookieStatus: String = "MISSING",
    val stats: UserStatsDto = UserStatsDto(),
    val recent: RecentFive? = null,
    val activities: List<ActivityEvent> = emptyList(),
    val savedAtSec: Long = 0,
)

/**
 * 缓存的编解码。
 *
 * 这里的 Json **不是** `IdeJson`（那个是在解官方接口，带一堆宽容策略）。
 * 缓存是我们自己写自己读的，只要能在字段增删后不炸就行，所以只开 `ignoreUnknownKeys`：
 * 老版本 app 写进去的字段，新版本读的时候多余的直接丢；反过来少字段会走默认值。
 */
object OverviewCacheCodec {
    private val Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun encode(cache: OverviewCache): String = Json.encodeToString(cache)

    /** 解析不出来返回 null，调用方当"没有缓存"处理——缓存读不了不该让 app 挂掉。 */
    fun decode(raw: String): OverviewCache? =
        runCatching { Json.decodeFromString<OverviewCache>(raw) }.getOrNull()
}
