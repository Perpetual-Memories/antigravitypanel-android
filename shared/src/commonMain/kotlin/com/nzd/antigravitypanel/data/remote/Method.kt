package com.nzd.antigravitypanel.data.remote

/**
 * `eas_url` 是随 method 一起发的页面标识，两者必须成对。
 *
 * 实测（2026-09-14 抓包）确认的四个页面：record / recordinfo / index / creation / strategy。
 * 同一个 method 在小程序不同入口会带上不同页面，这里取抓包里命中过的那个。
 */
enum class IdePage(val easUrl: String) {
    Record("http://wechatmini.qq.com/-/-/pages/record/record/"),
    RecordInfo("http://wechatmini.qq.com/-/-/pages/recordinfo/recordinfo/"),
    Index("http://wechatmini.qq.com/-/-/pages/index/index/"),
    Creation("http://wechatmini.qq.com/-/-/pages/creation/creation/"),
    Strategy("http://wechatmini.qq.com/-/-/pages/strategy/strategy/"),
    /**
     * `packagespecial/pages/special`：官方 PC 端拉活动日历时带的页面。
     * 和 creation 不是同一个 eas_url，带错拿不到 `rilipeizhi` 那一支内容。
     */
    Special("http://wechatmini.qq.com/-/-/packagespecial/pages/special/special/"),

    /** 福利站。签到、积分兑换、任务中心都挂在这个页面下。 */
    Welfare("http://wechatmini.qq.com/-/-/pages/welfare/welfare/"),
}

/**
 * `comm.ams.game.qq.com/ide/` 这一台服务器上挂着**好几组活动**，每组各自一套
 * `iChartId / sIdeToken`，而且响应的壳长得也不一样。
 *
 * 已经实测到的两组：
 * - [Main]：战绩、图鉴、配置那 26 个 method，`iChartId=430662` / `sIdeToken=NoOapI`，
 *   业务数据在 `jData.data.data`
 * - [Welfare]：福利站的签到 / 积分兑换 / 任务中心，`iChartId=541709` / `sIdeToken=VAs3zJ`，
 *   业务数据在 `jData.welfareStationData.data`
 *
 * 抓包里两组是**混着发**的（进福利站一秒内先打 541709 的签到，再打 430662 的今日运势），
 * 所以不能靠"当前在哪个页面"推断，必须让每个 method 自己声明属于哪一组。
 *
 * @param withSourceParams 表单里要不要带 `from_source` 与 `seasonID`。
 *   [Welfare] 那组抓包里**没有**这两项，多发了可能被当成参数不合法。
 * @param envelopeKey 响应里包着业务数据的那个节点名，见 `IdeEnvelope.unwrapIdeResponse`。
 */
enum class IdeChart(
    val withSourceParams: Boolean,
    val envelopeKey: String,
) {
    Main(withSourceParams = true, envelopeKey = "data"),
    Welfare(withSourceParams = false, envelopeKey = "welfareStationData"),
}

/**
 * 26 个 method 里去掉两个订阅消息相关的（`grant` / `GetAndBindWxOpenid`），
 * 那两个走的是另一套 `iChartId=492965 / sIdeToken=uYyNiR / source=communityMini`，
 * 与数据查询无关，不在这里。
 */
enum class IdeMethod(
    val apiName: String,
    val page: IdePage,
    /** 这个 method 属于哪组活动。默认 [IdeChart.Main]，即现有的那 26 个。 */
    val chart: IdeChart = IdeChart.Main,
) {
    // 战绩
    UserStats("center.user.stats", IdePage.Record),
    GameList("center.user.game.list", IdePage.Record),
    GameDetail("center.game.detail", IdePage.RecordInfo),
    MapStats("center.user.map.stats", IdePage.Record),
    MapDrop("center.user.map.drop", IdePage.Index),
    MapItemList("center.map.item.list", IdePage.Record),

    // 配置下发
    ConfigList("center.config.list", IdePage.Record),

    // 首页 / 用户
    UserDay("center.user.day", IdePage.Index),
    UserInfo("user.info", IdePage.Index),
    WeeklyReportStatus("weekly.report.status", IdePage.Index),
    EventReport("event.report", IdePage.Index),

    // 图鉴
    CollectionHome("collection.home", IdePage.Index),
    CollectionWeapon("collection.weapon.list", IdePage.Record),
    CollectionTrap("collection.trap.list", IdePage.Record),
    CollectionPlugin("collection.plugin.list", IdePage.Record),
    CollectionRole("collection.role.list", IdePage.Record),
    CollectionPendant("collection.pendant.list", IdePage.Record),
    CollectionMecha("collection.mecha.list", IdePage.Record),
    CollectionMechaSkin("collection.mecha.skin.list", IdePage.Record),
    CollectionTowerSkin("collection.tower.skin.list", IdePage.Record),

    // 配装 / 社区
    GearConfig("gear.config.list", IdePage.Creation),
    GearEquipmentHot("gear.equipment.hot.list", IdePage.Creation),
    DistContents("dist.contents", IdePage.Creation),
    /**
     * 同样是 `dist.contents`，但带 [IdePage.Special] 的 eas_url + 活动日历那套参数。
     * 官方 PC 端就是这么取的（`fetchCalendar`），见 `domain/ActivityCalendar.kt`。
     */
    DistCalendar("dist.contents", IdePage.Special),
    ThreadSearch("thread.search", IdePage.Strategy),

    // 福利站（2026-09-21 抓小程序签到页拿到）
    /**
     * 签到看板。param 里的 `userTime` 抓包时是空串，服务端按当天返回。
     * 响应带 groupList，里面是「今天签没签 / 连续与累计天数 / 今日奖励 / 任务列表」。
     */
    SignInList("/api/signin/list", IdePage.Welfare, IdeChart.Welfare),

    /**
     * 签到。param 只带 `groupID`（抓包里是 0）。
     *
     * 重复调用的行为服务端没给明说，所以**不要**无脑打这个接口：
     * 先走 [SignInList] 看 `isSignIn`，没签才打。
     */
    SignInDo("/api/signin/do", IdePage.Welfare, IdeChart.Welfare),
}

/** 图鉴分类 → method。图鉴页要按分类切换，用枚举比散着传字符串好维护。 */
enum class CollectionKind(val method: IdeMethod) {
    Weapon(IdeMethod.CollectionWeapon),
    Trap(IdeMethod.CollectionTrap),
    Plugin(IdeMethod.CollectionPlugin),
    Role(IdeMethod.CollectionRole),
    Pendant(IdeMethod.CollectionPendant),
    Mecha(IdeMethod.CollectionMecha),
    MechaSkin(IdeMethod.CollectionMechaSkin),
    TowerSkin(IdeMethod.CollectionTowerSkin),
}
