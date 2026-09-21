package com.nzd.antigravitypanel.ui.mapdist

import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.data.remote.dto.MapItemDto
import com.nzd.antigravitypanel.data.remote.ApiException
import com.nzd.antigravitypanel.data.remote.CookieExpiredException
import com.nzd.antigravitypanel.data.remote.MissingCredentialException
import com.nzd.antigravitypanel.data.remote.ProtocolException
import com.nzd.antigravitypanel.data.remote.dto.MapStatsDto
import com.nzd.antigravitypanel.domain.COUNTABLE_MODES
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.MapSection
import com.nzd.antigravitypanel.domain.MapStatEntry
import com.nzd.antigravitypanel.domain.aggregateByMap
import com.nzd.antigravitypanel.domain.buildOfficialMapStats
import com.nzd.antigravitypanel.domain.groupMapsBySeason
import com.nzd.antigravitypanel.domain.serverMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
 * **首选 `center.user.map.stats`（官方终身统计），拿不到才退到本地库。**
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
 */
class MapDistributionViewModel(
    private val api: NzApi,
    dao: MatchDao,
    private val config: StateFlow<GameConfigDto>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _mode = MutableStateFlow(GameMode.HUNT)
    val mode: StateFlow<GameMode> = _mode.asStateFlow()

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
    private val _officialStats = MutableStateFlow<Map<GameMode, List<MapStatsDto>>>(emptyMap())
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

    /** 当前模式的统计。**官方数据优先**；只有官方这份真的拿不到时才退本地。 */
    val entries: StateFlow<List<MapStatEntry>> =
        combine(allMatches, config, _mode, _officialStats, officialReady) { matches, cfg, mode, all, ready ->
            val stats = if (ready) all[mode] else null
            if (stats == null) return@combine aggregateByMap(matches, cfg, mode = mode)

            val local = aggregateByMap(matches, cfg, mode = mode)
            // 官方口径的 total 是通关数，拿它当分母算通关率永远是 100%，
            // 所以卡片底部那条进度要的是本地的 win / plays
            val official = buildOfficialMapStats(
                stats, cfg, mode, local.associate { it.mapId to it.winRate },
            )
            // 配置里这个模式一张图都没有时（`buildOfficialMapStats` 给不出条目），
            // 才退本地 —— 那是"官方这条路的骨架没搭起来"，不是"官方说你 0 场"。
            if (official.isEmpty()) local else official
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 分好节的卡片列表：猎场按赛季，其余按模式，没打过的地图补 0 场。 */
    val sections: StateFlow<List<MapSection>> =
        combine(entries, config, _mode) { list, cfg, mode ->
            groupMapsBySeason(mode, list, cfg)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _drops = MutableStateFlow<Map<Int, List<MapItemDto>>>(emptyMap())
    val drops: StateFlow<Map<Int, List<MapItemDto>>> = _drops.asStateFlow()

    /**
     * 掉落物是不是已经拉回来过一次。
     *
     * 得把"还没拉到"和"这张图确实没有收集品"分开：前者卡片正面退回显示通关率、
     * 背面写"需要登录"，后者是真的 0 件。只有一个空的 `drops` 是分不出这两种情况的。
     */
    private val _dropsLoaded = MutableStateFlow(false)
    val dropsLoaded: StateFlow<Boolean> = _dropsLoaded.asStateFlow()

    private val _dropError = MutableStateFlow<String?>(null)
    val dropError: StateFlow<String?> = _dropError.asStateFlow()

    private var loadingDrops = false

    init {
        // 配置是官方统计的必要输入（地图表 + 每个副本对应的难度）。
        // 页面开在配置到位之前的话，第一次请求白跑；这里等配置一到就补一次。
        // 用户先导入 JSON、之后才粘 cookie 的场景走的也是这条路。
        scope.launch {
            config.collect { cfg ->
                if (cfg.mapInfo.isNotEmpty() && _officialStats.value == null) {
                    loadOfficial(force = true)
                }
            }
        }
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
        scope.launch {
            val results = coroutineScope {
                COUNTABLE_MODES.associateWith { mode ->
                    async { runCatching { api.mapStats(mode.serverMode) } }
                }.mapValues { (_, deferred) -> deferred.await() }
            }
            val ok = results.mapNotNull { (mode, result) ->
                result.getOrNull()?.let { mode to it }
            }.toMap()
            if (ok.isNotEmpty()) _officialStats.value = _officialStats.value + ok

            _officialError.value = results.values
                .firstNotNullOfOrNull { it.exceptionOrNull() }
                ?.let { describeFailure(it) }
                ?: if (ok.size == COUNTABLE_MODES.size) null else "官方统计只拿到了 ${ok.size}/${COUNTABLE_MODES.size} 个模式"
        }
    }

    /** 把几种常见失败说成人话 —— 这条文案是直接给用户看的。 */
    private fun describeFailure(t: Throwable): String = when (t) {
        is MissingCredentialException -> "还没有设置 cookie，取不到官方统计"
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
    fun loadDrops() {
        if (loadingDrops || _dropsLoaded.value) return
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
                }
                .onFailure { _dropError.value = it.message ?: "掉落物加载失败" }
            loadingDrops = false
        }
    }

    fun close() {
        scope.cancel()
    }
}
