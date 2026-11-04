package com.nzd.antigravitypanel.data.supercore

import com.nzd.antigravitypanel.data.remote.IdeJson
import com.nzd.antigravitypanel.data.xinyue.decodeRoleText
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** 某个 flow 的剩余/总次数。签到和任务都是 0/1，抽奖是 0..27。 */
data class SupercoreHold(val remain: Int = 0, val total: Int = 0)

/** 「当前积分」那个 flow 返回的两个数。 */
data class SupercorePoints(val remain: Int?, val total: Int?)

/** 队伍状态。没成团时领取会被服务端拒掉（只回"网络繁忙"）。 */
data class SupercoreTeam(
    val inTeam: Boolean = false,
    val confirmed: Boolean = false,
    val isLeader: Boolean = false,
    val current: Int = 0,
    val limit: Int = 0,
    val selfName: String = "",
    val selfAvatar: String = "",
) {
    val full: Boolean get() = limit > 0 && current >= limit

    /**
     * 官方页每条领取请求都会带的 `user_attach`（昵称头像的字符串化 JSON）。
     *
     * 从队伍成员里的 `self` 取，和页面 `userInfo` 是同一份；拿不到就留空，
     * 不编一个假昵称。
     */
    val userAttach: String
        get() = if (selfName.isBlank() && selfAvatar.isBlank()) {
            ""
        } else {
            buildJsonObject {
                put("nickName", selfName)
                put("avatar", selfAvatar)
                put("avatarUrl", selfAvatar)
            }.toString()
        }
}

/** 活动账号绑定的游戏角色。领取请求要原样回传。 */
data class SupercoreRole(
    val gameOpenId: String = "",
    val gameAppId: String = "",
    val roleId: String = "",
    val roleName: String = "",
    val partitionName: String = "",
    val areaId: Int = 0,
    val platId: Int = 0,
    val partitionId: Int = 0,
    val flag: Int = 0,
) {
    /** 多角色账号用它做稳定 key（base64 原串，不走显示名）。 */
    val key: String get() = "$gameOpenId|$partitionId|$roleId"

    val displayName: String get() = decodeRoleText(roleName).ifBlank {
        if (roleId.isBlank()) "当前角色" else "角色 ${roleId.takeLast(4)}"
    }

    val displayPartition: String get() = decodeRoleText(partitionName)
}

/**
 * 超核活动页的 WPE 网关（`bgw.xinyue.qq.com/amp2.WPESrv/WPEIndex`）。
 *
 * 协议是「一条 POST 一种 flow」：`?flowId=<flow>&actId=<活动号>`，body 里带
 * `biz_id / act_id / flow_id / role / gid / data`。`data` 本身又是一段**字符串化的 JSON**
 * （页面配置从这里取 `ceiba_plat_id` / `c_frontend_context`）。
 *
 * 返回值同样是两层：外层 `{ret,msg,data}`，`data` 是字符串；里面还有一层
 * `{ret,msg,...}`。**业务失败用内层 ret 表达**（活动没开是 40006），
 * 只看外层 ret 会把"没开"当成成功。
 */
