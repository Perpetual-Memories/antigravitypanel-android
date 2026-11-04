package com.nzd.antigravitypanel.data.supercore

import com.nzd.antigravitypanel.util.currentEpochSeconds
import com.nzd.antigravitypanel.util.serverDateKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 超核活动此刻的状态。
 *
 * 和另三块签到一样，**只放能从服务端确认的数字**：
 * - [points] 是「当前积分」，抽一次扣 5；
 * - [signClaimed] / [signTotal] 是 9 天签到里已领 / 总共；
 * - [drawsRemain] 是抽奖还能抽几次（服务端按 27 次封顶）；
 * - [signedToday] 是**本机确认过**的"今天签过到"——服务端没有单独的签到 flow，
 *   签到就是领当天的奖励，所以这个结论只能由"今天这次领取成功"推出来。
 */
data class SupercoreStatus(
    val points: Int? = null,
    val pointsTotal: Int? = null,
    val signClaimed: Int = 0,
    val signTotal: Int = 0,
    val tasksClaimed: Int = 0,
    val tasksTotal: Int = 0,
    val drawsRemain: Int? = null,
    val drawsTotal: Int? = null,
    val signedToday: Boolean = false,
    /** 活动还没到开始时间（服务端 40006）。 */
    val notStarted: Boolean = false,
    /** 服务端那句原文，比如「活动将于11月3日00:00开启」。 */
    val message: String = "",
    /** 缓存不是今天写的。 */
    val stale: Boolean = false,
    val savedAtSec: Long = 0,
) {
    val tasksDone: Boolean get() = tasksTotal == 0 || tasksClaimed >= tasksTotal
}

/** 落盘的今日状态。`signedToday` 只在同一天有效，由 [toStatus] 判掉。 */
@Serializable
data class SupercoreCache(
    val points: Int? = null,
    val pointsTotal: Int? = null,
    val signClaimed: Int = 0,
    val signTotal: Int = 0,
    val tasksClaimed: Int = 0,
    val tasksTotal: Int = 0,
    val drawsRemain: Int? = null,
    val drawsTotal: Int? = null,
    val signedToday: Boolean = false,
    val notStarted: Boolean = false,
    val message: String = "",
    val savedAtSec: Long = 0,
)

object SupercoreCacheCodec {
    private val Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun encode(cache: SupercoreCache): String = Json.encodeToString(cache)

    fun decode(raw: String): SupercoreCache? =
        runCatching { Json.decodeFromString<SupercoreCache>(raw) }.getOrNull()
}

fun SupercoreStatus.toCache(nowSec: Long): SupercoreCache = SupercoreCache(
    points = points,
    pointsTotal = pointsTotal,
    signClaimed = signClaimed,
    signTotal = signTotal,
    tasksClaimed = tasksClaimed,
    tasksTotal = tasksTotal,
    drawsRemain = drawsRemain,
    drawsTotal = drawsTotal,
    signedToday = signedToday,
    notStarted = notStarted,
    message = message,
    savedAtSec = nowSec,
)

/**
 * 缓存 → 展示模型。
 *
 * ⚠️ 跨天只把 [SupercoreStatus.signedToday] 收走，**积分和累计签到天数留着**：
 * 它们是活动期内的累计值，昨天写的今天照样成立。收走"今天已签到"是因为
 * 那是唯一一件按天重置的事。
 */
fun SupercoreCache.toStatus(nowSec: Long = currentEpochSeconds()): SupercoreStatus {
    val stale = savedAtSec > 0 && serverDateKey(savedAtSec) != serverDateKey(nowSec)
    return SupercoreStatus(
        points = points,
        pointsTotal = pointsTotal,
        signClaimed = signClaimed,
        signTotal = signTotal,
        tasksClaimed = tasksClaimed,
        tasksTotal = tasksTotal,
        drawsRemain = drawsRemain,
        drawsTotal = drawsTotal,
        signedToday = signedToday && !stale,
        notStarted = notStarted,
        message = message,
        stale = stale,
        savedAtSec = savedAtSec,
    )
}

/**
 * 官方「领奖记录」里的一条。
 *
 * 数据来自 `GetCeibaGiftRecord`，**不再本地记账**：换设备、在网页领的、
 * 以前领的，都能看到同一份。`giftTime` 直接用服务端的 `yyyy-MM-dd HH:mm:ss`。
 */
data class SupercoreGiftRecord(
    val giftName: String = "",
    val giftTime: String = "",
    val realGift: Boolean = false,
)

/** 一页领奖记录。 */
data class SupercoreGiftRecordPage(
    val records: List<SupercoreGiftRecord> = emptyList(),
    val total: Int = 0,
)
