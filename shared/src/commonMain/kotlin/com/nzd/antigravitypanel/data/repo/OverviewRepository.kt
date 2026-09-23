package com.nzd.antigravitypanel.data.repo

import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.remote.dto.GameDetailDto
import com.nzd.antigravitypanel.data.remote.dto.UserStatsDto
import com.nzd.antigravitypanel.domain.ActivityEvent
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.modeOf
import com.nzd.antigravitypanel.domain.parseCalendar
import com.nzd.antigravitypanel.domain.upcomingActivities
import kotlinx.serialization.Serializable

/**
 * 「近五场」聚合。全部是对猎场局算的，塔防 / 追猎没有 Boss 伤害口径。
 *
 * Boss 伤害和金币只有逐局详情接口里有，本地库（含导入的 JSON）存不下，
 * 所以这两项是 **可空的**：本地统计时为 null，UI 就把这一格整个拿掉，
 * 而不是显示一个假的 0。
 */
@Serializable
data class RecentFive(
    /** 实际取到详情的局数。网络挂了可能少于 5，UI 要如实显示。 */
    val sampleCount: Int,
    val mvpCount: Int,
    val avgScore: Long,
    val avgBossDamage: Long? = null,
    val avgCoin: Long? = null,
    /** 场均击杀。同样只在本地统计里出现（详情接口那边没取这一项）。 */
    val avgKills: Int? = null,
    val winCount: Int? = null,
    /** true = 这组数字是从本地库算的，Boss 伤害 / 金币拿不到。 */
    val localOnly: Boolean = false,
)

data class OverviewSnapshot(
    val stats: UserStatsDto = UserStatsDto(),
    val recent: RecentFive? = null,
    val activities: List<ActivityEvent> = emptyList(),
    /**
     * 每日首胜宝箱的可领数量，上限 [FIRST_WIN_CHEST_LIMIT]。
     *
     * 来自 `center.user.day` 的 `firstWinLeftCount`（官方 PC 端同一个字段）。
     * 拿不到（没登录 / 接口挂了）时为 null —— **null 和 0 是两回事**：
     * 0 是"今天领完了"，null 是"我不知道"，UI 上不该把两者都画成红点 0。
     */
    val firstWinCount: Int? = null,
)

/**
 * 概览页的数据源。
 *
 * 这里混了三个接口：`center.user.stats`（总览数字）、`center.game.detail`
 * （近五场的 Boss 伤害 / MVP / 金币明细）、`dist.contents`（活动日历）。
 * 三者互不依赖，任何一个失败都不该让整页挂掉，所以都单独 try。
 */
class OverviewRepository(
    private val api: NzApi,
    private val dao: MatchDao,
) {
    suspend fun load(cookie: MiniProgramCredential, nowSec: Long): OverviewSnapshot {
        api.updateCookie(cookie)
        val stats = runCatching { api.userStats() }.getOrDefault(UserStatsDto())
        val recent = runCatching { loadRecentFive() }.getOrNull()
        val activities = runCatching {
            upcomingActivities(parseCalendar(api.distCalendar().content), nowSec)
        }.getOrDefault(emptyList())
        if (activities.isEmpty()) {
            // 这条只在调试期有用：活动解析失败是静默的（它不该让整页挂掉），
            // 但"静默"很容易变成"永远查不出为什么是空的"，至少留个能 grep 的痕迹。
            println("[Calendar] no activity parsed from dist.contents")
        }
        // 首胜宝箱单独一个接口，挂了只影响那一个红点，不该拖累整页
        val firstWin = runCatching { api.userDay().firstWinLeftCount }
            .getOrNull()
            ?.coerceIn(0, FIRST_WIN_CHEST_LIMIT)
        return OverviewSnapshot(
            stats = stats,
            recent = recent,
            activities = activities,
            firstWinCount = firstWin,
        )
    }

    /**
     * 只从本地库算总览，一个网络请求都不发。
     *
     * 给"已导入 JSON 但还没登录"这种状态用：总览接口和对局详情都要 cookie，
     * 而本地库里场次、时长、地图都在，够把概览页的数字撑起来——
     * 这正是导入功能存在的意义，不这么做的话导入完概览页还是一片 0。
     *
     * Boss 伤害 / 金币那几项本地算不出来（只存在于逐局详情接口里），
     * 但评分 / MVP / 击杀 / 胜场都在库里，照样能凑出一份近五场，
     * 所以这里 [OverviewSnapshot.recent] 不再是 null。
     */
    suspend fun loadLocal(): OverviewSnapshot {
        val all = dao.page(Int.MAX_VALUE, 0)
        var hunt = 0
        var tower = 0
        var timeHunt = 0
        var mecha = 0
        var playtime = 0L
        for (match in all) {
            playtime += match.duration.toLong()
            when (modeOf(match.mapId)) {
                GameMode.HUNT -> hunt++
                GameMode.TOWER -> tower++
                GameMode.TIME_HUNT -> timeHunt++
                GameMode.MECHA -> mecha++
                GameMode.UNKNOWN -> Unit
            }
        }
        return OverviewSnapshot(
            stats = UserStatsDto(
                playtime = playtime,
                huntGameCount = hunt,
                towerGameCount = tower,
                timeHuntGameCount = timeHunt,
                mechaGameCount = mecha,
            ),
            // 活动日历要 cookie，本地模式下一律空——UI 那边已经有空态文案
            recent = summarizeLocalRecent(
                all.filter { it.finished && modeOf(it.mapId) == GameMode.HUNT }.take(RECENT_LIMIT),
            ),
        )
    }

    /**
     * 取最近 5 局**已结束的猎场**，逐局拉详情。
     *
     * 和 NZM 的做法一致：Boss 伤害只存在于详情接口里，列表接口没有。
     * 单局失败就跳过那一局，不整体失败——5 局里有 1 局 404 不该让用户看不到剩下 4 局。
     */
    suspend fun loadRecentFive(limit: Int = RECENT_LIMIT): RecentFive? {
        val candidates = dao.page(CANDIDATE_SCAN_SIZE, 0)
            .filter { it.finished && modeOf(it.mapId) == GameMode.HUNT }
            .take(limit)
        if (candidates.isEmpty()) return null

        val details = ArrayList<GameDetailDto>(candidates.size)
        for (match in candidates) {
            val detail = runCatching { api.gameDetail(match.roomId) }.getOrNull() ?: continue
            details.add(detail)
        }
        return summarizeRecent(details)
    }

    companion object {
        /**
         * 从最近的多少局里挑猎场局。取得太少（比如正好 5）时，
         * 最近五场里混了塔防局就会凑不满；取 30 局基本够用，代价只是多扫几行本地数据。
         */
        const val CANDIDATE_SCAN_SIZE = 30

        /** 「近五场」的窗口。UI 上的标题就是这个词，别单独改一处。 */
        const val RECENT_LIMIT = 5

        /**
         * 每日首胜宝箱的上限。官方前端写死 `/ 7`，并在这个数上显示「次数已满」。
         */
        const val FIRST_WIN_CHEST_LIMIT = 7
    }
}

