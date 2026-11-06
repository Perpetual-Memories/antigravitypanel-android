package com.nzd.antigravitypanel.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** Reqable 官网。链接点了走系统默认浏览器（`LinkAnnotation.Url` 最终走 `LocalUriHandler`）。 */
private const val REQABLE_URL = "https://reqable.com"

/**
 * 点凭证状态卡、但本机还没有 Cookie 时先弹这条对话框。
 *
 * 做成"先看说明、再决定要不要填"两步，是因为抓 Cookie 这件事本身有门槛：
 * 直接把输入框怼上去，用户往往不知道那一长串该从哪复制，就会直接关掉 app。
 * 让「先去获取」成为一个体面的退出按钮，比留一个"取消"更容易理解。
 *
 * 已 root 那条路线写得**具体到每一步**（装 CA → 小飞机 → 打开哪个小程序的哪个页面 →
 * 导出 → 从哪导入）而不是"自己抓一下包"：这条路每一步都能卡住人，而卡在哪一步
 * 用户自己说不清，只会回一句"抓不到"。
 *
 * @param onGoGet 点「先去获取」：关掉对话框，用户去抓 cookie，之后再点卡进来。
 * @param onAlreadyHave 点「我已知悉」：直接进「账号详细信息」填 / 扫码。
 *   文案不说「已有Cookie」是因为下面第三条路（扫码）不需要用户手里先有 Cookie，
 *   说「已有」会把这条路堵死——按钮的意思只是"看完了，下一步"。
 */
@Composable
fun CookieGuideDialog(
    show: Boolean,
    onGoGet: () -> Unit,
    onAlreadyHave: () -> Unit,
) {
    // Reqable 那个词用主题的主色染一下 + 下划线：这块正文全是灰字，
    // 没有颜色差异的话没人知道「Reqable」是可以点的（点了走默认浏览器打开官网）。
    val linkColor = MiuixTheme.colorScheme.primary
    val rootDetail = buildAnnotatedString {
        append("安装 ")
        withLink(
            LinkAnnotation.Url(
                url = REQABLE_URL,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
            ),
        ) {
            append("Reqable")
        }
        append(
            "并在应用引导下安装 CA。" +
                "点右下角小飞机按钮开始抓取后，打开微信小程序“逆战未来工具箱”，" +
                "并点开“我的”，之后停止抓取，并导出抓取结果。" +
                "在账号详细信息的对话框左上角导入文件，" +
                "或在导出的 HAR 文件中找到包含 openid、acctype、appid、access_token 的内容，" +
                "粘贴到对话框中。",
        )
    }

    WindowDialog(
        show = show,
        title = "如何获取 Cookie",
        onDismissRequest = onGoGet,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 扫码这条路**不需要用户手里先有 Cookie**——PC 端把当前账号的凭证编成二维码，
            // 手机扫一下就完事，比在电脑和手机之间来回粘一长串快得多，所以它排在第一条。
            // 这段把 PC 端怎么出码、手机端从哪扫码**两头都说了**：
            // 只说一头的话用户会卡在"二维码在哪"上，最后还是回去手动抓。
            // 微信区多一条"另一部设备的微信直接扫"的方案：那条路能绕开本机装不装证书的问题。
            GuideBranch(
                title = "手机无 root",
                detail = buildAnnotatedString {
                    append(
                        "推荐使用扫码登录。自 PC 端反重力数据面板 1.8.9 起，" +
                            "可在系统设置-账号详细信息中查看 Cookie 二维码，" +
                            "轻触 APP 首页 Cookie 状态卡，在弹出的面板右上角可找到扫码入口。" +
                            "另外，针对微信区账号，还额外提供使用另一部设备的微信直接扫码登录的方案。",
                    )
                },
            )
            GuideBranch(
                title = "手机已 root",
                detail = rootDetail,
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
                    Text("我已知悉")
                }
            }
        }
    }
}

@Composable
private fun GuideBranch(title: String, detail: AnnotatedString) {
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