class SupercoreApi(
    private val json: Json = IdeJson,
    private val http: HttpClient = createHttpClient(),
) {
    /** 抓活动页 HTML / app.js。和 WPE 不同，这里要带浏览器 UA，否则 CDN 回 567。 */
    suspend fun fetchText(url: String): String {
        val response = try {
            http.get(url) {
                header("User-Agent", BROWSER_USER_AGENT)
                header("Referer", "https://act.supercore.qq.com/")
                header("Accept", "text/html,application/javascript,*/*")
                header("Accept-Language", "zh-CN,zh;q=0.9")
            }
        } catch (t: Throwable) {
            throw SupercoreRequestException("拉活动页失败：${t.message}", t)
        }
        if (response.status.value !in 200..299) {
            throw SupercoreActivityException("活动页返回 HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }

    /**
     * 读一期活动的配置，解析出 flow 计划。
     *
     * 页面结构：`index.html` → `js/app.<hash>.js` → 里面那段 `JSON.parse('…')`。
     * 三步任何一步认不出来都报 [SupercoreActivityException]，上层会保留旧的计划。
     */
    suspend fun loadPlan(url: String, nowSec: Long): SupercorePlan {
        val base = supercoreBaseUrl(url)
        val html = fetchText(base + "index.html")
        val script = supercoreAppScript(html)
            ?: throw SupercoreActivityException("活动页里没找到 app.js")
        val jsUrl = if (script.startsWith("http", true)) script else base + script.trimStart('/')
        val js = fetchText(jsUrl)
        val config = extractSupercoreConfig(js)
            ?: throw SupercoreActivityException("活动页配置认不出来，可能官方换版了")
        val plan = supercorePlanFromConfig(url, config, nowSec)
            ?: throw SupercoreActivityException("活动配置里没有活动号")
        if (plan.lotteryFlow == null) {
            throw SupercoreActivityException("认不出这一期的抽奖按钮")
        }
        return plan
    }

    /** 所有 flow 的 remain/total。 */
    suspend fun holds(credential: SupercoreCredential, plan: SupercorePlan): Map<Int, SupercoreHold> {
        val flow = plan.holdsFlow ?: return emptyMap()
        val data = runFlow(credential, plan, flow, role = null)
        return holdsOf(data)
    }

    /**
     * 「当前积分」。
     *
     * 抓包里这条带的是**完整角色**（不像 holdList 那条是空角色），照着带：
     * 积分是按角色算的还是按账号算的没写在文档里，原样回传最稳。
     */
    suspend fun points(
        credential: SupercoreCredential,
        plan: SupercorePlan,
        role: SupercoreRole? = null,
    ): SupercorePoints {
        val flow = plan.pointsFlow ?: return SupercorePoints(null, null)
        val data = runFlow(credential, plan, flow, role)
        return pointsOf(data)
    }

    /**
     * 跑一个 flow。领奖 / 签到 / 抽奖都走它，区别只在 flow id 和 role。
     *
     * @return 内层 `data` 对象（已从字符串解开）。
     */
    suspend fun runFlow(
        credential: SupercoreCredential,
        plan: SupercorePlan,
        flow: Int,
        role: SupercoreRole?,
        /** 只有队伍信息 flow 需要带；普通领取带 `group_team` 反而可能被当成组队操作。 */
        teamInvite: String? = null,
        /** 官方页每条领取都带的昵称头像 JSON（从队伍成员的 self 取）。 */
        userAttach: String = "",
    ): JsonObject {
        val body = buildJsonObject {
            put("biz_id", plan.bizId)
            put("act_id", plan.activityId)
            put("flow_id", flow)
            putJsonObject("role") { fillRole(role) }
            put("gid", plan.gid)
            put("data", pageData(plan, teamInvite, userAttach))
        }
        return post(credential, "$WPE_PATH?flowId=$flow&actId=${plan.activityId}", body)
    }

    /**
     * 队伍信息（`queryFlowIdInviteInfo`）。
     *
     * 活动规则写明「队长确认成团后才开启后续任务」；没成团时服务端对领取**只回一句
     * "网络繁忙"**，看不出是队伍的问题。所以每次领取前先读它。
     */
    suspend fun team(
        credential: SupercoreCredential,
        plan: SupercorePlan,
        role: SupercoreRole?,
    ): SupercoreTeam? {
        val flow = plan.teamInfoFlow ?: return null
        val data = runFlow(credential, plan, flow, role, teamInvite = plan.inviteCode)
        // 这一层是 `{ret,msg,status,data}`，队伍对象在 `data` 里
        val team = data["data"] as? JsonObject ?: return null
        val members = team["memberList"] as? JsonArray
        val self = members?.firstOrNull { (it as? JsonObject)?.int("self") == 1 } as? JsonObject
        val isLeader = self?.int("leader") == 1
        return SupercoreTeam(
            inTeam = (team.int("inTeam") ?: 0) != 0,
            confirmed = (team.int("confirm") ?: 0) != 0,
            isLeader = isLeader,
            current = team.int("current_mate") ?: 0,
            limit = team.int("limit_mate") ?: 0,
            selfName = self?.string("name").orEmpty(),
            selfAvatar = self?.string("avatar").orEmpty(),
        )
    }

    /** 队长确认成团。**不可逆**：确认后成员锁定，不能退出或更换。 */
    suspend fun confirmTeam(
        credential: SupercoreCredential,
        plan: SupercorePlan,
        role: SupercoreRole?,
    ) {
        val flow = plan.confirmTeamFlow
            ?: throw SupercoreActivityException("这一期没有确认成团的接口")
        val data = runFlow(credential, plan, flow, role)
        // 确认成团的业务失败走内层 `status`（页面也是先判 status 再判 ret）
        val status = data.int("status") ?: 0
        if (status != 0) {
            throw SupercoreApiException(status, data.string("msg"))
        }
    }

    /**
     * 官方「领奖记录」。
     *
     * 页面 `c-supercore-user-area-flatten` 的「领奖记录」就是这条接口：
     * `ceiba.mrms.CeibaMrmsSrv/GetCeibaGiftRecord`，参数只有
     * `game_code / activity_id / business_id / page_size / page_index`。
     * 返回 `{ret,msg,records:[{gift_name,gift_time,package_real_flag,...}],total}`。
     *
     * 用它替掉本地抽奖记录：换设备、网页领的、以前领的都能看到同一份。
     */
    suspend fun giftRecords(
        credential: SupercoreCredential,
        plan: SupercorePlan,
        pageIndex: Int = 1,
        pageSize: Int = 50,
    ): SupercoreGiftRecordPage {
        val body = buildJsonObject {
            put("game_code", plan.gameCode)
            put("activity_id", plan.activityId.toString())
            put("business_id", plan.bizId)
            put("page_size", pageSize)
            put("page_index", pageIndex)
        }
        val root = postRoot(credential, GIFT_RECORD_PATH, body)
        val records = giftRecordsOf(root)
        return SupercoreGiftRecordPage(
            records = records,
            total = root.int("total") ?: records.size,
        )
    }

    /** 活动账号绑定的角色。多区服会并发几次查询，合并去重。 */
    suspend fun roles(credential: SupercoreCredential, plan: SupercorePlan): List<SupercoreRole> {
        if (plan.gameCode.isBlank()) return emptyList()
        val areaBody = buildJsonObject {
            put("game_code", plan.gameCode)
            plan.gid.toIntOrNull()?.let { put("gid", it) }
            put("device", "android")
            put("area", "qq")
        }
        val areas = postRoot(credential, AREA_CHANNEL_PATH, areaBody)
        val ids = (areas["list"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.int("id") }
            .orEmpty()
            .ifEmpty { listOf(1) }

        val out = LinkedHashMap<String, SupercoreRole>()
        for (id in ids.take(MAX_AREA_CHANNELS)) {
            val body = buildJsonObject {
                put("game_code", plan.gameCode)
                put("partition_id", id)
                put("area_channel_id", id.toString())
                put("device", "android")
            }
            val response = runCatching { postRoot(credential, ROLE_LIST_PATH, body) }.getOrNull()
                ?: continue
            for (value in (response["roles"] as? JsonArray).orEmpty()) {
                val obj = value as? JsonObject ?: continue
                val role = SupercoreRole(
                    gameOpenId = obj.string("game_open_id").orEmpty(),
                    gameAppId = obj.string("game_app_id").orEmpty(),
                    roleId = obj.string("role_id").orEmpty(),
                    roleName = obj.string("role_name").orEmpty(),
                    partitionName = obj.string("partition_name").orEmpty(),
                    areaId = obj.int("area_id") ?: 0,
                    platId = obj.int("plat_id") ?: 0,
                    partitionId = obj.int("partition_id") ?: 0,
                    flag = obj.int("flag") ?: 0,
                )
                if (role.roleId.isBlank() && role.gameOpenId.isBlank()) continue
                out[role.key] = role
            }
        }
        return out.values.toList()
    }

    private fun JsonObjectBuilder.fillRole(role: SupercoreRole?) {
        put("game_open_id", role?.gameOpenId.orEmpty())
        put("game_app_id", role?.gameAppId.orEmpty())
        put("area_id", role?.areaId ?: 0)
        put("plat_id", role?.platId ?: 0)
        put("partition_id", role?.partitionId ?: 0)
        put("partition_name", role?.partitionName.orEmpty())
        put("role_id", role?.roleId.orEmpty())
        put("role_name", role?.roleName.orEmpty())
        put("device", "android")
        put("flag", role?.flag ?: 0)
    }

    /**
     * `data` 那段字符串。页面原样带的是 `ceiba_plat_id / gid / c_frontend_context / cExtData`，
     * 外加一个 `user_attach`（昵称头像）。
     *
     * 我们**不带 `user_attach`**：这套功能不做组队，昵称头像只用于页面上的队伍展示；
     * 任务 / 签到 / 抽奖都不读它。少这一段也就少一个把 QQ 昵称写进本地存储的理由。
     */
    private fun pageData(
        plan: SupercorePlan,
        teamInvite: String? = null,
        userAttach: String = "",
    ): String =
        buildJsonObject {
            put("ceiba_plat_id", "android")
            put("gid", plan.gid)
            put("c_bind_club_id", "")
            // 官方页永远带这一段；缺了它领奖时服务端可能只回一句"网络繁忙"
            put("user_attach", userAttach)
            // 页面从 URL 的 via / via_source 取，原样带上；活动方按来源做统计
            put("c_frontend_context", buildJsonObject {
                put("via", plan.via)
                put("via_source", plan.viaSource)
            }.toString())
            if (!teamInvite.isNullOrBlank()) {
                putJsonObject("group_team") { put("invite_code", teamInvite) }
            }
            putJsonObject("cExtData") {}
        }.toString()

    private suspend fun post(
        credential: SupercoreCredential,
        path: String,
        body: JsonObject,
    ): JsonObject = parseSupercoreEnvelope(postText(credential, path, body), json)

    /**
     * RoleSrv 的响应是 `{ret,msg,roles}` / `{ret,msg,list}`，**没有 `data` 字段**。
     *
     * 之前误用 [post]（它按 WPE 的两层信封解），`data` 缺失时直接返回空对象，
     * 结果角色列表永远是空的 —— 领取请求的 `role` 是一堆空字符串。
     */
    private suspend fun postRoot(
        credential: SupercoreCredential,
        path: String,
        body: JsonObject,
    ): JsonObject = parseSupercoreRoot(postText(credential, path, body), json)

    private suspend fun postText(
        credential: SupercoreCredential,
        path: String,
        body: JsonObject,
    ): String {
        return try {
            http.post("$BASE${path}") {
                contentType(ContentType.Application.Json)
                header("Accept", "application/json, text/plain, */*")
                header("T-OPENID", credential.openId)
                header("T-ACCESS-TOKEN", credential.accessToken)
                header("T-APPID", credential.appId)
                header("T-ACCOUNT-TYPE", "qc")
                header("T-DEVICE-TYPE", "android")
                header("T-MODE", "true")
                header("T-ACT-ENV", "production")
                header("X-Request-Source", "ceiba")
                header("Origin", "https://act.supercore.qq.com")
                header("Referer", "https://act.supercore.qq.com/")
                header("X-Requested-With", "com.tencent.tim")
                header("User-Agent", WPE_USER_AGENT)
                setBody(json.encodeToString(JsonObject.serializer(), body))
            }.bodyAsText()
        } catch (t: Throwable) {
            throw SupercoreRequestException("请求 ${path.substringBefore('?')} 失败：${t.message}", t)
        }
    }

    companion object {
        private const val BASE = "https://bgw.xinyue.qq.com"
        private const val WPE_PATH = "/amp2.WPESrv/WPEIndex"
        private const val ROLE_LIST_PATH = "/amp2.RoleSrv/GetSpecifyRoleList"
        private const val AREA_CHANNEL_PATH = "/amp2.RoleSrv/GetAreaChannelList"
        private const val GIFT_RECORD_PATH = "/ceiba.mrms.CeibaMrmsSrv/GetCeibaGiftRecord"

        /** 最多查几个区服。正常账号只有 1 个（`GetAreaChannelList` 只回一条）。 */
        private const val MAX_AREA_CHANNELS = 4

        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; 25102RKBEC Build/BP2A.250605.031.A3) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

        /** 抓包原样：QQ 内嵌 webview 的 UA（WPE 网关会看）。 */
        private const val WPE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; 25102RKBEC Build/BP2A.250605.031.A3; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.7339.207 " +
                "Mobile Safari/537.36 V1_AND_SQ_9.0.95_0_RDM_B QQ/9.0.95.4050 MIT/4.1.0 " +
                "MIT/AND_4.1.0 NetType/WIFI WebP/0.4.1 AppId/537298352 Pixel/1200 " +
                "StatusBarHeight/144 SimpleUISwitch/1 QQTheme/2971 StudyMode/0 " +
                "CurrentMode/1 CurrentFontScale/1.0 GlobalDensityScale/1.0 AllowLandscape/false InMagicWin/0"

        fun createHttpClient(serializationJson: Json = IdeJson): HttpClient = HttpClient {
            expectSuccess = false
            install(ContentNegotiation) { json(serializationJson) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 25_000
            }
            install(Logging) { level = LogLevel.NONE }
        }
    }
}

// ---------------- 响应解析 ----------------

/**
 * 解开两层信封，返回内层 `data`。
 *
 * 三种失败分开：
 * - 外层 `ret != 0`：网关/登录态问题；
 * - `data` 不是合法 JSON：结构变了；
 * - 内层 `ret != 0`：业务拒绝（没开活动 40006、积分不足…）。
 */
internal fun parseSupercoreEnvelope(text: String, json: Json = IdeJson): JsonObject {
    val root = parseSupercoreRoot(text, json)
    val dataValue = root["data"] ?: return JsonObject(emptyMap())
    val data: JsonObject = when (dataValue) {
        is JsonObject -> dataValue
        is JsonPrimitive -> {
            val inner = dataValue.content
            if (inner.isBlank()) return JsonObject(emptyMap())
            val parsed = runCatching { json.parseToJsonElement(inner) }.getOrNull()
                ?: throw SupercoreProtocolException("活动接口的 data 不是 JSON")
            parsed as? JsonObject
                ?: throw SupercoreProtocolException("活动接口的 data 不是对象")
        }
        else -> throw SupercoreProtocolException("活动接口的 data 类型不认识")
    }

    val innerRet = data.int("ret") ?: 0
    if (innerRet != 0) {
        throw SupercoreApiException(innerRet, data.string("msg") ?: root.string("msg"))
    }
    return data
}

/**
 * 只解一层：用于 RoleSrv 这种 `{ret,msg,roles}` 的平铺响应。
 *
 * WPE 的 `data` 是字符串化的第二层，只有 [parseSupercoreEnvelope] 需要。
 */
internal fun parseSupercoreRoot(text: String, json: Json = IdeJson): JsonObject {
    val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        ?: throw SupercoreProtocolException("活动接口返回的不是 JSON")
    val ret = root.int("ret") ?: 0
    if (ret != 0) {
        throw SupercoreApiException(ret, root.string("msg"))
    }
    return root
}

/** `GetCeibaGiftRecord` 的 `records` → 列表。 */
internal fun giftRecordsOf(root: JsonObject): List<SupercoreGiftRecord> =
    (root["records"] as? JsonArray).orEmpty().mapNotNull { value ->
        val obj = value as? JsonObject ?: return@mapNotNull null
        val name = obj.string("gift_name").orEmpty().trim()
        val time = obj.string("gift_time").orEmpty().trim()
        if (name.isBlank() && time.isBlank()) return@mapNotNull null
        SupercoreGiftRecord(
            giftName = name.ifBlank { "奖励" },
            giftTime = time,
            realGift = (obj.string("package_real_flag") ?: "0") != "0",
        )
    }

/** `holdList` → flowId / 剩余。 */
internal fun holdsOf(data: JsonObject): Map<Int, SupercoreHold> {
    val list = data["holdList"] as? JsonObject ?: return emptyMap()
    val out = LinkedHashMap<Int, SupercoreHold>()
    for ((key, value) in list) {
        val flow = key.toIntOrNull() ?: continue
        val obj = value as? JsonObject ?: continue
        out[flow] = SupercoreHold(
            remain = obj.int("remain") ?: 0,
            total = obj.int("total") ?: 0,
        )
    }
    return out
}

/** `{ret,remain,total}` → 积分。 */
internal fun pointsOf(data: JsonObject): SupercorePoints =
    SupercorePoints(remain = data.int("remain"), total = data.int("total"))

/**
 * 领奖响应里的奖励文案。
 *
 * 页面那边取的是 `data.msg.replace("恭喜您获得了礼包： ", "")`；抽奖也一样走这个字段。
 * 没给 msg 时依次退到 `gift_name / package_name`，都没有就算"抽奖成功"，
 * 不编一个不存在的奖励名。
 */
internal fun rewardTextOf(data: JsonObject): String {
    val msg = data.string("msg").orEmpty()
        .replace("恭喜您获得了礼包：", "")
        .replace("恭喜您获得了礼包:", "")
        // 官方那句"请注意：游戏虚拟道具奖品将会在24小时内到账"不展示
        .replace(Regex("""\s*[,，]\s*请注意[:：].*$"""), "")
        .trim()
    if (msg.isNotBlank()) return msg
    for (key in listOf("gift_name", "package_name", "prize_name", "name")) {
        val value = data.string(key).orEmpty().trim()
        if (value.isNotBlank()) return value
    }
    return "抽奖成功"
}

/** 网络层挂了。 */
class SupercoreRequestException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** 活动页 / 配置认不出来。 */
class SupercoreActivityException(message: String) : Exception(message)

/** 接口结构变了。 */
class SupercoreProtocolException(message: String) : Exception(message)

/** 业务拒绝（外层或内层 ret != 0）。 */
class SupercoreApiException(val ret: Int, val msg: String?) :
    Exception("活动返回 ret=$ret${msg?.let { "，$it" } ?: ""}") {

    /** 活动还没到开始时间。 */
    val notStarted: Boolean
        get() {
            val text = msg.orEmpty()
            return text.contains("开启") || text.contains("未开始") ||
                (text.contains("活动") && text.contains("开始"))
        }

    /** 今天这件事已经办过了（签到 / 领取）。 */
    val alreadyDone: Boolean
        get() {
            val text = msg.orEmpty()
            return listOf("已领取", "已签到", "已经领", "重复", "明日", "明天")
                .any { text.contains(it) }
        }

    /** 登录态 / appid 校验失败，要重新抓凭证。 */
    val loginExpired: Boolean
        get() {
            val text = msg.orEmpty()
            return ret == 7001 || ret == 10_001 || ret == 10_002 ||
                text.contains("登录") || text.contains("token", ignoreCase = true) ||
                text.contains("校验不通过")
        }
}
