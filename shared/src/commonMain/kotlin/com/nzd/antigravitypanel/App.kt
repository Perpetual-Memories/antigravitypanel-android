package com.nzd.antigravitypanel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import com.nzd.antigravitypanel.ui.theme.AppTheme
import com.nzd.antigravitypanel.ui.theme.ColorMode
import kotlinx.coroutines.flow.drop

/**
 * 应用根 composable。
 *
 * @param padding 外部传入的窗口内边距（宽屏分栏时非 0）。
 * @param onColorModeChange 主题模式变化回调，供 Activity 同步系统栏配色。
 */
@Composable
fun App(
    padding: PaddingValues = PaddingValues(0.dp),
    onColorModeChange: (Int) -> Unit,
) {
    var colorMode by remember { mutableIntStateOf(ColorMode.SYSTEM) }
    val currentOnColorModeChange by rememberUpdatedState(onColorModeChange)
    LaunchedEffect(Unit) {
        snapshotFlow { colorMode }.drop(1).collect { currentOnColorModeChange(it) }
    }

    AppTheme(colorMode = colorMode) {
        AppContent(
            padding = padding,
            colorMode = colorMode,
            onColorModeChange = { colorMode = it },
        )
    }
}
