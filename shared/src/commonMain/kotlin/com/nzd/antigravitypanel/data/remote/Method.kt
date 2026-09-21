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
}

/**
 * 26 个 method 里去掉两个订阅消息相关的（`grant` / `GetAndBindWxOpenid`），
 * 那两个走的是另一套 `iChartId=492965 / sIdeToken=uYyNiR / source=communityMini`，
 * 与数据查询无关，不在这里。
 */
enum class IdeMethod(val apiName: String, val page: IdePage) {
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
