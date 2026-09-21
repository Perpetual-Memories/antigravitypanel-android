package com.nzd.antigravitypanel.ui.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 图鉴（P0 骨架）。武器 / 陷阱 / 插件 / 角色等由 `collection.*` 系列接口下发。 */
@Composable
fun CollectionScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "图鉴数据将在接入配置接口后加载",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
