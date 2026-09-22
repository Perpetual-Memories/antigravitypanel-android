package com.nzd.antigravitypanel.data.store

/**
 * 少量键值持久化。cookie 是账号凭证，所以实现端必须加密（Android 用 EncryptedSharedPreferences）。
 *
 * 只放 String：这个项目要存的就两类东西——cookie 原文和几个设置项，
 * 为它们引入一整套类型转换器不划算。
 */
interface KeyValueStore {
    suspend fun read(key: String): String?

    suspend fun write(key: String, value: String)

    suspend fun remove(key: String)

    /**
     * **同步**读一次。
     *
     * 存在的唯一理由：有些值必须在**第一帧之前**就拿到。概览的状态卡要是靠
     * [read] 去取缓存，冷启动一定会先用默认状态画一帧「未输入」，
     * 等协程跑完才跳成缓存里的「已识别」——用户看着就是"状态丢了又回来了"。
     *
     * 代价是它**不能自己去初始化实例**（那要读磁盘，不能在合成期间干），
     * 所以平台侧必须先跑一次预热（Android 上是 `primeKeyValueStore()`，
     * 由 `MainActivity` 在 `setContent` 之前调用）；没预热就返回 null，
     * 调用方退回 [read] 那条异步路径。
     */
    fun peek(key: String): String?
}

/** 平台实现。Android 侧依赖 Context，所以只能放在 androidMain。 */
expect fun createKeyValueStore(): KeyValueStore

object StoreKey {
    /** cookie 原文。存原文而不是解析后的字段，这样以后改解析规则老数据也能受益。 */
    const val COOKIE_RAW = "cookie_raw"

    /** 本地保留月数，0 表示无上限。 */
    const val RETENTION_MONTHS = "retention_months"

    /** 主题模式，取值见 [com.nzd.antigravitypanel.ui.theme.ColorMode]。 */
    const val COLOR_MODE = "color_mode"

    /** 自动刷新间隔（分钟）。0 表示"每次打开时刷新"。 */
    const val AUTO_REFRESH_MINUTES = "auto_refresh_minutes"

    /** 预测返回时二级页的最大横移距离（百分比），0..100。 */
    const val PREDICTIVE_BACK_TRANSLATION = "predictive_back_translation"

    /**
     * 预测返回最大横移距离的默认值（百分比）。
     * 与 [com.nzd.antigravitypanel.ui.component.DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT] 是同一个数。
     */
    const val PREDICTIVE_BACK_TRANSLATION_DEFAULT = 75

    /** 置顶的 roomId 集合，逗号分隔。 */
    const val PINNED_ROOMS = "pinned_rooms"

    /** 收藏的 roomId 集合，逗号分隔。 */
    const val FAVORITE_ROOMS = "favorite_rooms"

    /**
     * 最近一次 JSON 导入的摘要文案，同时兼作"有没有导入过"的标志位。
     * 存文案而不是布尔 + 数字：设置页要显示它，概览页只要判断它在不在。
     */
    const val JSON_IMPORT_SUMMARY = "json_import_summary"

    /**
     * 概览页缓存（[com.nzd.antigravitypanel.data.repo.OverviewCache] 的 JSON）。
     *
     * 里面只有场次数字、近五场统计和活动列表，不含 cookie 原文——
     * 它和 cookie 共用同一个加密存储只是图省事，不是因为它需要加密。
     */
    const val OVERVIEW_CACHE = "overview_cache"

    /**
     * 签到看板缓存（`data.signin.SignInCache` 的 JSON）。
     * 冷启动先把上一次的数字摆回去，避免签到卡先空一拍。
     */
    const val SIGNIN_CACHE = "signin_cache"

    /**
     * 自动签到的去重标记，值形如 `20260921|openid`。
     * 同一天同一个号只自动试一次，登出时和 [SIGNIN_CACHE] 一起清掉。
     */
    const val SIGNIN_AUTO_MARK = "signin_auto_mark"

    /**
     * QQ（含 TIM）登录 cookie 原文，**和 [COOKIE_RAW] 是两套凭证**。
     *
     * 存在同一个加密存储里只是图省事——它同样是账号凭证，必须加密，
     * 而且比小程序那串更敏感（能直接操作 QQ 侧的业务）。
     */
    const val QQ_COOKIE_RAW = "qq_cookie_raw"

    /** 游戏中心周签到的缓存（[com.nzd.antigravitypanel.data.qq.QqGiftCache] 的 JSON）。 */
    const val QQ_GIFT_CACHE = "qq_gift_cache"

    /** 游戏中心自动领取的去重标记，值形如 `20260921|uin`。 */
    const val QQ_GIFT_AUTO_MARK = "qq_gift_auto_mark"

    /** 游戏中心「自动领取」开关。1 = 开。 */
    const val QQ_GIFT_AUTO_CLAIM = "qq_gift_auto_claim"

    /**
     * 心悦登录凭证原文（`T-OPENID` / `T-ACCESS-TOKEN`）。
     *
     * **第三套凭证**，和 [COOKIE_RAW]、[QQ_COOKIE_RAW] 互不相干。
     * 存在同一个加密存储里同样只是图省事——它能直接操作心悦账号的业务。
     */
    const val XINYUE_CREDENTIAL_RAW = "xinyue_credential_raw"

    /** 悦享卡状态的缓存（[com.nzd.antigravitypanel.data.xinyue.XinyueCardCache] 的 JSON）。 */
    const val XINYUE_CARD_CACHE = "xinyue_card_cache"

    /** 悦享卡自动领取的去重标记，值形如 `20260921|openid`。 */
    const val XINYUE_AUTO_MARK = "xinyue_auto_mark"

    /**
     * 悦享卡「自动领取」开关。**只有 "0" 才是关**——默认开，
     * 见 [com.nzd.antigravitypanel.data.settings.UserSettings.setAutoClaimXinyueGift]。
     */
    const val XINYUE_AUTO_CLAIM = "xinyue_auto_claim"

    /**
     * Build 计划（[com.nzd.antigravitypanel.data.build.BuildPlan] 的 JSON）。
     *
     * 玩家自己记的"我要给哪把枪刷哪四个插件"，纯本地、服务端没有这个概念。
     * 放在这个加密存储里只是图省事——它该是持久的，但并不敏感。
     */
    const val BUILD_PLAN = "build_plan"
}
