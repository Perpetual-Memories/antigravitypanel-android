package com.nzd.antigravitypanel.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 屏幕圆角半径（dp）。照 Miuix 的 `rememberNavSystemCornerRadius`：
 *
 * 1. API 31+ 走标准接口 `WindowInsets.getRoundedCorner(POSITION_BOTTOM_LEFT)`，
 *    这是系统给应用的标准答案，比猜资源名可靠；
 * 2. 老设备 / 拿不到 insets 时退回 framework 的 `rounded_corner_radius_bottom` dimen；
 * 3. 都没有就是 0 —— 真平屏不该硬造圆角。
 */
@Composable
internal actual fun rememberScreenCornerRadius(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val view = LocalView.current
    val insets = view.rootWindowInsets
    val radiusPx = remember(context, view, insets) {
        val fromInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.takeIf { it > 0 }
        } else {
            null
        }
        fromInsets ?: bottomCornerRadiusFromResources(context)
    }
    return (radiusPx / density).dp
}

@SuppressLint("DiscouragedApi")
private fun bottomCornerRadiusFromResources(context: Context): Int {
    val id = context.resources.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
    return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
}
