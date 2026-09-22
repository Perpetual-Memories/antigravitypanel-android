package com.nzd.antigravitypanel.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzd.antigravitypanel.data.update.AppUpdate
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 一次检查的结局。照 HyperIsland 的 `UpdateDialogState`。 */
internal sealed interface UpdateCheckState {

    /** 确实有新版。 */
    data class Available(
        val currentVersion: String,
        val update: AppUpdate,
    ) : UpdateCheckState

    /** 没查成（网络不通 / GitHub 抽风）。 */
    data object Failed : UpdateCheckState
}

/**
 * 检查更新的两个结局各一个弹窗。
 *
 * 「已是最新」**不用弹窗**：那是一个不用用户做任何决定的结论，
 * 弹窗逼他点一下"好的"纯属浪费，交给 Toast 就够了。
 */
@Composable
internal fun UpdateCheckDialogs(
    state: UpdateCheckState?,
    onDismiss: () -> Unit,
    onDownload: (AppUpdate) -> Unit,
) {
    val available = state as? UpdateCheckState.Available
    WindowDialog(
        show = available != null,
        title = "发现新版本",
        onDismissRequest = onDismiss,
    ) {
        if (available != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "当前版本  v${available.currentVersion}",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = "最新版本  v${available.update.version}",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                if (available.update.changelog.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MiuixTheme.colorScheme.dividerLine),
                    )
                    ReleaseNotes(available.update.changelog)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { onDownload(available.update) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("前往下载")
                    }
                }
            }
        }
    }

    WindowDialog(
        show = state == UpdateCheckState.Failed,
        title = "检查更新失败",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // api.github.com 在国内时好时坏，说清楚"可能是网络"，别让人以为 app 坏了
            Text(
                text = "没能连上 GitHub 的检查接口，多半是网络问题，稍后再试一次。",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("好的")
            }
        }
    }
}

/**
 * 更新日志。GitHub 的 release 正文是 markdown，这里只挑出最常见的三种行
 * 转成纯文本（`ReleaseNotes` 的做法照 HyperIsland）：
 * 一级标题、列表项、普通段落。链接保留成「文字 (地址)」，不然网址会吃掉半句。
 */
@Composable
private fun ReleaseNotes(changelog: String) {
    val lines = changelog.trim().lines()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        items(lines) { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(3.dp))

                line.startsWith("#") -> Text(
                    text = markdownPlainText(line.trimStart('#').trim()),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                line.startsWith("- ") || line.startsWith("* ") -> Text(
                    text = "• ${markdownPlainText(line.drop(2))}",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                else -> Text(
                    text = markdownPlainText(line),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 去掉最常见的几种行内标记：链接、加粗、下划线、代码。 */
private fun markdownPlainText(source: String): String = source
    .replace(MARKDOWN_LINK) { match -> "${match.groupValues[1]} (${match.groupValues[2]})" }
    .replace("**", "")
    .replace("__", "")
    .replace("`", "")

private val MARKDOWN_LINK = Regex("""\[([^]]+)]\(([^)]+)\)""")
