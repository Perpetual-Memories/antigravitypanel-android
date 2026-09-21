package com.nzd.antigravitypanel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏磨砂。整份是 HyperIsland `compose/component/BlurBars.kt` 的搬运行，
 * 只留下本项目用到的三块：`BarBlurHost` / `BarBackdropContent` / `BlurredBar`。
 *
 * 它和"自己画一层半透明色"的区别在于 **backdrop**：
 * [BarBlurHost] 开一块离屏 layer，[BarBackdropContent] 把页面内容画进去，
 * [BlurredBar] 再从这块 layer 上采样做模糊。
 * 所以**少了 [BarBackdropContent] 那一环，顶栏糊的就是一层纯色**——
 * 离屏 layer 里只有 [BarBlurHost] 铺的那张 `surfaceColor` 底，糊出来当然还是色块。
 */
internal val LocalBarBlurEnabled = staticCompositionLocalOf { false }

internal val LocalBarBlurBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 磨砂的宿主：开离屏 layer 并把 [content] 整个包进 [Box]。
 *
 * 只有 RuntimeShader 可用时才建 backdrop，否则后面一律退化成不透明表面色——
 * 不这么做的话，低端机上 `progressiveTextureBlur` 拿不到采样结果，
 * 顶栏会变成一块实色，比直接退化还难看。
 *
 * @param captureForEffects 关掉磨砂但仍然要 backdrop 的场景（比如返回手势的虚化层）。
 */
@Composable
fun BarBlurHost(
    enabled: Boolean,
    captureForEffects: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = if ((enabled || captureForEffects) && isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    CompositionLocalProvider(
        LocalBarBlurEnabled provides enabled,
        LocalBarBlurBackdrop provides backdrop,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * 把页面内容接进 backdrop。
 *
 * 必须包在 **铺满整屏** 的那一层上，而且它里面不能再拿顶栏高度去 padding——
 * 内容要一直画到屏幕顶边，滚动时从顶栏底下穿过去，顶栏才有东西可糊。
 * 页面的"躲开顶栏"靠列表自己的 `contentPadding` 实现，不是靠裁掉这块区域。
 */
@Composable
fun BarBackdropContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalBarBlurBackdrop.current
    Box(
        modifier = modifier.then(
            if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        ),
    ) {
        content()
    }
}

/**
 * 顶栏那一层。[topGradient] 为真时上满下空，底缘不会切出一条硬边。
 *
 * @param enabled 关掉时**连底色都不铺**（关于页要的就是这个：没滚到顶之前顶栏
 *   完全透明地浮在渐变上，见 miuix 示例 `AboutPage` 的 `barColor`）。
 *   只有 [enabled] 为真、但设备拿不到 backdrop 时，才退化成不透明表面色。
 */
@Composable
fun BlurredBar(
    topGradient: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.barBlurBackground(
            shape = RectangleShape,
            topGradient = topGradient,
            enabled = enabled,
        ),
    ) {
        content()
    }
}

@Composable
fun Modifier.barBlurBackground(
    shape: Shape,
    topGradient: Boolean = false,
    enabled: Boolean = true,
): Modifier {
    val backdrop = LocalBarBlurBackdrop.current
    val blurEnabled = LocalBarBlurEnabled.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    return if (!enabled) {
        Modifier
    } else if (blurEnabled && backdrop != null) {
        val blendColors = listOf(
            BlendColorEntry(color = surfaceColor.copy(alpha = TOP_BAR_SURFACE_ALPHA)),
        )
        progressiveTextureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = BAR_BLUR_RADIUS,
            gradient = TOP_BAR_PROGRESSIVE_BLUR,
            colors = BlurDefaults.blurColors(blendColors = blendColors),
        )
    } else {
        background(color = surfaceColor, shape = shape)
    }
}

/** HyperIsland 同款顶栏渐变：上满下空，中段过渡带偏长。 */
private val TOP_BAR_PROGRESSIVE_BLUR = ProgressiveBlur.Top.copy(
    startFraction = 0.12f,
    endFraction = 1f,
    curve = 1.25f,
)
private const val BAR_BLUR_RADIUS = 16f
private const val TOP_BAR_SURFACE_ALPHA = 0.66f
