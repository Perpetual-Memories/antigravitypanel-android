package com.nzd.antigravitypanel.data.supercore

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs
import kotlin.math.max

/** 超核活动页的域名。活动页都挂在它下面，`/supercore/act/<hash>/index.html`。 */
const val SUPERCORE_ACTIVITY_HOST = "act.supercore.qq.com"

/** 5 积分抽一次。规则写在活动页图片里，配置里没有这个数，先用本期口径。 */
const val SUPERCORE_DRAW_COST = 5

/**
 * 计划缓存的格式版本。
 *
 * 加队伍相关 flow（`teamInfoFlow` / `confirmTeamFlow`）时升到 2：
 * 旧缓存解出来这两个字段是 null，会让队伍检查整个跳过 → 又回到"网络繁忙"。
 */
const val SUPERCORE_PLAN_VERSION = 2

/**
 * 从活动页配置里认出来的一张「领奖按钮」。
 *
 * 位置不是给人看的，是拿来**分类**的：这一套活动页是模板生成的，
 * 每日签到是 3×n 的等尺寸小按钮、任务领取是更窄的两颗、抽奖是唯一一颗大方块。
 * 比按钮名字可靠 —— 名字全是 `领奖按钮BPaJ2` 这种自动生成的串。
 */
data class SupercoreButton(
    val flowId: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val area: Int get() = width * height
}

/**
 * 一期活动的「flow 计划」。全部来自活动页配置，**不写死活动号**，
 * 所以用户换一期网址时不用等 app 更新。
 */
@Serializable
data class SupercorePlan(
    val url: String = "",
    val activityId: Int = 0,
    val bizId: String = "supercore",
    val gid: String = "",
    val gameCode: String = "",
    val title: String = "",
    /** 页面自带的 `quaFlowId`，返回所有 flow 的 remain/total（`holdList`）。 */
    val holdsFlow: Int? = null,
    /** `c-text-dynamic` 那个「当前积分」的 flow。 */
    val pointsFlow: Int? = null,
    /** 抽奖按钮。 */
    val lotteryFlow: Int? = null,
    /** 「今日登录游戏」「每日完成任意模式游戏」这类积分任务的领取 flow。 */
    val taskFlows: List<Int> = emptyList(),
    /** 每日签到的 9 颗（按 1..n 天顺序）。 */
    val signFlows: List<Int> = emptyList(),
    val drawCost: Int = SUPERCORE_DRAW_COST,
    /**
     * 队伍信息 flow（`queryFlowIdInviteInfo`）。返回 `confirm` / `current_mate` / `limit_mate`，
     * 用来判断"成团了没有"——没成团时服务端对领取只回一句"网络繁忙"，看不出原因。
     */
    val teamInfoFlow: Int? = null,
    /** 队长确认成团的 flow（`queryFlowIdConfirmTeam`）。 */
    val confirmTeamFlow: Int? = null,
    /** 活动网址里的 `team_inv_code`，队伍信息 flow 要带。 */
    val inviteCode: String = "",
    /** 活动网址里的 `via` / `via_source`，原样塞进 `c_frontend_context`。 */
    val via: String = "",
    val viaSource: String = "",
    val planVersion: Int = 0,
    val fetchedAtSec: Long = 0,
) {
    /** 够不够跑起来。缺抽奖按钮就没法自动抽，宁可报错也别瞎点。 */
    val ready: Boolean get() = activityId > 0 && lotteryFlow != null
}

/**
 * 规整用户粘进来的活动网址。
 *
 * 只做最低限度的校验：域名对、路径里有 `/supercore/act/`。
 * **不要求带 `team_inv_code`** —— 我们只领任务/签到/抽奖，不碰组队，
 * 邀请码对我们的请求没有意义；用户从分享链接里粘过来会自然带着，留着也无妨。
 */
fun normalizeSupercoreUrl(input: String): String? {
    var text = input.trim()
    if (text.isEmpty()) return null
    if (!text.startsWith("http://", true) && !text.startsWith("https://", true)) {
        text = "https://$text"
    }
    val host = text.substringAfter("://", "").substringBefore('/').substringBefore('?')
    if (!host.equals(SUPERCORE_ACTIVITY_HOST, ignoreCase = true)) return null
    if (!text.contains("/supercore/act/")) return null
    return text
}

/** 活动页所在目录，末尾带 `/`。抓 `index.html` 和 `js/app.*.js` 都相对它。 */
fun supercoreBaseUrl(url: String): String {
    val noQuery = url.substringBefore('?').substringBefore('#')
    val slash = noQuery.lastIndexOf('/')
    return if (slash >= 0) noQuery.substring(0, slash + 1) else "$noQuery/"
}

