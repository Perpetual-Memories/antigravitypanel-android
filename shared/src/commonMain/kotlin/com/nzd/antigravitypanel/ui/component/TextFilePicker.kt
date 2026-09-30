package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable

/**
 * 系统文件选择器，返回一个"点了就弹选择器"的启动函数。
 *
 * 返回的是**文件内容**（UTF-8 文本）而不是 Uri / 路径：SAF 给的 Uri 只在被授予的
 * 那一次能读，activity 重建后就没了；把读文件这一步也放在平台侧，commonMain
 * 拿到的就是可以直接解析的字符串。
 *
 * 用户取消、或选完读不出来时回调 `null`，调用方不用区分这两种情况。
 *
 * 叫"文本"而不是"JSON"是因为它同时供两处用：导入 `nzm_matches.json` 的对局，
 * 和导入抓包工具导出的 `.har`（都是 UTF-8 文本，走的同一个选择器）。
 */
@Composable
expect fun rememberTextFilePicker(onResult: (String?) -> Unit): () -> Unit
