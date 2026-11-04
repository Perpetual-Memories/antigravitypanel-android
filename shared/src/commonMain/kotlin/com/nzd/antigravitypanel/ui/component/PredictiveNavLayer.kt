package com.nzd.antigravitypanel.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.squircle.absoluteSquircleClip
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

    /**
     * @param additionalAnimation 提交动画期间**同时**要跑的别的动画（当前只有悬浮底栏的
     *   归位）。它拿到的 duration / easing 和页面滑出用的是同一组，所以底栏是跟着页面
     *   一起回位的，而不是各跑各的。
     */
    internal suspend fun commitBack(
        maxTranslationPercent: Long,
        additionalAnimation: suspend (durationMillis: Int, easing: Easing) -> Unit = { _, _ -> },
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
                launch { additionalAnimation(duration, settleEasing) }
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
    /**
     * 要不要**预测式**（跟手）返回。false = 注册普通返回回调：手势期间页面一动不动，
     * 事件结束才整页滑出（[PredictiveNavLayer] 的 exit 那段 `slideOutHorizontally`）。
     *
     * ⚠️ 内容里有 WebView 的页（wiki）传 false。跟手位移的本质是**每帧改一次摆放**，
     * 整颗 WebView 就跟着一帧一帧重新合成；普通 Composable 吃得消，
     * WebView 拖起来是又卡又闪（同一条坑见 `WikiScreen` 里 `BarBlurHost(enabled=false)`）。
     *
     * ⚠️ 关掉之后系统那边也不再播预测返回的预览动画 —— `BackHandler` 注册的是
     * **不带动画**的回调，framework 见到没有 `OnBackAnimationCallback` 就退回老路径，
     * 手势滑到底才回调。这正是"禁用预测性返回手势"要的效果。
     */
    predictive: Boolean = true,
    /**
     * 返回**之前**先过的一道闸：返回 true 表示"这次返回被拦下了"。
     *
     * 被拦下时页面**仍然跟着手动**（不至于按了没反应），但松手后弹回原位、不关页面 ——
     * 走的是取消那条路，唯一区别是不调 [onDismiss]。wiki 的「再按一次退出」就靠它。
     * ⚠️ [predictive] 为 false 时没有"跟着手"这一说，页面压根不动，只弹提示。
     *
     * ⚠️ 判据在**手势一开始就取**：那时候还不知道用户会拖多远，所以这里只能
     * 根据"当前状态"（比如网页是不是已经在首页）决定，不能根据松手时的位移。
     */
    confirmBack: () -> Boolean = { false },
    onDismiss: () -> Unit,
    additionalCommitAnimation: suspend (durationMillis: Int, easing: Easing) -> Unit = { _, _ -> },
) {
    /**
     * 非预测式那条路上"正在滑出"的记号。
     *
     * 预测式那条路有 `isCommitting` 撑着（[commitBack] 期间按住返回不放它穿出去），
     * 这条路压根不走 commit，[onDismiss] 一调 `visible` 就转假、
     * 上面那个 handler 立刻失能 —— 退出动画还在跑的那几百毫秒里，
     * 再按一下返回会直接穿到一级页去。所以自己记一个。
     */
    var dismissing by remember { mutableStateOf(false) }

    LaunchedEffect(visible, state.isBackActive) {
        state.animateVisibility(visible)
    }

    if (predictive) {
        PredictiveBackHandlerCompat(enabled = enabled) { progress ->
            val rejected = confirmBack()
            var cancelled = false
            try {
                progress.collect { event -> state.trackBack(event.progress) }
            } catch (_: CancellationException) {
                cancelled = true
            }
            if (!cancelled && !rejected) {
                state.commitBack(maxTranslationPercent, additionalCommitAnimation, onDismiss)
            } else {
                // 手势中途放弃、以及被 [confirmBack] 拦下的那次，都弹回原位
                state.cancelBack()
            }
        }
    } else {
        // ⚠️ 不用 PredictiveBackHandlerCompat：它注册的是**带动画**的回调，
        // 系统一见到它就会把这段手势交给 app 自己跟手做 —— 那正是要禁掉的东西。
        BackHandlerCompat(enabled = enabled) {
            if (confirmBack()) return@BackHandlerCompat
            dismissing = true
            onDismiss()
        }
    }

    // 提交动画 / 退出动画跑完之前，后续按下的返回键一律吞掉：
    // 这段时间页面其实还没关，放过去会让用户连按两下返回穿到上一层
    BackHandlerCompat(
        enabled = dismissing || (state.isBackActive && state.isCommitting),
    ) {
        // 故意什么都不做
    }

    LaunchedEffect(dismissing) {
        if (!dismissing) return@LaunchedEffect
        // 非预测式那条路没有 commit（`isCommitting` 永远为 false），
        // 只能自己记一段时间把上面那道闸放下来
        delay(LAYER_EXIT_DURATION.toLong())
        dismissing = false
    }
}

