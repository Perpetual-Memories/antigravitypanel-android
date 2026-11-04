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
import com.nzd.antigravitypanel.data.supercore.SupercoreStatus
import com.nzd.antigravitypanel.data.xinyue.XinyueCardStatus
import com.nzd.antigravitypanel.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 概览页的签到卡。点开进二级页看明细。
 *
 * 四块签到并排，右侧图标是**同一个四态**（[SignInRowState]），各判各的：
 * - 正在签到（这一轮还在拉）：转圈的 `CircularProgressIndicator`
 * - 这一轮拉失败：红叉
 * - 拿到了但今天没签 / 每日任务奖励还没领 / 没登录：灰叉
 * - 拿到了且签到和奖励都办完：打勾
 */
@Composable
fun SignInCard(
    status: SignInStatus,
    available: Boolean,
    loading: Boolean,
    /** 这一轮拉失败的原因。非空时第一行要打叉，不能还挂着勾。 */
    signInError: String? = null,
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
    /** 超核组队活动。**第四套凭证**：活动页的 T-OPENID / T-ACCESS-TOKEN / T-APPID。 */
    supercoreStatus: SupercoreStatus,
    supercoreAvailable: Boolean,
    supercoreBound: Boolean,
    supercoreError: String? = null,
    supercoreLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 和下面三行同一个四态。这里**不看 loading 决定"办完没办完"**：冷启动摆的是
    // 缓存里「今天已办完」的结论，这一轮还在拉就把勾收走，等于让结论先闪没再回来。
    // loading 只用来回答"这一轮是不是还在签到"——已经有结论的行不转圈。
    val signInFailed = signInError != null
    val signInDone = available && !signInFailed && status.allDoneToday
    val signInPhase = when {
        signInFailed -> SignInRowState.Failed
        signInDone -> SignInRowState.Done
        loading -> SignInRowState.Signing
        else -> SignInRowState.Pending
    }
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
                SignInStateIcon(state = signInPhase)
            }

            Text(
                text = signInSummaryText(status, available, signInError),
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 13.sp,
                color = if (signInFailed) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
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

            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.surfaceVariant),
            )

            SupercoreSignInRow(
                status = supercoreStatus,
                available = supercoreAvailable,
                bound = supercoreBound,
                loading = supercoreLoading,
                error = supercoreError,
            )
        }
    }
}

/**
 * 卡片里「超核组队活动」那一行。
 *
 * 打勾的条件比另三行多一条：签到和任务**都要办完**。这块的签到是"领第 N 天奖励"，
 * 任务积分不领就没有抽奖次数，两件事一起才算今天的活干完了。
 */
@Composable
private fun SupercoreSignInRow(
    status: SupercoreStatus,
    available: Boolean,
    bound: Boolean,
    loading: Boolean,
    error: String?,
) {
    val failed = error != null
    val done = available && !failed && !status.stale && !status.notStarted &&
        status.signedToday && status.tasksDone
    // 活动还没开始时不转圈：那不是"正在签到"，是"今天没这回事"。
    val phase = when {
        failed -> SignInRowState.Failed
        done -> SignInRowState.Done
        loading && !status.notStarted -> SignInRowState.Signing
        else -> SignInRowState.Pending
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "超核组队活动",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = supercoreSummaryText(status, available, bound, loading, error),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 13.sp,
                color = if (failed) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
        SignInStateIcon(state = phase)
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
 * 右侧图标有**四种**（[SignInRowState]），不是"勾 / 不勾"两态：
 * - **转圈**：这一轮还在拉 = 正在签到。
 * - **叉**：这一轮拉失败了（`error` 非空）。之前失败也挂着勾——
 *   实际上只是把上一次缓存的结论又显示了一遍，用户看不出已经坏了。
 * - **勾**：今天真的确认过了，且今天没有可领的（= 已领取）。
 * - **灰叉**：还没确认完 / 今天确实没签。这里包含"缓存已经跨天"的情况：
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
    // ⚠️ **不看 loading 判"办完没办完"**：冷启动摆的是缓存里"今天这份"的结论，
    // 这一轮还在拉就把勾收走，等于让结论先闪没再回来。判断"还没确认完"只看
    // **有没有今天的数据**：`!available` 是压根没缓存，`status.stale` 是缓存已经跨天。
    // loading 只回答另一件事：这一轮是不是还在签到（→ 转圈）。
    val pending = !available || status.stale
    val done = available && !failed && !pending && !status.canClaim
    val phase = when {
        failed -> SignInRowState.Failed
        done -> SignInRowState.Done
        loading -> SignInRowState.Signing
        else -> SignInRowState.Pending
    }
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
        SignInStateIcon(state = phase)
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
    // 和 QQ 那行同一个四态：失败打叉、这一轮还在拉就转圈、没确认完打灰叉、确认领完了打勾。
    // ⚠️ **不看 loading**（第一行就是这么定的）：冷启动摆的是缓存里"今天已领完"的
    // 结论，这一轮还在拉就把勾收走，等于让结论先闪没再回来。
    // 判断"还没确认完"靠的是 **有没有今天的数据**：`!available`（压根没缓存）
    // 或 `status.stale`（缓存是前几天写的）。
    val failed = error != null
    val done = available && !failed && !status.stale && status.hasCard &&
        !status.expired && !status.canClaim
    val phase = when {
        failed -> SignInRowState.Failed
        done -> SignInRowState.Done
        loading -> SignInRowState.Signing
        else -> SignInRowState.Pending
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "悦享卡每日礼包",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = xinyueSummaryText(status, available, bound, loading),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 13.sp,
                color = if (failed) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
        SignInStateIcon(state = phase)
    }
}

/**
 * 签到行右侧那颗状态图标。
 *
 * 四态，颜色和图标一起换：
 * - [SignInRowState.Signing]：`CircularProgressIndicator` 的不确定态（转圈）。
 *   用 Miuix 的进度指示器而不是某个"加载中"图标——它不是图标，是"还在跑"这件事本身。
 * - [SignInRowState.Failed] / [SignInRowState.Pending]：红叉。**两态都红**：
 *   灰叉在浅色主题下几乎和正文一个色，扫一眼分不出"没办"和"办完了"；
 *   红色是这套卡片里唯一的"还差一步"信号（具体的失败原因由下面那行小字说）。
 * - [SignInRowState.Done]：绿勾。
 *
 * ⚠️ 两颗图标都用 **Demibold** 字重（`MiuixIcons.Demibold.*`）：默认那档在 18dp 上
 * 笔画偏细，和旁边 15sp Medium 的标题摆在一起显得发虚。
 *
 * ⚠️ 转圈那颗的轨道色设成透明：18dp 的环上再叠一圈 `secondaryContainer`，
 * 看起来像一颗实心圆环，反而看不出在转。
 */
@Composable
private fun SignInStateIcon(state: SignInRowState) {
    when (state) {
        SignInRowState.Signing -> CircularProgressIndicator(
            progress = null,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                backgroundColor = Color.Transparent,
            ),
            strokeWidth = 2.dp,
            size = 18.dp,
        )
        SignInRowState.Failed, SignInRowState.Pending -> Icon(
            imageVector = MiuixIcons.Demibold.Close,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MiuixTheme.colorScheme.error,
        )
        SignInRowState.Done -> Icon(
            imageVector = MiuixIcons.Demibold.Ok,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = signInAccentColor(),
        )
    }
}

