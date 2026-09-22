package com.nzd.antigravitypanel.data.signin

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 签到看板的落盘缓存。
 *
 * 理由和概览那份一样：**冷启动不该先空一拍**。签到卡在概览页第一屏，
 * 不缓存的话每次开 app 都会先显示"还没拿到签到状态"再跳成真实数字。
 *
 * 只缓存"看一眼就知道"的那几个数，[SignInStatus.days] 不落盘——
 * 它是列表，冷启动摆回去也未必还有效，而且二级页会重新拉一次。
 *
 * 覆盖策略：只在拉取**成功**时写。拉失败要留着上一份，见 ViewModel。
 */
@Serializable
data class SignInCache(
    val date: String = "",
    val signedToday: Boolean = false,
    val continuousDays: Int = 0,
    val totalDays: Int = 0,
    val totalTarget: Int = 0,
    val monthDays: Int = 0,
    val monthTarget: Int = 0,
    val todayGiftName: String = "",
    val savedAtSec: Long = 0,
)

object SignInCacheCodec {
    // 自己写自己读，只开 ignoreUnknownKeys：以后加字段时老缓存不会把 app 搞崩
    private val Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun encode(cache: SignInCache): String = Json.encodeToString(cache)

    fun decode(raw: String): SignInCache? =
        runCatching { Json.decodeFromString<SignInCache>(raw) }.getOrNull()
}

fun SignInStatus.toCache(nowSec: Long): SignInCache = SignInCache(
    date = date,
    signedToday = signedToday,
    continuousDays = continuousDays,
    totalDays = totalDays,
    totalTarget = totalTarget,
    monthDays = monthDays,
    monthTarget = monthTarget,
    todayGiftName = todayGiftName,
    savedAtSec = nowSec,
)

/**
 * 缓存 → 展示模型。
 *
 * 日期对不上今天（缓存是昨天写的）时不改数字，只把 [SignInStatus.signedToday]
 * 置回 false —— 跨天后"昨天签过了"不该让今天显示成已签。
 * 数字本身留着：连续天数之类的要等拉到新数据才能更新，摆一份旧的比摆 0 靠谱。
 *
 * @param todayKey 今天的日期键，形如 `20260921`（[com.nzd.antigravitypanel.util.serverDateKey]）。
 *   缓存里的 [SignInCache.date] 是服务端原样的 `2026-09-21`，比较前先抹掉横线。
 */
fun SignInCache.toStatus(todayKey: String): SignInStatus = SignInStatus(
    date = date,
    signedToday = signedToday && date.replace("-", "") == todayKey,
    continuousDays = continuousDays,
    totalDays = totalDays,
    totalTarget = totalTarget,
    monthDays = monthDays,
    monthTarget = monthTarget,
    todayGiftName = todayGiftName,
)
