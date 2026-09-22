package com.nzd.antigravitypanel.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.ui.component.copyToClipboard
import com.nzd.antigravitypanel.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 概览里点凭证状态卡弹出的「账号详细信息」。
 *
 * 结构照官方 PC 端「系统设置 → 账号详细信息」那张卡：
 * 一段说明 → 一个 Cookie 输入框 → 一个只读的 ACCOUNT ID (OPENID) → 最下面一个退出登录。
 *
 * Cookie 输入框回显的是用户**粘贴的原文**（[NzCookie.raw]），不是解析后拼回去的串：
 * 拼回去会漏掉我们不认识的键，也会把被改写的 appid 掺进去，用户复制出去就用不了了。
 *
 * 左右留白**不加**：miuix 的 `OverlayBottomSheet` 已经在内容区套了 `insideMargin`（横向 24dp），
 * 再叠一层 20dp 就是 44dp，整块内容被挤得很窄。这里只补一个底部间距。
 *
 * @param onLogout 退出登录。此处**不该关弹层**：清掉凭证后弹层继续留在屏幕上，
 *   OPENID 变空、Cookie 输入框被清空，用户得重新粘一条才能再保存——
 *   这正是官方 PC 端退出后的状态（Account ID 显示「未登录」、Cookie 显示「暂无可用凭证」）。
 * @param onScanClick 右上角扫码按钮：扫 PC 端给出的二维码来登录。
 *   放在标题右侧（[OverlayBottomSheet] 的 `endAction`）而不是内容里——
 *   它是"换一种方式填这个框"，和这个弹层平级，摆进内容区会像是表单的一栏。
 */
@Composable
fun AccountDetailSheet(
    show: Boolean,
    cookie: NzCookie?,
    saving: Boolean = false,
    error: String? = null,
    onSave: (String) -> Unit,
    onLogout: () -> Unit,
    onScanClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cookieState = rememberTextFieldState()
    val openIdState = rememberTextFieldState()
    val input = remember { derivedStateOf { cookieState.text.toString().trim() } }.value
    val loggedIn = cookie != null

    // 两个框都只跟着已保存的凭证走：退出登录后 raw / openid 变空，
    // 这里会顺势把两个框清掉，逼用户重新粘一条。
    LaunchedEffect(show, cookie?.raw) {
        val wanted = cookie?.raw.orEmpty()
        if (cookieState.text.toString() != wanted) {
            cookieState.edit { replace(0, length, wanted) }
        }
    }
    LaunchedEffect(show, cookie?.openid) {
        val wanted = cookie?.openid.orEmpty()
        if (openIdState.text.toString() != wanted) {
            openIdState.edit { replace(0, length, wanted) }
        }
    }

    val openId = cookie?.openid.orEmpty()

    OverlayBottomSheet(
        show = show,
        title = "账号详细信息",
        allowDismiss = !saving,
        // 扫码登录的入口。登录态和未登录态都给：扫码本来就是"没凭证时最快的上手方式"，
        // 只在登出状态才显示的话，想换号登录的人反而找不到它
        endAction = {
            IconButton(onClick = onScanClick, enabled = !saving) {
                Icon(
                    imageVector = MiuixIcons.Scan,
                    contentDescription = "扫码登录",
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HintCard(loggedIn = loggedIn)

            TextField(
                state = cookieState,
                label = "Cookie",
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                // 保存做成输入框内的尾图标，照 HyperIsland 的 AI 通知摘要页那两个输入框
                // （它在输入框里塞的是"显示/隐藏"和"搜索"，同一个套路）。
                // 不再用下面那条通栏「保存」：输入框和它的操作应该是一体的，
                // 分开摆会把"填"和"存"拆成两步，还白占一行。
                trailingIcon = {
                    IconButton(
                        onClick = { if (input.isNotBlank()) onSave(input) },
                        enabled = input.isNotBlank() && !saving,
                    ) {
                        if (saving) {
                            CircularProgressIndicator(progress = null, size = 20.dp, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = MiuixIcons.Ok,
                                contentDescription = "保存 Cookie",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )

            // ACCOUNT ID 是给用户看的，点一下直接复制到剪贴板：
            // 它长到没法手抄，而用户拿它来干什么我们也管不着（多半是填到别的地方）。
            // 输入框保持 disabled，上面盖一层透明的可点击 Box 来接点击——
            // Box 的子节点是后加的先命中，禁用态的输入框不会吃掉这次点击。
            Box(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    state = openIdState,
                    label = "ACCOUNT ID（点击复制）",
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    enabled = false,
                    textStyle = MiuixTheme.textStyles.main.copy(
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    ),
                )
                if (openId.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { copyToClipboard(openId, "ACCOUNT ID") },
                            ),
                    )
                }
            }

            if (error != null) {
                Text(
                    text = error,
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }

            // 退出登录放最下面，并且只在真的有凭证时给出——
            // 已经退出去了还摆一个"退出"按钮，只会让人以为自己还登着
            if (loggedIn) {
                Button(
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    // 底色照 error 走，文案用纯白：默认的字数色是 onSecondaryVariant，
                    // 压在红底上偏灰，看着像"禁用态"
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
                    onClick = onLogout,
                ) {
                    Text("退出登录")
                }
            }
        }
    }
}

/**
 * 顶部的说明块：黄底圆角卡片，位置紧跟标题。
 *
 * 版式照 HyperIsland 的 AI 通知摘要页那块警告卡（`AiConfigPage` 的 `ai_warning`）：
 * 浅色 `0xFFFFF3D6` / 深色 `0xFF3A2D12`，横向 18dp、纵向 16dp。
 * 颜色直接用了凭证卡「过期」那一组 —— 同屏出现两块黄要是色调不一样，一眼就别扭。
 *
 * 挪到顶部是因为它是"打开这张卡先要知道的事"：Cookie 存在哪、怎么退出。
 * 原先压在最下面，得滚到底才看得到，等于没写。
 */
@Composable
private fun HintCard(loggedIn: Boolean) {
    val dark = isInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (dark) Color(0xFF3A2D12) else Color(0xFFFFF3D6),
        ),
    ) {
        Text(
            text = if (loggedIn) {
                "Cookie 只保存在本机（加密），不会上传到任何服务器；" +
                    "退出登录只删本机凭证，已经累积的对局不受影响。上面的 ACCOUNT ID 点击即可复制。"
            } else {
                "已退出登录。粘贴一条新的 Cookie 后点保存即可重新识别。"
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            fontSize = 13.sp,
            color = if (dark) Color(0xFFFFD978) else Color(0xFF704D00),
        )
    }
}
