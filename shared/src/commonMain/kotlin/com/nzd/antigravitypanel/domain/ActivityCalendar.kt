package com.nzd.antigravitypanel.domain

import com.nzd.antigravitypanel.data.remote.IdeJson
import com.nzd.antigravitypanel.util.civilFromDays
import com.nzd.antigravitypanel.util.parseServerTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 一条活动。
 *
 * 字段跟着官方前端的活动日历来：列表里每行渲染的是 `title` / `extParam` / `desc`
 * 三个字段（`extParam` 就是那行"时间区间"文案）。时间我们另外尽力解析一份，
 * 用来排序和判断"进行中 / 即将开始"，解析不出来就原样展示 [extParam]，不丢内容。
 */
@Serializable
data class ActivityEvent(
    val title: String,
    val startSec: Long = 0,
    val endSec: Long = 0,
    val description: String = "",
    /** 官方下发的时间文案，例如 `2026.09.10-2026.09.24`。解析不出时间时直接展示它。 */
    val extParam: String = "",
) {
    /** 已经开始了吗（用于 UI 上显示"进行中"还是"即将开始"）。 */
    fun isOngoing(nowSec: Long): Boolean = startSec > 0 && startSec <= nowSec && nowSec <= endSec

    /** 还没开始。两端时间都拿不到时返回 false，UI 就不会乱标状态。 */
    fun isUpcoming(nowSec: Long): Boolean = startSec > 0 && nowSec < startSec

    /** 展示用时间文案。解析得到日期就用 `09-10 ~ 09-24`，否则原样给出服务端文案。 */
    fun periodText(): String = buildString {
        val start = formatDate(startSec)
        val end = formatDate(endSec)
        when {
            start != null && end != null && end != start -> append(start).append(" ~ ").append(end)
            start != null -> append(start)
            extParam.isNotBlank() -> append(extParam)
        }
    }

    private fun formatDate(sec: Long): String? {
        if (sec <= 0) return null
        // 服务端时间按 UTC+8 解释，这里同样先挪回本地时钟空间再取月日
        val local = sec + 8L * 3600L
        val days = Math.floorDiv(local, 86400L)
        val (_, month, day) = civilFromDays(days)
        return "${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }
}

private const val CALENDAR_KEY = "rilipeizhi"

/**
 * 解析活动日历。
 *
 * 结构是从官方 PC 端（`index-Bakyk83a.js` 的 `fetchCalendar`）扒出来的，不用再猜：
 * ```
 * dist.contents(contentType=rilipeizhi)
 *   → content: "<JSON 字符串>"
 *     → rilipeizhi.data: [ { id, title, extParam, desc }, ... ]
 * ```
 *
 * 接口带了 `filterTime=true`，服务端只回还没结束的活动，所以这里**不做时间过滤**，
 * 顺序也保持服务端给的——它自己就是按开始时间排的。
 *
 * 拿不到这一支时退回 [parseActivitiesLoose]：那份是按 key 名猜的宽松解析，
 * 结构上"能捞到几条算几条"，保底用。
 */
fun parseCalendar(content: String): List<ActivityEvent> {
    val root = runCatching { IdeJson.parseToJsonElement(content) }.getOrNull()
    return parseCalendar(root)
}

/**
 * @param content `dist.contents` 下发的内容节点。可能是对象，也可能是"字符串包着的 JSON"
 *   （运营位的结构没保证，两种情况都见过），这里统一先剥到真正能读的一层。
 */
fun parseCalendar(content: JsonElement?): List<ActivityEvent> {
    val node = content ?: return emptyList()
    if (node is JsonPrimitive) {
        // 不是字符串（比如只是个数字）就没得剥
        if (!node.isString) return emptyList()
        val text = node.content.trim()
        // 只剥一层 JSON，剥完还不是对象就到此为止，避免套娃时死循环
        if (!text.startsWith("{") && !text.startsWith("[")) return emptyList()
        return parseCalendar(text)
    }
    val exact = parseOfficialCalendar(node)
    if (exact.isNotEmpty()) return exact
    return parseActivitiesLoose(node)
}

private fun parseOfficialCalendar(root: JsonElement): List<ActivityEvent> {
    val node = (root as? JsonObject)?.get(CALENDAR_KEY) ?: return emptyList()
    val data = when (node) {
        is JsonObject -> node["data"]
        is JsonArray -> node
        else -> null
    } as? JsonArray ?: return emptyList()

    return data.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val title = obj.text("title") ?: return@mapNotNull null
        val extParam = obj.text("extParam").orEmpty()
        val (start, end) = parsePeriod(extParam)
        ActivityEvent(
            title = title,
            description = obj.text("desc").orEmpty(),
            extParam = extParam,
            startSec = start,
            endSec = end,
        )
    }.distinctBy { it.title to it.startSec }
}

/**
 * 从 `extParam` 里抠出开始 / 结束时间。
 *
 * 这个字段的写法没有样本，见过的形态从"2026.09.10-2026.09.24"到"9月10日-9月24日"
 * 都可能有，所以只做一件事：把里面所有像日期的片段挑出来，能解析就取前两个。
 * 解析不出来返回 (0, 0)，由 [ActivityEvent.periodText] 原样展示服务端文案。
 */
