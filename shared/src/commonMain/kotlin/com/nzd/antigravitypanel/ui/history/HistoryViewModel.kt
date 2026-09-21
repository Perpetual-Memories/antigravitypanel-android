package com.nzd.antigravitypanel.ui.history

import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.data.settings.MatchMarks
import com.nzd.antigravitypanel.domain.MatchFilter
import com.nzd.antigravitypanel.domain.applyMatchFilter
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 每次"加载更多"追加的条数。 */
private const val PAGE_SIZE = 20

/**
 * 历史战绩页。
 *
 * 分页是**在内存里做的**：本地库受保留期限制，撑死几千行，一次性读进来再按页显示
 * 比"每次翻页打一次 SQL + 把筛选条件翻译成 WHERE"简单得多，也避免了
 * "筛选条件变了但已加载的页还是旧条件"这个经典 bug。
 *
 * 真到几千行卡了，把 [allMatches] 换成 DAO 的 SQL 分页即可，接口不用动。
 */
class HistoryViewModel(
    private val dao: MatchDao,
    private val marks: MatchMarks,
    private val config: StateFlow<GameConfigDto>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 本地全量对局。对外暴露是因为筛选面板要拿它算"地图下拉里有哪些地图"——
     * 只给筛完的结果的话，选了 A 地图之后下拉里就只剩 A 了。
     */
    val allMatches: StateFlow<List<MatchEntity>> = dao.observeAll()
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    /**
     * 筛选条件。**不含翻页用的 limit**——limit 一变就把整份筛选重算一遍，
     * 而"加载更多"只是把已经算好的结果多显示几条，不该碰筛选。
     */
    private val criteria = MutableStateFlow(Criteria())
    private val limit = MutableStateFlow(PAGE_SIZE)

    private val _filter = MutableStateFlow(MatchFilter())
    val filter: StateFlow<MatchFilter> = _filter.asStateFlow()

    /**
     * 筛完的全量结果。
     *
     * **[applyMatchFilter] 只在这里跑一次。** 之前 `items` 和 `hasMore` 各 combine 一遍，
     * 等于每条数据都筛两次、排序两次——配置迟到、置顶变化、同步写入都会触发，
     * 白扔掉一半开销。
     *
     * 另外这里一律用 [SharingStarted.Lazily] 而不是 `WhileSubscribed`：一旦开始就别停。
     * 停了再回来要重新起一遍管道，而重起的第一帧是 `emptyList()`——页面会先闪一下空态
     * 再跳成列表，看着就是"进来卡了一下"。本地库就那么点数据，常驻不值一提。
     */
    private val filtered: StateFlow<List<MatchEntity>> = combine(
        allMatches,
        criteria,
        marks.favorite,
        marks.pinned,
        config,
    ) { matches, c, favorites, pinned, cfg ->
        applyMatchFilter(
            matches = matches,
            filter = c.filter,
            config = cfg,
            favorites = favorites,
            pinned = pinned,
            nowSec = c.nowSec,
        )
    }.stateIn(scope, SharingStarted.Lazily, emptyList())

    /** 当前显示到哪儿了：筛完的结果取前 [limit] 条。 */
    val items: StateFlow<List<MatchEntity>> = combine(filtered, limit) { list, l ->
        list.take(l)
    }.stateIn(scope, SharingStarted.Lazily, emptyList())

    val hasMore: StateFlow<Boolean> = combine(filtered, limit) { list, l ->
        list.size > l
    }.stateIn(scope, SharingStarted.Lazily, false)

    /**
     * 加载更多：只是把已经算好的结果多显示几条，不碰筛选。
     *
     * **故意不看 [hasMore]**：它是 `stateIn(Lazily)`，在没有订阅者时 `.value` 永远是
     * 初值 false，拿它当闸门会把"加载更多"整个废掉。多涨几次 limit 是无害的——
     * `take` 会自己截断，拦住重复调用的活由 UI 那边的 `nearEnd && hasMore` 干。
     *
     * 将来换成 SQL 分页时，这个入口改成"再查一页"即可，UI 不用动。
     */
    fun loadMore() {
        limit.value += PAGE_SIZE
    }

    /** 换筛选条件：重置到第一页，并刷新"现在几点"这个基准。 */
    fun setFilter(value: MatchFilter) {
        _filter.value = value
        criteria.value = Criteria(filter = value, nowSec = currentEpochSeconds())
        limit.value = PAGE_SIZE
    }

    fun togglePin(roomId: String) {
        scope.launch { marks.togglePin(roomId) }
    }

    fun toggleFavorite(roomId: String) {
        scope.launch { marks.toggleFavorite(roomId) }
    }

    fun close() {
        scope.cancel()
    }

    private data class Criteria(
        val filter: MatchFilter = MatchFilter(),
        val nowSec: Long = currentEpochSeconds(),
    )
}
