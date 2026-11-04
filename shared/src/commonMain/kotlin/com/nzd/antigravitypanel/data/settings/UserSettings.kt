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
     * 「超核组队活动自动签到、领奖并抽奖」。默认**开**。
     *
     * 默认开的理由：这一套和另三块签到不同 —— 用户必须先手动填活动网址、再抓一遍
     * HAR 才能用，配好那一刻本身就是"我要它自动"的意思。而且领取接口全是
     * **按 flow 单点**的（不会批量领走未知奖励），抽奖也只花这个活动里攒的积分。
     */
    private val _autoSupercore = MutableStateFlow(DEFAULT_AUTO_SUPERCORE)
    val autoSupercore: StateFlow<Boolean> = _autoSupercore.asStateFlow()

    /**
     * 「概览页顶栏显示账号信息」（设置 → 外观）。默认**开**。
     *
     * 开了之后一级页的工具条左侧换成"头像 + 昵称 + 分区"那颗胶囊，
     * 应用名就不再收进折叠后的工具条里（它仍然是大标题，位置没变）。
     */
    private val _topBarAccount = MutableStateFlow(DEFAULT_TOP_BAR_ACCOUNT)
    val topBarAccount: StateFlow<Boolean> = _topBarAccount.asStateFlow()

    /**
     * 「用综合分替换经济转化」（设置 → 实验性功能）。默认**开**。
     *
     * 综合分把「伤害占比」和「按金币档位修正过的效率」合成一个 0~1 的数，
     * 算法在 `domain/CompositeScore.kt`。默认开的理由是它只是**换一种读法**：
     * 数据来源和「经济转化」完全一样（Boss 伤害 + 金币），不发请求、不改落盘，
     * 关掉就能原样回到比值那一格。
     */
    private val _compositeScore = MutableStateFlow(DEFAULT_COMPOSITE_SCORE)
    val compositeScore: StateFlow<Boolean> = _compositeScore.asStateFlow()

    /**
     * 「积分显示单位」：哪些页面把积分折成万显示（设置 → 外观）。
     * 默认 [ScoreUnitTarget.DEFAULT]（历史战绩 + 概览近五场用万，详情页完整数字）。
     */
    private val _scoreWanTargets = MutableStateFlow(ScoreUnitTarget.DEFAULT)
    val scoreWanTargets: StateFlow<Set<ScoreUnitTarget>> = _scoreWanTargets.asStateFlow()

    /**
     * 「代理模式（暂停官方同步）」（设置 → 实验性功能）。默认**关**。
     *
     * 打开之后**一个官方请求都不发**：战绩 / 活动 / 游戏配置这些"读"，
     * 加上四块签到与领取这些"写"，全部停掉。
     *
     * 存在的理由是地理限制：wiki 主站部署在腾讯云 EdgeOne，少部分地区访问不通，
     * 用户会临时挂代理来查资料。代理是**系统级**的，app 自己的请求也会跟着走，
     * 而签到那些接口带的是**账号凭证**——从境外 IP 发过去，性质上就是"异地操作账号"。
     * 这个开关让用户有能力主动掐断。
     *
     * ⚠️ 生命周期是**一次性**的，不是普通落盘开关：
     * - 打开 → 写盘（[StoreKey.WIKI_PROXY_MODE]）+ 内存态生效
     * - 冷启动 → 读到就**消费**：跳过这一轮自动签到，然后清键，UI 显示为关
     *
     * 这样"重启即恢复"和"重启那一次不误发请求"同时成立。两个反例都别走：
     * 常驻落盘（用户忘了关 → 长期漏签丢奖励）、纯内存（堵不住冷启动抢跑）。
     */
    private val _proxyMode = MutableStateFlow(false)
    val proxyMode: StateFlow<Boolean> = _proxyMode.asStateFlow()

    /**
     * **本次**冷启动要不要跳过自动签到。由 [restore] 消费 [StoreKey.WIKI_PROXY_MODE] 得到，
     * 只用一次。
     *
     * 对外只暴露 [officialNetworkPaused]：它是"开关开着"或"本轮被保护"的并集，
     * 调用点一律看那个，不用关心这一次是哪一种原因停的。
     */
    private var skipAutoActionsOnce = false

    /**
     * 当前是不是**不该发任何官方请求**。
     *
     * 各刷新入口（`AppContent` 的 `refreshAll` 和三个自动签到的 effect）统一判这一个值，
     * 免得以后有人加了新入口、只记得判开关、漏掉冷启动那一轮保护。
     */
    private val _officialNetworkPaused = MutableStateFlow(false)
    val officialNetworkPaused: StateFlow<Boolean> = _officialNetworkPaused.asStateFlow()

    private fun syncPaused() {
        _officialNetworkPaused.value = _proxyMode.value || skipAutoActionsOnce
    }

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

        _autoSupercore.value = store.read(StoreKey.SUPERCORE_AUTO) != "0"

        _topBarAccount.value = store.read(StoreKey.TOP_BAR_ACCOUNT) != "0"

        _compositeScore.value = store.read(StoreKey.COMPOSITE_SCORE) != "0"

        _scoreWanTargets.value = ScoreUnitTarget.decode(store.read(StoreKey.SCORE_WAN_TARGETS))

        // 代理模式的一次性标记：读到就**消费**掉（先置 skip 再清键，中间没有挂起点，
        // 不会被重入切开）。之后这次进程里开关一律显示关，但这一轮自动签到被跳过。
        skipAutoActionsOnce = store.read(StoreKey.WIKI_PROXY_MODE) == "1"
        if (skipAutoActionsOnce) store.remove(StoreKey.WIKI_PROXY_MODE)
        syncPaused()
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
     * 超核组队活动的自动签到 / 领奖 / 抽奖。默认开（[DEFAULT_AUTO_SUPERCORE]）。
     *
     * 关掉之后 app 只拉状态，不领也不抽；卡片上那颗「立即领取并抽奖」不受影响。
     */
    suspend fun setAutoSupercore(enabled: Boolean) {
        store.write(StoreKey.SUPERCORE_AUTO, if (enabled) "1" else "0")
        _autoSupercore.value = enabled
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

    /**
     * 「用综合分替换经济转化」。默认开（[DEFAULT_COMPOSITE_SCORE]）。
     *
     * 关掉是**纯回退**：详情页那一格回到 Boss 伤害 ÷ 金币的比值，长按也不再弹算式。
     */
    suspend fun setCompositeScore(enabled: Boolean) {
        store.write(StoreKey.COMPOSITE_SCORE, if (enabled) "1" else "0")
        _compositeScore.value = enabled
    }

    /**
     * 「积分显示单位」：[ScoreUnitTarget] 的多选集合，写盘 + 内存一起更新。
     *
     * 传空集合是合法输入（= 三个页面都显示完整数字），不要在这里偷偷回退成默认值 ——
     * 那会让用户永远关不掉。
     */
    suspend fun setScoreWanTargets(targets: Set<ScoreUnitTarget>) {
        store.write(StoreKey.SCORE_WAN_TARGETS, ScoreUnitTarget.encode(targets))
        _scoreWanTargets.value = targets
    }

    /**
     * 切「代理模式」。
     *
     * 打开时顺带落一个一次性标记，让**下一次**冷启动也知道要保护一轮；
     * 关掉时把标记清掉，下次冷启动就恢复正常同步。
     *
     * ⚠️ 落盘的是"下一次启动要不要保护"，不是开关本身 ——
     * 开关状态只在内存里，所以重启后必然是关。
     */
    suspend fun setProxyMode(enabled: Boolean) {
        if (enabled) {
            store.write(StoreKey.WIKI_PROXY_MODE, "1")
        } else {
            store.remove(StoreKey.WIKI_PROXY_MODE)
        }
        _proxyMode.value = enabled
        syncPaused()
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

        /**
         * 预测返回默认的最大横移距离（屏幕宽度的百分比）。语义 = 「页面跟手比例」，
         * 理由见 [com.nzd.antigravitypanel.ui.component.DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT]。
         */
        const val DEFAULT_PREDICTIVE_BACK_TRANSLATION = 40

        /** 小程序签到自动签到默认**开**，理由见 [setAutoSignIn]。 */
        const val DEFAULT_AUTO_SIGN_IN = true

        /** 任务中心自动领取默认**开**，理由见 [setAutoClaimWelfareTask]。 */
        const val DEFAULT_AUTO_CLAIM_WELFARE_TASK = true

        /** 游戏中心自动领取默认**关**，理由见 [setAutoClaimQqGift]。 */
        const val DEFAULT_AUTO_CLAIM_QQ_GIFT = false

        /** 心悦悦享卡自动领取默认**开**，理由见 [setAutoClaimXinyueGift]。 */
        const val DEFAULT_AUTO_CLAIM_XINYUE_GIFT = true

        /** 超核组队活动自动签到 / 领奖 / 抽奖默认**开**，理由见 [setAutoSupercore]。 */
        const val DEFAULT_AUTO_SUPERCORE = true

        /** 「概览页顶栏显示账号信息」默认开，理由见 [setTopBarAccount]。 */
        const val DEFAULT_TOP_BAR_ACCOUNT = true

        /** 综合分替换经济转化默认**开**，理由见 [setCompositeScore]。 */
        const val DEFAULT_COMPOSITE_SCORE = true

        fun autoRefreshLabel(minutes: Int): String = when {
            minutes <= ON_OPEN -> "每次打开时刷新"
            minutes < 60 -> "每 $minutes 分钟自动刷新"
            else -> "每 ${minutes / 60} 小时自动刷新"
        }
    }
}
