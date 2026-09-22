package com.nzd.antigravitypanel.ui.overview

import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.remote.CookieExpiredException
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.repo.OverviewCache
import com.nzd.antigravitypanel.data.repo.OverviewCacheCodec
import com.nzd.antigravitypanel.data.repo.OverviewMode
import com.nzd.antigravitypanel.data.repo.OverviewRepository
import com.nzd.antigravitypanel.data.repo.OverviewSnapshot
import com.nzd.antigravitypanel.data.repo.RecentFive
import com.nzd.antigravitypanel.data.remote.dto.UserStatsDto
import com.nzd.antigravitypanel.domain.ActivityEvent
import com.nzd.antigravitypanel.domain.upcomingActivities
import com.nzd.antigravitypanel.ui.component.StatusGlyph
import com.nzd.antigravitypanel.ui.home.describeSyncError
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 凭证状态。
 *
 * 「过期」不是靠读 cookie 里的过期时间判断的——官方根本没下发这个字段。
 * 只有真的拿它去请求、拿到 `iRet != 0` 才知道失效了，所以这个状态是**事后**标出来的。
 *
 * 「已导入json」是第四种：没有凭证，但本地库里有导入进来的对局，
 * 概览照样能算出场次和时长。它不是"登录态"，只是告诉用户"你现在看的是本地数据"。
 */
enum class CookieStatus(val label: String, val glyph: StatusGlyph) {
    MISSING("未输入", StatusGlyph.ALERT),
    OK("已识别", StatusGlyph.CHECK),
    EXPIRED("过期", StatusGlyph.ALERT),

    /** 没有凭证，但本地库里有导入的 JSON——照样能看，所以给打勾而不是感叹号。 */
    IMPORTED_JSON("已导入json", StatusGlyph.CHECK),
}

data class OverviewUiState(
    val cookieStatus: CookieStatus = CookieStatus.MISSING,
    val stats: UserStatsDto = UserStatsDto(),
    val mode: OverviewMode = OverviewMode.HUNT,
    val recent: RecentFive? = null,
    val activities: List<ActivityEvent> = emptyList(),
    /** 每日首胜宝箱可领数量；null = 没拉到（没登录或接口失败），不是 0。 */
    val firstWinCount: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 当前数字全部来自本地库（已导入 JSON、未登录）。近五场那几项算不出来，UI 要如实说明。 */
    val localOnly: Boolean = false,

    /**
     * 卡片上显示的是**上次缓存下来的状态**，这一轮的凭证还没被服务端确认。
     *
     * 冷启动时概览要先拿缓存把页面撑起来（否则会先空一拍），但那个"已识别"是上一次的结论，
     * 这次的 cookie 可能已经过期了。所以卡片**先变灰、盖上「同步中」**，
     * 等 [refresh] 跑完（成功或失败都算）再撤掉遮罩，状态该改成什么就改成什么。
     *
     * 只在"确实有一份非 [CookieStatus.MISSING] 的缓存"时才为 true：
     * 本来就没登录的状态卡不该装作正在同步。
     */
    val cookieVerifying: Boolean = false,
)

/**
 * 概览页状态。
 *
 * 概览要混三个接口（总览数字 / 近五场详情 / 运营活动），任何一个失败都不该把整页打空，
 * 所以 [OverviewRepository.load] 内部逐个容错，这里只负责把"凭证过期"单独拎出来——
 * 那是个必须让用户重新抓 cookie 的强信号，不能混在普通错误里。
 *
 * 另外这里还兼着**冷启动缓存**：[restoreCached] 先把上一次的页面内容原样摆回去，
 * [refresh] 拿到新数据后再覆盖。刷新失败时**保留**旧值而不是清空——
 * 一次网络抖动就把已经显示出来的内容打回空态，比慢更难受。
 */