private fun parsePeriod(text: String): Pair<Long, Long> {
    if (text.isBlank()) return 0L to 0L
    val hits = FULL_DATE.findAll(text)
        .mapNotNull { match ->
            val (year, month, day) = match.destructured
            parseServerTime("$year-${month.padStart(2, '0')}-${day.padStart(2, '0')} 00:00:00")
        }
        .toList()
    if (hits.size >= 2) return hits[0] to hits[1]
    if (hits.size == 1) return hits[0] to 0L

    // 只写了月日（"9.10-9.24"）：年份取当前年，跨年的活动会落错年，
    // 但只是排序和"进行中/未开始"的判断会差，展示文案仍然用服务端那份，不影响阅读
    val short = SHORT_DATE.findAll(text)
        .mapNotNull { match ->
            val (month, day) = match.destructured
            parseServerTime("${currentYear()}-${month.padStart(2, '0')}-${day.padStart(2, '0')} 00:00:00")
        }
        .toList()
    if (short.size >= 2) return short[0] to short[1]
    if (short.size == 1) return short[0] to 0L
    return 0L to 0L
}

/** `2026-09-10` / `2026.09.10` / `2026年9月10日` 都能吃下。 */
private val FULL_DATE = Regex("(\\d{4})\\s*[-./年]\\s*(\\d{1,2})\\s*[-./月]\\s*(\\d{1,2})")

/** 只有月日的写法：`9.10` / `09-10`。 */
private val SHORT_DATE = Regex("(\\d{1,2})\\s*[-./月]\\s*(\\d{1,2})")

private fun currentYear(): Int {
    val local = com.nzd.antigravitypanel.util.currentEpochSeconds() + 8L * 3600L
    val days = Math.floorDiv(local, 86400L)
    return civilFromDays(days).first
}

private fun JsonObject.text(key: String): String? {
    val value = this[key] as? JsonPrimitive ?: return null
    val text = value.content.trim()
    return text.takeIf { it.isNotEmpty() && it != "null" }
}

private val TITLE_KEYS = listOf("title", "name", "activityName", "activityTitle", "content")
private val START_KEYS = listOf("startTime", "start", "beginTime", "begin", "startDate", "sTime")
private val END_KEYS = listOf("endTime", "end", "finishTime", "finish", "endDate", "eTime")
private val DESC_KEYS = listOf("description", "desc", "detail", "content", "remark")

/**
 * 兜底的宽松解析：按 key 名在整个 JSON 里捞对象，捞到什么算什么。
 *
 * 只在官方那支结构拿不到时才走这里。宁可"解析不出活动"，也不能因为结构变了让概览页崩掉。
 */
fun parseActivitiesLoose(raw: String): List<ActivityEvent> {
    if (raw.isBlank()) return emptyList()
    val root = runCatching { IdeJson.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
    return parseActivitiesLoose(root)
}

private fun parseActivitiesLoose(root: JsonElement): List<ActivityEvent> =
    collectObjects(root).mapNotNull { toLooseEvent(it) }.distinctBy { it.title to it.startSec }

private fun collectObjects(root: JsonElement): List<JsonObject> {
    val out = ArrayList<JsonObject>()
    fun walk(el: JsonElement, depth: Int) {
        if (depth > 6) return
        when (el) {
            is JsonObject -> {
                out.add(el)
                for (value in el.values) walk(value, depth + 1)
            }

            is JsonArray -> for (item in el) walk(item, depth + 1)
            is JsonPrimitive -> Unit
        }
    }
    walk(root, 0)
    return out
}

private fun toLooseEvent(obj: JsonObject): ActivityEvent? {
    val title = firstText(obj, TITLE_KEYS) ?: return null
    val start = firstTime(obj, START_KEYS) ?: return null
    val end = firstTime(obj, END_KEYS) ?: start
    return ActivityEvent(
        title = title,
        description = firstText(obj, DESC_KEYS).orEmpty(),
        startSec = start,
        endSec = end,
    )
}

private fun firstText(obj: JsonObject, keys: List<String>): String? {
    for (key in keys) {
        val value = obj[key] as? JsonPrimitive ?: continue
        val text = value.content.trim()
        if (text.isNotEmpty() && !text.startsWith("{") && !text.startsWith("[")) return text
    }
    return null
}

private fun firstTime(obj: JsonObject, keys: List<String>): Long? {
    for (key in keys) {
        val value = obj[key] as? JsonPrimitive ?: continue
        val text = value.content.trim()
        if (text.isEmpty()) continue
        // 秒级时间戳（10 位）或毫秒级（13 位）都见过，按位数判断
        text.toLongOrNull()?.let { number ->
            if (text.length >= 12) return number / 1000L
            if (text.length >= 9) return number
        }
        parseServerTime(text)?.let { return it }
    }
    return null
}

/**
 * 过滤掉已经结束的，按开始时间升序。
 *
 * 两端时间都拿不到（[ActivityEvent.endSec] == 0）时一律保留——宁可多显示一条，
 * 也不要因为解析不出时间就把活动整条藏掉。
 */
fun upcomingActivities(events: List<ActivityEvent>, nowSec: Long): List<ActivityEvent> =
    events
        .filter { it.endSec <= 0 || it.endSec >= nowSec }
        .sortedWith(compareBy<ActivityEvent> { it.startSec }.thenBy { it.title })

/**
 * 概览页挑最该看的那几场：**按结束时间升序**，先死的先埋。
 *
 * 和 [upcomingActivities] 的排序不是一回事——那个按开始时间排，是给"完整列表"用的；
 * 概览只放得下三行，用户真正关心的是"哪个快没了"。
 * 拿不到结束时间的排在最后：宁可先展示确定的，也别让一个解析不出来的挤掉前三。
 */
fun soonestActivities(events: List<ActivityEvent>, limit: Int = 3): List<ActivityEvent> =
    events
        .sortedWith(
            compareBy<ActivityEvent> { if (it.endSec <= 0) Long.MAX_VALUE else it.endSec }
                .thenBy { it.title },
        )
        .take(limit)
