package com.nzd.antigravitypanel.data.remote

import com.nzd.antigravitypanel.data.config.RemoteConfig
import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
import com.nzd.antigravitypanel.data.remote.dto.CollectionHomeDto
import com.nzd.antigravitypanel.data.remote.dto.CollectionListDto
import com.nzd.antigravitypanel.data.remote.dto.ConfigListResponseDto
import com.nzd.antigravitypanel.data.remote.dto.DistCalendarDto
import com.nzd.antigravitypanel.data.remote.dto.DistContentsDto
import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto
import com.nzd.antigravitypanel.data.remote.dto.GameDetailDto
import com.nzd.antigravitypanel.data.remote.dto.GameListPageDto
import com.nzd.antigravitypanel.data.remote.dto.MapDropDto
import com.nzd.antigravitypanel.data.remote.dto.MapItemListDto
import com.nzd.antigravitypanel.data.remote.dto.MapStatsDto
import com.nzd.antigravitypanel.data.remote.dto.RawConfigDto
import com.nzd.antigravitypanel.data.remote.dto.SignInDoDto
import com.nzd.antigravitypanel.data.remote.dto.SignInListDto
import com.nzd.antigravitypanel.data.remote.dto.ThreadSearchDto
import com.nzd.antigravitypanel.data.remote.dto.UserDayDto
import com.nzd.antigravitypanel.data.remote.dto.UserInfoDto
import com.nzd.antigravitypanel.data.remote.dto.UserStatsDto
import com.nzd.antigravitypanel.data.remote.dto.WeeklyReportDto
import com.nzd.antigravitypanel.domain.GameMode
import com.nzd.antigravitypanel.domain.serverMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * 赛季探测用哪个模式探：官方只探猎场那一份（`map_mode: '猎场'`），
 * 三个模式的结果对"这个赛季有没有数据"这件事是一致的。
 */
private val PROBE_MAP_MODE: String = GameMode.HUNT.serverMode

/**
 * 对 [IdeClient] 的业务封装：一个 method 一个方法，参数与返回值都是具体类型。
 * cookie 和 config 是「运行时才知道」的东西（用户还没粘贴 / 远程配置还没拉到），
 * 所以做成运行时可替换，而不是构造参数——P3 接到 Repository 上即可。
 */
