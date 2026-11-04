package com.nzd.antigravitypanel.ui.component

// 顶栏玻璃按钮的阴影 —— 直接照 NexioSchedule `ui/basic/LiquidTopBarButton.kt` 的写法：
// android.graphics.Path 画一个挖空的环（外圆 CW + 内圆 CCW）+ BlurMaskFilter 柔化。
// 之所以放到 androidMain：nativeCanvas / BlurMaskFilter 是平台 API。

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/** 阴影的基准色。参考实现是浅色 `#12000000` / 深色 `#20000000`，画的时候再 ×3.2。 */
private const val SHADOW_BASE_ALPHA_LIGHT = 0x12
private const val SHADOW_BASE_ALPHA_DARK = 0x20

/** 参考实现里那句 `alpha * 3.2f`：基准色只是个"档位"，真要压得住得放大三倍多。 */
private const val SHADOW_ALPHA_GAIN = 3.2f

/**
 * ⚠️ 模糊半径 / 外扩量写死在下面（`10f * density` / `2f * density`），就是参考实现的原值
 * （总柔光约 12dp）。别再往回收：
 *
 * 这两个数一度被压到 `4dp / 0.5dp`，那是玻璃还待在 Miuix 顶栏槽位里的权宜之计 ——
 * `TopAppBar` / `SmallTopAppBar` 内部挂了 `clipToBounds()`
 * （javap `TopAppBarKt`：`windowInsetsPadding(systemBars.only(Top)).clipToBounds()`），
 * 玻璃 42dp、工具条那行只有 52dp（`CollapsedHeight`），上下各剩 5dp，
 * 12dp 的柔光有一大半落在裁切线外，看着就是"阴影被切了一段直线"。
 *
 * 现在玻璃画在 `BlurredBar` 那层**不被裁的 overlay** 上（见 BarBlur.kt 的
 * `TopBarGlassLayer`），裁切线不存在了 —— 想收小只能靠改这里的字面量，
 * 而不是像以前那样"因为被裁所以缩水"。
 */
internal actual fun Modifier.glassDropShadow(
    glassAlpha: Float,
    darkTheme: Boolean,
    capsule: Boolean,
): Modifier = drawBehind {
    // 下面这一段是 NexioSchedule `LiquidTopBarButton` 的 drawBehind 的逐行搬运
    // （spread ↔ glassAlpha），别再动数值：都是参考实现的原值。
    val spread = glassAlpha
    if (spread > 0.01f) {
        val maxBlurRadius = 10f * density
        val maxShadowSpread = 2f * density
        val blurRadius = maxBlurRadius * spread
        val shadowSpread = maxShadowSpread * spread
        val path = Path().apply {
            // 内圈反向绕 → 挖空内部：阴影不会从半透明的玻璃底下透出来
            if (capsule) {
                // 横向胶囊：半径取**高度**的一半，外圈按 spread 外扩。
                // 半径不能取 minDimension/2 再当圆用 —— 那样只在正中间画一个小圆环。
                val innerRadius = size.height / 2f
                val outerRadius = innerRadius + shadowSpread
                addRoundRect(
                    -shadowSpread,
                    -shadowSpread,
                    size.width + shadowSpread,
                    size.height + shadowSpread,
                    outerRadius,
                    outerRadius,
                    Path.Direction.CW,
                )
                addRoundRect(
                    0f,
                    0f,
                    size.width,
                    size.height,
                    innerRadius,
                    innerRadius,
                    Path.Direction.CCW,
                )
            } else {
                val outerRadius = size.minDimension / 2f + shadowSpread
                val innerRadius = size.minDimension / 2f
                addCircle(center.x, center.y, outerRadius, Path.Direction.CW)
                addCircle(center.x, center.y, innerRadius, Path.Direction.CCW)
            }
        }
        val shadowColor = if (darkTheme) SHADOW_BASE_ALPHA_DARK else SHADOW_BASE_ALPHA_LIGHT
        val paint = Paint().apply {
            color = android.graphics.Color.argb(
                (shadowColor * SHADOW_ALPHA_GAIN).coerceAtMost(255f).toInt(),
                0,
                0,
                0,
            )
            // BlurMaskFilter 的半径必须 > 0，收敛到 0 时会抛
            maskFilter = BlurMaskFilter(blurRadius.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawPath(path, paint)
        }
    }
}
