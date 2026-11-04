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
    const val PREDICTIVE_BACK_TRANSLATION_DEFAULT = 40

    /** 置顶的 roomId 集合，逗号分隔。 */
    const val PINNED_ROOMS = "pinned_rooms"

    /** 收藏的 roomId 集合，逗号分隔。 */
    const val FAVORITE_ROOMS = "favorite_rooms"

    /**
     * 地图分布页「按赛季筛选」——**猎场**勾了哪几个赛季，逗号分隔的 key（`S0`…）。
     * 猎场和塔防各存一份（要能分别筛），见 `data.settings.MapSeasonFilter`。
     * 不存在 = 从没设过 = 全选。
     */
    const val MAP_SEASON_FILTER_HUNT = "map_season_filter_hunt"

    /** 同上，塔防那份。 */
    const val MAP_SEASON_FILTER_TOWER = "map_season_filter_tower"

    /**
     * 地图分布页「官方统计」的缓存（`data.repo.MapStatsCache` 的 JSON）。
     *
     * 缓存它才不会在冷启动同步的那几秒里先摆一份本地场次（和小程序对不上的那个数）。
     * 里面只有地图 id 和场次，不含凭证，放在这个加密存储里只是图省事。
     */
    const val MAP_STATS_CACHE = "map_stats_cache"

    /**
     * 地图分布页掉落物的缓存（`data.repo.MapDropsCache` 的 JSON）。
     *
     * 理由同 [MAP_STATS_CACHE]：卡片背面的收集进度不该因为"凭证还没挂上"
     * 先显示一句「还没有设置 cookie」。
     */
    const val MAP_DROPS_CACHE = "map_drops_cache"

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
     * 「打开APP自动签到」开关。**只有 "0" 才是关**——默认开，
     * 见 [com.nzd.antigravitypanel.data.settings.UserSettings.setAutoSignIn]。
     */
    const val SIGNIN_AUTO = "signin_auto"

    /**
     * 「打开APP自动领取任务中心奖励」开关。**只有 "0" 才是关**——默认开。
     *
     * 和游戏中心那个默认关不一样，理由同样是接口性质：任务中心这个领取接口
     * **一次只领传进去的那一个任务**（`groupID + taskID`），而游戏中心的
     * `exchange-all-gifts` 是批量的。前者等价于"每天领一次该领的东西"。
     */
    const val WELFARE_TASK_AUTO_CLAIM = "welfare_task_auto_claim"

    /**
     * 任务中心自动领取的去重标记，值形如 `20260929|openid|65928,65929`。
     *
     * 前两段是「日期|openid」，和 [SIGNIN_AUTO_MARK] 一个套路；末尾是这一天
     * **已经领过**的 taskId 集合。之所以要记到任务粒度：每日任务和每周任务
     * 不是同时达成的——早上只领到每日那一个，晚上每周那个达成时必须还能再领一次，
     * 按天整段去重会把第二次吞掉。
     */
    const val WELFARE_TASK_AUTO_MARK = "welfare_task_auto_mark"

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
     * 超核组队活动的网址（每期不同，用户手动粘）。
     *
     * 它是这一套功能的**入口**：活动号（actId）、gid、以及抽奖/签到按钮的 flow id
     * 全部要从这个网址对应的活动页配置里读出来，所以它和凭证一样必须持久化。
     */
    const val SUPERCORE_ACTIVITY_URL = "supercore_activity_url"

    /**
     * 超核组队活动的请求头凭证（`T-OPENID` / `T-ACCESS-TOKEN` / `T-APPID`）。
     *
     * ⚠️ **第四套凭证**，和 [COOKIE_RAW]、[QQ_COOKIE_RAW]、[XINYUE_CREDENTIAL_RAW] 都不通用：
     * 它走 QQ 互联登录（appid 101813972），服务端会校验 appid，
     * 心悦悦享卡那对 header（appid 101484782）拿过来会直接 `appid校验不通过`。
     */
    const val SUPERCORE_CREDENTIAL_RAW = "supercore_credential_raw"

    /** 超核活动页配置解析出来的按钮/flow 计划（`SupercorePlan` 的 JSON），避免每次冷启动都去抓页面。 */
    const val SUPERCORE_PLAN_CACHE = "supercore_plan_cache"

    /** 超核活动的今日状态缓存（`SupercoreStatus` 的 JSON）。 */
    const val SUPERCORE_CACHE = "supercore_cache"

    /** 旧版本的本机抽奖历史。新版本改用官方「领奖记录」，保留键名只为兼容旧备份。 */
    const val SUPERCORE_DRAW_HISTORY = "supercore_draw_history"

    /**
     * 超核「打开APP自动签到、领奖并抽奖」开关。**只有 "0" 才是关**——默认开
     * （默认开的理由见 `UserSettings.setAutoSupercore`）。
     */
    const val SUPERCORE_AUTO = "supercore_auto"

    /** 选中的角色（`game_open_id|partition_id|role_id`），多角色账号用。 */
    const val SUPERCORE_ROLE_KEY = "supercore_role_key"

    /** 「概览页顶栏显示账号信息」开关。**只有 "0" 才是关**——默认开。 */
    const val TOP_BAR_ACCOUNT = "top_bar_account"

    /**
     * 「代理模式」的**一次性**标记，存在即代表"下一次冷启动要保护一轮"。
     *
     * ⚠️ 它**不是**普通的持久化开关，别照 [SIGNIN_AUTO] 那套"没写过就是开"来读：
     * 这里没写过就是**关**（默认不暂停）。写盘的唯一目的是让"下一次冷启动"
     * 知道要跳过一次自动签到——因为冷启动那一发请求发生在任何 UI 出现之前，
     * 靠内存态根本拦不住（见 `UserSettings.restore`）。
     */
    const val WIKI_PROXY_MODE = "wiki_proxy_mode"

    /**
     * 「用综合分替换经济转化」开关。**只有 "0" 才是关**——默认开。
     *
     * 见 [com.nzd.antigravitypanel.data.settings.UserSettings.setCompositeScore]。
     */
    const val COMPOSITE_SCORE = "composite_score"

    /**
     * 「积分显示单位」：哪几个页面用万。逗号分隔的 [com.nzd.antigravitypanel.data.settings.ScoreUnitTarget] 名字。
     *
     * ⚠️ **键不存在 ≠ 空串**：不存在 = 从没设过 = 用默认值（历史 / 概览用万），
     * 空串 = 用户把所有勾都取消了 = 全部显示完整数字。
     */
    const val SCORE_WAN_TARGETS = "score_wan_targets"

    /**
     * Build 计划（[com.nzd.antigravitypanel.data.build.BuildPlan] 的 JSON）。
     *
     * 玩家自己记的"我要给哪把枪刷哪四个插件"，纯本地、服务端没有这个概念。
     * 放在这个加密存储里只是图省事——它该是持久的，但并不敏感。
     */
    const val BUILD_PLAN = "build_plan"
}