/** 从活动页 HTML 里找构建产物 `js/app.<hash>.js`。 */
fun supercoreAppScript(html: String): String? {
    val patterns = listOf(
        Regex("""src\s*=\s*["']?([^"'\s>]*?js/app\.[^"'\s>]*?\.js)"""),
        Regex("""src\s*=\s*["']?([^"'\s>]*?app\.[^"'\s>]*?\.js)"""),
        // 没带 hash 的老构建（`js/app.js`）
        Regex("""src\s*=\s*["']?([^"'\s>]*?js/app\.js)"""),
    )
    for (re in patterns) {
        val found = re.find(html)?.groupValues?.getOrNull(1)?.trim()
        if (!found.isNullOrBlank()) return found
    }
    return null
}

/**
 * 把活动 JS 里那份 `e.exports=JSON.parse('…')` 抠出来。
 *
 * 那份配置是**单引号 JS 字符串包着的 JSON**（不是 JSONP，也不是 base64），
 * 所以只需要按 JS 的转义规则解一遍再交给 JSON 解析。官方换个打包器就可能变形，
 * 解不出来时返回 null，让上层报"这一期认不出来"，而不是崩掉。
 */
fun extractSupercoreConfig(js: String): JsonObject? {
    val marker = "JSON.parse("
    var from = 0
    while (true) {
        val at = js.indexOf(marker, from)
        if (at < 0) return null
        from = at + marker.length
        var i = from
        while (i < js.length && js[i].isWhitespace()) i++
        if (i >= js.length) continue
        val quote = js[i]
        if (quote != '\'' && quote != '"') continue
        i++
        val out = StringBuilder()
        while (i < js.length) {
            val c = js[i]
            if (c == '\\') {
                val next = js.getOrNull(i + 1) ?: return null
                when (next) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'u' -> {
                        val hex = js.substring(i + 2, minOf(i + 6, js.length))
                        val code = hex.toIntOrNull(16)
                        if (code != null) out.append(code.toChar()) else out.append(next)
                        i += 4
                    }
                    else -> out.append(next)
                }
                i += 2
                continue
            }
            if (c == quote) {
                return runCatching { Json.parseToJsonElement(out.toString()) as? JsonObject }
                    .getOrNull()
            }
            out.append(c)
            i++
        }
    }
}

/**
 * 配置 → flow 计划。
 *
 * 分类规则（对着 31115 这期真配置校过）：
 * - 数量最多、等尺寸、尺寸在 150..320 × 24..90 的那组 = 每日签到（本期 9 颗，204×47）；
 * - 在签到组上方、数量 2..4 的同尺寸小组 = 积分任务（本期 2 颗，176×47）；
 * - 剩下的、近似正方的大按钮 = 抽奖（本期 213×214）；
 * - `c-text-dynamic` 里 targetText 含 `{{remain}}` 的 = 积分。
 *
 * 认不出签到组时**不抛**：签到 flow 留空，其余照跑。宁可少领一块，
 * 也别让一次模板改动把整个功能打哑。
 */