/**
 * 用本地库算一份近五场。给导入 JSON / 未登录的场景用。
 *
 * 和 [summarizeRecent] 的口径差别要说清楚：
 * - Boss 伤害 / 金币本地根本没有，返回 null，UI 直接不显示这一格
 * - MVP 的判据是 `rankValue == 1`（列表接口下发的是名次），不是详情接口那套
 *   "自己是不是这局最高分"，两者在绝大多数局里一致，但不保证完全相等
 * - 评分 / 击杀 / 胜场都直接来自库里的字段
 */
fun summarizeLocalRecent(matches: List<MatchEntity>): RecentFive? {
    if (matches.isEmpty()) return null
    var score = 0L
    var kills = 0
    var mvp = 0
    var win = 0
    for (match in matches) {
        score += match.score
        kills += match.kills
        if (match.rankValue == 1) mvp++
        if (match.isWin) win++
    }
    val count = matches.size
    return RecentFive(
        sampleCount = count,
        mvpCount = mvp,
        avgScore = score / count,
        avgKills = kills / count,
        winCount = win,
        localOnly = true,
    )
}

/**
 * 纯计算，方便单测。
 *
 * MVP 的判据是"自己是不是这局积分最高的人"——和游戏内结算口径一致，
 * 用 `loginUserDetail` 对不上时用 `list` 里积分最高且等于自己分数的那位。
 */
fun summarizeRecent(details: List<GameDetailDto>): RecentFive? {
    if (details.isEmpty()) return null
    var bossDamage = 0L
    var score = 0L
    var coin = 0L
    var mvp = 0
    var counted = 0

    for (detail in details) {
        val self = detail.loginUserDetail ?: continue
        counted++
        bossDamage += self.huntingDetails?.damageTotalOnBoss ?: 0L
        coin += self.huntingDetails?.totalCoin ?: 0L
        val selfScore = self.baseDetail?.iScore ?: 0L
        score += selfScore
        val maxScore = detail.list.maxOfOrNull { it.baseDetail?.iScore ?: 0L } ?: selfScore
        if (selfScore > 0 && selfScore >= maxScore) mvp++
    }
    if (counted == 0) return null

    return RecentFive(
        sampleCount = counted,
        mvpCount = mvp,
        avgScore = score / counted,
        avgBossDamage = bossDamage / counted,
        avgCoin = coin / counted,
    )
}

/** 概览页"模式切换"下拉的四个选项，与 [UserStatsDto] 的四个计数字段对应。 */
enum class OverviewMode(val label: String) {
    HUNT("僵尸猎场"),
    TOWER("塔防"),
    MECHA("机甲排位"),
    TIME_HUNT("时空追猎"),
}

fun UserStatsDto.countOf(mode: OverviewMode): Int = when (mode) {
    OverviewMode.HUNT -> huntGameCount
    OverviewMode.TOWER -> towerGameCount
    OverviewMode.MECHA -> mechaGameCount
    OverviewMode.TIME_HUNT -> timeHuntGameCount
}

/** 在线时长（秒）转成 `N 小时 M 分`。 */
fun formatPlaytime(seconds: Long): String {
    if (seconds <= 0) return "0 小时"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours <= 0) "$minutes 分" else "$hours 小时 $minutes 分"
}

