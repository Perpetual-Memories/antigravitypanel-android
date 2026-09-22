package com.nzd.antigravitypanel.data.remote.dto

import com.nzd.antigravitypanel.data.remote.LenientBoolean
import com.nzd.antigravitypanel.data.remote.LenientInt
import com.nzd.antigravitypanel.data.remote.LenientString
import kotlinx.serialization.Serializable

/**
 * 福利站签到的响应模型。2026-09-21 抓小程序福利站拿到的真实结构。
 *
 * 和战绩那批 DTO 一样，数字字段一律走 [LenientInt] / [LenientString]：
 * 这个服务端在不同 method 下会把同一个 id 时而给数字、时而给字符串
 * （`pkgGroupId` 就是 `"4629891"` 这种带引号的）。
 *
 * 注意 `isSignIn` / `isGift` 是真布尔值，[LenientBoolean] 两种都能吃。
 */

/** `/api/signin/list` 的业务数据。 */
@Serializable
data class SignInListDto(
    val groupList: List<SignInGroupDto> = emptyList(),
)

@Serializable
data class SignInGroupDto(
    val group: SignInGroupMetaDto? = null,
    /** 累计 / 连续签到的三个时间窗口。 */
    val total: SignInTotalsDto? = null,
    /** 今天这一格。看板主要读它。 */
    val currentDay: SignInDayDto? = null,
    val signInTask: SignInTaskDto? = null,
)

@Serializable
data class SignInGroupMetaDto(
    @Serializable(with = LenientInt::class) val id: Int = 0,
    val name: String = "",
    @Serializable(with = LenientInt::class) val offsetHours: Int = 0,
    @Serializable(with = LenientBoolean::class) val isDefault: Boolean = false,
)

@Serializable
data class SignInTotalsDto(
    /** 累计：不管中间断没断，签过就算。 */
    val cumulative: SignInPeriodsDto? = null,
    /** 连续：断一天就归零。 */
    val continuous: SignInPeriodsDto? = null,
)

@Serializable
data class SignInPeriodsDto(
    /** 活动总周期。字段名是官方拼错的（acTtotal），照抄。 */
    val acTtotal: SignInProgressDto? = null,
    val weektotal: SignInProgressDto? = null,
    val monthtotal: SignInProgressDto? = null,
)

@Serializable
data class SignInProgressDto(
    @Serializable(with = LenientInt::class) val progress: Int = 0,
    @Serializable(with = LenientInt::class) val total: Int = 0,
    val startTime: String = "",
    val endTime: String = "",
)

@Serializable
data class SignInTaskDto(
    val taskList: List<SignInDayDto> = emptyList(),
    @Serializable(with = LenientInt::class) val refreshPeriodType: Int = 0,
)

@Serializable
data class SignInDayDto(
    @Serializable(with = LenientInt::class) val id: Int = 0,
    val name: String = "",
    val desc: String = "",
    /** 形如 `2026-09-21`。判断"是不是今天"要跟服务端给的这个日期比，别用本地时钟。 */
    val date: String = "",
    /** 周几（1=周一）。 */
    @Serializable(with = LenientInt::class) val week: Int = 0,
    /** 本月的第几天。 */
    @Serializable(with = LenientInt::class) val month: Int = 0,
    val gift: GiftPackageGroupDto? = null,
    // 抓包里是真布尔值，但同一套接口在别处会把这类开关给成 "1" / "0"，宽松收
    @Serializable(with = LenientBoolean::class) val isSignIn: Boolean = false,
    /** 奖励有没有领。签到接口只管"签"，领奖励是另一回事，UI 要区分开。 */
    @Serializable(with = LenientBoolean::class) val isGift: Boolean = false,
)

@Serializable
data class GiftPackageGroupDto(
    @Serializable(with = LenientString::class) val pkgGroupId: String = "",
    val pkgGroupName: String = "",
    val pkgInfo: List<GiftPackageDto> = emptyList(),
)

@Serializable
data class GiftPackageDto(
    @Serializable(with = LenientString::class) val pkgId: String = "",
    val pkgName: String = "",
    val pkgNum: String = "",
    val pkgGuidePrice: String = "",
    val pkgItemInfo: List<GiftItemDto> = emptyList(),
)

@Serializable
data class GiftItemDto(
    val itemName: String = "",
    @Serializable(with = LenientString::class) val itemCode: String = "",
    @Serializable(with = LenientInt::class) val itemCount: Int = 0,
    @Serializable(with = LenientString::class) val itemType: String = "",
    @Serializable(with = LenientString::class) val itemValue: String = "",
    val itemImage: String = "",
    val itemBigImage: String = "",
)

/** `/api/signin/do` 的业务数据。 */
@Serializable
data class SignInDoDto(
    val signIn: SignInDoResultDto? = null,
)

@Serializable
data class SignInDoResultDto(
    /** 这次签到实际发到手的奖励。 */
    val gift: GiftPackageGroupDto? = null,
    val amsSerialNum: String = "",
    val ext: SignInDoExtDto? = null,
)

@Serializable
data class SignInDoExtDto(
    val taskId: String = "",
    val taskName: String = "",
)