fun supercorePlanFromConfig(
    url: String,
    config: JsonObject,
    fetchedAtSec: Long = 0,
): SupercorePlan? {
    val activityId = config.int("activity_id") ?: return null
    val pages = config["config"]?.jsonObject?.get("configs")?.jsonObject ?: return null

    var holdsFlow: Int? = null
    var pointsFlow: Int? = null
    var teamInfoFlow: Int? = null
    var confirmTeamFlow: Int? = null
    val buttons = ArrayList<SupercoreButton>()

    for ((_, pageValue) in pages) {
        val page = pageValue as? JsonObject ?: continue
        val canvas = page["canvasConfig"] as? JsonObject
        if (holdsFlow == null) {
            holdsFlow = canvas?.int("quaFlowId")?.takeIf { it > 0 }
        }

        val components = page["componentConfig"] as? JsonArray ?: continue
        for (componentValue in components) {
            val component = componentValue as? JsonObject ?: continue
            val type = component.string("component").orEmpty()
            val data = component["configurationData"] as? JsonObject ?: continue
            val flow = data.int("flowID")?.takeIf { it > 0 } ?: 0

            when (type) {
                "c-button-reward" -> if (flow > 0) {
                    val style = (component["styles"] as? JsonObject)
                        ?.get("base") as? JsonObject
                    buttons += SupercoreButton(
                        flowId = flow,
                        x = style.px("left"),
                        y = style.px("top"),
                        width = style.px("width"),
                        height = style.px("height"),
                    )
                }

                "c-text-dynamic" -> if (flow > 0 && pointsFlow == null) {
                    val target = data.string("targetText").orEmpty()
                    val prepend = data.string("prependText").orEmpty()
                    if (target.contains("remain") || prepend.contains("积分")) pointsFlow = flow
                }

                // 组队组件不挂 flowID，它的 flow 全在 configurationData 里
                "c-group-team-new" -> {
                    if (teamInfoFlow == null) {
                        teamInfoFlow = data.int("queryFlowIdInviteInfo")
                            ?: data.int("queryFlowIdTeamInfo")
                    }
                    if (confirmTeamFlow == null) {
                        confirmTeamFlow = data.int("queryFlowIdConfirmTeam")
                    }
                }
            }
        }
    }

    val groups = buttons.groupBy { it.width to it.height }.values
    val signGroup = groups
        .filter { it.size >= 5 && it.first().width in 150..320 && it.first().height in 24..90 }
        .maxByOrNull { it.size }
    val sign = signGroup?.sortedWith(compareBy({ it.y }, { it.x })).orEmpty()
    val signTop = signGroup?.minOfOrNull { it.y } ?: Int.MAX_VALUE
    val signIds = sign.map { it.flowId }.toSet()

    val taskGroup = groups
        .filter { it !== signGroup && it.size in 2..4 }
        .filter { it.first().height in 24..90 && it.first().width in 100..320 }
        .filter { it.minOf { button -> button.y } < signTop }
        .maxByOrNull { it.size }
    val tasks = taskGroup?.sortedWith(compareBy({ it.y }, { it.x })).orEmpty()
    val taskIds = tasks.map { it.flowId }.toSet()

    val lottery = buttons
        .filter { it.flowId !in signIds && it.flowId !in taskIds }
        .filter { it.width >= 120 && it.height >= 120 }
        .filter { abs(it.width - it.height) <= max(it.width, it.height) / 4 }
        .maxByOrNull { it.area }
        ?.flowId

    return SupercorePlan(
        url = url,
        activityId = activityId,
        bizId = config.string("ast")?.takeIf { it.isNotBlank() } ?: "supercore",
        gid = config.string("gid").orEmpty(),
        gameCode = config.string("game_code").orEmpty(),
        title = (config["config"]?.jsonObject?.get("configs")?.jsonObject)
            ?.values?.firstOrNull()
            ?.let { page ->
                ((page as? JsonObject)?.get("canvasConfig") as? JsonObject)
                    ?.get("pageMeta")?.let { meta ->
                        (meta as? JsonObject)?.string("title")
                    }
            }
            .orEmpty()
            .ifBlank { config.string("activity_name").orEmpty() },
        holdsFlow = holdsFlow,
        pointsFlow = pointsFlow,
        lotteryFlow = lottery,
        taskFlows = tasks.map { it.flowId },
        signFlows = sign.map { it.flowId },
        teamInfoFlow = teamInfoFlow?.takeIf { it > 0 },
        confirmTeamFlow = confirmTeamFlow?.takeIf { it > 0 },
        inviteCode = supercoreQueryParam(url, "team_inv_code").orEmpty(),
        via = supercoreQueryParam(url, "via").orEmpty(),
        viaSource = supercoreQueryParam(url, "via_source").orEmpty(),
        planVersion = SUPERCORE_PLAN_VERSION,
        fetchedAtSec = fetchedAtSec,
    )
}

/**
 * 取 URL 查询参数。只处理普通百分号编码——活动链接里这几个参数（`team_inv_code` /
 * `via` / `via_source`）都是字母数字，但别的地方可能带中文，顺手解一下。
 */
internal fun supercoreQueryParam(url: String, key: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isBlank()) return null
    for (part in query.split('&')) {
        val eq = part.indexOf('=')
        if (eq <= 0) continue
        if (part.substring(0, eq) != key) continue
        return decodeQueryValue(part.substring(eq + 1))
    }
    return null
}

private fun decodeQueryValue(text: String): String {
    if (!text.contains('%')) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '%' && i + 2 < text.length) {
            val code = text.substring(i + 1, i + 3).toIntOrNull(16)
            if (code != null) {
                out.append(code.toChar())
                i += 3
                continue
            }
        }
        out.append(if (c == '+') ' ' else c)
        i++
    }
    return out.toString()
}

/** 读 JSON 里的字符串/数字，统一成 String。`gid` 有时候是字符串、有时候是数字。 */
internal fun JsonObject.string(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.contentOrNull
}

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

/**
 * 样式里的 `3355px` / `3355` / `3355.5px` 统一成像素整数。
 *
 * ⚠️ 必须自己去掉 `px`：`JsonPrimitive.intOrNull` 只认纯数字，
 * 对 `"213px"` 返回 null —— 早先漏了这一步，所有按钮宽高都成了 0，
 * 分类全塌成一组（单测里 `lotteryFlow` 直接 null）。
 */
private fun JsonObject?.px(key: String): Int {
    val raw = this?.get(key) as? JsonPrimitive ?: return 0
    val text = raw.content.trim().removeSuffix("px").removeSuffix("PX").trim()
    return text.toDoubleOrNull()?.toInt() ?: 0
}
