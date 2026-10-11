package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable

/**
 * 系统保存对话框（SAF），返回一个"给文件名 + MIME + 内容就弹保存位置"的启动函数。
 *
 * 和 [rememberTextFilePicker] 是一对：那边读、这边写，都走 SAF，
 * 免掉存储权限（Android 11+ 的分区存储下公共目录不给直接写的句柄）。
 *
 * 内容是 UTF-8 文本，平台侧写完就完事，不返回 Uri —— 那个 Uri 只在被授予的那一次能用，
 * 交回 commonMain 也留不住。
 *
 * @param onResult 用户选了位置且写成功了给 true；取消 / 写失败给 false。
 */
@Composable
expect fun rememberTextFileSaver(
    onResult: (saved: Boolean) -> Unit,
): (fileName: String, mimeType: String, content: String) -> Unit
