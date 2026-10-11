package com.nzd.antigravitypanel.ui.overview

import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.remote.CookieExpiredException
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.repo.AccountProfile
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
import com.nzd.antigravitypanel.util.serverDateKey
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

    /**
     * 今日机甲战局数（`n / [MECHA_TODAY_GOAL] 场` 里那个 n）。
     *
     * null = **还不知道**（没切到那个选项 / 没凭证 / 这一轮失败了），不是 0 ——
     * 0 是"今天一局没打"，null 是"我没问到"，这两个在卡片上必须分得开。
     * ⚠️ 只有 [OverviewMode.MECHA_TODAY] 被选中时才会去拉，平时不占配额。
     */
    val mechaTodayCount: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 当前数字全部来自本地库（已导入 JSON、未登录）。近五场那几项算不出来，UI 要如实说明。 */
    val localOnly: Boolean = false,

    /**
     * 顶栏账号区要的名片（头像 + 昵称 + 归属 openid）。null = 还没有 / 拿不到。
     *
     * ⚠️ **展示前要拿它的 openid 跟当前凭证对一遍**：这是上一次拉到的结论，
     *   扫码换号之后新号的第一帧还不该顶着旧名字（比对在 AppContent 那边做）。
     */
    val account: AccountProfile? = null,

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

    fun refresh(cookie: MiniProgramCredential?) {
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
                    // 没凭证就拉不到机甲对局，留着上一次的数会让人以为今天打过
                    mechaTodayCount = null,
                    cookieVerifying = false,
                    // 没凭证了就没有"当前账号"，名片必须跟着清
                    account = null,
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
                    // 取不到就留着上一份：换一次网络抖动就把顶栏的名片打空，
                    // 比"慢一点才更新"难受得多
                    account = snapshot.account ?: _state.value.account,
                )
                // 今天打了几局一直在变，手动刷新时如果正停在这一项就跟着重数一次。
                // ⚠️ 另起一个协程：重数要翻好几页对局列表，等它跑完再落盘的话
                // 这一轮拿到的近五场 / 活动会被拖到几秒后才存下来。
                if (_state.value.mode == OverviewMode.MECHA_TODAY) {
                    scope.launch { loadMechaToday() }
                }
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

    /**
     * 把当前这一份页面内容落盘，供下一次冷启动先用着。
     *
     * @param status 这一轮确认下来的凭证状态；不给时沿用当前那份（切模式那种场合
     *   根本没重新确认过凭证，写个别的值等于把上一轮的结论改掉了）。
     */
    private suspend fun persist(status: CookieStatus = _state.value.cookieStatus) {
        val current = _state.value
        val cache = OverviewCache(
            cookieStatus = status.name,
            stats = current.stats,
            recent = current.recent,
            activities = current.activities,
            account = current.account,
            // 用户选的模式跟着一起存：下次开 app 那张卡该还是他上次看的那个口径
            mode = current.mode.name,
            savedAtSec = currentEpochSeconds(),
            // 今日机甲战也跟着落盘：它是「今天打了几局」，换一次进程就重新翻页去数
            // 的话，冷启动那一两秒卡片上只能摆个 `—`。日期一起写，跨天自动作废。
            mechaTodayCount = current.mechaTodayCount,
            mechaTodayDate = serverDateKey(currentEpochSeconds()),
        )
        runCatching { store.write(StoreKey.OVERVIEW_CACHE, OverviewCacheCodec.encode(cache)) }
    }

    fun setMode(mode: OverviewMode) {
        _state.value = _state.value.copy(mode = mode)
        // ⚠️ 必须**立刻**落盘：模式是用户自己选的、不跟着任何一次刷新走，
        // 只在 refresh 里顺手存的话，选完就杀进程 / 一直没拉成功的情况下
        // 下次开 app 又回到默认的「僵尸猎场」，等于没记住。
        scope.launch { persist() }
        // 切到「今日机甲战」才去拉：机甲不在日常同步里，这一路是唯一的数据来源，
        // 每次切回来都要重数（今天打了几局一直在变，只摆缓存是不够的）
        if (mode == OverviewMode.MECHA_TODAY) scope.launch { loadMechaToday() }
    }

    /**
     * 今日机甲战局数。失败时**保留上一次的值**（可能是缓存里那份），不把卡片打回 0 ——
     * 一次网络抖动就把"今天打了 12 局"变成"0 局"比慢更难受。
     *
     * ⚠️ 数完要自己落一次盘：这是唯一能把新数字写进缓存的地方。
     * [refresh] 里那次 [persist] 在另一个协程上，跟它抢的话存下来的多半还是旧值。
     */
    private suspend fun loadMechaToday() {
        val count = runCatching { repository.loadMechaTodayCount(currentEpochSeconds()) }
            .getOrNull()
        _state.value = _state.value.copy(mechaTodayCount = count ?: _state.value.mechaTodayCount)
        persist()
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
        account = cache.account,
        // 缓存可能是几天前写的，里面有些活动已经结束了，先滤掉
        activities = upcomingActivities(cache.activities, nowSec),
        // 上次选的模式。认不出来（老版本写的缓存里没这个字段）就退回默认那个，
        // 而不是整份缓存作废——模式和凭证状态无关，不该被它连坐
        mode = OverviewMode.entries.firstOrNull { it.name == cache.mode } ?: OverviewMode.HUNT,
        // 今日机甲战：只有"同一天"数出来的才算数。跨天那份是昨天的进度，
        // 摆上去会让人以为今天已经打过了，所以直接当没数过（卡片显示 `—`）。
        // 它不是 null 就等于 0：0 是"今天一局没打"，null 是"我还不知道"。
        mechaTodayCount = cache.mechaTodayCount
            .takeIf { cache.mechaTodayDate == serverDateKey(nowSec) },
        // 缓存的是「未输入」说明上一次就没登录，那不该显示"正在同步"
        cookieVerifying = status != CookieStatus.MISSING,
    )
}
