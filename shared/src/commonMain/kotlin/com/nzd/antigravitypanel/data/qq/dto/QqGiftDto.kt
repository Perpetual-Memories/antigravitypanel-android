package com.nzd.antigravitypanel.data.qq.dto

import com.nzd.antigravitypanel.data.remote.LenientBoolean
import com.nzd.antigravitypanel.data.remote.LenientInt
import com.nzd.antigravitypanel.data.remote.LenientLong
import com.nzd.antigravitypanel.data.remote.LenientString
import kotlinx.serialization.Serializable

/**
 * QQ 游戏中心「福利」接口的响应模型。2026-09-21 抓 QQ 游戏中心礼包页拿到。
 *
 * 这一组和 AMS 那边的习惯不同：数字大多是**真数字**而不是字符串，
 * 但 `actId` / `appid` 又偏偏是字符串，而 `beginTime` 在有的接口里是 `"0"`、
 * 在另一个里是 `0`。所以全部走宽松转换，别赌类型。
 */

@Serializable
data class QqGameUserInfoDto(
    val roles: List<QqRoleDto> = emptyList(),
)

@Serializable
data class QqRoleDto(
    @Serializable(with = LenientString::class) val appid: String = "",
    val areaInfo: QqAreaInfoDto? = null,
)

/**
 * 游戏角色所在的区服。领取礼包必须带上它——服务端按这个把道具发到具体角色上。
 *
 * 抓包里的值：`area=1, partition=1, platId=1, roleId=419175704459784`。
 * 和 `static.mie.qq.com/.../idip/1110484610.json` 那份静态配置对得上
 * （`area.qq_android=1`、`partition=1`，Android 的 `platid=1`）。
 */
@Serializable
data class QqAreaInfoDto(
    @Serializable(with = LenientInt::class) val area: Int = 0,
    @Serializable(with = LenientString::class) val roleId: String = "",
    @Serializable(with = LenientInt::class) val platId: Int = 0,
    @Serializable(with = LenientInt::class) val partition: Int = 0,
    val newRoleId: String = "",
    val roleName: String = "",
    val partitionName: String = "",
)

@Serializable
data class QqFirstScreenDto(
    val firstGame: QqFirstGameDto? = null,
)

@Serializable
data class QqFirstGameDto(
    @Serializable(with = LenientString::class) val appid: String = "",
    val gifts: List<QqGiftDto> = emptyList(),
)

/**
 * 一个礼包。
 *
 * ⚠️ 两个接口里这个对象的**层级不一样**：`single-game-firstscreen` 直接给 `QqGiftDto`，
 * 而 `exchange-all-gifts` 外面还包了一层 `{ "gift": {...} }`。所以要两个包装类型，
 * 别指望一个解码器吃两边。
 */
@Serializable
data class QqGiftDto(
    @Serializable(with = LenientString::class) val actId: String = "",
    val name: String = "",
    val desc: String = "",
    /** true = 这是签到类礼包（逆战未来那个叫「周签到礼包」）。 */
    @Serializable(with = LenientBoolean::class) val isSign: Boolean = false,
    /** 签到进行的第几天。周签到是 1..8。 */
    @Serializable(with = LenientInt::class) val signDate: Int = 0,
    /** 现在能不能领。领完服务端置 false——这是唯一可信的"今天领没领"信号。 */
    @Serializable(with = LenientBoolean::class) val canGot: Boolean = false,
    @Serializable(with = LenientLong::class) val beginTime: Long = 0,
    @Serializable(with = LenientLong::class) val endTime: Long = 0,
    /** 今天能领到的东西。 */
    val props: List<QqPropDto> = emptyList(),
    /** 明天能领到的东西。UI 用来显示"明天是…"。 */
    val nextSignProps: List<QqPropDto> = emptyList(),
    /** 整轮的每日清单，长度就是周期天数（逆战未来是 8）。 */
    val weekItems: List<QqWeekItemDto> = emptyList(),
)

@Serializable
data class QqPropDto(
    val name: String = "",
    /** 数量。抓包里是字符串 `"1"`；同一个字段在别的接口里出现过数字，走宽松转换。 */
    @Serializable(with = LenientString::class) val itemNum: String = "",
    val image: String = "",
)

@Serializable
data class QqWeekItemDto(
    @Serializable(with = LenientString::class) val actId: String = "",
    val name: String = "",
    @Serializable(with = LenientInt::class) val signDate: Int = 0,
    /**
     * 这一天领没领。
     *
     * ⚠️ 抓包里刚领完的那一天这里**仍是 false**，所以它不能用来判断"今天领了没"。
     * 判断今天只看 [QqGiftDto.canGot]。这个字段目前只作展示参考，UI 不依赖它出结论。
     */
    @Serializable(with = LenientBoolean::class) val got: Boolean = false,
    val img: String = "",
    @Serializable(with = LenientString::class) val itemNum: String = "",
)

/** `exchange-all-gifts` 里礼包的包装层。 */
@Serializable
data class QqExchangeGiftDto(
    val gift: QqGiftDto? = null,
)

@Serializable
data class QqExchangeResultDto(
    val gifts: List<QqExchangeGiftDto> = emptyList(),
    val extraNotGot: Int = 0,
)
