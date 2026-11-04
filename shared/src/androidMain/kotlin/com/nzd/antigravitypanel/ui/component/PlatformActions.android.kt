package com.nzd.antigravitypanel.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.nzd.antigravitypanel.requireContext

actual fun openUrl(url: String) {
    val context = requireContext()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

actual fun copyToClipboard(text: String, label: String) {
    val context = requireContext()
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}

actual fun shareText(text: String, title: String) {
    val context = requireContext()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (title.isNotBlank()) putExtra(Intent.EXTRA_TITLE, title)
    }
    // ⚠️ 必须手动加选择器：直接 startActivity 会走系统默认，
    // 在某些 ROM 上会弹一个"仅此一次 / 始终"的二选一，用户一不留神就被钉死了。
    val chooser = Intent.createChooser(intent, title.ifBlank { null }).apply {
        // ⚠️⚠️ `FLAG_ACTIVITY_NEW_TASK` 必须加在**选择器**上，加在里面那条
        // intent 上等于没加：`Intent.createChooser` 只从目标里拷 URI 授权那几个
        // flag（AOSP 行为），NEW_TASK 不在其中。而这里是从 Application 起的
        // Activity，少了它就抛 AndroidRuntimeException，又被下面那个 runCatching
        // 一口吞掉 —— 表现就是点了「分享链接」毫无反应，看着像没实现。
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // 起不来就吱一声：静默失败会让用户以为这个功能还没做
    runCatching { context.startActivity(chooser) }
        .onFailure { showToast("没有可以接收分享的应用") }
}

actual fun showToast(text: String) {
    val context = requireContext()
    // Compose 的点击回调就在主线程上，直接 show 即可；
    // 不套 Handler.post 是因为那样会把提示推到下一帧之后，连点两次会显得迟钝
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

actual fun appVersionName(): String {
    val context = requireContext()
    return runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: ""
    }.getOrDefault("")
}
