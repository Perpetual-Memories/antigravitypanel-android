package com.nzd.antigravitypanel.ui.mapdist

import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.data.remote.dto.MapItemDto
import com.nzd.antigravitypanel.data.remote.ApiException
import com.nzd.antigravitypanel.data.remote.CookieExpiredException
import com.nzd.antigravitypanel.data.remote.MissingCredentialException
import com.nzd.antigravitypanel.data.remote.ProtocolException
import com.nzd.antigravitypanel.data.remote.dto.MapStatsDto
import com.nzd.antigravitypanel.data.repo.MapDropsCacheCodec
import com.nzd.antigravitypanel.data.repo.MapStatsCacheCodec
import com.nzd.antigravitypanel.data.repo.toDropsCache
import com.nzd.antigravitypanel.data.repo.toIntMap
import com.nzd.antigravitypanel.data.repo.toModeMap
import com.nzd.antigravitypanel.data.repo.toStatsCache
import com.nzd.antigravitypanel.data.settings.MapSeasonFilter
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.util.currentEpochSeconds
import com.nzd.antigravitypanel.domain.COUNTABLE_MODES
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.MapSection
import com.nzd.antigravitypanel.domain.MapSeason
import com.nzd.antigravitypanel.domain.MapStatEntry
import com.nzd.antigravitypanel.domain.aggregateByMap
import com.nzd.antigravitypanel.domain.buildOfficialMapStats
import com.nzd.antigravitypanel.domain.groupMapsBySeason
import com.nzd.antigravitypanel.domain.hasSeasonFilter
import com.nzd.antigravitypanel.domain.mergeMapStats
import com.nzd.antigravitypanel.domain.seasonOrderOf
import com.nzd.antigravitypanel.domain.serverMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 地图分布页。
 *
 * ## 统计口径
 *
 * **首选 `center.user.map.stats`（官方终身统计），它没覆盖到的图才退到本地库。**
 *
 * 降级是**逐张图**做的（[mergeMapStats]），不是"整页挑一边"：官方那份里缺失的地图
 * 会被填成 0，那时候要用本地的场次把它顶上去。
 *
 * 这不是偷懒换数据源，而是原来那条路根本对不上数：本地库存的是"拉得到、且在保留期内"
 * 的**场次**（含没打完和输了的），官方统计给的是**通关**数。实测同一张图：
 *
 * | | 本地库 | 官方 `map.stats`（= 小程序） |
 * | --- | --- | --- |
 * | 销金之城 总数 | 75 场 | 77 通关 |
 * | 销金之城 折磨 | 20 | 28 |
 * | 精绝古城 总数 | 374 场 | 515 通关 |
 *
 * 差值是两边必然的：保留期会裁掉老数据，服务端对局列表本来也只留滚动窗口，
 * 而 `map.stats` 是服务端账本，跟设备在不在没关系。要和小程序对得上，只能用它。
 *
 * 本地库依然保留成兜底 —— 没登录、或者只有 JSON 导入的场景下它是唯一的数据源，
 * 那时候 UE 上显示的就是"场次"，标签也要跟着变（见 `MapStatEntry.official`）。
 *
 * ## 两张数据来源分工
 *
 * - **统计**：`center.user.map.stats`（官方，优先）/ 本地库（兜底）。
 * - **收集品**：不存在于对局数据里，只能调 `center.map.item.list`。
 *
 * ## 什么时候发请求
 *
 * **只在宿主 [refresh] 带上凭证之后。** 这两路都要 cookie，而 cookie 是
 * `AppContent.refreshAll` 里 `api.updateCookie()` 才挂上去的（也可能是用户刚粘的）。
 * 以前这一页在自己构造时就抢着拉一次，那一发**必然**是 `MissingCredentialException`，
 * 于是冷启动的头几秒里卡片背面写着「还没有设置 cookie」、正面摆的是本地场次
 * （和小程序对不上的那个数），等宿主那一轮刷新跑完才跳回来。
 *
 * 在这之前先摆**缓存**（[MapStatsCacheCodec] / [MapDropsCacheCodec]），
 * 那是上一次确认过的真值，比半截的本地库接近真相。
 */
