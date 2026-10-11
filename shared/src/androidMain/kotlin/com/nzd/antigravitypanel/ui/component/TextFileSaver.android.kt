package com.nzd.antigravitypanel.ui.component

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * [rememberTextFileSaver] 的 Android 实现。
 *
 * MIME 决定用哪个 launcher：`CreateDocument` 的 MIME 是**构造时**定死的、
 * 只有文件名能运行时传，所以 CSV / JSON 各注册一个，按调用方给的 MIME 分派。
 * 两个都注册而不是只注册一个通配 MIME，是因为通配符下部分文件管理器不会给默认扩展名，
 * 导出的文件会变成没有后缀的 `matches_20261008`。
 *
 * 待写的内容先放进 [PendingContent] 再启动选择器：SAF 回调是在另一个时间点触发的，
 * 拿 State 去接有可能读到启动前那一帧的旧值，用一个可变盒子最省心。
 */
@Composable
actual fun rememberTextFileSaver(
    onResult: (saved: Boolean) -> Unit,
): (fileName: String, mimeType: String, content: String) -> Unit {
    val context = LocalContext.current
    val pending = remember { PendingContent() }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_CSV),
    ) { uri -> onResult(writeOrFalse(context, uri, pending.content)) }

    val jsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri -> onResult(writeOrFalse(context, uri, pending.content)) }

    return remember(csvLauncher, jsonLauncher) {
        { fileName: String, mimeType: String, content: String ->
            pending.content = content
            if (mimeType == MIME_JSON) jsonLauncher.launch(fileName) else csvLauncher.launch(fileName)
        }
    }
}

private class PendingContent {
    var content: String? = null
}

private const val MIME_CSV = "text/csv"
private const val MIME_JSON = "application/json"

/**
 * 把内容写进用户选的 Uri。
 *
 * 用 `openOutputStream` 而不是先写临时文件再拷：SAF 给的 Uri 直接可写，
 * 临时文件那套还要处理清理，导出失败一次就留一个垃圾文件在私有目录里。
 */
private fun writeOrFalse(context: Context, uri: Uri?, content: String?): Boolean {
    if (uri == null || content == null) return false
    return runCatching {
        val stream = context.contentResolver.openOutputStream(uri) ?: return false
        stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        true
    }.getOrDefault(false)
}
