package com.nzd.antigravitypanel.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 点凭证状态卡、但本机还没有 Cookie 时先弹这条对话框。
 *
 * 做成"先看说明、再决定要不要填"两步，是因为抓 Cookie 这件事本身有门槛：
 * 直接把输入框怼上去，用户往往不知道那一长串该从哪复制，就会直接关掉 app。
 * 让「先去获取」成为一个体面的退出按钮，比留一个"取消"更容易理解。
 *
 * @param onGoGet 点「先去获取」：关掉对话框，用户去抓 cookie，之后再点卡进来。
 * @param onAlreadyHave 点「已有Cookie」：直接进「账号详细信息」填。
 */
@Composable
fun CookieGuideDialog(
    show: Boolean,
    onGoGet: () -> Unit,
    onAlreadyHave: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "如何获取 Cookie",
        onDismissRequest = onGoGet,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Cookie 只保存在本机（加密），不会上传到任何服务器。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
            )

            GuideBranch(
                title = "手机已 root",
                detail = "装 Reqable，抓取微信小程序的 HTTPS 流量，" +
                    "在请求头里复制整条 Cookie。",
            )
            GuideBranch(
                title = "手机没有 root",
                detail = "用电脑版「反重力数据面板」抓取，再把 Cookie 粘到手机。",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "先去获取",
                    onClick = onGoGet,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onAlreadyHave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("已有Cookie")
                }
            }
        }
    }
}

@Composable
private fun GuideBranch(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "·",
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
        Text(
            text = detail,
            modifier = Modifier.padding(start = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
        )
    }
}