class MapDistributionViewModel(
    private val api: NzApi,
    dao: MatchDao,
    private val config: StateFlow<GameConfigDto>,
    /**
     * 赛季筛选的勾选（猎场 / 塔防各一份）。
     *
     * 和 `MatchMarks` 一样是纯 UI 偏好，不进对局表。
     */
    private val seasonFilter: MapSeasonFilter,
    private val store: KeyValueStore,
    /**
     * 冷启动时**同步**读到的官方统计缓存原文（宿主预热存储后 peek 的）。
     * 走 suspend 的话第一帧早就画完了，那一帧会先摆一份本地场次。
     */
    statsSeed: String? = null,
    /** 同上，掉落物那份。 */
    dropsSeed: String? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 当前凭证。**null 有两种含义**，靠 [credentialResolved] 区分：
     * 没解析过（冷启动头几帧，宿主还没调 [refresh]）/ 真的没有凭证。
     *
     * 没解析过时 [loadOfficial] / [loadDrops] 要**安静跳过**而不是报
     * 「还没有设置 cookie」——那句话是结论，不是"还没开始"。
     */
    @Volatile
    private var credential: MiniProgramCredential? = null

    @Volatile
    private var credentialResolved = false

    private val _mode = MutableStateFlow(GameMode.HUNT)
    val mode: StateFlow<GameMode> = _mode.asStateFlow()

    /**
     * 当前模式有哪些赛季可选。空列表 = 这个模式没有赛季划分（时空追猎），
     * UI 那边就不该摆筛选入口。
     */
    val seasonOptions: StateFlow<List<MapSeason>> = _mode
        .map { seasonOrderOf(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), seasonOrderOf(GameMode.HUNT))

    /**
     * 当前模式勾了哪些赛季。**null = 全选**（包括"从没设过"和"没有赛季表"两种）。
     */
    val seasonSelection: StateFlow<Set<String>?> = combine(
        _mode,
        seasonFilter.selected,
    ) { mode, all -> all[mode] }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** 当前模式能不能按赛季筛。时空追猎没有赛季表，入口要整个藏起来。 */
    fun canFilterBySeason(mode: GameMode = _mode.value): Boolean = hasSeasonFilter(mode)

    fun toggleSeason(key: String) {
        scope.launch { seasonFilter.toggle(_mode.value, key) }
    }

    private val allMatches = dao.observeAll()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- 官方统计 ----------------

    /**
     * 服务端返回的原始统计，**三个模式都在里面**（`GameMode -> 该模式的统计`）。
     *
     * 存**原始值**而不是算好的条目，是为了让配置晚到时能直接重算 ——
     * 见 [officialReady] 那条踩过的坑。
     *
     * 一次把三个模式都拉回来是照官方来的（`fetchOfficialMapData` 里 `Promise.all`
     * 三个 `center.user.map.stats`），切 Tab 时不用再发请求。
     */
    private val _officialStats = MutableStateFlow<Map<GameMode, List<MapStatsDto>>>(
        statsSeed?.let { MapStatsCacheCodec.decode(it) }?.toModeMap() ?: emptyMap()
    )

    /** 同步那一路有没有读到。没有的话 [restoreCached] 还要再补一次。 */
    private val seededStats = _officialStats.value.isNotEmpty()

    private val _officialError = MutableStateFlow<String?>(null)
    /** 官方统计拿不到时的原因。UI 要把它显示出来，否则用户只能对着"总场次"猜。 */
    val officialError: StateFlow<String?> = _officialError.asStateFlow()

    /**
     * 官方口径**现在能不能用**（针对当前选中的模式）。
     *
     * ⚠️ **必须把"配置里有地图表"算进条件里**。踩过的坑：页面可能在
     * `center.config.list` 回来之前就打开了，那一次 `map.stats` 请求照样能成功，
     * 但 `mapInfo` 是空的，算出来是九张全 0 的卡片、难度列一根都没有。
     *
     * 存原始 `map.stats` + 让"可用"跟着 config 走，配置一到就自动重算出正确结果。
     */
    private val officialReady: StateFlow<Boolean> =
        combine(_officialStats, config, _mode) { all, cfg, mode ->
            cfg.mapInfo.isNotEmpty() && all[mode] != null
        }.stateIn(scope, SharingStarted.Eagerly, false)

    /** 当前这张表是不是官方口径 —— 用来区分「这张图没打过」和「压根没拿到数据」。 */
    val officialLoaded: StateFlow<Boolean> = officialReady

    /** 当前模式的统计。**官方数据优先**，但它没覆盖到的图退到本地（见 [mergeMapStats]）。 */
    val entries: StateFlow<List<MapStatEntry>> =
        combine(allMatches, config, _mode, _officialStats, officialReady) { matches, cfg, mode, all, ready ->
            val local = aggregateByMap(matches, cfg, mode = mode)
            val stats = if (ready) all[mode] else null
            if (stats == null) return@combine local

            // 官方口径的 total 是通关数，拿它当分母算通关率永远是 100%，
            // 所以卡片底部那条进度要的是本地的 win / plays
            val official = buildOfficialMapStats(
                stats, cfg, mode, local.associate { it.mapId to it.winRate },
            )
            // 配置里这个模式一张图都没有时（`buildOfficialMapStats` 给不出条目），
            // 才整份退本地 —— 那是"官方这条路的骨架没搭起来"，不是"官方说你 0 场"。
            if (official.isEmpty()) return@combine local

            // 逐图兜底：官方说话的图用官方的，官方没下发的图用本地的。
            // 少了这一步，新赛季刚开、服务端还没把新图纳进统计的那几天，
            // 这几张图会一直是 0，而同样的对局在本地库里查得到。
            mergeMapStats(official, local, stats.mapTo(mutableSetOf()) { it.map_id })
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 分好节的卡片列表：猎场 / 塔防按赛季，其余按模式，没打过的地图补 0 场。
     *
     * 赛季筛选跟着当前模式走 —— 猎场和塔防各存一份勾选，切 Tab 时各读各的。
     */
    val sections: StateFlow<List<MapSection>> =
        combine(entries, config, _mode, seasonSelection) { list, cfg, mode, seasons ->
            groupMapsBySeason(mode, list, cfg, seasons)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _drops = MutableStateFlow<Map<Int, List<MapItemDto>>>(
        dropsSeed?.let { MapDropsCacheCodec.decode(it) }?.toIntMap() ?: emptyMap()
    )
    val drops: StateFlow<Map<Int, List<MapItemDto>>> = _drops.asStateFlow()

    /** 同上，掉落物那份有没有读到。 */
    private val seededDrops = _drops.value.isNotEmpty()

    /**
     * 掉落物是不是已经拉回来过一次。
     *
     * 得把"还没拉到"和"这张图确实没有收集品"分开：前者卡片正面退回显示通关率、
     * 背面写"需要登录"，后者是真的 0 件。只有一个空的 `drops` 是分不出这两种情况的。
     *
     * 冷启动摆缓存时直接算已拿到：那份数据是上一次真的拉回来过的。
     */
    private val _dropsLoaded = MutableStateFlow(seededDrops)
    val dropsLoaded: StateFlow<Boolean> = _dropsLoaded.asStateFlow()

    private val _dropError = MutableStateFlow<String?>(null)
    val dropError: StateFlow<String?> = _dropError.asStateFlow()

    private var loadingDrops = false

    init {
        // 勾选是存在加密存储里的，读出来之前 UI 那边一律当"全选"
        scope.launch { seasonFilter.restore() }
        //
        // ⚠️ 这里**不再**抢着拉官方统计。以前配置一到就发一次请求，那一发必然
        // 落在 `api.updateCookie()` 之前（凭证是宿主在 refreshAll 里挂的），
        // 结果就是冷启动先摆「还没有设置 cookie」+ 本地场次，等刷新跑完才更正。
        // 现在第一次请求由 [refresh] 发起，这里只在"请求已经能发、但还没凑齐
        // 三个模式"时补漏（某个模式单独超时的情况）。
        //
        // 配置那一项条件可以留着：它是判定"这一页算不算得出来"的输入之一，
        // 但拉统计本身不需要它（存的是原始值，配置到了会自动重算）。
        scope.launch {
            config.collect { _ ->
                if (credentialResolved && _officialStats.value.size < COUNTABLE_MODES.size) {
                    loadOfficial(force = true)
                }
            }
        }
    }

    /**
     * 同步那一路没读到时（存储预热失败）的兜底。仍然要排在首刷之前。
     *
     * 只补缺的那一份：缓存可能只有统计、没有掉落物（比如上一次掉落物拉失败了）。
     */
    suspend fun restoreCached() {
        if (!seededStats) {
            val raw = runCatching { store.read(StoreKey.MAP_STATS_CACHE) }.getOrNull()
            val restored = raw?.let { MapStatsCacheCodec.decode(it) }?.toModeMap()
            if (!restored.isNullOrEmpty()) _officialStats.value = _officialStats.value + restored
        }
        if (!seededDrops) {
            val raw = runCatching { store.read(StoreKey.MAP_DROPS_CACHE) }.getOrNull()
            val restored = raw?.let { MapDropsCacheCodec.decode(it) }?.toIntMap()
            if (!restored.isNullOrEmpty()) {
                _drops.value = _drops.value + restored
                _dropsLoaded.value = true
            }
        }
    }

    /**
     * 宿主刷新入口。**这一页的网络请求只从这里发起。**
     *
     * @param cookie null = 当前确实没有凭证。这时官方口径用不了，
     *   页面退本地场次并把原因说出来；磁盘上那份缓存留着（换号登录时还能先顶一下）。
     */
    fun refresh(cookie: MiniProgramCredential?) {
        credentialResolved = true
        credential = cookie
        if (cookie == null) {
            _officialStats.value = emptyMap()
            _drops.value = emptyMap()
            _dropsLoaded.value = false
            _officialError.value = NO_CREDENTIAL_TEXT
            _dropError.value = NO_CREDENTIAL_TEXT
            return
        }
        // 和别处一样自己先挂一遍，不依赖"宿主什么时候设过"这种隐式先后关系
        api.updateCookie(cookie)
        // 三个模式一次拉齐，切 Tab 不再发请求
        loadOfficial(force = true)
        // 掉落物带"已解锁"标记，是跟账号走的，换号要重拉
        loadDrops(force = true)
    }

    fun setMode(value: GameMode) {
        _mode.value = value
        // 三个模式是一次性全拉回来的，切 Tab 不需要重新请求。
        // 但万一当前这个模式当时失败了（比如那一次请求单独超时），补一次。
        if (_officialStats.value[value] == null) loadOfficial(force = true)
    }

    /**
     * 拉**三个模式**的官方地图统计（并行，照官方 `fetchOfficialMapData`）。
     *
     * **逐个模式独立 catch**：一个模式失败不该拖垮另外两个 ——
     * 官方那边也是各收各的（`t.success && ...` 才 concat）。
     * 只要有一个成功就把它写进去，失败原因单独留着给 UI 显示。
     */
    fun loadOfficial(force: Boolean = false) {
        if (!force && _officialStats.value.size == COUNTABLE_MODES.size) return
        // 宿主还没把凭证递过来：**这一发不发**。发就是必然失败，
        // 而且会把「还没有设置 cookie」摆到一个其实正在刷新的页面上。
        if (!credentialResolved) return
        if (credential == null) {
            _officialError.value = NO_CREDENTIAL_TEXT
            return
        }
        scope.launch {
            val results = coroutineScope {
                COUNTABLE_MODES.associateWith { mode ->
                    async { runCatching { api.mapStats(mode.serverMode) } }
                }.mapValues { (_, deferred) -> deferred.await() }
            }
            val ok = results.mapNotNull { (mode, result) ->
                result.getOrNull()?.let { mode to it }
            }.toMap()
            if (ok.isNotEmpty()) {
                _officialStats.value = _officialStats.value + ok
                persistStats()
            }

            _officialError.value = results.values
                .firstNotNullOfOrNull { it.exceptionOrNull() }
                ?.let { describeFailure(it) }
                ?: if (ok.size == COUNTABLE_MODES.size) null else "官方统计只拿到了 ${ok.size}/${COUNTABLE_MODES.size} 个模式"
        }
    }

    /** 官方统计落盘。只在**拉到东西**时写：拉失败要留着上一份。 */
    private suspend fun persistStats() {
        val raw = MapStatsCacheCodec.encode(_officialStats.value.toStatsCache(currentEpochSeconds()))
        runCatching { store.write(StoreKey.MAP_STATS_CACHE, raw) }
    }

    /** 把几种常见失败说成人话 —— 这条文案是直接给用户看的。 */
    private fun describeFailure(t: Throwable): String = when (t) {
        is MissingCredentialException -> NO_CREDENTIAL_TEXT
        is CookieExpiredException -> "cookie 失效了（${t.message}），重新粘一次"
        is ApiException -> "接口报错：${t.message}"
        is ProtocolException -> "网络或响应异常：${t.message}"
        else -> t.message ?: "未知错误"
    }

    /**
     * 一次把**全部地图**的掉落物拉回来。
     *
     * 原来是翻到哪张卡才拉哪张，但卡片正面现在要显示「核心收集进度 x / y」，
     * 那就得在渲染之前知道每张图有几件收集品 —— 按需拉的话，没翻过的卡全是空的。
     * `mapID` 传空数组时服务端返回全部地图，一次请求就够（官方 PC 端也是这么拿的）。
     *
     * 失败只记一条错误文案，不清空已有结果：翻面翻到一半网络抖了，
     * 用户至少还能看到上一次的结果。
     */
    fun loadDrops(force: Boolean = false) {
        if (loadingDrops || (!force && _dropsLoaded.value)) return
        // 同上：凭证还没递过来就别发，等 [refresh]
        if (!credentialResolved) return
        if (credential == null) {
            _dropError.value = NO_CREDENTIAL_TEXT
            return
        }
        loadingDrops = true
        scope.launch {
            runCatching { api.mapItemList() }
                .onSuccess { response ->
                    _drops.value = response.itemMap.mapNotNull { (key, list) ->
                        // 服务端的 key 是字符串形式的 mapID，转不出 int 的直接丢
                        key.toIntOrNull()?.let { it to list }
                    }.toMap()
                    _dropsLoaded.value = true
                    _dropError.value = null
                    persistDrops()
                }
                .onFailure { _dropError.value = it.message ?: "掉落物加载失败" }
            loadingDrops = false
        }
    }

    /** 掉落物落盘。同样只在拉到时写。 */
    private suspend fun persistDrops() {
        val raw = MapDropsCacheCodec.encode(_drops.value.toDropsCache(currentEpochSeconds()))
        runCatching { store.write(StoreKey.MAP_DROPS_CACHE, raw) }
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        /** 「没有凭证」这句结论。用户看到它就知道该去粘 cookie 了。 */
        private const val NO_CREDENTIAL_TEXT = "还没有设置 cookie，取不到官方统计"
    }
}
