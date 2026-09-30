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

    /**
     * 「打开APP自动签到」——小程序活动中心那个每日签到。默认**开**。
     *
     * 这个开关管的是"要不要帮你点一下签到"，和下面那几个"要不要帮你领东西"
     * 不是一回事：签到本身不消耗任何东西，漏了就少一天连续。
     */
    private val _autoSignIn = MutableStateFlow(DEFAULT_AUTO_SIGN_IN)
    val autoSignIn: StateFlow<Boolean> = _autoSignIn.asStateFlow()

    /**
     * 「打开APP自动领取任务中心奖励」——每日完成1局与每周对局5次的积分。默认**开**。
     *
     * 默认开的理由和心悦那份一样：这个接口**一次只领传进去的那一个任务**，
     * 而且只在"服务端说已完成且未领取"时才发请求，等价于每天领一次该领的东西。
     */
    private val _autoClaimWelfareTask = MutableStateFlow(DEFAULT_AUTO_CLAIM_WELFARE_TASK)
    val autoClaimWelfareTask: StateFlow<Boolean> = _autoClaimWelfareTask.asStateFlow()

    private val _autoClaimQqGift = MutableStateFlow(DEFAULT_AUTO_CLAIM_QQ_GIFT)
    val autoClaimQqGift: StateFlow<Boolean> = _autoClaimQqGift.asStateFlow()

    private val _autoClaimXinyueGift = MutableStateFlow(DEFAULT_AUTO_CLAIM_XINYUE_GIFT)
    val autoClaimXinyueGift: StateFlow<Boolean> = _autoClaimXinyueGift.asStateFlow()

    /**
     * 「概览页顶栏显示账号信息」（设置 → 外观）。默认**开**。
     *
     * 开了之后一级页的工具条左侧换成"头像 + 昵称 + 分区"那颗胶囊，
     * 应用名就不再收进折叠后的工具条里（它仍然是大标题，位置没变）。
     */
    private val _topBarAccount = MutableStateFlow(DEFAULT_TOP_BAR_ACCOUNT)
    val topBarAccount: StateFlow<Boolean> = _topBarAccount.asStateFlow()

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

        // 两个默认开的都判"关"而不是判"开"：没写过这个键时结果是 true
        _autoSignIn.value = store.read(StoreKey.SIGNIN_AUTO) != "0"

        _autoClaimWelfareTask.value = store.read(StoreKey.WELFARE_TASK_AUTO_CLAIM) != "0"

        _autoClaimQqGift.value = store.read(StoreKey.QQ_GIFT_AUTO_CLAIM) == "1"

        // 默认开，所以判"关"而不是判"开"：没写过这个键时结果是 true
        _autoClaimXinyueGift.value = store.read(StoreKey.XINYUE_AUTO_CLAIM) != "0"

        _topBarAccount.value = store.read(StoreKey.TOP_BAR_ACCOUNT) != "0"
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
     * 小程序活动中心的自动签到。默认开（[DEFAULT_AUTO_SIGN_IN]）。
     *
     * 关掉之后 app 只会**看**签到状态，不再帮你签——想签自己在签到页点按钮。
     */
    suspend fun setAutoSignIn(enabled: Boolean) {
        store.write(StoreKey.SIGNIN_AUTO, if (enabled) "1" else "0")
        _autoSignIn.value = enabled
    }

    /**
     * 任务中心每日 / 每周奖励的自动领取。默认开（[DEFAULT_AUTO_CLAIM_WELFARE_TASK]）。
     *
     * 领取接口一次只领一个任务，且只在"已完成且未领取"时才发请求，
     * 所以它等价于"每天把该领的积分领了"，性质上和签到一样安全。
     */
    suspend fun setAutoClaimWelfareTask(enabled: Boolean) {
        store.write(StoreKey.WELFARE_TASK_AUTO_CLAIM, if (enabled) "1" else "0")
        _autoClaimWelfareTask.value = enabled
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

    /**
     * 「概览页顶栏显示账号信息」（设置 → 外观）。默认开（[DEFAULT_TOP_BAR_ACCOUNT]）。
     *
     * 关掉是**纯回退**：工具条左侧空出来，应用名照旧折叠进工具条，和这一版之前一模一样。
     */
    suspend fun setTopBarAccount(enabled: Boolean) {
        store.write(StoreKey.TOP_BAR_ACCOUNT, if (enabled) "1" else "0")
        _topBarAccount.value = enabled
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

        /** 小程序签到自动签到默认**开**，理由见 [setAutoSignIn]。 */
        const val DEFAULT_AUTO_SIGN_IN = true

        /** 任务中心自动领取默认**开**，理由见 [setAutoClaimWelfareTask]。 */
        const val DEFAULT_AUTO_CLAIM_WELFARE_TASK = true

        /** 游戏中心自动领取默认**关**，理由见 [setAutoClaimQqGift]。 */
        const val DEFAULT_AUTO_CLAIM_QQ_GIFT = false

        /** 心悦悦享卡自动领取默认**开**，理由见 [setAutoClaimXinyueGift]。 */
        const val DEFAULT_AUTO_CLAIM_XINYUE_GIFT = true

        /** 「概览页顶栏显示账号信息」默认开，理由见 [setTopBarAccount]。 */
        const val DEFAULT_TOP_BAR_ACCOUNT = true

        fun autoRefreshLabel(minutes: Int): String = when {
            minutes <= ON_OPEN -> "每次打开时刷新"
            minutes < 60 -> "每 $minutes 分钟自动刷新"
            else -> "每 ${minutes / 60} 小时自动刷新"
        }
    }
}
