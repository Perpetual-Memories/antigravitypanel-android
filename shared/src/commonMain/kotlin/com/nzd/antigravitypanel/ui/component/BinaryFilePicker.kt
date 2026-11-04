package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable

/**
 * 系统文件选择器（SAF），返回**文件字节**。
 *
 * 和 [rememberTextFilePicker] 是两条路，不能合并：那边读的是 UTF-8 文本（`.json` / `.har`），
 * 这里读的是二进制数据包（`.zip`）。用文本那一条去接 zip 会把字节按字符集解码坏掉，
 * 再编码回来已经不是同一份文件了。
 *
 * 用户取消 / 读不出来 / 文件过大时回调 `null`。
 */
@Composable
expect fun rememberBinaryFilePicker(onResult: (ByteArray?) -> Unit): () -> Unit