class OverviewViewModel(
    private val api: NzApi,
    private val dao: MatchDao,
    private val store: KeyValueStore,
    /** 有没有导入过 JSON。没有凭证时靠它决定显示「未输入」还是「已导入json」。 */
    private val importedJson: StateFlow<Boolean> = MutableStateFlow(false),
    /**
     * 冷启动时**同步**读到的缓存原文（宿主在 `setContent` 之前预热好存储后取的）。
     *
     * 这一路是必须的：走 suspend 的话第一帧早就用默认状态画完了，
     * 状态卡会先闪一帧「未输入」再跳成「已识别」，看着像状态丢了。
     * 传 null 表示没读到（首次安装 / 预热失败），由 [restoreCached] 兜底。
     */
    private val cachedSeed: String? = null,
) {
    private val repository = OverviewRepository(api, dao)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 构造时同步算出来的那份初始 state；null = 这份缓存用不了。 */
    private val seededState: OverviewUiState? = restoreOverviewFromCache(cachedSeed, currentEpochSeconds())

    private val _state = MutableStateFlow(seededState ?: OverviewUiState())
    val state: StateFlow<OverviewUiState> = _state.asStateFlow()

    /**
     * 兜底：同步那一路没读到时（存储没预热成功），再异步读一次。
     *
     * 仍然要排在 `restored = true` 之前——那是首刷的触发点。
     * 已经同步生效过就直接返回，别把 [refresh] 可能已经拿到的新数据盖回去。
     */
    suspend fun restoreCached() {
        if (seededState != null) return
        val raw = runCatching { store.read(StoreKey.OVERVIEW_CACHE) }.getOrNull() ?: return
        val restored = restoreOverviewFromCache(raw, currentEpochSeconds()) ?: return
        _state.value = restored
    }

    fun refresh(cookie: NzCookie?) {
        if (cookie == null) {
            if (!importedJson.value) {
                _state.value = _state.value.copy(
                    cookieStatus = CookieStatus.MISSING,
                    // 没凭证也没导入：把缓存一起清掉。留着上一份近五场活动日历，
                    // 会让人以为"我已经登录过了，为什么卡片说没输入"。
                    stats = UserStatsDto(),
                    recent = null,
                    activities = emptyList(),
                    loading = false,
                    error = null,
                    localOnly = false,
                    firstWinCount = null,
                    cookieVerifying = false,
                )
                scope.launch { persist(CookieStatus.MISSING) }
                return
            }
            scope.launch {
                _state.value = _state.value.copy(loading = true, error = null)
                val snapshot = runCatching { repository.loadLocal() }
                    .getOrDefault(OverviewSnapshot())
                _state.value = _state.value.copy(
                    cookieStatus = CookieStatus.IMPORTED_JSON,
                    stats = snapshot.stats,
                    // 近五场用本地库算：导入的 JSON 里有评分 / 名次 / 击杀，
                    // 够凑出一份近五场，只是没有 Boss 伤害和金币
                    recent = snapshot.recent ?: _state.value.recent,
                    activities = emptyList(),
                    // 本地模式下这个接口根本没发，显示"不知道"而不是 0
                    firstWinCount = null,
                    loading = false,
                    error = null,
                    localOnly = true,
                    cookieVerifying = false,
                )
                persist(CookieStatus.IMPORTED_JSON)
            }
            return
        }
        scope.launch {
            // 每一轮刷新都重新盖上「同步中」遮罩，冷启动和手动刷新一视同仁：
            // 卡片那句「已识别」是**上一次**的结论，这一轮还没被服务端确认过，
            // 不盖等于拿旧结论冒充新结论。跑完（成功或失败都算）由下面置回 false。
            // 已经是「未输入」时不盖——本来就没登录的状态卡不该装作正在同步。
            if (_state.value.cookieStatus != CookieStatus.MISSING) {
                _state.value = _state.value.copy(cookieVerifying = true)
            }
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val snapshot = repository.load(cookie, currentEpochSeconds())
                _state.value = _state.value.copy(
                    cookieStatus = CookieStatus.OK,
                    stats = snapshot.stats,
                    // 近五场要逐局拉详情，任何一局超时都会让整份变成 null。
                    // 拿不到就留着上一份（可能是缓存，也可能是上一次拉到的），别清空。
                    recent = snapshot.recent ?: _state.value.recent,
                    activities = snapshot.activities.ifEmpty { _state.value.activities },
                    firstWinCount = snapshot.firstWinCount,
                    loading = false,
                    error = null,
                    localOnly = false,
                    cookieVerifying = false,
                )
                persist(CookieStatus.OK)
            } catch (e: Throwable) {
                val status = if (e is CookieExpiredException) CookieStatus.EXPIRED else _state.value.cookieStatus
                _state.value = _state.value.copy(
                    cookieStatus = status,
                    loading = false,
                    error = describeSyncError(e),
                    cookieVerifying = false,
                )
                persist(status)
            }
        }
    }

    /** 把当前这一份页面内容落盘，供下一次冷启动先用着。 */
    private suspend fun persist(status: CookieStatus) {
        val current = _state.value
        val cache = OverviewCache(
            cookieStatus = status.name,
            stats = current.stats,
            recent = current.recent,
            activities = current.activities,
            savedAtSec = currentEpochSeconds(),
        )
        runCatching { store.write(StoreKey.OVERVIEW_CACHE, OverviewCacheCodec.encode(cache)) }
    }

    fun setMode(mode: OverviewMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun close() {
        scope.cancel()
    }
}

/**
 * 缓存原文 → 冷启动要摆回去的那份 state。
 *
 * 抽成顶层纯函数是为了能脱离 ViewModel 单测——VM 要真 NzApi / MatchDao 才造得出来，
 * 而这里恰好是"状态卡冷启动显示什么"的全部规则。
 *
 * @return null 表示**整份缓存不生效**：没缓存，或者状态名认不出来
 *   （解码器开了 isLenient，老版本写的 `"123"` 这类也会被宽容地收成字符串）。
 *   认不出就整体作废，而不是挑一半字段用——状态对不上时显示任何数字都是错的。
 */
internal fun restoreOverviewFromCache(raw: String?, nowSec: Long): OverviewUiState? {
    val cache = raw?.let { OverviewCacheCodec.decode(it) } ?: return null
    val status = CookieStatus.entries.firstOrNull { it.name == cache.cookieStatus } ?: return null
    return OverviewUiState(
        cookieStatus = status,
        stats = cache.stats,
        recent = cache.recent,
        // 缓存可能是几天前写的，里面有些活动已经结束了，先滤掉
        activities = upcomingActivities(cache.activities, nowSec),
        // 缓存的是「未输入」说明上一次就没登录，那不该显示"正在同步"
        cookieVerifying = status != CookieStatus.MISSING,
    )
}
