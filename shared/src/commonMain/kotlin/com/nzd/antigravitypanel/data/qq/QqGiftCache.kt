package com.nzd.antigravitypanel.data.qq

import com.nzd.antigravitypanel.util.currentEpochSeconds
import com.nzd.antigravitypanel.util.serverDateKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 周签到状态的落盘缓存。理由和小程序签到那份一样：概览不该先空一拍。
 *
 * [QqWeeklySignIn.canClaim] 不落盘——它是"现在能不能领"，缓存里那份
 * 冷启动时拿出来只会误导（昨天显示可领，今天可能已经领过了）。
 */
@Serializable
data class QqGiftCache(
    val day: Int = 0,
    val totalDays: Int = 0,
    val todayReward: String = "",
    val nextReward: String = "",
    val fullWeekBonus: String = "",
    val roleName: String = "",
    val savedAtSec: Long = 0,
)

object QqGiftCacheCodec {
    private val Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun encode(cache: QqGiftCache): String = Json.encodeToString(cache)

    fun decode(raw: String): QqGiftCache? =
        runCatching { Json.decodeFromString<QqGiftCache>(raw) }.getOrNull()
}

fun QqWeeklySignIn.toCache(nowSec: Long): QqGiftCache = QqGiftCache(
    day = day,
    totalDays = totalDays,
    todayReward = todayReward,
    nextReward = nextReward,
    fullWeekBonus = fullWeekBonus,
    roleName = roleName,
    savedAtSec = nowSec,
)

/**
 * 缓存 → 展示模型。`canClaim` 恒为 false：没有拉到新数据之前不声称"可以领"。
 *
 * @param nowSec 用来判跨天。缓存自己带着 [QqGiftCache.savedAtSec]，
 *   存的那天和今天不是同一天就把 [QqWeeklySignIn.stale] 置起来——
 *   周签到是按周走的，隔天的旧数字不能当今天的结论用。
 *   默认取当前时间，调用方一般不用传。
 */
fun QqGiftCache.toStatus(nowSec: Long = currentEpochSeconds()): QqWeeklySignIn = QqWeeklySignIn(
    day = day,
    totalDays = totalDays,
    canClaim = false,
    todayReward = todayReward,
    nextReward = nextReward,
    fullWeekBonus = fullWeekBonus,
    roleName = roleName,
    stale = savedAtSec > 0 && serverDateKey(savedAtSec) != serverDateKey(nowSec),
)
