package com.nzd.antigravitypanel.data.xinyue.dto

import com.nzd.antigravitypanel.data.remote.LenientInt
import com.nzd.antigravitypanel.data.remote.LenientLong
import com.nzd.antigravitypanel.data.remote.LenientString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 心悦俱乐部「我的卡」接口的响应模型。2026-07-29 抓心悦 App 悦享卡页拿到。
 *
 * 和 AMS / 游戏中心那两套都不一样：字段名是 **snake_case**（那两边是 camelCase），
 * 而且数字是**真 JSON 数字**（`gid: 1471` 而不是 `"1471"`）。
 * 但 `record_id` 在悦享卡上是数字、在黑曜卡上是空串，`effective_time` 又偏偏是字符串，
 * 所以除确定是文本的字段外一律走宽松转换，不赌类型。
 *
 * 属性名用驼峰 + `@SerialName`：这一层是"线上格式"，不该把下划线渗进业务代码。
 */

@Serializable
data class XyMyCardListDto(
    /**
     * 生效中的卡。
     *
     * 一个账号可能同时有多张（抓包里就有悦享卡 + 黑曜卡两张），
     * 靠 [XyUserCardDto.cardType] 区分，别直接取第一条。
     */
    @SerialName("my_user_info") val myUserInfo: List<XyUserCardDto> = emptyList(),

    /**
     * 已过期的卡。服务端会把过期的卡挪到这里——
     * 所以「没开通」和「已过期」是**可以区分**的两种状态，别合并成一句「没有卡」。
     */
    @SerialName("expire_user_info") val expireUserInfo: List<XyUserCardDto> = emptyList(),
)

@Serializable
data class XyUserCardDto(
    /** `month` = 悦享卡（月卡），`hmc` = 黑曜卡。我们只认 `month`。 */
    @SerialName("card_type") val cardType: String = "",
    @SerialName("card_id") val cardId: String = "",
    @SerialName("card_group") val cardGroup: String = "",
    /** 游戏 id。逆战未来是 1471。 */
    @SerialName("gid") @Serializable(with = LenientInt::class) val gid: Int = 0,
    /** 绑在这张卡上的游戏角色。领礼包时整个回传给服务端。 */
    @SerialName("role") val role: XyRoleDto? = null,
    /** 卡的实例 id。悦享卡上是 `"11737452"`，黑曜卡上是空串。 */
    @SerialName("record_id") @Serializable(with = LenientString::class) val recordId: String = "",
    @SerialName("month") val month: XyMonthDto? = null,
)

@Serializable
data class XyMonthDto(
    @SerialName("base_info") val baseInfo: XyMonthBaseInfoDto? = null,
)

@Serializable
data class XyMonthBaseInfoDto(
    /**
     * 卡本身的开通状态。抓包里见过 `101`（2026-07 那张）和 `102`（2026-09 那张），
     * 两张都是**生效中**的卡，所以这个数字既不是"是否过期"也不是"第几档"。
     * 含义没搞清楚之前 UI 不碰它——判断过期只看 [endTime]，判断今天领没领只看 [giftStatus]。
     */
    @SerialName("card_status") @Serializable(with = LenientInt::class) val cardStatus: Int = 0,
    @SerialName("start_time") @Serializable(with = LenientLong::class) val startTime: Long = 0,
    /** 到期时间（epoch 秒）。过了它就是过期，无论服务端有没有把它挪走。 */
    @SerialName("end_time") @Serializable(with = LenientLong::class) val endTime: Long = 0,

    /**
     * **今天领没领**：`0` = 今天还能领，`1` = 今天已经领过。
     *
     * 这个结论是抓包对出来的：同一天里领取前那份响应是 `0`（`pay_text` 是「领取」），
     * 领完再查是 `1`（`pay_text` 变成「明日可领」）。
     */
    @SerialName("gift_status") @Serializable(with = LenientInt::class) val giftStatus: Int = 0,

    /** 已经领了几次。 */
    @SerialName("gift_got_num") @Serializable(with = LenientInt::class) val gotNum: Int = 0,
    @SerialName("gift_toget_num") @Serializable(with = LenientInt::class) val toGetNum: Int = 0,

    /** 整张卡一共能领几次。抓的这张是 30 次（30 天）。 */
    @SerialName("gift_total_num") @Serializable(with = LenientInt::class) val totalNum: Int = 0,

    @SerialName("x_day_card_id") @Serializable(with = LenientString::class) val xDayCardId: String = "",

    /** 这张卡带哪些礼包。抓的这张是 `["reopen","daily"]`——续费礼包 + 每日礼包。 */
    @SerialName("gift_type") val giftType: List<String> = emptyList(),
)

/**
 * 游戏角色。
 *
 * ⚠️ [partitionName] 和 [roleName] 是 **base64**——抓包里分别是
 * `6buY6K6k5pyN5Yqh5Zmo`（默认服务器）和 `5oiR5aW96I-c55qE5Za1`（我好菜的喵）。
 * 展示前要解码；**回传给服务端时必须原样发 base64**，不能把解码后的串塞回去。
 */
@Serializable
data class XyRoleDto(
    @SerialName("game_open_id") val gameOpenId: String = "",
    @SerialName("game_app_id") val gameAppId: String = "",
    @SerialName("area_id") @Serializable(with = LenientInt::class) val areaId: Int = 0,
    @SerialName("plat_id") @Serializable(with = LenientInt::class) val platId: Int = 0,
    @SerialName("partition_id") @Serializable(with = LenientInt::class) val partitionId: Int = 0,
    @SerialName("partition_name") val partitionName: String = "",
    @SerialName("role_id") @Serializable(with = LenientString::class) val roleId: String = "",
    @SerialName("role_name") val roleName: String = "",
    @SerialName("device") val device: String = "",
    @SerialName("flag") @Serializable(with = LenientInt::class) val flag: Int = 0,
)

@Serializable
data class XyReceiveGiftDto(
    /** 这次真的发出去的东西。**奖励内容只在领取响应里有**，查询接口没有。 */
    @SerialName("gift_info") val giftInfo: List<XyGiftDto> = emptyList(),
)

@Serializable
data class XyGiftDto(
    /** `daily` = 每日礼包。一次可能回多个（比如同时补发开通礼包）。 */
    @SerialName("type") val type: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("items") val items: List<XyGiftItemDto> = emptyList(),
    @SerialName("num") @Serializable(with = LenientInt::class) val num: Int = 0,
)

@Serializable
data class XyGiftItemDto(
    @SerialName("name") val name: String = "",
    @SerialName("quantity") @Serializable(with = LenientInt::class) val quantity: Int = 0,
)
