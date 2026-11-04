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
 * [rememberZipFileSaver] 的 Android 实现。
 *
 * 待写的字节先放进 [PendingBytes] 再启动选择器：SAF 回调是在另一个时间点触发的，
 * 拿 State 去接有可能读到启动前那一帧的旧值。
 */
@Composable
actual fun rememberZipFileSaver(
    onResult: (saved: Boolean) -> Unit,
): (fileName: String, bytes: ByteArray) -> Unit {
    val context = LocalContext.current
    val pending = remember { PendingBytes() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_ZIP),
    ) { uri -> onResult(writeOrFalse(context, uri, pending.bytes)) }

    return remember(launcher) {
        { fileName: String, bytes: ByteArray ->
            pending.bytes = bytes
            launcher.launch(fileName)
        }
    }
}

private class PendingBytes {
    var bytes: ByteArray? = null
}

private const val MIME_ZIP = "application/zip"

/**
 * 把字节写进用户选的 Uri。
 *
 * 用 `openOutputStream` 而不是先写临时文件再拷：SAF 给的 Uri 直接可写，
 * 临时文件那套还要处理清理，导出失败一次就留一个垃圾文件在私有目录里。
 */
private fun writeOrFalse(context: Context, uri: Uri?, bytes: ByteArray?): Boolean {
    if (uri == null || bytes == null) return false
    return runCatching {
        val stream = context.contentResolver.openOutputStream(uri) ?: return false
        stream.use { it.write(bytes) }
        true
    }.getOrDefault(false)
}
