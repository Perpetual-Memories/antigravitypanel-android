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
import com.nzd.antigravitypanel.data.xinyue.XinyueCardStatus
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
 * 签到页里「悦享卡每日礼包」那一块。
 *
 * 和上面两块并列，但**又要一套凭证**：心悦的 `T-OPENID` / `T-ACCESS-TOKEN`
 * 两个请求头。没填之前整块是引导，不是错误。
 *
 * 凭证和「自动领取」开关同样收进 [CredentialSheet]，轻触卡片呼出——
 * 和周签到那块一个套路，理由也相同。
 *
 * @param notice 领取结果的一次性提示，显示完要回调 [onConsumeNotice]。
 */
@Composable
fun XinyueSection(
    status: XinyueCardStatus,
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

    SmallTitle(text = "悦享卡每日礼包")
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
                // 留白用标准的 insideMargin，和弹层里那块、设置页条目一份
                BasicComponent(insideMargin = SettingsItemMargin) {
                    Text(
                        // 同上：心悦这边收的是两个**请求头**（T-OPENID / T-ACCESS-TOKEN），
                        // 不是 Cookie，照着小程序的那份抓必然绑不上
                        text = "轻触卡片填写请求头，和逆战未来工具箱小程序Cookie不通用，请自行抓取。",
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
                    Text("填入心悦凭证")
                }
            } else {
                val done = available && status.hasCard && !status.expired && !status.canClaim
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            loading && !available -> "同步中…"
                            !available -> "还没拿到状态"
                            !status.hasCard -> "没有悦享卡"
                            status.expired -> "已过期"
                            status.canClaim -> "今天可以领"
                            else -> "已领取"
                        },
                        modifier = Modifier.weight(1f),
                        // 字号字重和小程序那块「今天已签到」那行一致，理由同 QQ 那块
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (done) signInAccentColor() else MiuixTheme.colorScheme.onSurface,
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
                    // 只留「已领 / 到期」两格：
                    // 「状态」和上面那行大字是同一件事说两遍；「发往」是心悦返回的角色名，
                    // 绑的是哪个号用户自己知道，占一格不值。
                    InfoGrid(
                        items = buildList {
                            add(
                                "已领" to if (status.totalNum > 0) {
                                    "${status.gotNum} / ${status.totalNum}"
                                } else {
                                    "${status.gotNum}"
                                },
                            )
                            add("到期" to status.endDate.ifBlank { "—" })
                        },
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }

                if (available && status.hasCard && !status.expired && status.canClaim) {
                    Button(
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth(),
                        onClick = onClaim,
                        enabled = !claiming,
                    ) {
                        Text("领取每日礼包")
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
            title = "悦享卡每日礼包",
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
        hint = "在心悦 App 里进「我的卡 → 悦享卡」抓一次请求，把 T-OPENID 和 T-ACCESS-TOKEN " +
            "两个请求头粘进来（一行一个）。凭证只保存在本机（加密），不会上传到任何服务器；" +
            "重新登录心悦后会失效，那时得重新抓一次。",
        inputLabel = "心悦请求头",
        savedRaw = credentialRaw,
        bound = bound,
        autoClaim = autoClaim,
        autoClaimSummary = if (autoClaim) {
            "打开 app 时顺手领一次，每天最多一次"
        } else {
            "关着：需要手动点「领取每日礼包」"
        },
        error = error,
        onSave = onSaveCredential,
        onAutoClaimChange = onAutoClaimChange,
        onClear = onClearCredential,
        onDismiss = { sheet = false },
    )
}

/**
 * 概览那张卡里「悦享卡」那行的副标题。
 *
 * 三件事分开说：**有没有卡 / 过期没过期 / 今天领没领**。
 * 没绑定时不显示"0/30"——那是"不知道"，不是"0"。
 */
internal fun xinyueSummaryText(status: XinyueCardStatus, available: Boolean, bound: Boolean): String {
    if (!bound) return "需要心悦凭证"
    if (!available) return "还没拿到，点开看看"
    if (!status.hasCard) return "这个号没有悦享卡"
    if (status.expired) {
        return "已过期" + status.endDate.takeIf { it.isNotBlank() }?.let { " · $it 到期" }.orEmpty()
    }
    val progress = if (status.totalNum > 0) "已领 ${status.gotNum}/${status.totalNum}" else "已领 ${status.gotNum}"
    // 末尾不挂「已领取」：右侧的勾已经说了，一行里说两遍纯占地方。
    // 只有"还没领"才值得写出来——那是"还有事没办"，勾表达不出来。
    return if (status.canClaim) "$progress · 今天可领" else progress
}