@Composable
internal fun PredictiveNavLayer(
    visible: Boolean,
    state: PredictiveNavLayerState,
    modifier: Modifier = Modifier,
    maxTranslationPercent: Long = DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT,
    backgroundState: PredictiveNavLayerState? = null,
    /**
     * 跟手位移要不要走 `graphicsLayer`。**内容里有 WebView 的页必须传 false**（wiki）。
     *
     * `graphicsLayer` 只要位移不是 0 就会真开出一块离屏 RenderNode，整页内容被录进去；
     * WebView 落在这种"每帧重录"的离屏层里就是整页闪，拖久了还会把渲染进程拖崩
     * （同一条坑的另一半见 `WikiScreen` 里 `BarBlurHost(enabled = false)` 那段）。
     * 传 false 走 [predictiveNavSlide]：只改摆放位置，不开任何离屏层。
     *
     * ⚠️ 传 false 时不该再传 [backgroundState]：那条路的缩放/视差本来就是靠
     * `graphicsLayer` 做的，跟这条路不兼容。
     */
    transformInLayer: Boolean = true,
    content: @Composable () -> Unit,
) {
    val cornerRadius = rememberScreenCornerRadius()
    // ⚠️ WebView 那一页不能用 squircle 的 shader 版本，见 [rememberLeadingCornerClip]。
    val leadingClip = rememberLeadingCornerClip(cornerRadius, shaderMask = transformInLayer)
    // 只在"正在动"的时候裁。照 Miuix `NavDisplayEffects.shouldClipCornersAt`（Leading 模式
    // 只认 -1 < depth < 0）：停稳的栈顶就是直角，圆角是过渡期间的临时状态。
    // 注意不能只判 backgroundDepth —— 那只跟随手刚起步的那一帧，此时 depth 还是 1，
    // 得把 isBackActive 一起带上，否则出现"拖动开始是直角、动起来才变圆"的跳变。
    //
    // ⚠️ **不挂 [transformInLayer]**：那一项管的是位移走哪条路（离屏 vs 摆放），
    // 和"要不要圆角"是两件事。wiki 那页位移走摆放（避开离屏层），圆角照旧要；
    // 之前把它俩绑在一起，wiki 就永远没有圆角。
    val clipCorners = state.isBackActive ||
        state.isCommitting ||
        state.backgroundDepth.value < 1f

    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .fillMaxSize()
            // ⚠️ clip 必须**在**位移之后：Modifier 链里先写的在外层，
            // 先 clip 后位移的话，裁剪框留在原地，会把滑出屏幕的内容切掉；
            // 反过来先位移再裁，圆角就长在被拖着走的那张卡片自己身上。
            .then(
                // ⚠️ 必须写 `Modifier.` 前缀：这两个是 Modifier 的扩展函数，
                // 脱离接收者裸着调会被当成未声明的符号（Kotlin 报 unresolved reference）。
                if (transformInLayer) {
                    Modifier.predictiveNavTransform(
                        foregroundState = state,
                        backgroundState = backgroundState,
                        maxTranslationPercent = maxTranslationPercent,
                    )
                } else {
                    Modifier.predictiveNavSlide(
                        foregroundState = state,
                        maxTranslationPercent = maxTranslationPercent,
                    )
                }
            ),
        enter = slideInHorizontally(
            tween(LAYER_ENTER_DURATION, easing = FastOutSlowInEasing),
        ) { it },
        exit = if (state.isBackActive) {
            // 手势提交后位移已经由跟手位移接管，再跑一次 slideOut 会打架
            ExitTransition.None
        } else {
            slideOutHorizontally(
                tween(LAYER_EXIT_DURATION, easing = FastOutSlowInEasing),
            ) { it }
        },
    ) {
        // ⚠️ 裁剪挂在这层（content 侧），不能挂到上面的 modifier 上：
        // AnimatedVisibility 的 enter / exit slide 是作用在它内部 content 上的，
        // 挂在外层的话裁剪框钉死在屏幕位置不动，页面滑进来时圆角留在屏幕角上，
        // 看着就像没有圆角——圆角必须长在那张正在移动的卡片自己身上。
        // 跟手的位移（predictiveNavTransform）在外层，所以这条对两种情况都成立。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (clipCorners) leadingClip else Modifier),
        ) {
            content()
        }
    }
}

