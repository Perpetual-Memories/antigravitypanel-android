package com.nzd.antigravitypanel.data.qq

import com.nzd.antigravitypanel.data.qq.dto.QqFirstScreenDto
import com.nzd.antigravitypanel.data.qq.dto.QqGiftDto
import com.nzd.antigravitypanel.data.qq.dto.QqPropDto

/**
 * 游戏中心的**周签到礼包**状态。
 *
 * ⚠️ 逆战未来在游戏中心里**没有每日签到，是 8 天一轮的周签到**，而且这 8 格
 * **不是 8 个自然日**：前 7 格是周一到周日，第 8 格是**本周满签的额外奖励**
 * （抓包里是「赛季补给箱*2」，前 7 格里有两格是「赛季补给箱*1」）。
 * 所以 [day] 会走到 8，而 [totalDays] 恒为 7 —— 显示成「第 8/8 天」是错的。
 *
 * 只挑 `isSign == true` 的那个礼包，其余（等级礼、渠道 CDK 礼、启动礼）不进这里——
 * 概览页那块就是"签到"，不该被一堆别的礼包淹没。
 */
data class QqWeeklySignIn(
    /**
     * 已经连续签到第几天（1 起）。8 表示这一周七天都签到了、正在领满签奖励。
     * 0 = 没读到。
     */
    val day: Int = 0,

    /**
     * 一周的签到格数，**恒为 7**。
     *
     * 不取 `weekItems.size`（那是 8）：第 8 格是满签奖励不是一周里的第 8 天，
     * 直接拿列表长度会让「本周 x/8」这种说法凭空多出一天。
     */
    val totalDays: Int = 0,

    /** 今天能不能领。这是唯一可信的信号。 */
    val canClaim: Boolean = false,
    /** 今天发的奖励，比如「GP点*500」。 */
    val todayReward: String = "",
    /** 明天发的奖励。服务端在 `nextSignProps` 里直接给了，不用自己猜。 */
    val nextReward: String = "",
    /**
     * 一周七天全签满之后额外发的那一格（`weekItems` 的第 8 项）。
     * 取不到就是空串——那是"官方没挂"，不是"没有"。
     */
    val fullWeekBonus: String = "",
    /** 角色名，让用户确认领到的是不是自己的号。 */
    val roleName: String = "",
) {
    val hasData: Boolean get() = totalDays > 0

    /** 本周进度分子：第 8 天（满签奖励）按 7 算，不然会出现「7/8」这种越界的说法。 */
    val weekDay: Int get() = day.coerceAtMost(totalDays)
}

internal fun propText(props: List<QqPropDto>): String =
    props.map { it.name }.filter { it.isNotBlank() }.joinToString("、")

/**
 * 从首页响应里挑出周签到礼包。纯函数，能脱离网络单测。
 *
 * 有多个 `isSign` 的礼包时取 `signDate` 最小的那个——抓包里只有一个，
 * 但官方以后可能同时挂月签到（DTO 里已经有 `monthSignGiftExtra` 这个字段了）。
 */
internal fun QqFirstScreenDto.weeklySignIn(): QqWeeklySignIn {
    val gifts = firstGame?.gifts.orEmpty()
    val sign = gifts.filter { it.isSign }.minByOrNull { it.signDate } ?: return QqWeeklySignIn()
    return sign.toWeeklySignIn()
}

internal fun QqGiftDto.toWeeklySignIn(): QqWeeklySignIn = QqWeeklySignIn(
    day = signDate,
    totalDays = WEEK_DAYS,
    canClaim = canGot,
    todayReward = propText(props).ifBlank { desc },
    nextReward = propText(nextSignProps),
    // weekItems 的前 7 项是一周一到周日，第 8 项是满签奖励。
    // 万一官方只挂了 7 项（没有满签奖励），这里自然是空串。
    fullWeekBonus = weekItems.getOrNull(WEEK_DAYS)?.name.orEmpty(),
)

/**
 * 一周的签到格数。
 *
 * 写死 7 而不是取 `weekItems.size`：第 8 格是"七天全签满的额外奖励"，
 * 它不属于一周里的任何一天。取列表长度会把周期说成 8 天。
 */
internal const val WEEK_DAYS = 7
