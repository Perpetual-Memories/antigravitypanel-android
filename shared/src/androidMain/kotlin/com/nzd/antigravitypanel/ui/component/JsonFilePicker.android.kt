package com.nzd.antigravitypanel.ui.component

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * [rememberJsonFilePicker] 的 Android 实现，走系统的 SAF（Storage Access Framework）。
 *
 * 用 SAF 而不是直接读 `/sdcard`：Android 11+ 的分区存储下，公共目录只有媒体文件能
 * 用 MediaStore 直接读，其它一律拿不到句柄。SAF 让用户自己选，也顺带免掉存储权限。
 *
 * MIME 列表最后带的通配项是刻意的——部分文件管理器给 `.json` 标的 MIME 是
 * `application/octet-stream`，只按 `application/json` 过滤会看不到文件。
 */
@Composable
actual fun rememberJsonFilePicker(onResult: (String?) -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        onResult(uri?.let { readTextOrNull(context, it) })
    }
    return remember(launcher) { { launcher.launch(MIME_TYPES) } }
}

private val MIME_TYPES = arrayOf(
    "application/json",
    "text/plain",
    "text/*",
    "*/*",
)

private fun readTextOrNull(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        stream.readBytes().decodeToString().removePrefix(BOM)
    }
}.getOrNull()

/** 从 Windows 上拷过来的文件偶尔带 BOM，不去掉会让 JSON 解析器把首字符认成乱码。 */
private const val BOM = "\uFEFF"
