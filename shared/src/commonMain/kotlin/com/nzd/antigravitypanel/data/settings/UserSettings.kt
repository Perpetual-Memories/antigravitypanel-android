package com.nzd.antigravitypanel.data.settings

import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户设置。目前只有本地保留策略——服务端滚动窗口会把老对局永久删掉，
 * 所以"本地留多久"直接决定统计页能看到多长的区间。
 */
class UserSettings(
    private val store: KeyValueStore,
) {
    private val _retentionMonths = MutableStateFlow(DEFAULT_RETENTION_MONTHS)
    val retentionMonths: StateFlow<Int> = _retentionMonths.asStateFlow()

    private val _colorMode = MutableStateFlow(DEFAULT_COLOR_MODE)
    val colorMode: StateFlow<Int> = _colorMode.asStateFlow()

    private val _autoRefreshMinutes = MutableStateFlow(DEFAULT_AUTO_REFRESH_MINUTES)
    val autoRefreshMinutes: StateFlow<Int> = _autoRefreshMinutes.asStateFlow()

    /**
     * 预测返回的最大横移距离（百分比）。纯视觉参数，和 `PredictiveNavLayer` 同名参数对应。
     */
    private val _predictiveBackTranslation =
        MutableStateFlow(DEFAULT_PREDICTIVE_BACK_TRANSLATION)
    val predictiveBackTranslation: StateFlow<Int> = _predictiveBackTranslation.asStateFlow()

    suspend fun restore() {
        val saved = store.read(StoreKey.RETENTION_MONTHS)?.toIntOrNull()
        _retentionMonths.value = saved?.takeIf { it in OPTIONS } ?: DEFAULT_RETENTION_MONTHS

        _colorMode.value = store.read(StoreKey.COLOR_MODE)?.toIntOrNull()
            ?.takeIf { it in COLOR_MODE_OPTIONS } ?: DEFAULT_COLOR_MODE

        _autoRefreshMinutes.value = store.read(StoreKey.AUTO_REFRESH_MINUTES)?.toIntOrNull()
            ?.takeIf { it in AUTO_REFRESH_OPTIONS } ?: DEFAULT_AUTO_REFRESH_MINUTES

        _predictiveBackTranslation.value =
            store.read(StoreKey.PREDICTIVE_BACK_TRANSLATION)?.toIntOrNull()
                ?.takeIf { it in 0..100 } ?: DEFAULT_PREDICTIVE_BACK_TRANSLATION
    }

    suspend fun setRetentionMonths(months: Int) {
        val value = months.takeIf { it in OPTIONS } ?: DEFAULT_RETENTION_MONTHS
        store.write(StoreKey.RETENTION_MONTHS, value.toString())
        _retentionMonths.value = value
    }

    suspend fun setColorMode(mode: Int) {
        val value = mode.takeIf { it in COLOR_MODE_OPTIONS } ?: DEFAULT_COLOR_MODE
        store.write(StoreKey.COLOR_MODE, value.toString())
        _colorMode.value = value
    }

    suspend fun setAutoRefreshMinutes(minutes: Int) {
        val value = minutes.takeIf { it in AUTO_REFRESH_OPTIONS } ?: DEFAULT_AUTO_REFRESH_MINUTES
        store.write(StoreKey.AUTO_REFRESH_MINUTES, value.toString())
        _autoRefreshMinutes.value = value
    }

    suspend fun setPredictiveBackTranslation(percent: Int) {
        val value = percent.takeIf { it in 0..100 } ?: DEFAULT_PREDICTIVE_BACK_TRANSLATION
        store.write(StoreKey.PREDICTIVE_BACK_TRANSLATION, value.toString())
        _predictiveBackTranslation.value = value
    }

    companion object {
        const val DEFAULT_RETENTION_MONTHS = 6

        /** 无上限用 0 表示，与 MatchSyncer 里 `retentionMonths <= 0 不裁剪` 对齐。 */
        const val UNLIMITED = 0

        /** 顺序即设置页下拉顺序。 */
        val OPTIONS = listOf(3, 6, 9, UNLIMITED)

        fun labelOf(months: Int): String = when (months) {
            UNLIMITED -> "无上限"
            else -> "$months 个月"
        }

        /** 主题模式取值区间，与 [com.nzd.antigravitypanel.ui.theme.ColorMode] 对齐。 */
        val COLOR_MODE_OPTIONS = 0..5
        const val DEFAULT_COLOR_MODE = 0

        /**
         * 自动刷新间隔（分钟）。[ON_OPEN] 表示"每次打开时刷新"，不注册任何周期任务——
         * Android 的后台管理越来越严，常驻周期任务在没有自启动权限时基本不生效。
         */
        const val ON_OPEN = 0
        val AUTO_REFRESH_OPTIONS = listOf(ON_OPEN, 5, 10, 15, 30, 60)
        const val DEFAULT_AUTO_REFRESH_MINUTES = ON_OPEN

        /** 预测返回默认的最大横移距离（屏幕宽度的百分比）。 */
        const val DEFAULT_PREDICTIVE_BACK_TRANSLATION = 75

        fun autoRefreshLabel(minutes: Int): String = when {
            minutes <= ON_OPEN -> "每次打开时刷新"
            minutes < 60 -> "每 $minutes 分钟自动刷新"
            else -> "每 ${minutes / 60} 小时自动刷新"
        }
    }
}