/** 签到行右侧图标的四态。判定规则各行的差异写在各自的注释里。 */
private enum class SignInRowState { Signing, Pending, Failed, Done }

/** 周签到那行的副标题。没绑定时不显示"第几天"——那是"不知道"，不是"第 0 天"。 */
internal fun qqSummaryText(
    status: QqWeeklySignIn,
    available: Boolean,
    bound: Boolean,
    /**
     * 正在拉。
     *
     * ⚠️ 它**只在还没有今天的结论时才说话**：手上已经有一份今天的缓存时，
     * 这一轮拉没拉完不该改变显示的内容 —— 换成「正在确认…」等于让已经确认过的
     * 结论先闪没再回来（第一行的小程序签到就是这么定的，那三行必须一致）。
     */
    loading: Boolean = false,
    error: String? = null,
): String {
    if (!bound) return "需要 QQ 登录凭证"
    // 失败要说得比"没拿到"更重：进了这行说明之前是配好的，是这一轮坏了
    if (error != null) return "这一轮没拉到：${error.take(28)}"
    if (!available) return if (loading) "正在确认今天的状态…" else "还没拿到，点开看看"
    // 缓存跨天了：还没拿到今天的结论，别拿旧数字冒充
    if (status.stale) return if (loading) "正在确认今天的状态…" else "还没确认今天的状态"
    // 一周是 7 天：第 8 格是七天全签满之后的额外奖励，不当成一周里的第 8 天。
    val day = if (status.totalDays > 0) "本周 ${status.weekDay}/${status.totalDays}" else "第 ${status.day} 天"
    val reward = status.todayReward.takeIf { it.isNotBlank() }?.let { " · 今日 $it" } ?: ""
    // 末尾不挂「已领取」：那件事右侧的勾已经说了，同一行说两遍纯占地方。
    // 「可领」留着——它是"还有事没办"，勾表达不出来。
    val state = if (status.canClaim) " · 可领" else ""
    return "$day$reward$state"
}

/**
 * 超核那行的副标题。
 *
 * 「还没开始」优先于数字：活动没开时服务端也会给一份 holdList（所有 remain 都是 1），
 * 照着念会变成"签到 0/9、可领"，那是假话。
 */
internal fun supercoreSummaryText(
    status: SupercoreStatus,
    available: Boolean,
    bound: Boolean,
    loading: Boolean = false,
    error: String? = null,
): String {
    if (!bound) return "需要活动网址和凭证"
    if (error != null) return "这一轮没拉到：${error.take(28)}"
    if (status.notStarted) return status.message.ifBlank { "活动还没开始" }.take(30)
    if (!available) return if (loading) "正在确认今天的状态…" else "还没拿到，点开看看"
    if (status.stale) return if (loading) "正在确认今天的状态…" else "还没确认今天的状态"
    val parts = buildList {
        status.points?.let { add("积分 $it") }
        if (status.signTotal > 0) add("签到 ${status.signClaimed}/${status.signTotal}")
        // 「已抽 / 上限」而不是「剩余」：活动没开时 remain 就是上限 27，
        // 写"剩 27 次"会让人以为自己一次没抽却已经占了 27 次额度。
        if (status.drawsTotal != null && status.drawsRemain != null) {
            val used = (status.drawsTotal - status.drawsRemain).coerceAtLeast(0)
            add("抽奖 $used/${status.drawsTotal}")
        }
    }
    if (parts.isEmpty()) return "暂时拿不到活动数据"
    val prefix = if (status.signedToday) "今天已签到 · " else ""
    return prefix + parts.joinToString(" · ")
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
internal fun signInSummaryText(
    status: SignInStatus,
    available: Boolean,
    /** 这一轮拉失败的原因。和下面两行一样：进了这行说明之前是配好的，是这一轮坏了。 */
    error: String? = null,
): String {
    if (!available) return "登录后可见，打开 app 时会自动帮你签到"
    if (error != null) return "这一轮没拉到：${error.take(28)}"
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
