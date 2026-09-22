package com.nzd.antigravitypanel.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.ui.component.CardCornerRadius
import com.nzd.antigravitypanel.ui.settings.SettingsItemMargin
import com.nzd.antigravitypanel.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 那句风险提示。三块签到共用一套文案，改一处即可。 */
internal const val SIGNIN_RISK_NOTICE =
    "使用APP手动或自动签到不排除封号或账号被标记的可能，风险请自行考量承担，" +
        "APP不对您账号造成的任何损失负责"

/**
 * 轻触签到卡片呼出的「cookie 设定」。QQ 游戏中心周签到和心悦悦享卡共用一份。
 *
 * 之所以收进弹层而不是摊在页面上：**凭证和"自动领取"是配置，不是每天要看的信息**。
 * 摊开的话一屏里一半是输入框和警告，真正要看的"今天领没领"被挤到角落；
 * 收进弹层后卡片上只留状态和数字，配置按需取用。
 *
 * 版式照「账号详细信息」那个弹层（`AccountDetailSheet`）：
 * 纵向 14dp 间距、只补底部间距（`OverlayBottomSheet` 已经套了横向 insideMargin）。
 *
 * 保存按钮在**输入框内部最右侧**（照 HyperIsland 的 AI 通知摘要页里 API Key / 模型
 * 那两个 `TextField` 的 `trailingIcon`）：输入框和它的操作应该是一体的，
 * 摊在下面做一条通栏按钮会把"填"和"存"拆成两步，还白占一行。
 *
 * @param hint 顶部提示文字。套在 Card 里，**不是可点条目**。
 * @param savedRaw 已保存的凭证原文，用于回显。跟着它走：清除凭证后输入框会顺势清空。
 */
@Composable
fun CredentialSheet(
    show: Boolean,
    hint: String,
    inputLabel: String,
    savedRaw: String,
    bound: Boolean,
    autoClaim: Boolean,
    autoClaimSummary: String,
    error: String?,
    onSave: (String) -> Unit,
    onAutoClaimChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTextFieldState()
    var confirmClear by remember { mutableStateOf(false) }

    // 只跟着已保存的原文走：清除凭证后 savedRaw 变空，这里顺势把输入框清掉。
    // 条件判断不能省——无脑赋值会把用户正在输入的内容冲掉。
    LaunchedEffect(show, savedRaw) {
        if (state.text.toString() != savedRaw) {
            state.edit { replace(0, length, savedRaw) }
        }
    }

    OverlayBottomSheet(
        show = show,
        title = "Cookie 设定",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HintCard(text = hint)

            val input = state.text.toString()
            TextField(
                state = state,
                label = inputLabel,
                modifier = Modifier.fillMaxWidth(),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                // 保存做成输入框内的尾图标：照 HyperIsland 的 AI 通知摘要那两个输入框。
                // 点它就存——没有"保存成功"之外的话要说，失败由下面那行红字负责。
                trailingIcon = {
                    IconButton(
                        onClick = { if (input.isNotBlank()) onSave(input) },
                        enabled = input.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = "保存凭证",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )

            // 已经存过就不用再谎报一次；"已保存"这三个字得跟着**当前保存值**走，
            // 用户在输入框里改着的时候不该显示。
            if (bound && error == null && input == savedRaw) {
                Text(
                    text = "已保存到本机",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            if (error != null) {
                Text(
                    text = error,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.error,
                )
            }

            RiskNotice()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
            ) {
                SwitchPreference(
                    checked = autoClaim,
                    onCheckedChange = onAutoClaimChange,
                    title = "打开APP自动签到",
                    summary = autoClaimSummary,
                    insideMargin = SettingsItemMargin,
                )
            }

            // 清除凭证只在真的存过时才给：没凭证还摆一个"清除"会让人以为自己配过。
            // 红色长条 + 白字，和「退出登录」那颗一个套路；清掉之后不可恢复，
            // 所以点了先弹一次确认。
            if (bound) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
                    onClick = { confirmClear = true },
                ) {
                    Text("清除凭证")
                }
            }
        }
    }

    ClearCredentialDialog(
        show = confirmClear,
        onDismiss = { confirmClear = false },
        onConfirm = {
            confirmClear = false
            onClear()
        },
    )
}

/**
 * 说明文字块。样式照 **HyperIsland「AI 通知摘要」页最底下那张 `ai_tips` 卡**：
 * 副色底 + `body2` 字号的灰色说明，横向 18dp、纵向 16dp。
 *
 * 之所以是副色底而不是普通卡片色：这块是"该怎么用"的说明，不是数据，
 * 和周围的灰卡片拉开一点色差，眼睛才会把它当成另一类东西。
 * **注意别用这套配色去做告警**——告警是下面 [RiskNotice] 那块黄底棕字。
 *
 * 内部走 `BasicComponent`（**不传 onClick**：它不是可点条目，不能有按下反馈），
 * `insideMargin` 直接给 18×16，等价于 HyperIsland 那层 padding。
 *
 * 弹层和二级页那两块未绑定的卡片共用一份，改版式只改这里。
 */
@Composable
fun HintCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer,
            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        BasicComponent(insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = text,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 黄底棕字的风险提示。
 *
 * 配色沿用凭证卡「过期」那组（`AccountDetailSheet.HintCard` 同款）：
 * 浅色 `0xFFFFF3D6` / 深色 `0xFF3A2D12`，字色 `0xFF704D00` / `0xFFFFD978`。
 * 顶部那块提示是普通卡片（"这是什么"），这块是警告（"想清楚再用"），
 * 两者必须一眼能分开，所以只有这块上黄底。
 */
@Composable
private fun RiskNotice() {
    val dark = isInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        colors = CardDefaults.defaultColors(
            color = if (dark) Color(0xFF3A2D12) else Color(0xFFFFF3D6),
        ),
    ) {
        Text(
            text = SIGNIN_RISK_NOTICE,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            fontSize = 13.sp,
            color = if (dark) Color(0xFFFFD978) else Color(0xFF704D00),
        )
    }
}

@Composable
private fun ClearCredentialDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "清除凭证",
        onDismissRequest = onDismiss,
    ) {
        Column {
            Text(
                text = "将删除本机保存的这段凭证和对应的缓存，删除后需要重新抓一次才能再用。",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Row(modifier = Modifier.padding(top = 16.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(),
                    onClick = onDismiss,
                ) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
                    onClick = onConfirm,
                ) {
                    Text("清除")
                }
            }
        }
    }
}
