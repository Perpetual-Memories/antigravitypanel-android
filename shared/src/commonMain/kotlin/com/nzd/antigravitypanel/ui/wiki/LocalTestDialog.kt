package com.nzd.antigravitypanel.ui.wiki

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 把「本地测试」输入框里的内容补成可以直接交给 WebView 的地址。
 *
 * 规则：
 * - 带了 scheme 的只认 `http` / `https` —— `javascript:`、`file:` 这类既不该由
 *   网页加载入口放进来，也不该被一句 `loadUrl` 直接执行；
 * - 没带 scheme 的按主机名分流：本机 / 私有网段补 `http://`（本地 dev server
 *   基本都跑在明文 http 上），其余域名补 `https://`。
 *
 * 返回 null 表示这串没法访问（空串、夹空格、只有路径没有主机），
 * 调用方据此把对号置灰，而不是点了之后弹一个错误。
 */
fun normalizeLocalTestUrl(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty() || text.any { it.isWhitespace() }) return null

    val schemeEnd = text.indexOf("://")
    if (schemeEnd >= 0) {
        val scheme = text.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null
        // `http://` 后面什么都没有，等于没填主机
        if (text.length <= schemeEnd + 3) return null
        return text
    }

    // 没有 `://` 但长成 `xxx:...` 的样子：要么是 `host:port`（冒号后是纯数字端口），
    // 要么是 `javascript:` / `data:` / `file:` 这类不该放行的 scheme。
    // ⚠️ 不能只看"有没有冒号"—— `localhost:3000` 也是字母开头带冒号。
    val colon = text.indexOf(':')
    if (colon > 0) {
        val prefix = text.substring(0, colon)
        val looksLikeScheme = prefix.first().isLetter() &&
            prefix.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
        if (looksLikeScheme) {
            val afterColon = text.substring(colon + 1)
                .substringBefore('/').substringBefore('?').substringBefore('#')
            val isPort = afterColon.isNotEmpty() && afterColon.all(Char::isDigit)
            if (!isPort) return null
        }
    }

    val authority = text.substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isEmpty()) return null
    val host = if (authority.startsWith("[")) {
        // IPv6 字面量：`[::1]:3000`
        authority.substringBefore(']') + "]"
    } else {
        authority.substringBefore(':')
    }
    if (host.isEmpty()) return null

    val scheme = if (isLocalHost(host)) "http" else "https"
    return "$scheme://$text"
}

/** 本机或私有网段 —— 这些地址按 http 试，其余按 https。 */
private fun isLocalHost(host: String): Boolean {
    val lower = host.lowercase()
    if (lower == "localhost" || lower.endsWith(".localhost")) return true
    if (lower.endsWith(".local")) return true
    if (lower == "0.0.0.0" || lower.startsWith("[")) return true
    if (lower.startsWith("127.")) return true
    if (lower.startsWith("10.")) return true
    if (lower.startsWith("192.168.")) return true
    // 172.16.0.0 – 172.31.255.255
    return lower.startsWith("172.") && lower.removePrefix("172.").substringBefore('.')
        .toIntOrNull()?.let { it in 16..31 } == true
}

/**
 * 「更多 → 本地测试」的输入弹窗。
 *
 * 刻意**只有一行输入框**：这是调试入口，不写标题、不写说明 ——
 * 那种"请输入地址"的提示语在这里只会占掉一行高度，还把注意力从输入框上引开。
 * 确认动作摆在输入框右侧的对号里（`trailingIcon`），不另起按钮行，
 * 于是弹窗高度就只有输入框本身，软键盘弹起也不会把按钮顶出可视区。
 *
 * 地址非法时对号变淡且点不动；合法时点对号或键盘上的「前往」都会触发 [onConfirm]。
 */
@Composable
internal fun LocalTestDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val url = normalizeLocalTestUrl(value.text)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(show) {
        if (!show) {
            // 下次打开是空的，别把上一次试的地址留在框里
            value = TextFieldValue("")
            return@LaunchedEffect
        }
        // 等弹窗进场那一小段动画走完再抢焦点，否则键盘会弹在动画中间。
        // ⚠️ 慢设备上这一帧可能还没量完布局，FocusRequester 未挂上时会抛
        // IllegalStateException —— 抢不到焦点只是没自动弹键盘，不该让整页崩掉。
        delay(120)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    WindowDialog(
        // title / summary 都不传：这一层只有输入框
        show = show,
        onDismissRequest = onDismiss,
    ) {
        TextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { url?.let(onConfirm) }),
            trailingIcon = {
                IconButton(
                    onClick = { url?.let(onConfirm) },
                    enabled = url != null,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "访问",
                        tint = if (url != null) {
                            MiuixTheme.colorScheme.onSurface
                        } else {
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            },
        )
    }
}
