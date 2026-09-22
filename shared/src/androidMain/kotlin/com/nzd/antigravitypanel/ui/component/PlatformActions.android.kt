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
