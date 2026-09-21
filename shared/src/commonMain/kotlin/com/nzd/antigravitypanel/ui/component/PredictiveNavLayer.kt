package com.nzd.antigravitypanel.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 二级详情页图层。整份搬自 HyperIsland 的 `PredictiveNavigationLayer.kt`。
 *
 * 它替代的是"往导航栈里 push 一条路由"：二级页不是一条 route，而是**一级页之上的一层**。
 * 好处是返回手势能带着页面跟手位移、背景同步缩放+虚化——这些都是拿不到跨页面
 * 合成上下文的路由框架做不出来的；代价是父子关系得自己维护（哪个页 + 是否可见两个 state）。
 *
 * 使用方式见 `AppContent`：
 * ```
 * BarBlurHost {
 *     Scaffold { ...一级页... }
 *     PredictiveNavBackdrop(state)          // 手势期间压在中间那层
 *     PredictiveNavLayer(visible, state) {  // 必须放在 Scaffold **之后**，否则盖不住悬浮底栏
 *         when (visibleDetail) { ... }
 *     }
 *     PredictiveNavBackHandler(visible, state, onDismiss)
 * }
 * ```
 */
@Stable
internal class PredictiveNavLayerState internal constructor() {
    /** 手指正在拖返回手势。 */
    var isBackActive by mutableStateOf(false)
        private set

    /** 已经松手、正在跑提交动画。 */
    var isCommitting by mutableStateOf(false)
        private set

    val progress = Animatable(0f)
    val backdropIntensity = Animatable(0f)
    val backgroundDepth = Animatable(0f)

    private val motion = PredictiveBackMotionTracker()

    internal suspend fun animateVisibility(visible: Boolean) {
        if (isBackActive) return
        val target = if (visible) 1f else 0f
        val duration = if (visible) LAYER_ENTER_DURATION else LAYER_EXIT_DURATION
        coroutineScope {
            launch {
                backdropIntensity.animateTo(target, tween(duration, easing = FastOutSlowInEasing))
            }
            launch {
                backgroundDepth.animateTo(target, tween(duration, easing = FastOutSlowInEasing))
            }
        }
    }

    internal suspend fun trackBack(progress: Float) {
        if (!isBackActive) {
            motion.reset(progress)
        } else {
            motion.update(progress)
        }
        isBackActive = true
        this.progress.snapTo(progress)
        val smoothProgress = smootherStep(progress)
        backdropIntensity.snapTo(predictiveEffectIntensity(smoothProgress))
        backgroundDepth.snapTo(1f - smoothProgress)
    }

    internal suspend fun cancelBack() {
        if (isCommitting) return
        coroutineScope {
            launch {
                progress.animateTo(0f, tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing))
            }
            launch {
                backdropIntensity.animateTo(1f, tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing))
            }
            launch {
                backgroundDepth.animateTo(1f, tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing))
            }
        }
        motion.reset()
        isBackActive = false
    }

    internal suspend fun commitBack(
        maxTranslationPercent: Long,
        onDismiss: () -> Unit,
    ) {
        if (isCommitting) return
        isCommitting = true
        try {
            val targetProgress = predictiveExitProgress(maxTranslationPercent)
            val duration = predictiveSettleDuration(
                progress = progress.value,
                maxTranslationPercent = maxTranslationPercent,
            )
            val settleEasing = predictiveSettleEasing(
                releaseVelocity = motion.releaseVelocity(),
                currentProgress = progress.value,
                targetProgress = targetProgress,
                durationMillis = duration,
            )
            coroutineScope {
                launch { progress.animateTo(targetProgress, tween(duration, easing = settleEasing)) }
                launch { backdropIntensity.animateTo(0f, tween(duration, easing = settleEasing)) }
                launch { backgroundDepth.animateTo(0f, tween(duration, easing = settleEasing)) }
            }
            onDismiss()
            // 给 AnimatedVisibility 一点时间把退出动画跑完再复位，
            // 否则下一帧就会看到页面闪回原位
            delay(PREDICTIVE_DISMISS_DURATION.toLong())
        } finally {
            withContext(NonCancellable) {
                progress.snapTo(0f)
                motion.reset()
                isBackActive = false
                isCommitting = false
            }
        }
    }
}

@Composable
internal fun rememberPredictiveNavLayerState(): PredictiveNavLayerState =
    remember { PredictiveNavLayerState() }

/**
 * 返回事件的挂载点。
 *
 * `enabled` 只在图层可见时为真：不可见时不该抢系统的返回键——
 * 否则一级页（底部导航那几页）自己的返回就失灵了。
 */
