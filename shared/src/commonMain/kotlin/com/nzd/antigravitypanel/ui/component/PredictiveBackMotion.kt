package com.nzd.antigravitypanel.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * 预测性返回的"手势速度"追踪。整份搬自 HyperIsland 的 `PredictiveBackMotion.kt`。
 *
 * 为什么要它：预测性返回只在松手那一刻给你一个瞬时速度，而"是滑出去还是弹回来"
 * 得看用户甩得有多快。这里对采样到的瞬时速度做指数平滑，并且**按空闲时长衰减**——
 * 手指停在屏幕上半天再松手，速度不该还算数（[releaseVelocity] 里的 idleRetention）。
 *
 * 时间基准用 [TimeSource.Monotonic] 而不是 `System.nanoTime`：这份代码在 commonMain，
 * 拿不到 java 平台的时钟。
 */
internal class PredictiveBackMotionTracker(
    private val clock: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private val origin = clock.markNow()
    private var lastProgress = 0f
    private var lastSampleNanos = Long.MIN_VALUE
    private var lastMovementNanos = Long.MIN_VALUE
    private var filteredVelocity = 0f
    private var latchedReleaseVelocity: Float? = null

    private fun nowNanos(): Long = (clock.markNow() - origin).toLong(DurationUnit.NANOSECONDS)

    fun reset(progress: Float = 0f) {
        lastProgress = progress
        lastSampleNanos = nowNanos()
        lastMovementNanos = lastSampleNanos
        filteredVelocity = 0f
        latchedReleaseVelocity = null
    }

    fun update(progress: Float) {
        val now = nowNanos()
        val delta = progress - lastProgress
        val elapsedNanos = now - lastSampleNanos
        if (elapsedNanos in 1..PREDICTIVE_MAX_VELOCITY_SAMPLE_NANOS &&
            abs(delta) >= PREDICTIVE_MOTION_EPSILON
        ) {
            if (delta > 0f) {
                val elapsedSeconds = elapsedNanos / NANOS_PER_SECOND
                val instantVelocity = (delta / elapsedSeconds)
                    .coerceIn(0f, PREDICTIVE_MAX_TRACKED_VELOCITY)
                val blend = (elapsedSeconds / PREDICTIVE_VELOCITY_FILTER_SECONDS)
                    .coerceIn(PREDICTIVE_MIN_VELOCITY_BLEND, PREDICTIVE_MAX_VELOCITY_BLEND)
                filteredVelocity += (instantVelocity - filteredVelocity) * blend
            } else {
                filteredVelocity = 0f
            }
            lastMovementNanos = now
        }
        lastProgress = progress
        lastSampleNanos = now
        latchedReleaseVelocity = null
    }

    fun releaseVelocity(): Float {
        latchedReleaseVelocity?.let { return it }
        val idleNanos = (nowNanos() - lastMovementNanos).coerceAtLeast(0L)
        val idleRetention = when {
            idleNanos <= PREDICTIVE_FULL_MOMENTUM_IDLE_NANOS -> 1f
            idleNanos >= PREDICTIVE_ZERO_MOMENTUM_IDLE_NANOS -> 0f
            else -> {
                val remaining = (PREDICTIVE_ZERO_MOMENTUM_IDLE_NANOS - idleNanos).toFloat()
                val range = (PREDICTIVE_ZERO_MOMENTUM_IDLE_NANOS -
                    PREDICTIVE_FULL_MOMENTUM_IDLE_NANOS).toFloat()
                smootherStep(remaining / range)
            }
        }
        return (filteredVelocity * idleRetention).also { latchedReleaseVelocity = it }
    }
}

/**
 * 提交后的 easing：把松手速度当成曲线**起始斜率**塞进贝塞尔。
 * 甩得快 → 起步陡 → 看起来是"被甩出去的"；轻轻放 → 接近标准减速曲线。
 */
