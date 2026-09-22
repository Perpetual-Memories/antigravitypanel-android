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

    private val _autoClaimQqGift = MutableStateFlow(DEFAULT_AUTO_CLAIM_QQ_GIFT)
    val autoClaimQqGift: StateFlow<Boolean> = _autoClaimQqGift.asStateFlow()

    private val _autoClaimXinyueGift = MutableStateFlow(DEFAULT_AUTO_CLAIM_XINYUE_GIFT)
    val autoClaimXinyueGift: StateFlow<Boolean> = _autoClaimXinyueGift.asStateFlow()

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

        _autoClaimQqGift.value = store.read(StoreKey.QQ_GIFT_AUTO_CLAIM) == "1"

        // 默认开，所以判"关"而不是判"开"：没写过这个键时结果是 true
        _autoClaimXinyueGift.value = store.read(StoreKey.XINYUE_AUTO_CLAIM) != "0"
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

    /**
     * 游戏中心周签到礼包的自动领取。默认关（[DEFAULT_AUTO_CLAIM_QQ_GIFT]）。
     *
     * 之所以默认关：那个领取接口是**批量**的，会把服务端认为能领的礼包一起领走，
     * 而且真的会把道具发进游戏账号。它和"只读地查战绩"不是一个性质的操作，
     * 该由用户主动开。
     */
    suspend fun setAutoClaimQqGift(enabled: Boolean) {
        store.write(StoreKey.QQ_GIFT_AUTO_CLAIM, if (enabled) "1" else "0")
        _autoClaimQqGift.value = enabled
    }

    /**
     * 心悦悦享卡每日礼包的自动领取。默认**开**（[DEFAULT_AUTO_CLAIM_XINYUE_GIFT]）。
     *
     * 和游戏中心那个默认关不一样，理由是接口性质不同：
     * 心悦的 `ReceiveGift` **只领传进去的那一张卡**，就是悦享卡的每日礼包；
     * 而游戏中心的 `exchange-all-gifts` 是批量的，会把服务端认为能领的礼包一起领掉。
     * 前者等价于"每天领一次该领的东西"，后者是一次未知的批量发货——
     * 所以这里默认开，那边默认关。
     */
    suspend fun setAutoClaimXinyueGift(enabled: Boolean) {
        store.write(StoreKey.XINYUE_AUTO_CLAIM, if (enabled) "1" else "0")
        _autoClaimXinyueGift.value = enabled
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

        /** 游戏中心自动领取默认**关**，理由见 [setAutoClaimQqGift]。 */
        const val DEFAULT_AUTO_CLAIM_QQ_GIFT = false

        /** 心悦悦享卡自动领取默认**开**，理由见 [setAutoClaimXinyueGift]。 */
        const val DEFAULT_AUTO_CLAIM_XINYUE_GIFT = true

        fun autoRefreshLabel(minutes: Int): String = when {
            minutes <= ON_OPEN -> "每次打开时刷新"
            minutes < 60 -> "每 $minutes 分钟自动刷新"
            else -> "每 ${minutes / 60} 小时自动刷新"
        }
    }
}
