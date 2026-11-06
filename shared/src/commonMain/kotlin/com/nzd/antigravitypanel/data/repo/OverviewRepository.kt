package com.nzd.antigravitypanel.data.repo

import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.remote.CookieExpiredException
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.remote.dto.GameDetailDto
import com.nzd.antigravitypanel.data.remote.dto.GameRecordDto
import com.nzd.antigravitypanel.data.remote.dto.UserStatsDto
import com.nzd.antigravitypanel.data.remote.dto.avatarDecoded
import com.nzd.antigravitypanel.data.remote.dto.nicknameDecoded
import com.nzd.antigravitypanel.domain.ActivityEvent
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.MECHA_MAP_MODE
import com.nzd.antigravitypanel.domain.isMecha
import com.nzd.antigravitypanel.domain.matchTagOf
import com.nzd.antigravitypanel.domain.modeOf
import com.nzd.antigravitypanel.domain.parseCalendar
import com.nzd.antigravitypanel.domain.upcomingActivities
import com.nzd.antigravitypanel.util.parseServerTime
import com.nzd.antigravitypanel.util.startOfServerDay
import kotlinx.serialization.Serializable

/**
 * 顶栏账号区要的**账号名片**。
 *
 * ⚠️ 头像和昵称**只在 `center.game.detail` 的 `loginUserDetail` 里有**，
 * 总览接口（`center.user.stats`）下发的那堆数字里一个字都没有 ——
 * 官方 PC 端也是这么取的：它的 `stats` 加载流程是拉完对局列表后，
 * 再从列表第一局的详情里抠出 `loginUserDetail.avatar / nickname` 存成 `userInfo`。
 * 想换个数据源基本没得选。
 *
 * @param openid 这份名片属于哪个账号。**换号登录时要靠它判定缓存是不是别人的**：
 *   退出 / 扫码换号后 `OVERVIEW_CACHE` 里还留着上一个号的昵称，
 *   不比对的话新号第一帧会先挂一会儿旧名字。
 */
@Serializable
data class AccountProfile(
    val openid: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
)

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
    /**
     * 场均通关时长（秒）。取不到（详情里 `iDuration` 全是 0、本地库也没存）时为 null，
     * UI 整格不显示 —— 摆一个「—」会让这一格永远占着位置却不给信息。
     */
    val avgDuration: Int? = null,
    /**
     * 「尽力 / 带飞」局数：这几局里命中任意一个标签的场次。
     *
     * 判据就是历史页那两个角标（[com.nzd.antigravitypanel.domain.matchTagOf]），
     * 不另立一套。本地库模式拿不到全队 Boss 伤害 ⇒ null。
     */
    val tagCount: Int? = null,
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

    /**
     * 头像 + 昵称。null = 这一轮没取到（本地库是空的，或逐局详情都没回 `loginUserDetail`）。
     *
     * 和 [RecentFive] 一样出自同一批 `center.game.detail`，见 [AccountProfile]。
     */
    val account: AccountProfile? = null,

    /**
     * 赛季等级（顶栏胶囊第二行的 `LV.n`）。
     *
     * 来自 `center.mweapon.list` 的万象武器等级 —— `center.user.stats` 里没有等级字段，
     * PC 端也是从这里取的（它存成 `nzm_season_level` 再画账号卡）。
     * 0 = 这一轮没取到（新号还没有万象武器 / 接口挂了），UI 就不摆。
     */
    val seasonLevel: Int = 0,
)

/**
 * 概览页的数据源。
 *
 * 这里混了三个接口：`center.user.stats`（总览数字）、`center.game.detail`
 * （近五场的 Boss 伤害 / MVP / 金币明细，**外加顶栏那颗账号胶囊要的头像和昵称**）、
 * `dist.contents`（活动日历）。三者互不依赖，任何一个失败都不该让整页挂掉，所以都单独 try。
 */
