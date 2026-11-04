package com.nzd.antigravitypanel.ui.component

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * [rememberBinaryFilePicker] 的 Android 实现。
 *
 * MIME 列表里 `application/zip` 之外还带了 JSON / 纯文本：老版本导出的
 * `nzm_matches.json` 仍然能从同一个入口导入，不用让用户先想"我这份是哪种"。
 * 最后那个通配项是刻意的 —— 部分文件管理器给 `.zip` 标的类型是
 * `application/octet-stream`，只按精确类型过滤会在选择器里看不到文件。
 */
@Composable
actual fun rememberBinaryFilePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        onResult(uri?.let { readBytesOrNull(context, it) })
    }
    return remember(launcher) { { launcher.launch(MIME_TYPES) } }
}

private val MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/json",
    "text/plain",
    "*/*",
)

/**
 * 读文件内容，超过 [MAX_PICK_BYTES] 当读不出来处理。
 *
 * 必须边读边数，不能先 `readBytes()` 再判断大小 —— 那一下已经把整份文件塞进内存了，
 * 真要防的就是这一步。数据包正常也就几 MB，64MB 足够，再大多半是选错文件。
 *
 * 注：返回 null 同时表示"取消"和"读不出来"，调用方不用区分（和文本选择器一致）。
 */
private fun readBytesOrNull(context: Context, uri: Uri): ByteArray? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        readCapped(stream)
    }
}.getOrNull()

private fun readCapped(stream: InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        total += read
        require(total <= MAX_PICK_BYTES) { "文件太大" }
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private const val MAX_PICK_BYTES = 64L * 1024 * 1024