/**
 * 只裁**前缘**那一侧的两个角，照 Miuix 的 `NavCornerClipMode.Leading`。
 *
 * 跟手时页面是往手这边让的，真正离开屏幕边缘的只有前缘一侧；另外两个角还贴在屏上，
 * 给它们圆角反而会露出一条缝。所以 LTR 只有左上、左下，RTL 只有右上、右下——
 * 用的是 `absoluteSquircleClip`（物理角，不跟着布局方向翻转），边要自己按方向挑。
 *
 * 形状走 squircle 而不是 `RoundedCornerShape`：MIUI 的圆角是平滑连续曲线，
 * 普通圆弧接直线的地方在中文 UI 上一眼能看出折点。
 *
 * @param shaderMask 用 Miuix 的 squircle shader 版本。
 *   ⚠️⚠️ **内容里有 WebView 的页必须传 false**（wiki）。
 *   shader 版（`absoluteSquircleClip`）的 draw 是 `contentLayer.record { drawContent() }`
 *   —— 整页内容**每帧**被录进一块离屏 GraphicsLayer 再用 SDF 抠角。普通 Composable
 *   无所谓，WebView 落在这种"每帧重录"的层里就是整页闪（同一条坑见 [PredictiveNavLayer]
 *   的 [transformInLayer]）。
 *   传 false 走普通 `Modifier.clip` —— 它在 HWUI 那边只是 RenderNode 的
 *   `clipToOutline` 属性，不开离屏缓冲，圆角从 squircle 退化成圆弧（就是
 *   `absoluteSquircleClip` 自己在 API < 33 上的兜底，观感差得有限）。
 */
@Composable
private fun rememberLeadingCornerClip(radius: Dp, shaderMask: Boolean): Modifier {
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val topLeft = if (isLtr) radius else 0.dp
    val bottomLeft = if (isLtr) radius else 0.dp
    val topRight = if (isLtr) 0.dp else radius
    val bottomRight = if (isLtr) 0.dp else radius
    return if (shaderMask) {
        Modifier.absoluteSquircleClip(
            topLeft = topLeft,
            bottomLeft = bottomLeft,
            topRight = topRight,
            bottomRight = bottomRight,
        )
    } else {
        Modifier.clip(
            AbsoluteRoundedCornerShape(
                topLeft = topLeft,
                topRight = topRight,
                bottomRight = bottomRight,
                bottomLeft = bottomLeft,
            ),
        )
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

/**
 * 只改**摆放位置**、不开离屏层的跟手位移。
 *
 * 和 [predictiveNavTransform] 的区别就是"位移落在哪一环"：`graphicsLayer` 的
 * translationX 是把内容录进一块离屏 RenderNode 再整体挪，这里的 `layout` 只是把
 * placeable 摆到另一个坐标 —— HWUI 那边就是改一下 RenderNode 的矩阵，不重栅格化。
 *
 * `slideInHorizontally` 用的正是这种位移，二级页**滑进来**那一程走的也是它；
 * 那一程 progress 恒为 0、`graphicsLayer` 退化成恒等不开层，所以 WebView 是稳的。
 * 一跟着手动起来（translationX ≠ 0 → 开层）才开始闪 —— 换到这条路就回到进场那一程
 * 的渲染路径上了。
 *
 * ⚠️ 状态读在 place 的闭包里：Compose 只会把这一层标成"要重摆"，缓存下来的
 * MeasureResult 直接复用，不会每帧重新 measure 整棵子树。
 */
private fun Modifier.predictiveNavSlide(
    foregroundState: PredictiveNavLayerState,
    maxTranslationPercent: Long,
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        val progress = foregroundState.progress.value.coerceAtLeast(0f)
        val dx = (
            placeable.width * progress *
                predictiveTranslationFraction(maxTranslationPercent)
            ).roundToInt()
        placeable.place(IntOffset(dx, 0))
    }
}

/**
 * 手势位移灵敏度：手指走满一屏宽时，页面只走这个百分比，松手后再补到整屏滑出。
 *
 * ⚠️ 平台的 `BackEvent.progress` 就是**手指水平位移 / 屏宽**（AOSP `BackAnimation`：
 * `navigation_edge_action_progress_threshold = 412dp`，屏宽 ≤ 412dp 走纯线性分支；
 * 官方文档原文是"约 1 时手指在屏幕另一侧"）。所以这个值等于"页面跟手比例"：
 * 75 就是页面走手指的 0.75 倍。
 *
 * 原来取 75 太"冲"了 —— 拖满一屏页面就滑走 3/4 屏，而 MIUI 自己那套在拖拽期间
 * 当前页只让开一点点。降到 40：拖满一屏页面走 0.4 屏，松手后剩下的交给 settle 动画。
 */
internal const val DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT = 40L

private const val PREDICTIVE_BACK_BLUR_RADIUS = 12f
private const val PREDICTIVE_BACK_DIM_ALPHA = 0.16f
