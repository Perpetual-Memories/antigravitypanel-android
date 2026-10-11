package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * 设备自身的屏幕圆角半径。二级详情页跟手时按它裁剪前缘，做出 MIUI 那种卡片感。
 *
 * 必须是**屏幕自己的**值：MIUI / HyperOS 的圆角由系统定，每代机器都不一样，
 * 写死一个数总会和状态栏、手势条那几个角对不上。
 *
 * 取值顺序照 Miuix 的 `rememberNavSystemCornerRadius`：优先 API 31+ 的
 * `WindowInsets.getRoundedCorner()` 标准接口，取不到再退回 framework 的
 * `rounded_corner_radius_bottom` dimen，**最后兜底是 0**——
 * 平屏设备（真的没圆角）就该不裁，硬给它一个 32dp 反而和屏幕对不上。
 */
@Composable
internal expect fun rememberScreenCornerRadius(): Dp