internal fun predictiveSettleEasing(
    releaseVelocity: Float,
    currentProgress: Float,
    targetProgress: Float,
    durationMillis: Int,
): Easing {
    val remainingProgress = (targetProgress - currentProgress).coerceAtLeast(PREDICTIVE_MOTION_EPSILON)
    val normalizedInitialSlope = releaseVelocity * (durationMillis / MILLIS_PER_SECOND) /
        remainingProgress
    val initialY = (PREDICTIVE_EASING_INITIAL_X * normalizedInitialSlope)
        .coerceIn(0f, PREDICTIVE_MAX_INITIAL_EASING_Y)
    return CubicBezierEasing(PREDICTIVE_EASING_INITIAL_X, initialY, 0.35f, 1f)
}

/** 五次平滑。比线性更像"跟手"，两端都更软。 */
internal fun smootherStep(progress: Float): Float {
    val value = progress.coerceIn(0f, 1f)
    return value * value * value * (value * (value * 6f - 15f) + 10f)
}

/** 手势进行中背景虚化的强度：滑到一半时才完全不虚，最小值兜住 0.5。 */
internal fun predictiveEffectIntensity(smoothProgress: Float): Float =
    1f - smoothProgress * (1f - PREDICTIVE_MIN_EFFECT_INTENSITY)

internal fun predictiveTranslationFraction(percent: Long): Float =
    percent.coerceIn(1L, 100L).toFloat() / 100f

internal fun predictiveExitProgress(maxTranslationPercent: Long): Float =
    1f / predictiveTranslationFraction(maxTranslationPercent)

/** 已经滑出去多远，剩下的动画就按比例缩短——否则每次都跑满 480ms 会显得拖沓。 */
internal fun predictiveSettleDuration(progress: Float, maxTranslationPercent: Long): Int {
    val translationFraction = predictiveTranslationFraction(maxTranslationPercent)
    val currentTranslation = (progress.coerceAtLeast(0f) * translationFraction).coerceIn(0f, 1f)
    return (PREDICTIVE_SETTLE_DURATION * (1f - currentTranslation))
        .roundToInt()
        .coerceIn(PREDICTIVE_MIN_SETTLE_DURATION, PREDICTIVE_SETTLE_DURATION)
}

internal const val LAYER_ENTER_DURATION = 420
internal const val LAYER_EXIT_DURATION = 380
internal const val PREDICTIVE_CANCEL_DURATION = 280
internal const val PREDICTIVE_DISMISS_DURATION = 24
internal const val BACKGROUND_SCALE_REDUCTION = 0.035f
internal const val BACKGROUND_PARALLAX = 0.025f
internal const val EFFECT_VISIBILITY_THRESHOLD = 0.001f

private const val PREDICTIVE_SETTLE_DURATION = 480
private const val PREDICTIVE_MIN_SETTLE_DURATION = 24
private const val PREDICTIVE_MIN_EFFECT_INTENSITY = 0.5f
private const val PREDICTIVE_MOTION_EPSILON = 0.0005f
private const val PREDICTIVE_MAX_VELOCITY_SAMPLE_NANOS = 120_000_000L
private const val PREDICTIVE_FULL_MOMENTUM_IDLE_NANOS = 40_000_000L
private const val PREDICTIVE_ZERO_MOMENTUM_IDLE_NANOS = 160_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MILLIS_PER_SECOND = 1_000f
private const val PREDICTIVE_VELOCITY_FILTER_SECONDS = 0.05f
private const val PREDICTIVE_MIN_VELOCITY_BLEND = 0.2f
private const val PREDICTIVE_MAX_VELOCITY_BLEND = 0.65f
private const val PREDICTIVE_MAX_TRACKED_VELOCITY = 6f
private const val PREDICTIVE_EASING_INITIAL_X = 0.30f
private const val PREDICTIVE_MAX_INITIAL_EASING_Y = 0.42f
