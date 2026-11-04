package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable

/**
 * 系统保存对话框（SAF），专门用来写 zip。
 *
 * 为什么不复用 [rememberTextFileSaver]：那个是按文本写的（`String` → UTF-8 字节），
 * 而数据包是**已经是字节**的二进制，再走一遍字符串会把非 UTF-8 的内容弄坏。
 * 另外 `CreateDocument` 的 MIME 是构造时定死的，多一个 zip 就得单独注册一个 launcher。
 *
 * @param onResult 用户选了位置且写成功了给 true；取消 / 写失败给 false。
 */
@Composable
expect fun rememberZipFileSaver(
    onResult: (saved: Boolean) -> Unit,
): (fileName: String, bytes: ByteArray) -> Unit
