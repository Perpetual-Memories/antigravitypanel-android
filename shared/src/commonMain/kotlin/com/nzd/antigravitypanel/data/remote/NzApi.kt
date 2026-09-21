package com.nzd.antigravitypanel.data.remote

import com.nzd.antigravitypanel.data.config.RemoteConfig
import com.nzd.antigravitypanel.data.credential.NzCookie
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
import com.nzd.antigravitypanel.data.remote.dto.ThreadSearchDto
import com.nzd.antigravitypanel.data.remote.dto.UserDayDto
import com.nzd.antigravitypanel.data.remote.dto.UserInfoDto
import com.nzd.antigravitypanel.data.remote.dto.UserStatsDto
import com.nzd.antigravitypanel.data.remote.dto.WeeklyReportDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * 对 [IdeClient] 的业务封装：一个 method 一个方法，参数与返回值都是具体类型。
 *
 * cookie 和 config 是「运行时才知道」的东西（用户还没粘贴 / 远程配置还没拉到），
 * 所以做成运行时可替换，而不是构造参数——P3 接到 Repository 上即可。
 */
class NzApi(
    private val client: IdeClient = IdeClient(),
    cookie: NzCookie? = null,
    config: RemoteConfig = RemoteConfig(),
) {
    private var cookie: NzCookie? = cookie
    private var config: RemoteConfig = config

    fun updateCookie(value: NzCookie) {
        cookie = value
    }

    fun updateConfig(value: RemoteConfig) {
        config = value
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
    ): GameListPageDto = call(IdeMethod.GameList, "page" to page, "limit" to limit, "map_mode" to mapMode)

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

    // ---------------- 内部 ----------------

    private suspend inline fun <reified T> call(
        method: IdeMethod,
        vararg params: Pair<String, Any?>,
    ): T {
        val activeCookie = cookie ?: throw MissingCredentialException()
        return client.postIde(method, buildParam(*params), activeCookie, config)
    }

    /** seasonID 除了表单顶层那份，param 里还要再带一次。 */
    private fun buildParam(vararg params: Pair<String, Any?>): JsonObject {
        val entries = buildList<Pair<String, JsonElement>> {
            add("seasonID" to JsonPrimitive(config.seasonID))
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
