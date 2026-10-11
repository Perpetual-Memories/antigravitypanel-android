package com.nzd.antigravitypanel.data.xinyue

import com.nzd.antigravitypanel.util.currentEpochSeconds
import com.nzd.antigravitypanel.util.serverDateKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 悦享卡状态的落盘缓存。理由和另两块签到一样：概览不该先空一拍。
 *
 * [XinyueCardStatus.canClaim] 不落盘——它是"今天领没领"，缓存里那份
 * 冷启动时拿出来只会误导（昨天显示可领，今天可能已经领过了）。
 */
@Serializable
data class XinyueCardCache(
    val hasCard: Boolean = false,
    val expired: Boolean = false,
    val gotNum: Int = 0,
    val totalNum: Int = 0,
    val roleName: String = "",
    val partitionName: String = "",
    val endDate: String = "",
    val rewardText: String = "",
    val savedAtSec: Long = 0,
)

object XinyueCardCacheCodec {
    private val Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun encode(cache: XinyueCardCache): String = Json.encodeToString(cache)

    fun decode(raw: String): XinyueCardCache? =
        runCatching { Json.decodeFromString<XinyueCardCache>(raw) }.getOrNull()
}

fun XinyueCardStatus.toCache(nowSec: Long): XinyueCardCache = XinyueCardCache(
    hasCard = hasCard,
    expired = expired,
    gotNum = gotNum,
    totalNum = totalNum,
    roleName = roleName,
    partitionName = partitionName,
    endDate = endDate,
    rewardText = rewardText,
    savedAtSec = nowSec,
)

/**
 * 缓存 → 展示模型。`canClaim` 恒为 false：没有拉到新数据之前不声称"可以领"。
 *
 * @param nowSec 用来判跨天（北京时间自然日）。缓存自己带着 [XinyueCardCache.savedAtSec]，
 *   存的那天和今天不是同一天就把 [XinyueCardStatus.stale] 置起来 ——
 *   悦享卡是每日礼包，隔天的"已领 n/m"不能当今天的结论。
 */
fun XinyueCardCache.toStatus(nowSec: Long = currentEpochSeconds()): XinyueCardStatus =
    XinyueCardStatus(
        hasCard = hasCard,
        expired = expired,
        canClaim = false,
        gotNum = gotNum,
        totalNum = totalNum,
        roleName = roleName,
        partitionName = partitionName,
        endDate = endDate,
        rewardText = rewardText,
        stale = savedAtSec > 0 && serverDateKey(savedAtSec) != serverDateKey(nowSec),
    )
