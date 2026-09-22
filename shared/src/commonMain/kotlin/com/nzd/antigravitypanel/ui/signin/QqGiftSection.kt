package com.nzd.antigravitypanel.ui.signin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.qq.QqWeeklySignIn
import com.nzd.antigravitypanel.ui.component.InfoGrid
import com.nzd.antigravitypanel.ui.settings.SettingsItemMargin
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 签到页里「QQ游戏中心周签到礼包」那一块。
 *
 * 和上面的小程序每日签到并列，但**是另一套凭证**：要用户单独粘一段 QQ 登录 cookie
 * （`uin` / `p_skey` 那串）。没粘之前整块是引导，不是错误。
 *
 * 凭证和「自动领取」开关**收进了弹层**（[CredentialSheet]）：天天要看的只有
 * "今天领没领"和几个数字，输入框和警告摊在页面上会把它们挤到角落。
 * 轻触这张卡片呼出弹层——卡片本身不跳转，只是个开关。
 *
 * @param notice 领取结果的一次性提示，显示完要回调 [onConsumeNotice]。
 */
@Composable
fun QqGiftSection(
    status: QqWeeklySignIn,
    bound: Boolean,
    available: Boolean,
    loading: Boolean,
    claiming: Boolean,
    error: String?,
    notice: String?,
    /** 已保存的凭证原文，弹层里回显用。 */
    credentialRaw: String,
    autoClaim: Boolean,
    onClaim: () -> Unit,
    onSaveCredential: (String) -> Unit,
    onAutoClaimChange: (Boolean) -> Unit,
    onClearCredential: () -> Unit,
    onConsumeNotice: () -> Unit,
) {
    var sheet by remember { mutableStateOf(false) }

    SmallTitle(text = "QQ游戏中心周签到礼包")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = { sheet = true },
    ) {
        // 未绑定时整块交给 BasicComponent 自己管留白，外面不再垫 16dp——
        // 垫了会变成 16+18 双层留白，比已绑定时的文字还往里缩一截
        Column(modifier = if (bound) Modifier.padding(16.dp) else Modifier) {
            if (!bound) {
                // 说明文字走 BasicComponent（**不传 onClick**：它不是可点条目），
                // 留白用标准的 insideMargin，和弹层里那块、设置页条目一份。
                // 外面这张 Card 就是"套在外面的 Card"，所以这里不再垫一层 16dp——
                // 垫了会变成 16+18 双层留白，文字比已绑定时那块还往里缩。
                BasicComponent(insideMargin = SettingsItemMargin) {
                    Text(
                        // 必须点名"和小程序的 Cookie 不通用"：这是最常被问的一句，
                        // 用户手上已经有一段抓好的 Cookie，直接粘进来会一直绑不上
                        text = "轻触卡片填写登录凭证，和逆战未来工具箱小程序Cookie不通用，请自行抓取。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Button(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp)
                        .fillMaxWidth(),
                    onClick = { sheet = true },
                ) {
                    Text("填入 QQ 登录凭证")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            loading && !available -> "同步中…"
                            !available -> "还没拿到状态"
                            status.canClaim -> "今天可以领"
                            // 领完 canGot 就翻成 false。除了"今天领过了"，
                            // 理论上也可能是活动停了，但官方这个入口常年挂着，
                            // 99% 的情况就是领过了——说"今天不能领"反而让人反复点。
                            else -> "已领取"
                        },
                        modifier = Modifier.weight(1f),
                        // 字号字重和上面「今天已签到」那行一致：三块是并列的，
                        // 领完了就是"今天这件事办完了"，视觉权重也该一样
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (available && !status.canClaim) {
                            signInAccentColor()
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        },
                    )
                    if (claiming) {
                        CircularProgressIndicator(
                            progress = null,
                            size = 22.dp,
                            strokeWidth = 3.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.primary,
                                backgroundColor = MiuixTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }

                if (available) {
                    // 版式和小程序那块一致：两列指标，左边进度、右边奖励。
                    // 不列整周的奖励清单——概览不是礼包列表，七天各发什么点开官方看更准。
                    //
                    // 「连续签到」取 weekDay 而不是 day：day 会走到 8，但那不是一周里的
                    // 第 8 天，是七天全签满之后的额外奖励，显示成「8 天」是错的。
                    //
                    // 本周签满之后服务端通常不再给 nextSignProps，这时下一格就是满签奖励。
                    val next = status.nextReward.ifBlank {
                        status.fullWeekBonus.takeIf { status.weekDay >= status.totalDays }.orEmpty()
                    }
                    InfoGrid(
                        items = buildList {
                            add("连续签到" to "${status.weekDay} 天")
                            add("本周" to "${status.weekDay}/${status.totalDays}")
                            add("今日奖励" to status.todayReward.ifBlank { "—" })
                            add("明日奖励" to next.ifBlank { "—" })
                        },
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }

                if (available && status.canClaim) {
                    Button(
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth(),
                        onClick = onClaim,
                        enabled = !claiming,
                    ) {
                        Text("领取周签到礼包")
                    }
                }

                if (error != null) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(top = 12.dp),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    // 领取结果是"一次性"的：弹一次就清掉，不然下次进这页还会挂着一句旧话
    if (notice != null) {
        WindowDialog(
            show = true,
            title = "QQ游戏中心周签到礼包",
            onDismissRequest = onConsumeNotice,
        ) {
            Column {
                Text(
                    text = notice,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Button(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    onClick = onConsumeNotice,
                ) {
                    Text("好的")
                }
            }
        }
    }

    CredentialSheet(
        show = sheet,
        hint = "在 QQ 里进游戏中心的礼包页抓一次请求，把 Cookie 请求头整条粘进来。" +
            "里面要有 uin 和 p_skey。凭证只保存在本机（加密），不会上传到任何服务器；" +
            "重新登录 QQ 后会失效，那时得重新抓一次。",
        inputLabel = "QQ Cookie",
        savedRaw = credentialRaw,
        bound = bound,
        autoClaim = autoClaim,
        autoClaimSummary = if (autoClaim) {
            "打开 app 时顺手领一次，每天最多一次"
        } else {
            "默认关着：那个接口是批量领取，会把服务端认为能领的礼包一起领掉"
        },
        error = error,
        onSave = onSaveCredential,
        onAutoClaimChange = onAutoClaimChange,
        onClear = onClearCredential,
        onDismiss = { sheet = false },
    )
}
