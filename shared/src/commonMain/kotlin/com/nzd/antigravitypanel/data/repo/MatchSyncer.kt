package com.nzd.antigravitypanel.data.repo

import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.db.toEntity
import com.nzd.antigravitypanel.data.remote.dto.GameRecordDto
import com.nzd.antigravitypanel.domain.COUNTABLE_MODES
import com.nzd.antigravitypanel.domain.serverMode
import com.nzd.antigravitypanel.util.minusCalendarMonths

/**
 * 本地对局存储。
 *
 * 包一层而不是直接把 DAO 丢给同步逻辑，是为了让整条同步链路能用假实现做单测——
 * Room 的 DAO 是 KSP 生成的，宿主测试里跑不起来（androidMain 的 actual 依赖 Context）。
 */
interface MatchLocalSource {
    /**
     * 返回 `roomId -> 本地记录的完成状态`。查不到的 key 表示本地没有这条。
     * 完成状态参与判断，是因为未完成对局需要刷新，不能当成"已经同步过"。
     */
    suspend fun knownStates(roomIds: List<String>): Map<String, Boolean>

    suspend fun upsertAll(entities: List<MatchEntity>)

    /** 删掉早于 [cutoffSec] 的对局，返回删除条数。 */
    suspend fun deleteOlderThan(cutoffSec: Long): Int
}

/**
 * `center.user.game.list` 的 `map_mode` 参数值。
 *
 * ⚠️ **必须按模式分别拉**。官方前端是这么做的：
 *
 * ```js
 * let c = ['猎场','塔防','时空追猎'], l = [];
 * c.forEach(e => { for (let t = 0; t < 5; t++)
 *     l.push(Cc('center.user.game.list', { seasonID: 3, page: t+1, limit: 10, map_mode: e })) });
 * ```
 *
 * 不传 `map_mode` 时服务端只会给猎场那一份，塔防和时空追猎的对局**根本同步不下来**
 * —— 表现就是这两个模式在地图分布页上永远只有零星几场，难度场次自然也不对。
 *
 * 注意这里是 `猎场` 而不是 `僵尸猎场`：`僵尸猎场` 是 UI 上的叫法，
 * 服务端 `mapInfo.mode` 里写的就是 `猎场`。
 */
val SYNC_MAP_MODES: List<String> = COUNTABLE_MODES.map { it.serverMode }

/** 远端对局列表，按页取。`mapMode` 取自 [SYNC_MAP_MODES]。 */
interface MatchRemoteSource {
    suspend fun page(page: Int, limit: Int, mapMode: String): List<GameRecordDto>
}

/** 一次增量同步的结果。UI 拿它显示"新增 N 场"。 */
data class SyncResult(
    /** 新拉进来的对局数。 */
    val newCount: Int = 0,
    /** 库里原本就有、但当时没打完、这次刷新了的对局数。 */
    val refreshedCount: Int = 0,
    /** 本次一共从服务端读到多少条（含已存在的）。 */
    val fetchedCount: Int = 0,
    val pages: Int = 0,
    val deletedCount: Int = 0,
    /** 是否翻到了服务端数据的尽头（空页或不满一页）。没有的话说明被 maxPages 截断了。 */
    val reachedEnd: Boolean = false,
)

/** 单页的写入决策。抽成数据类是为了能脱离网络单独测。 */
data class PagePlan(
    val toWrite: List<GameRecordDto>,
    val newCount: Int,
    val refreshedCount: Int,
    /** true 表示后面不用再翻了。 */
    val stop: Boolean,
)

/**
 * 决定一页里哪些要写库、要不要继续往下翻。
 *
 * 依据：服务端按时间倒序返回，所以**一旦遇到一条本地已存在且已完成的记录，
 * 它后面所有更老的记录本地必然也都有了**——这就是"突破 100 场上限"的关键，
 * 不用把服务端那 100 场每次都重拉一遍。
 *
 * 例外：已存在但**未完成**的记录要重写。iFinTime / iIsWin / iScore 在打完之后才
 * 有值，第一次拉到时全是空串，不刷新的话这些局会永远算成"输了、0 分"。
 *
 * @param known `roomId -> 是否已完成`
 */
