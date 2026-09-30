package com.nzd.antigravitypanel.data.signin

import com.nzd.antigravitypanel.data.remote.dto.ScoreRedeemListDto
import com.nzd.antigravitypanel.data.remote.dto.TaskLabelDto
import com.nzd.antigravitypanel.data.remote.dto.TaskRewardDto
import com.nzd.antigravitypanel.data.remote.dto.WelfareTaskDto

/**
 * 任务中心里一个任务的展示态。UI 只读这个，不直接碰 DTO。
 *
 * 抽出来是为了让"DTO → 屏幕上那句话"能脱离网络单测：`period` 取错一支就会把
 * 「订阅小程序」这种一次性任务也自动领掉，从 UI 上完全看不出是这一层的问题。
 */
data class WelfareTaskState(
    val taskId: Int = 0,
    /** 领取接口要的是 `groupID + taskID`，两个都得留着。 */
    val groupId: Int = 0,
    val name: String = "",
    val progress: Int = 0,
    val target: Int = 0,
    /** 目标达成了。 */
    val finished: Boolean = false,
    /** 奖励已经领走了。 */
    val awarded: Boolean = false,
    /** `day` / `week` / `month` / `long`。 */
    val period: String = "",
) {
    /** 达成且还没领 = 现在可以领。 */
    val claimable: Boolean get() = finished && !awarded
}

/** `taskdata.ext.period` 的取值。自动领取只认前两个。 */
internal const val PERIOD_DAY = "day"
internal const val PERIOD_WEEK = "week"

/**
 * 当前积分总数。
 *
 * 服务端把同一个积分账户在每个可兑换项里都重复带一份（`tasks[].scoreList[]`），
 * 值是一样的，取最大的那份即可。拿不到（列表为空）时返回 null —— 那是"没拿到"，
 * 不是"0 分"，UI 上必须分开。
 */
internal fun ScoreRedeemListDto.totalScore(): Int? =
    tasks.asSequence().flatMap { it.scoreList.asSequence() }.map { it.totalScore }.maxOrNull()

/** 所有分组下的所有任务，摊平。 */
internal fun TaskLabelDto.allTasks(): List<WelfareTaskState> =
    taskgroups?.labelgrouptasks.orEmpty().flatMap { group ->
        val groupId = group.groupid
        group.grouptasks.map { it.toState(groupId) }
    }

/**
 * 概览小字里那一条：今日（`day`）任务。
 *
 * 抓包时它是「每日完成1局」。按 `period` 挑而不是按名字匹配——官方改任务名
 * 是很常见的事，改一次这里就显示不出来了，而 `period` 是服务端的结构性字段。
 */
internal fun TaskLabelDto.dailyTask(): WelfareTaskState? =
    allTasks().firstOrNull { it.period == PERIOD_DAY }

/**
 * 这一轮**该自动领**的任务：每日与每周里已达成、还没领的那些。
 *
 * `month`（累登N天）和 `long`（订阅小程序 / 添加企微）都不要：前者一个月才结算一次，
 * 后者是一次性的，都不属于"每天打开 app 顺手领一下"的范围。
 */
internal fun TaskLabelDto.claimableTasks(): List<WelfareTaskState> =
    allTasks().filter { it.claimable && (it.period == PERIOD_DAY || it.period == PERIOD_WEEK) }

internal fun WelfareTaskDto.toState(groupId: Int): WelfareTaskState {
    val data = taskdata
    return WelfareTaskState(
        taskId = taskid,
        groupId = groupId,
        name = taskinfo?.name.orEmpty(),
        progress = data?.progress ?: 0,
        target = data?.target ?: 0,
        finished = data?.isfinished ?: false,
        awarded = data?.isawarded ?: false,
        period = data?.ext?.period.orEmpty(),
    )
}

/**
 * 领到的东西。拿不到文案时退回任务名，至少让用户知道领的是哪一个。
 */
internal fun TaskRewardDto.rewardText(): String {
    val head = res.firstOrNull { it.Ret == 0 }?.H
    val msg = head?.amsmsg?.trim().orEmpty()
    if (msg.isNotBlank()) return msg
    return "奖励"
}
