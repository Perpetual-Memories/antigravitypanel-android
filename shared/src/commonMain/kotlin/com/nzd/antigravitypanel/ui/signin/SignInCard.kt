package com.nzd.antigravitypanel.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.qq.QqWeeklySignIn
import com.nzd.antigravitypanel.data.signin.SignInStatus
import com.nzd.antigravitypanel.data.signin.WelfareTaskState
import com.nzd.antigravitypanel.data.xinyue.XinyueCardStatus
import com.nzd.antigravitypanel.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 概览页的签到卡。点开进二级页看明细。
 *
 * 三态要说清楚，别都用一句话糊过去：
 * - [available] 为 false：还没拿到（没登录 / 还没拉到），卡片照常在，说"登录后可自动签到"
 * - 拿到了但今天没签：正常显示数字，标「未签到」
 * - 拿到了且已签：标「已签到」
 */
@Composable
fun SignInCard(
    status: SignInStatus,
    available: Boolean,
    loading: Boolean,
    /** 游戏中心的周签到。和小程序的每日签到是两套独立的凭证与接口。 */
    qqStatus: QqWeeklySignIn,
    qqAvailable: Boolean,
    qqBound: Boolean,
    /** 周签到这一轮拉失败的原因。非空时那一行要打叉，不能还挂着勾。 */
    qqError: String? = null,
    qqLoading: Boolean = false,
    /** 心悦悦享卡的每日礼包。又是另一套凭证与接口。 */
    xinyueStatus: XinyueCardStatus,
    xinyueAvailable: Boolean,
    xinyueBound: Boolean,
    xinyueError: String? = null,
    xinyueLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmallTitle(text = "每日签到")
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 这一行是**标题**不是状态：三块签到并排放，得先说清这块是哪一个。
            // 签没签到由下面那行小字和右侧的勾来说——概览一行放不下两套信息。
            //
            // 字号字重和下面两块**完全一致**（15sp / Medium）：三块是并列关系，
            // 第一块用大一号的字会显得它是"主标题"、另外两块是"子项"，看着像层级。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "小程序活动中心签到",
                    modifier = Modifier.weight(1f),
                    // 用普通前景色而不是强调色：三块签到的标题并列，
                    // 只有第一块染绿会被读成"这块状态特别好"，而绿色本来是给"已签"用的。
                    // 签没签到统一由右侧图标 + 下面那行小字说。
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = if (available && status.signedToday) MiuixIcons.Ok else MiuixIcons.Tasks,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (available && status.signedToday) {
                        signInAccentColor()
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }

            Text(
                text = signInSummaryText(status, available),
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            // 分隔线用一块 1dp 的方块画：项目没引入 material3，
            // 而 androidx.compose.material3 在 CMP 工程里根本不存在
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.surfaceVariant),
            )

            QqSignInRow(
                status = qqStatus,
                available = qqAvailable,
                bound = qqBound,
                loading = qqLoading,
                error = qqError,
            )

            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.surfaceVariant),
            )

            XinyueSignInRow(
                status = xinyueStatus,
                available = xinyueAvailable,
                bound = xinyueBound,
                loading = xinyueLoading,
                error = xinyueError,
            )
        }
    }
}

/**
 * 卡片里周签到那一行。
 *
 * 只显示"第几天 / 今天领什么 / 能不能领"三件事——概览不是礼包列表，
 * 其余十几个礼包不进这一屏。
 */
/**
 * 卡片里周签到那一行。
 *
 * 右侧图标有**三种**，不是"勾 / 不勾"两态：
 * - **叉**：这一轮拉失败了（`error` 非空）。之前失败也挂着勾——
 *   实际上只是把上一次缓存的结论又显示了一遍，用户看不出已经坏了。
 * - **勾**：今天真的确认过了，且今天没有可领的（= 已领取）。
 * - **任务图标（灰）**：还没确认完。这里包含"缓存已经跨天"的情况：
 *   周签到是按周走的，隔天的旧数字不能再当今天的结论，
 *   否则会出现"都星期六了还显示星期五已签到"。
 */
