package com.nzd.antigravitypanel.ui.component

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
internal actual fun PredictiveBackHandlerCompat(
    enabled: Boolean,
    onBack: suspend (progress: Flow<PredictiveBackEvent>) -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { events ->
        onBack(events.map { PredictiveBackEvent(it.progress) })
    }
}

@Composable
internal actual fun BackHandlerCompat(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