@Composable
internal fun PredictiveNavBackHandler(
    visible: Boolean,
    enabled: Boolean,
    state: PredictiveNavLayerState,
    maxTranslationPercent: Long = DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(visible, state.isBackActive) {
        state.animateVisibility(visible)
    }

    PredictiveBackHandlerCompat(enabled = enabled) { progress ->
        var cancelled = false
        try {
            progress.collect { event -> state.trackBack(event.progress) }
        } catch (_: CancellationException) {
            state.cancelBack()
            cancelled = true
        }
        if (!cancelled) {
            state.commitBack(maxTranslationPercent, onDismiss)
        }
    }

    // 提交动画跑完之前，后续按下的返回键一律吞掉：
    // 这段时间页面其实还没关，放过去会让用户连按两下返回穿到上一层
    BackHandlerCompat(enabled = state.isBackActive && state.isCommitting) {
        // 故意什么都不做
    }
}

@Composable
internal fun PredictiveNavLayer(
    visible: Boolean,
    state: PredictiveNavLayerState,
    modifier: Modifier = Modifier,
    maxTranslationPercent: Long = DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT,
    backgroundState: PredictiveNavLayerState? = null,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .fillMaxSize()
            .predictiveNavTransform(
                foregroundState = state,
                backgroundState = backgroundState,
                maxTranslationPercent = maxTranslationPercent,
            ),
        enter = slideInHorizontally(
            tween(LAYER_ENTER_DURATION, easing = FastOutSlowInEasing),
        ) { it },
        exit = if (state.isBackActive) {
            // 手势提交后位移已经由 graphicsLayer 接管，再跑一次 slideOut 会打架
            ExitTransition.None
        } else {
            slideOutHorizontally(
                tween(LAYER_EXIT_DURATION, easing = FastOutSlowInEasing),
            ) { it }
        },
    ) {
        content()
    }
}

/**
 * 手势期间压在"上层页面之下、下层内容之上"的那层虚化。
 *
 * 它读的是 [LocalBarBlurBackdrop]——所以必须待在 [BarBlurHost] 里面，
 * 否则没有可采样的离屏内容，虚化退化成一层纯色（那就只是变暗而已）。
 */
@Composable
internal fun PredictiveNavBackdrop(
    state: PredictiveNavLayerState,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalBarBlurBackdrop.current
    val intensity = state.backdropIntensity.value
    if (intensity <= EFFECT_VISIBILITY_THRESHOLD) return

    val effectIntensity = intensity.coerceIn(0f, 1f)
    val dimColor = MiuixTheme.colorScheme.windowDimming.copy(
        alpha = PREDICTIVE_BACK_DIM_ALPHA * effectIntensity,
    )
    Box(
        modifier = if (backdrop != null) {
            modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = PREDICTIVE_BACK_BLUR_RADIUS * effectIntensity,
                noiseCoefficient = 0f,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(BlendColorEntry(color = dimColor)),
                ),
            )
        } else {
            modifier.background(dimColor)
        },
    )
}

/** 图层可见 / 手势进行中时，宿主要多开一份离屏采样给虚化层用。 */
internal fun PredictiveNavLayerState.requiresBackdropCapture(visible: Boolean): Boolean =
    visible || isBackActive || backdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD

/** 被盖住的那一层：往旁边让一点并缩小，做出纵深。 */
internal fun Modifier.predictiveNavBackground(
    state: PredictiveNavLayerState,
): Modifier = graphicsLayer {
    val depth = state.backgroundDepth.value.coerceIn(0f, 1f)
    scaleX = 1f - depth * BACKGROUND_SCALE_REDUCTION
    scaleY = scaleX
    translationX = -size.width * depth * BACKGROUND_PARALLAX
}

private fun Modifier.predictiveNavTransform(
    foregroundState: PredictiveNavLayerState,
    backgroundState: PredictiveNavLayerState?,
    maxTranslationPercent: Long,
): Modifier = graphicsLayer {
    val progress = foregroundState.progress.value.coerceAtLeast(0f)
    val depth = backgroundState?.backgroundDepth?.value?.coerceIn(0f, 1f) ?: 0f
    translationX = -size.width * depth * BACKGROUND_PARALLAX +
        size.width * progress * predictiveTranslationFraction(maxTranslationPercent)
    scaleX = 1f - depth * BACKGROUND_SCALE_REDUCTION
    scaleY = scaleX
}

/** 手势位移灵敏度：手指走满一屏宽时，页面只走这个百分比，松手后再补到整屏滑出。 */
internal const val DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT = 75L

private const val PREDICTIVE_BACK_BLUR_RADIUS = 12f
private const val PREDICTIVE_BACK_DIM_ALPHA = 0.16f