@Composable
private fun QqSignInRow(
    status: QqWeeklySignIn,
    available: Boolean,
    bound: Boolean,
    loading: Boolean,
    error: String?,
) {
    val failed = error != null
    val pending = loading || status.stale
    val done = available && !failed && !pending && !status.canClaim
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "QQ游戏中心周签到礼包",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = qqSummaryText(status, available, bound, loading, error),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 13.sp,
                color = if (failed) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
        Icon(
            imageVector = when {
                failed -> MiuixIcons.Close
                done -> MiuixIcons.Ok
                else -> MiuixIcons.Tasks
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = when {
                failed -> MiuixTheme.colorScheme.error
                done -> signInAccentColor()
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
    }
}

/**
 * 卡片里悦享卡那一行。
 *
 * 只显示"今天领没领 / 已领几次"两件事——概览不是礼包列表。
 * 打勾的条件比周签到那行严：要有卡、没过期、且今天已经领过，
 * 缺一个都不算"今天这事办完了"。
 */
@Composable
private fun XinyueSignInRow(
    status: XinyueCardStatus,
    available: Boolean,
    bound: Boolean,
    loading: Boolean,
    error: String?,
) {
    // 和 QQ 那行同一个三态：失败打叉、没确认完打灰、确认领完了打勾
    val failed = error != null
    val done = available && !failed && !loading && status.hasCard &&
        !status.expired && !status.canClaim
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "悦享卡每日礼包",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = xinyueSummaryText(status, available, bound),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 13.sp,
                color = if (failed) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
        Icon(
            imageVector = when {
                failed -> MiuixIcons.Close
                done -> MiuixIcons.Ok
                else -> MiuixIcons.Tasks
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = when {
                failed -> MiuixTheme.colorScheme.error
                done -> signInAccentColor()
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
    }
}

/** 周签到那行的副标题。没绑定时不显示"第几天"——那是"不知道"，不是"第 0 天"。 */
internal fun qqSummaryText(
    status: QqWeeklySignIn,
    available: Boolean,
    bound: Boolean,
    /** 正在拉。拉的过程中不能说"已领取"——那是上一次的结论。 */
    loading: Boolean = false,
    error: String? = null,
): String {
    if (!bound) return "需要 QQ 登录凭证"
    // 失败要说得比"没拿到"更重：进了这行说明之前是配好的，是这一轮坏了
    if (error != null) return "这一轮没拉到：${error.take(28)}"
    if (!available) return "还没拿到，点开看看"
    if (loading) return "正在确认今天的状态…"
    // 缓存跨天了：还没拿到今天的结论，别拿旧数字冒充
    if (status.stale) return "还没确认今天的状态"
    // 一周是 7 天：第 8 格是七天全签满之后的额外奖励，不当成一周里的第 8 天。
    val day = if (status.totalDays > 0) "本周 ${status.weekDay}/${status.totalDays}" else "第 ${status.day} 天"
    val reward = status.todayReward.takeIf { it.isNotBlank() }?.let { " · 今日 $it" } ?: ""
    // 末尾不挂「已领取」：那件事右侧的勾已经说了，同一行说两遍纯占地方。
    // 「可领」留着——它是"还有事没办"，勾表达不出来。
    val state = if (status.canClaim) " · 可领" else ""
    return "$day$reward$state"
}

/**
 * 卡片副标题。没拿到数据时不说"连续 0 天"——那是"不知道"，不是"0 天"。
 *
 * 三段的顺序是"越往后越像待办"：连续天数和本月进度是看一眼就完事的结论，
 * 末尾那条是「每日完成1局」的**三态**，它是这一行里唯一可能"还有事没办"的。
 *
 * 累计天数不在这里：二级页那张卡的四格里已经有一格是它，概览一行塞三个数字
 * 会挤得换行。
 */
internal fun signInSummaryText(status: SignInStatus, available: Boolean): String {
    if (!available) return "登录后可见，打开 app 时会自动帮你签到"
    if (status.date.isBlank()) return "暂时拿不到签到状态"
    val parts = buildList {
        add("连续 ${status.continuousDays} 天")
        if (status.monthTarget > 0) add("本月 ${status.monthDays}/${status.monthTarget}")
        // 拿不到（刚冷启动、还没拉完）就整段不摆：昨天"已领取"的结论摆到今天是假话
        status.dailyTask?.let { task ->
            val name = task.name.ifBlank { DEFAULT_DAILY_TASK_NAME }
            add("$name ${dailyTaskStateText(task)}")
        }
    }
    return parts.joinToString(" · ")
}

/**
 * 任务中心那条的三态。
 *
 * 三种说法的长度刻意接近（都是 5 个字）：这一行是"连续 n 天 · 本月 n/m · …"，
 * 前两段短、末段长的话，末段一变长整行就换行，卡片高度会跟着跳。
 */
internal fun dailyTaskStateText(task: WelfareTaskState): String = when {
    task.awarded -> "奖励已领取"
    task.finished -> "奖励待领取"
    else -> "任务未完成"
}

/** 任务名拿不到时的兜底。抓包时服务端给的是「每日完成1局」。 */
internal const val DEFAULT_DAILY_TASK_NAME = "每日完成1局"

/** 已签到的强调色。沿用概览状态卡那组绿，深浅色各一份。 */
@Composable
internal fun signInAccentColor() =
    if (isInDarkTheme()) SIGNED_COLOR_DARK else SIGNED_COLOR_LIGHT

private val SIGNED_COLOR_DARK = Color(0xFF36D167)
private val SIGNED_COLOR_LIGHT = Color(0xFF0E7A33)