class NzApi(
    private val client: IdeClient = IdeClient(),
    cookie: MiniProgramCredential? = null,
    config: RemoteConfig = RemoteConfig(),
) {
    private var cookie: MiniProgramCredential? = cookie
    private var config: RemoteConfig = config

    /**
     * 探测出来的「有战绩的那个赛季」。null = 还没探测过。
     *
     * 见 [activeSeason]。
     */
    private var resolvedSeason: Int? = null

    /** 换了凭证 / 远程配置都要让探测结果失效 —— 换号之后数据完全不同。 */
    fun updateCookie(value: MiniProgramCredential) {
        cookie = value
        resolvedSeason = null
    }

    fun updateConfig(value: RemoteConfig) {
        config = value
        resolvedSeason = null
    }

    // ---------------- 战绩 ----------------

    suspend fun userStats(): UserStatsDto = call(IdeMethod.UserStats)

    /**
     * 对局列表，按页取。
     *
     * ⚠️ `map_mode` **不是可选参数**：服务端按 `猎场` / `塔防` / `时空追猎`
     * 分开给，不给的话只会拿到猎场那一份，另外两个模式的对局永远同步不下来。
     * 三个模式要各翻一遍（见 `MatchSyncer`），官方 PC 端就是这么做的。
     *
     * ⚠️ 服务端只保留滚动窗口内的对局（近 30 天 / 最近 100 场，先到先算），
     * 响应里**没有 total**，只能靠"翻到空页"判断结束。
     */
    suspend fun gameList(
        page: Int,
        limit: Int = config.pageSize,
        mapMode: String,
        /** 显式指定赛季。留空用 [activeSeason] 探测出来的那个。 */
        seasonID: Int? = null,
    ): GameListPageDto = call(
        IdeMethod.GameList,
        "page" to page,
        "limit" to limit,
        "map_mode" to mapMode,
        seasonID = seasonID,
    )

    /** 单局详情。`roomID` 就是列表里的 `DsRoomId`。 */
    suspend fun gameDetail(roomId: String): GameDetailDto =
        call(IdeMethod.GameDetail, "roomID" to roomId)

    /** 地图维度统计。`mapMode` 是中文，实测传过 "猎场"。 */
    suspend fun mapStats(mapMode: String): List<MapStatsDto> =
        call(IdeMethod.MapStats, "map_mode" to mapMode)

    suspend fun mapDrop(): MapDropDto = call(IdeMethod.MapDrop)

    /** `mapIds` 为空数组时服务端返回全部地图的掉落物。 */
    suspend fun mapItemList(mapIds: List<Int> = emptyList()): MapItemListDto =
        call(IdeMethod.MapItemList, "mapID" to mapIds)

    // ---------------- 配置 ----------------

    /**
     * 游戏配置下发。地图表里的 `supportStatistics` 决定哪些地图计入统计，
     * 不要自己硬编码排除规则。
     */
    suspend fun gameConfig(): GameConfigDto =
        call<ConfigListResponseDto>(IdeMethod.ConfigList).config
            ?: throw ProtocolException("center.config.list 没有返回 config")

    // ---------------- 用户 ----------------

    suspend fun userDay(): UserDayDto = call(IdeMethod.UserDay, "_source" to 1)

    suspend fun userInfo(): UserInfoDto = call(IdeMethod.UserInfo)

    /** `dtstatdate` 形如 "20260906"。 */
    suspend fun weeklyReportStatus(dtstatdate: String): WeeklyReportDto =
        call(IdeMethod.WeeklyReportStatus, "dtstatdate" to dtstatdate)

    suspend fun eventReport(): RawConfigDto = call(IdeMethod.EventReport)

    // ---------------- 图鉴 ----------------

    suspend fun collectionHome(limit: Int = 4): CollectionHomeDto =
        call(IdeMethod.CollectionHome, "limit" to limit)

    suspend fun collection(kind: CollectionKind): CollectionListDto = call(kind.method)

    // ---------------- 配装 / 社区 ----------------

    suspend fun gearConfig(): RawConfigDto = call(IdeMethod.GearConfig)

    suspend fun gearEquipmentHot(): RawConfigDto = call(IdeMethod.GearEquipmentHot)

    suspend fun distContents(): DistContentsDto = call(IdeMethod.DistContents)

    /**
     * 活动日历。参数照官方 PC 端的 `fetchCalendar` 抄：
     * `contentType=rilipeizhi` 是要"日历配置"那一支，`filterTime=true` 让服务端
     * 只回还没结束的活动（所以本地不用再过滤一遍），`withChild=true` 带子活动。
     *
     * 响应里 `content` 是一个 **JSON 字符串**，活动数组在它的 `rilipeizhi.data` 下。
     */
    suspend fun distCalendar(): DistCalendarDto = call(
        IdeMethod.DistCalendar,
        "distType" to "bannerManage",
        "subType" to "seasonCenter",
        "contentType" to "rilipeizhi",
        "filterTime" to true,
        "withChild" to true,
    )

    suspend fun threadSearch(keyword: String): ThreadSearchDto =
        call(IdeMethod.ThreadSearch, "keyword" to keyword)

    // ---------------- 福利站 ----------------

    /**
     * 签到看板。`userTime` 抓包时是空串（服务端按当天返回），原样带上。
     *
     * 判断"今天签没签"要用响应里的 `currentDay.isSignIn`，**不要**拿本地日期去推：
     * 服务端按自己的时区切天，东八区在 UTC 跨天后会有 8 小时的错位。
     */
    suspend fun signInList(): SignInListDto =
        call(IdeMethod.SignInList, "groupID" to 0, "userTime" to "")

    /**
     * 执行签到。返回这次实际发到手的奖励。
     *
     * 调用方必须先走 [signInList] 确认 `isSignIn == false`：重复调用会怎样服务端没明说，
     * 抓包里小程序也是先 list 再 do，照它的顺序来。
     */
    suspend fun signInDo(): SignInDoDto = call(IdeMethod.SignInDo, "groupID" to 0)

    // ---------------- 内部 ----------------

    /**
     * 当前该用哪个赛季号发战绩请求。
     *
     * **赛季刚更新的那几天必须靠这个**：玩家在新赛季一场没打时，服务端按
     * `seasonID = 4` 查会返回空数组，不回退的话概览和地图分布会整页变 0。
     * 官方前端是这么兜的（它的 `Us = [4, 3]`，逐个试到有数据的为止）：
     *
     * ```js
     * for (let i of o) if (s = await Sc(...{seasonID:i}), s.success && !r(s.raw))
     *     return {...s, seasonID:i}
     * ```
     *
     * 探测只做一次（结果缓存在 [resolvedSeason]），后续请求不再多发。
     * 全部赛季都空时退回 [RemoteConfig.seasonID] —— 那时是真的没数据，不是赛季号错。
     */
    private suspend fun activeSeason(): Int {
        resolvedSeason?.let { return it }
        val candidates = (listOf(config.seasonID) + config.fallbackSeasonIDs).distinct()
        for (id in candidates) {
            val hasData = runCatching {
                gameList(page = 1, limit = config.pageSize, mapMode = PROBE_MAP_MODE, seasonID = id)
                    .gameList
                    .isNotEmpty()
            }.getOrDefault(false)
            if (hasData) {
                resolvedSeason = id
                return id
            }
        }
        resolvedSeason = config.seasonID
        return config.seasonID
    }

    /**
     * @param seasonID 显式赛季号，只有赛季探测那一次会传；其余走 [activeSeason]。
     *   放在 vararg 后面是因为**它几乎不传**，让所有调用点能保持
     *   `call(method, "k" to v)` 的写法，不必为了一个基本用不到的参数给每个调用加名字。
     */
    private suspend inline fun <reified T> call(
        method: IdeMethod,
        vararg params: Pair<String, Any?>,
        seasonID: Int? = null,
    ): T {
        val activeCookie = cookie ?: throw MissingCredentialException()
        // 只有战绩那组带 seasonID；福利站连这个字段都没有（见 buildParam）
        val season = if (method.chart.withSourceParams) seasonID ?: activeSeason() else null
        return client.postIde(method, buildParam(method, season, *params), activeCookie, config)
    }

    /**
     * seasonID 除了表单顶层那份，param 里还要再带一次——但**只对战绩那组**成立：
     * 福利站的 param 抓包里是干净的 `{"groupID":0}`，多塞一个 seasonID 属于自作主张。
     *
     * @param seasonID 已经解析好的赛季号；null 表示这一组请求根本不带这个字段。
     */
    private fun buildParam(
        method: IdeMethod,
        seasonID: Int?,
        vararg params: Pair<String, Any?>,
    ): JsonObject {
        val entries = buildList<Pair<String, JsonElement>> {
            if (method.chart.withSourceParams && seasonID != null) {
                add("seasonID" to JsonPrimitive(seasonID))
            }
            for ((key, value) in params) add(key to toJsonElement(value))
        }
        return buildJsonObject {
            for ((key, element) in entries) put(key, element)
        }
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> buildJsonArray { value.forEach { add(toJsonElement(it)) } }
        else -> JsonPrimitive(value.toString())
    }
}