fun planPage(records: List<GameRecordDto>, known: Map<String, Boolean>): PagePlan {
    val toWrite = ArrayList<GameRecordDto>(records.size)
    var newCount = 0
    var refreshedCount = 0
    var stop = false

    for (record in records) {
        val state = known[record.DsRoomId]
        when {
            state == null -> {
                toWrite += record
                newCount++
            }

            state -> {
                // 本地有且已完成 → 后面的更老，也都有了
                stop = true
            }

            else -> {
                // 本地有但当时没打完 → 刷新一次
                toWrite += record
                refreshedCount++
            }
        }
        if (stop) break
    }

    return PagePlan(toWrite, newCount, refreshedCount, stop)
}

/**
 * 对局增量同步。
 *
 * @param pageSize 每页条数，来自 [com.nzd.antigravitypanel.data.config.RemoteConfig.pageSize]
 * @param maxPages 翻页上限，纯兜底防死循环；正常靠"遇到已知记录"或"空页"提前结束
 */
class MatchSyncer(
    private val remote: MatchRemoteSource,
    private val local: MatchLocalSource,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val maxPages: Int = DEFAULT_MAX_PAGES,
    /** 要拉的几个 `map_mode`，见 [SYNC_MAP_MODES]。 */
    private val mapModes: List<String> = SYNC_MAP_MODES,
) {

    /**
     * @param nowSec 当前时间（epoch 秒）。由调用方传入，不在这里读时钟，方便测试。
     * @param retentionMonths 本地保留月数，<= 0 表示永久保留
     */
    suspend fun sync(nowSec: Long, retentionMonths: Int = DEFAULT_RETENTION_MONTHS): SyncResult {
        var newCount = 0
        var refreshedCount = 0
        var fetchedCount = 0
        var pages = 0
        var reachedEnd = true

        // 每个模式各翻一轮：服务端按 map_mode 分开给，不是一个列表里混着三种。
        // 分页的"遇到已知已完成就停"依赖**这一个模式内部**是按时间倒序的，
        // 所以三个模式不能交叉翻页，也不能把三份结果拼一起再判断。
        for (mapMode in mapModes) {
            var modeEnded = false

            for (page in 1..maxPages) {
                val records = remote.page(page, pageSize, mapMode)
                if (records.isEmpty()) {
                    modeEnded = true
                    break
                }
                pages++
                fetchedCount += records.size

                val plan = planPage(records, local.knownStates(records.map { it.DsRoomId }))
                if (plan.toWrite.isNotEmpty()) {
                    local.upsertAll(plan.toWrite.map { it.toEntity(nowSec) })
                }
                newCount += plan.newCount
                refreshedCount += plan.refreshedCount

                if (plan.stop) {
                    modeEnded = true
                    break
                }
                // 不满一页 = 服务端这个模式没更多了。响应里没有 total，只能这么判断。
                if (records.size < pageSize) {
                    modeEnded = true
                    break
                }
            }

            // 只要有一个模式是被 maxPages 截断的，整轮就不能算翻到头
            if (!modeEnded) reachedEnd = false
        }

        val deletedCount = if (retentionMonths > 0) {
            local.deleteOlderThan(minusCalendarMonths(nowSec, retentionMonths))
        } else {
            0
        }

        return SyncResult(
            newCount = newCount,
            refreshedCount = refreshedCount,
            fetchedCount = fetchedCount,
            pages = pages,
            deletedCount = deletedCount,
            reachedEnd = reachedEnd,
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 10
        const val DEFAULT_MAX_PAGES = 10
        /** 与规划一致：默认留 6 个月，用户可在设置里改成永久。 */
        const val DEFAULT_RETENTION_MONTHS = 6
    }
}
