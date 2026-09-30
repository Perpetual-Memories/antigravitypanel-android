package com.nzd.antigravitypanel.data.remote.dto

import com.nzd.antigravitypanel.data.remote.LenientBoolean
import com.nzd.antigravitypanel.data.remote.LenientInt
import com.nzd.antigravitypanel.data.remote.LenientString
import kotlinx.serialization.Serializable

/**
 * 福利站的积分与任务中心。2026-09-29 抓小程序福利站拿到的真实结构。
 *
 * 和签到那批 DTO 一样走 [LenientInt] / [LenientString]：这套服务端在不同
 * 字段上时而给数字、时而给字符串（`taskID` 是 `"65928"`、`amsret` 是 `"0"`）。
 * `isfinished` / `isawarded` 是真布尔值，[LenientBoolean] 两种都能吃。
 */

// ---------------- /api/score/redeem/list ----------------

@Serializable
data class ScoreRedeemListDto(
    @Serializable(with = LenientInt::class) val actID: Int = 0,
    val tasks: List<ScoreRedeemTaskDto> = emptyList(),
)

@Serializable
data class ScoreRedeemTaskDto(
    @Serializable(with = LenientInt::class) val redeemID: Int = 0,
    /**
     * 这个兑换项要用到的积分账户。**同一个账户会在多个兑换项里重复出现**，
     * 余额（`totalScore`）是同一个值，所以取余额时要跨所有项去取。
     */
    val scoreList: List<ScoreBalanceDto> = emptyList(),
)

@Serializable
data class ScoreBalanceDto(
    val jfID: String = "",
    /** 这个账户现在的余额，也就是"当前积分总数"。 */
    @Serializable(with = LenientInt::class) val totalScore: Int = 0,
    @Serializable(with = LenientInt::class) val needScore: Int = 0,
)

// ---------------- /api/task/label ----------------

@Serializable
data class TaskLabelDto(
    val taskgroups: TaskGroupsDto? = null,
)

@Serializable
data class TaskGroupsDto(
    val labelgrouptasks: List<WelfareTaskGroupDto> = emptyList(),
)

@Serializable
data class WelfareTaskGroupDto(
    @Serializable(with = LenientInt::class) val groupid: Int = 0,
    val groupinfo: WelfareTaskGroupInfoDto? = null,
    val grouptasks: List<WelfareTaskDto> = emptyList(),
)

@Serializable
data class WelfareTaskGroupInfoDto(
    val name: String = "",
)

@Serializable
data class WelfareTaskDto(
    @Serializable(with = LenientInt::class) val taskid: Int = 0,
    val taskinfo: WelfareTaskInfoDto? = null,
    val taskdata: WelfareTaskDataDto? = null,
)

@Serializable
data class WelfareTaskInfoDto(
    /** 任务名，比如「每日完成1局」。概览小字直接拿它当前缀，别自己写死。 */
    val name: String = "",
    val desc: String = "",
)

@Serializable
data class WelfareTaskDataDto(
    @Serializable(with = LenientInt::class) val target: Int = 0,
    @Serializable(with = LenientInt::class) val progress: Int = 0,
    @Serializable(with = LenientBoolean::class) val isfinished: Boolean = false,
    /** 奖励领没领。领过之后 `isfinished` 仍然为真，两个字段要一起看。 */
    @Serializable(with = LenientBoolean::class) val isawarded: Boolean = false,
    val ext: WelfareTaskExtDto? = null,
)

@Serializable
data class WelfareTaskExtDto(
    /** `day` / `week` / `month` / `long`。自动领取只认前两个，见 `IdeMethod.TaskLabel`。 */
    val period: String = "",
)

// ---------------- /api/task/reward ----------------

@Serializable
data class TaskRewardDto(
    @Serializable(with = LenientInt::class) val status: Int = 0,
    val msg: String = "",
    val res: List<TaskRewardItemDto> = emptyList(),
)

@Serializable
data class TaskRewardItemDto(
    /** 0 = 真的发到手了。非 0 时 `H` 可能整个没有。 */
    @Serializable(with = LenientInt::class) val Ret: Int = 0,
    val H: TaskRewardHeadDto? = null,
)

@Serializable
data class TaskRewardHeadDto(
    /** 形如「恭喜您获得了礼包： 500积分 」，前后带空格，显示前要 trim。 */
    val amsmsg: String = "",
    @Serializable(with = LenientString::class) val amsret: String = "",
    @Serializable(with = LenientString::class) val taskID: String = "",
)
