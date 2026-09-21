package com.nzd.antigravitypanel.ui.component

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/**
 * 一次返回手势的进度快照。
 *
 * 平台自己的事件类型（`BackEventCompat`）是 android 专属的，commonMain 拿不到，
 * 所以这里只把真正用得上的 `progress` 挑出来过一遍，手势处理逻辑就能待在 commonMain。
 */
data class PredictiveBackEvent(val progress: Float)

/**
 * 预测性返回：`enabled` 期间先拿到一串进度，松手后 flow 正常结束=提交、被取消=放弃。
 *
 * actual 用 `androidx.activity.compose.PredictiveBackHandler` 实现（见 androidMain）。
 */
@Composable
internal expect fun PredictiveBackHandlerCompat(
    enabled: Boolean,
    onBack: suspend (progress: Flow<PredictiveBackEvent>) -> Unit,
)

/** 普通返回键拦截：不给进度，只说"按了"。 */
@Composable
internal expect fun BackHandlerCompat(
    enabled: Boolean,
    onBack: () -> Unit,
)
