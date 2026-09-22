package com.nzd.antigravitypanel.data.signin

import com.nzd.antigravitypanel.data.remote.dto.GiftPackageGroupDto
import com.nzd.antigravitypanel.data.remote.dto.SignInDoDto
import com.nzd.antigravitypanel.data.remote.dto.SignInGroupDto
import com.nzd.antigravitypanel.data.remote.dto.SignInListDto

/**
 * 签到看板的展示模型。UI 只读这个，不直接碰 DTO。
 *
 * 抽出来是为了让"DTO → 屏幕上那些字"这件事能脱离网络单测：
 * 官方这套结构把数字堆在三层嵌套里（`total.cumulative.acTtotal.progress`），
 * 取错一支就会显示成另一个含义完全不同的数（累计 vs 连续）。
 */
data class SignInStatus(
    /** 服务端认定的"今天"，形如 `2026-09-21`。以它为准，不要用设备时钟。 */
    val date: String = "",
    val signedToday: Boolean = false,
    /** 连续签到天数。断一天归零。 */
    val continuousDays: Int = 0,
    /** 累计签到天数与活动总天数。 */
    val totalDays: Int = 0,
    val totalTarget: Int = 0,
    /** 本月累计签到天数与本月的天数。 */
    val monthDays: Int = 0,
    val monthTarget: Int = 0,
    /** 活动周期起止（累计那个窗口的），形如 `2026-05-09`。 */
    val periodStart: String = "",
    val periodEnd: String = "",
    /** 今日奖励的标题，比如「100积分」。 */
    val todayGiftName: String = "",
    /** 今日奖励拆开来的条目，比如「福利中心-兑换币 x100」。 */
    val todayGiftItems: List<String> = emptyList(),
    /**
     * 签到任务里列出的每一天。抓包时只有今天一格，所以 UI 不能假定它是整月日历——
     * 有几格画几格。
     */
    val days: List<SignInDay> = emptyList(),
)

data class SignInDay(
    val date: String = "",
    /** 本月第几天。 */
    val monthDay: Int = 0,
    val signed: Boolean = false,
    val giftName: String = "",
    val isToday: Boolean = false,
)

/**
 * DTO → 展示模型。纯函数，不读时钟也不发请求。
 *
 * `groupList` 可能有多个分组（抓包时只有一个），优先取服务端标了 `isDefault` 的那个，
 * 都没有就取第一个——签到活动同时只可能有一个生效的分组。
 */
internal fun SignInListDto.toSignInStatus(): SignInStatus {
    val group = pickGroup()
    if (group == null) return SignInStatus()

    val current = group.currentDay
    val cumulative = group.total?.cumulative
    val continuous = group.total?.continuous
    val month = cumulative?.monthtotal
    val overall = cumulative?.acTtotal
    val days = group.signInTask?.taskList.orEmpty()
    val today = current?.date.orEmpty()

    return SignInStatus(
        date = today,
        signedToday = current?.isSignIn ?: false,
        continuousDays = continuous?.acTtotal?.progress ?: 0,
        totalDays = overall?.progress ?: 0,
        totalTarget = overall?.total ?: 0,
        monthDays = month?.progress ?: 0,
        monthTarget = month?.total ?: 0,
        periodStart = overall?.startTime.orEmpty(),
        periodEnd = overall?.endTime.orEmpty(),
        todayGiftName = giftTitleOf(current?.gift),
        todayGiftItems = giftItemsOf(current?.gift),
        days = days.map { day ->
            SignInDay(
                date = day.date,
                monthDay = day.month,
                signed = day.isSignIn,
                giftName = giftTitleOf(day.gift),
                isToday = day.date.isNotBlank() && day.date == today,
            )
        },
    )
}

private fun SignInListDto.pickGroup(): SignInGroupDto? =
    groupList.firstOrNull { it.group?.isDefault == true } ?: groupList.firstOrNull()

/**
 * 奖励标题。一个礼包组里可能有多个包（`100积分` + 别的），拼起来显示；
 * 全是空名字时退回包名（`任务奖励-每日签到`），至少让用户知道发了东西。
 */
internal fun giftTitleOf(gift: GiftPackageGroupDto?): String {
    if (gift == null) return ""
    val names = gift.pkgInfo.map { it.pkgName }.filter { it.isNotBlank() }
    if (names.isNotEmpty()) return names.joinToString(" + ")
    return gift.pkgGroupName
}

internal fun giftItemsOf(gift: GiftPackageGroupDto?): List<String> =
    gift?.pkgInfo.orEmpty().flatMap { pkg ->
        pkg.pkgItemInfo.map { item ->
            val name = item.itemName.ifBlank { item.itemCode }
            if (name.isBlank()) return@map ""
            "$name x${item.itemCount}"
        }
    }.filter { it.isNotBlank() }

/** 签到成功后那句话里的奖励名。拿不到就说「奖励」。 */
internal fun SignInDoDto.rewardText(): String {
    val gift = signIn?.gift
    val title = giftTitleOf(gift)
    if (title.isNotBlank()) return title
    val items = giftItemsOf(gift)
    return items.firstOrNull() ?: "奖励"
}