class OverviewRepository(
    private val api: NzApi,
    private val dao: MatchDao,
) {
    /**
     * 今日机甲战局数（`n / [MECHA_TODAY_GOAL]` 里那个 n）。
     *
     * ⚠️ **只有这一处会拉机甲对局**：机甲刻意不在 [com.nzd.antigravitypanel.domain.COUNTABLE_MODES]
     * 里，本地库除了 JSON 导入之外根本没有机甲对局，只能现拉。
     * 代价是**只在用户真的切到这个选项时才发**，平时不占配额。
     *
     * ⚠️ `map_mode` 只是"请服务端先滤一道"的请求参数，**不是判据**：服务端对
     * 「机甲非对称」这个值不买账（回过来的就是猎场那一份），所以逐条判定靠
     * [GameRecordDto.isMecha]。这个参数的存在仅仅是为了让"服务端哪天认了"时少翻几页。
     *
     * 翻页上限是纯兜底：正常靠"翻到昨天的记录"或"这一页不满"提前结束，
     * 一天最多也就几十局，一页就够。
     *
     * @throws Throwable 没凭证 / 网络挂了往上抛，由 ViewModel 决定怎么显示
     */
    suspend fun loadMechaTodayCount(nowSec: Long): Int {
        val dayStart = startOfServerDay(nowSec)
        var total = 0
        for (page in 1..MECHA_TODAY_MAX_PAGES) {
            val records = api.gameList(
                page = page,
                limit = MECHA_TODAY_PAGE_SIZE,
                mapMode = MECHA_MAP_MODE,
            ).gameList
            if (records.isEmpty()) break
            total += countMechaTodayGames(records, nowSec)
            // 判断"翻过今天"只看本页**最后一条**（服务端倒序，它是最老的）：
            // 中间夹一条时间解析不出来的脏数据不该把翻页提前截断
            val oldest = records.lastOrNull()?.dtEventTime?.let { parseServerTime(it) }
            if (oldest != null && oldest < dayStart) break
            if (records.size < MECHA_TODAY_PAGE_SIZE) break
        }
        return total
    }

    /**
     * 拉一轮概览。
     *
     * ⚠️ **凭证失效能往上抛**（[CookieExpiredException]），其余错误逐个吞掉。
     * 概览是「Cookie 状态卡」的唯一数据源，这里把失效也吞了的话，
     * 凭证明明已经过期（签到页那边已经在报「凭证失效」），状态卡还会写「已识别」。
     * 见 [tolerateUnlessExpired]。
     */
    suspend fun load(cookie: MiniProgramCredential, nowSec: Long): OverviewSnapshot {
        api.updateCookie(cookie)
        // 这一发是凭证的**主判据**：它都报失效，后面几路没有继续打的意义
        val stats = tolerateUnlessExpired { api.userStats() } ?: UserStatsDto()
        // 近五场统计和账号名片出自**同一批** `center.game.detail`：
        // 分开拉等于把同样的几局逐条请求两遍（每局详情都是一次独立的网络往返）。
        val details = tolerateUnlessExpired { loadRecentDetails(cookie.openid) } ?: emptyList()
        val activities = tolerateUnlessExpired {
            upcomingActivities(parseCalendar(api.distCalendar().content), nowSec)
        } ?: emptyList()
        if (activities.isEmpty()) {
            // 这条只在调试期有用：活动解析失败是静默的（它不该让整页挂掉），
            // 但"静默"很容易变成"永远查不出为什么是空的"，至少留个能 grep 的痕迹。
            println("[Calendar] no activity parsed from dist.contents")
        }
        // 首胜宝箱单独一个接口，挂了只影响那一个红点，不该拖累整页
        val firstWin = tolerateUnlessExpired { api.userDay().firstWinLeftCount }
            ?.coerceIn(0, FIRST_WIN_CHEST_LIMIT)
        // 名片优先从近五场那批详情里抠（同一批请求，不多打一次网络）；
        // 扣不到再按"任意模式"单独补一遍 —— 见 [loadSelfDetails] 里为什么不能只盯猎场
        val account = accountProfileOf(details, cookie.openid)
            ?: tolerateUnlessExpired { accountProfileOf(loadSelfDetails(cookie.openid), cookie.openid) }
        // 赛季等级：`center.user.stats` 里**没有**这一项，得单独拉万象武器列表。
        // 挂了只影响顶栏那一个 `LV.n`，不该拖累整页。
        val seasonLevel = tolerateUnlessExpired { api.mweaponList().seasonLevel } ?: 0
        return OverviewSnapshot(
            stats = stats,
            recent = summarizeRecent(details),
            activities = activities,
            firstWinCount = firstWin,
            account = account,
            seasonLevel = seasonLevel,
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
    suspend fun loadLocal(ownerOpenId: String = ""): OverviewSnapshot {
        val all = dao.page(ownerOpenId, Int.MAX_VALUE, 0)
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
     * 取最近 [limit] 局**已结束的猎场**的详情。
     *
     * 和 NZM 的做法一致：Boss 伤害 / 金币 / 昵称头像都只存在于详情接口里，列表接口没有。
     * 单局失败就跳过那一局，不整体失败——5 局里有 1 局 404 不该让用户看不到剩下 4 局。
     */
    private suspend fun loadRecentDetails(
        ownerOpenId: String,
        limit: Int = RECENT_LIMIT,
    ): List<GameDetailDto> {
        val candidates = dao.page(ownerOpenId, CANDIDATE_SCAN_SIZE, 0)
            .filter { it.finished && modeOf(it.mapId) == GameMode.HUNT }
            .take(limit)
        if (candidates.isEmpty()) return emptyList()

        val details = ArrayList<GameDetailDto>(candidates.size)
        for (match in candidates) {
            val detail = tolerateUnlessExpired { api.gameDetail(match.roomId) } ?: continue
            details.add(detail)
        }
        return details
    }

    /**
     * 取最近 [limit] 局**任意模式**的详情，只为了从里面抠自己的昵称和头像。
     *
     * 和上面那批"近五场"的区别要说清楚：近五场必须挑**已结束的猎场**（别的模式没有 Boss 伤害口径），
     * 而这里只需要知道"我是谁" —— `center.game.detail` 任何一款模式的详情里都有 `loginUserDetail`，
     * 打了一半的局也有。只盯着猎场的话，只玩塔防（或者最近全是塔防）的账号
     * 永远拿不到头像和昵称，顶栏就白摆一格。
     */
    private suspend fun loadSelfDetails(
        ownerOpenId: String,
        limit: Int = SELF_DETAIL_LIMIT,
    ): List<GameDetailDto> {
        val roomIds = dao.page(ownerOpenId, CANDIDATE_SCAN_SIZE, 0)
            .take(limit)
            .map { it.roomId }
            .ifEmpty { latestRoomIdsFromServer(limit) }

        val details = ArrayList<GameDetailDto>(roomIds.size)
        for (roomId in roomIds) {
            val detail = tolerateUnlessExpired { api.gameDetail(roomId) } ?: continue
            details.add(detail)
        }
        return details
    }

    /**
     * 本地库一行都没有时，直接向服务端要一页对局。
     *
     * 同步（`MatchRepository`）和概览是**并发**跑的：概览这一路先跑完时库里可能还是空的，
     * 不兜这一手的话刚登录那一次（以及清空本地数据之后那一次）顶栏会一直空着，
     * 要用户手动点一次刷新才出来。
     */
    private suspend fun latestRoomIdsFromServer(limit: Int): List<String> =
        tolerateUnlessExpired {
            api.gameList(page = 1, limit = limit.coerceAtLeast(3), mapMode = "猎场")
                .gameList
                .map { it.DsRoomId }
                .filter { it.isNotBlank() }
        } ?: emptyList()

    companion object {
        /**
         * 拉「今日机甲战」时每页条数。
         *
         * ⚠️⚠️ **这个值必须等于服务端每页实际返回的上限，不能写"我想要多少"**
         * （2026-10-10 抓包实测：官方小程序自己就是 `limit=10`，而请求 20 时服务端
         * 照样只给 10 条）。翻页终止有一条是"这一页不满 = 翻到头了"，
         * 拿一个比服务端上限大的数去比，第一页就判定成"翻到头了"——
         * 于是统计永远停在第一页的 10 条，今天打了 25 局也只显示 10。
         */
        const val MECHA_TODAY_PAGE_SIZE = 10

        /**
         * 翻页上限，纯兜底防死循环；正常靠"翻到昨天的记录"提前结束。
         *
         * ⚠️ 给到 10 页（100 条）是因为这一页是**全部模式混在一起**倒序返回的
         * （官方也不传 `map_mode`），今天打的其他模式越多，要翻到昨天就得翻越多的页。
         * 一页只有 10 条，5 页不够覆盖"打满达标场次 + 若干别的模式"这种日子。
         */
        const val MECHA_TODAY_MAX_PAGES = 10

        /**
         * 从最近的多少局里挑猎场局。取得太少（比如正好 5）时，
         * 最近五场里混了塔防局就会凑不满；取 30 局基本够用，代价只是多扫几行本地数据。
         */
        const val CANDIDATE_SCAN_SIZE = 30

        /** 「近五场」的窗口。UI 上的标题就是这个词，别单独改一处。 */
        const val RECENT_LIMIT = 5

        /** 找"我是谁"时最多试几局详情。取到第一局有 `loginUserDetail` 的就停。 */
        const val SELF_DETAIL_LIMIT = 6

        /**
         * 每日首胜宝箱的上限。官方前端写死 `/ 7`，并在这个数上显示「次数已满」。
         */
        const val FIRST_WIN_CHEST_LIMIT = 7
    }
}

/**
 * 概览的逐项容错，**唯一例外是凭证失效**。
 *
 * 概览混了好几个接口，任何一项挂了都不该把整页打空 —— 所以普通错误吞掉，
 * 这一项退回默认值，其余照常显示。但 [CookieExpiredException] 必须原样往上抛：
 *
 * - 概览页是「Cookie 状态卡」的**唯一数据源**（[com.nzd.antigravitypanel.ui.overview.CookieStatus]）；
 * - 之前这里一律 `runCatching`，于是凭证明明已经过期（签到页那边已经在报「凭证失效」），
 *   状态卡还照常写「已识别」，用户看着就是"app 说我的 cookie 好好的，可什么都拉不到"。
 *
 * 这几个接口都在 `IdeChart.Main` 那一组，用的是同一串 cookie，
 * 所以任何一路回 `iRet != 0` 都足以判定整份凭证失效，不存在"这个接口抖了"的误判。
 *
 * 写成顶层 `inline` 是为了能脱离 ViewModel / 网络单测（见 `OverviewExpiryTest`）。
 */
internal inline fun <T> tolerateUnlessExpired(block: () -> T): T? =
    try {
        block()
    } catch (e: CookieExpiredException) {
        throw e
    } catch (t: Throwable) {
        null
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
    var duration = 0L
    var durationCount = 0
    for (match in matches) {
        score += match.score
        kills += match.kills
        if (match.rankValue == 1) mvp++
        if (match.isWin) win++
        if (match.duration > 0) {
            duration += match.duration
            durationCount++
        }
    }
    val count = matches.size
    return RecentFive(
        sampleCount = count,
        mvpCount = mvp,
        avgScore = score / count,
        avgKills = kills / count,
        winCount = win,
        avgDuration = if (durationCount > 0) (duration / durationCount).toInt() else null,
        // 本地没有全队 Boss 伤害 ⇒ 判不出标签，null 让 UI 不摆这一格
        tagCount = null,
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
    var durationSum = 0L
    var durationCount = 0
    var tagged = 0

    for (detail in details) {
        val self = detail.loginUserDetail ?: continue
        counted++
        bossDamage += self.huntingDetails?.damageTotalOnBoss ?: 0L
        coin += self.huntingDetails?.totalCoin ?: 0L
        val selfScore = self.baseDetail?.iScore ?: 0L
        score += selfScore
        val maxScore = detail.list.maxOfOrNull { it.baseDetail?.iScore ?: 0L } ?: selfScore
        if (selfScore > 0 && selfScore >= maxScore) mvp++

        // 时长只统计**报得上来**的局：详情里这一项偶尔是 0（老对局 / 中途退出），
        // 把它算进分母会把场均时长拉低成一个假数字
        val duration = self.baseDetail?.iDuration ?: 0
        if (duration > 0) {
            durationSum += duration
            durationCount++
        }

        // 「尽力 / 带飞」= 命中历史页任一标签的那两档，规则完全复用
        bossDamageShareOf(detail)?.let { share ->
            if (matchTagOf(share.self, share.team, share.rival) != null) tagged++
        }
    }
    if (counted == 0) return null

    return RecentFive(
        sampleCount = counted,
        mvpCount = mvp,
        avgScore = score / counted,
        avgBossDamage = bossDamage / counted,
        avgCoin = coin / counted,
        avgDuration = if (durationCount > 0) (durationSum / durationCount).toInt() else null,
        tagCount = tagged,
    )
}

/**
 * 从一批对局详情里抠出**自己**的头像和昵称。纯计算，方便单测。
 *
 * 取**第一份**带回 `loginUserDetail` 的详情就够了：同一个账号打出来的局，
 * 里面的昵称和头像是一样的，没必要逐局比对。
 * 昵称和头像都可能缺字段（官方对 tapd / 游客账号下发的 `avatar` 是空的），
 * 所以两个**全空**才跳过这一份，只缺一个照样收下 —— 顶栏那边头像有兜底样式。
 */
fun accountProfileOf(details: List<GameDetailDto>, openid: String = ""): AccountProfile? {
    for (detail in details) {
        val self = detail.loginUserDetail ?: continue
        val nickname = self.nicknameDecoded.trim()
        val avatarUrl = httpsAvatar(self.avatarDecoded.trim())
        if (nickname.isBlank() && avatarUrl.isBlank()) continue
        return AccountProfile(openid = openid, nickname = nickname, avatarUrl = avatarUrl)
    }
    return null
}

/**
 * 头像地址统一升成 https。
 *
 * 官方下发的 `loginUserDetail.avatar` 是 **http** 打头的（`thirdqq.qlogo.cn` /
 * `thirdwx.qlogo.cn`），而 app 没开明文流量 —— 原样丢给 Coil 会**静默失败**：
 * 不报错、不显示，表现就是"ID 和分区都出来了，头像那一格空着"。
 * 那两个 CDN 走 https 是通的，所以这里改前缀而不是去开 usesCleartextTraffic。
 */
private fun httpsAvatar(url: String): String =
    if (url.startsWith("http://", ignoreCase = true)) "https://${url.substring(7)}" else url

/**
 * 概览页「模式切换」下拉的选项。
 *
 * 前四个直接对应 [UserStatsDto] 的四个**累计**计数字段；
 * [MECHA_TODAY] 是另一路：它显示的是**今日**机甲战局数 `n / [MECHA_TODAY_GOAL]`，
 * 不是总数，所以 [UserStatsDto.countOf] 对它返回 0，数字由
 * [OverviewRepository.loadMechaTodayCount] 单拉一次对局列表算出来。
 */
enum class OverviewMode(val label: String) {
    HUNT("僵尸猎场"),
    TOWER("塔防"),
    MECHA("机甲排位"),
    TIME_HUNT("时空追猎"),
    MECHA_TODAY("今日机甲战"),
}

/**
 * 「今日机甲战」那一格的分母（达标场次）。
 *
 * ⚠️ 它是**官方定的达标线**，不是我们算出来的；官方改了就跟着改这一个数，
 * 卡片上的 `n / 20 场` 是拿它拼出来的，别在 UI 里另写一份 20。
 * （2026-10-10 从 40 改成 20：赛季任务的实际达标线就是 20。）
 */
const val MECHA_TODAY_GOAL = 20

fun UserStatsDto.countOf(mode: OverviewMode): Int = when (mode) {
    OverviewMode.HUNT -> huntGameCount
    OverviewMode.TOWER -> towerGameCount
    OverviewMode.MECHA -> mechaGameCount
    OverviewMode.TIME_HUNT -> timeHuntGameCount
    // 今日机甲战不走累计口径，数字在 OverviewRepository.loadMechaTodayCount 里
    OverviewMode.MECHA_TODAY -> 0
}

/**
 * 从一批**按时间倒序**的对局里数出今天（北京时间 00:00 起）打了几局**机甲战**。
 *
 * 之所以能在中途 `break`：服务端是倒序返回的，一旦碰到一条早于今天 00:00 的，
 * 后面只会更老，不用再看。
 *
 * ⚠️ 边界是**自然日**，不是首胜宝箱那套「北京时间 05:00 刷新」——
 * 这里数的是"今天打了几局"，不是"这一天的奖励还剩几次"，游戏规则里那两个数不同源。
 *
 * ⚠️ **中途退出的不算**：没打完的局（`iFinTime == 0`）服务端照样会返回，
 * 但它不是"今天打了一局"，计进去会让这个数虚高。
 *
 * ⚠️⚠️ **必须逐条判是不是机甲**（[GameRecordDto.isMecha]），不能信"这一页是按
 * `map_mode=机甲非对称` 拉回来的"：实测服务端对这个 `map_mode` 不买账，
 * 拿回来的就是猎场那一份 —— 于是早上打两局猎场，卡片上「今日机甲战」就显示 2 局。
 * 请求带 `map_mode` 是为了尽量让服务端先滤一道，但它只是**优化**，不是判据；
 * 真正说了算的是每条记录自己的 `iMapId`。
 *
 * 纯函数：它是 `n / [MECHA_TODAY_GOAL]` 里那个 n 的全部规则，必须能单测。
 */
fun countMechaTodayGames(records: List<GameRecordDto>, nowSec: Long): Int {
    val dayStart = startOfServerDay(nowSec)
    var count = 0
    for (record in records) {
        // 解析不出来（未完成的局可能给空串）就跳过，不能因为一条脏数据把整页算废
        val time = parseServerTime(record.dtEventTime) ?: continue
        if (time < dayStart) break
        if (record.finished && record.isMecha()) count++
    }
    return count
}

/** 在线时长（秒）转成 `N 小时 M 分`。 */
fun formatPlaytime(seconds: Long): String {
    if (seconds <= 0) return "0 小时"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours <= 0) "$minutes 分" else "$hours 小时 $minutes 分"
}

