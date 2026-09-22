package com.nzd.antigravitypanel.domain

import com.nzd.antigravitypanel.data.remote.dto.GameConfigDto

/**
 * 模式归属：只认 mapId。
 *
 * 这套 id 分组取自《逆战：未来》官方数据 —— 原版「反重力数据面板」前端里
 * `iMapId -> { name, mode }` 的完整映射表，已同步到 **v1.8.8（S4·朔望计划，2026-09-22）**
 * 的 30 张图：S4 新增了猎场的 20 朔望计划 / 22 禁魔岛、塔防的 311 银河战舰。
 * 比早期参照 NZM 整理的那版全（僵尸猎场补了 13/15/18/19，塔防补了 309/310，
 * 时空追猎补了 424）。
 *
 * 之前缺的这几个 id 正是本地 `nzm_matches.json` 里出现、却会被判成 UNKNOWN 的那些。
 * 哪天官方改了地图 id，改下面这一个表和 [modeOf] 即可。
 * 同步时直接比前端产物 `assets/index-*.beautified.js` 里的 `Rs` 那一段。
 *
 * [GameMode.MECHA] 不计入统计（NZM 里 `modeName.includes('机甲') || iGameMode === 6`
 * 会被整体排除），但概览的模式切换里要单独展示，所以保留枚举值。
 */
enum class GameMode(val label: String, val countable: Boolean) {
    HUNT("僵尸猎场", true),
    TOWER("塔防", true),
    TIME_HUNT("时空追猎", true),
    MECHA("机甲非对称", false),
    UNKNOWN("其他", false),
}

/** 官方 `iMapId -> 地图名`。同一个模式里不同 id 可能同图（新旧版本地图），保留原样。 */
private val OFFICIAL_MAP_NAMES: Map<Int, String> = mapOf(
    1000 to "风暴峡谷",
    1001 to "风暴峡谷",
    1002 to "凯旋之地",

    112 to "黑暗复活节",
    114 to "大都会",
    115 to "冰点源起",
    12 to "黑暗复活节",
    13 to "樱之城",
    14 to "大都会",
    15 to "丛林魅影",
    16 to "昆仑神宫",
    17 to "精绝古城",
    18 to "销金之城",
    19 to "樱之渊",
    // —— S4·朔望计划（2026-09-22）新增的两张猎场图 ——
    20 to "朔望计划",
    21 to "冰点源起",
    22 to "禁魔岛",
    30 to "猎场-新手关",

    300 to "空间站",
    304 to "20号星港",
    306 to "联盟大厦",
    308 to "塔防-新手关",
    309 to "蔷薇庄园",
    310 to "失落游轮",
    // S4 新增的塔防图
    311 to "银河战舰",

    321 to "根除变异",
    322 to "夺回资料",
    323 to "猎杀南十字",
    324 to "追猎-新手关",
    424 to "月海火线",
)

private val HUNT_MAP_IDS = setOf(12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 30, 112, 114, 115)
private val TOWER_MAP_IDS = setOf(300, 304, 306, 308, 309, 310, 311)
private val TIME_HUNT_MAP_IDS = setOf(321, 322, 323, 324, 424)

/**
 * `center.user.game.list` 的 `map_mode` 参数值，也是 `config.mapInfo[*].mode` 的取值。
 *
 * ⚠️ 这里是 `猎场` 不是 [GameMode.HUNT] 的 `僵尸猎场` —— 后者只是 UI 上的叫法，
 * 服务端认的是 `猎场`。早年两个名字混着用，接口会静默返回空。
 */
val GameMode.serverMode: String
    get() = when (this) {
        GameMode.HUNT -> "猎场"
        GameMode.TOWER -> "塔防"
        GameMode.TIME_HUNT -> "时空追猎"
        else -> ""
    }

/** 参与同步 / 统计的三个模式及其服务端名。 */
val COUNTABLE_MODES: List<GameMode> = listOf(GameMode.HUNT, GameMode.TOWER, GameMode.TIME_HUNT)

fun modeOf(mapId: Int): GameMode = when {
    mapId in HUNT_MAP_IDS -> GameMode.HUNT
    mapId in TOWER_MAP_IDS -> GameMode.TOWER
    mapId in TIME_HUNT_MAP_IDS -> GameMode.TIME_HUNT
    // 1000 段以上都是机甲图，用区间兜底比逐个枚举稳（官方也可能再加）
    mapId >= 1000 -> GameMode.MECHA
    else -> GameMode.UNKNOWN
}

/**
 * 地图名。配置里有就用配置；没有就用官方表；两者都没有才退回 `未知(id)`。
 *
 * 中间那层是必要的：`center.config.list` 不一定下发全部地图，本地导入的历史对局
 * 里可能正好有配置没覆盖到的图，没有它 UI 会退化成 "未知(13)"。
 */
fun mapNameOf(mapId: Int, config: GameConfigDto = GameConfigDto()): String =
    config.mapInfo[mapId.toString()]?.name?.takeIf { it.isNotBlank() }
        ?: OFFICIAL_MAP_NAMES[mapId]
        ?: "未知($mapId)"

/**
 * 官方 `iSubModeType -> 难度名`。
 *
 * 和 [OFFICIAL_MAP_NAMES] 是同一个道理：`center.config.list` **只在有凭证时才拉得到**，
 * 而本地库里的对局完全可能来自 JSON 导入 —— 用户根本没登录。没有这张兜底表，
 * 导入的对局在地图分布页会全部落进「未知」那一列，实测就是这样：
 * 每张图只剩一个「未知 n」，而 n 正好等于总场次（因为所有对局都进了同一个桶）。
 *
 * ⚠️ **这张表是官方前端里 `Rs.labels` 的完整抄本，别只留"看着眼熟"的那半段。**
 *
 * `iSubModeType` **不是全局连续的**：猎场用 2-6 / 33，而塔防和时空追猎用的是
 * 另外几个号段（66-69、95、130-133、160）—— 官方前端把这些段全部硬编码进去恰恰是因为
 * **服务端下发的 `difficultyInfo` 里没有它们**（真实样本只有 0-12 / 32 / 33 / 64）。
 * 只抄猎场那半张表的后果就是：登录了也一样，塔防和时空追猎的难度全部解析不出来。
 *
 * 同一个难度名会出现在多个号段（2 / 66 / 130 都是「普通」），这是正常的 ——
 * 分档看的是**名字**，不是编号。
 */
private val OFFICIAL_DIFFICULTY_NAMES: Map<Int, String> = mapOf(
    0 to "默认",
    1 to "引导",
    2 to "普通",
    3 to "困难",
    4 to "英雄",
    5 to "炼狱",
    6 to "折磨I",
    7 to "折磨II",
    8 to "折磨III",
    9 to "折磨IV",
    10 to "折磨V",
    11 to "折磨VI",
    // 服务端 config 里的写法（官方前端的 Rs.labels 没有 12，但有 31 / 95）
    12 to "挑战模式",
    31 to "挑战",
    32 to "练习",
    33 to "超限",
    64 to "最大值",
    // —— 以下都是塔防 / 时空追猎的号段，缺了它们这两个模式就是满屏「未知」——
    66 to "普通",
    67 to "困难",
    68 to "英雄",
    69 to "炼狱",
    95 to "挑战",
    130 to "普通",
    131 to "困难",
    132 to "英雄",
    133 to "炼狱",
    160 to "训练场",
)

/**
 * 难度名。配置优先（官方可能加新难度），配置里没有时用内置表，两者都没有才返回 null。
 *
 * 中间那层不是冗余：只认服务端配置的话，**没登录 + 导入过 JSON** 这个组合下
 * 难度会全线退化成「未知」，而这恰恰是最常见的用法之一。
 */
fun difficultyNameOf(subModeType: Int, config: GameConfigDto = GameConfigDto()): String? =
    config.difficultyInfo[subModeType.toString()]?.name?.takeIf { it.isNotBlank() }
        ?: OFFICIAL_DIFFICULTY_NAMES[subModeType]

/**
 * 统计口径的**难度档位**名 —— 地图分布页按这个分组，而不是按 [difficultyNameOf] 的原始名。
 *
 * 三条归一化全部照官方前端（`GC()` 里给地图卡片算 `diffStats` 的那段，
 * 以及给对局列表补 `diffName` 的那段）：
 *
 * ```js
 * r.includes('折磨') && (r = '折磨')
 * (r === '挑战模式' || r === '默认' || r === '挑战') && (r = '挑战')
 * // 对局列表那条窄一些：n === '塔防' && r === '默认' && (r = '挑战')
 * ```
 *
 * 为什么要归一：
 * - **折磨I ~ 折磨VI 合成一列「折磨」**。不合成的话，同一张图会横着排出六列折磨，
 *   每列只有几场，既看不出总量也占满整张卡。官方就是合成一列。
 * - **塔防的「默认」就是「挑战」**。塔防没有"默认难度"这个概念，服务端给 0 是因为
 *   它的挑战关走的是默认通道。不改名的话塔防卡片上会莫名其妙多一列「默认」。
 *
 * 和 [difficultyGroupOf] 的分工：那个只管排序 / 筛选时的档位判定，不动名字本身；
 * 这个是**真的要改显示出来的名字**，只给地图分布用。详情页、历史页仍然显示原始名
 * （「折磨III」就是折磨III，玩家认这个）。
 *
 * 解析不出来时返回 null，调用方自己决定是标「未知」还是干脆不摆这一列。
 */
fun difficultyBucketOf(
    subModeType: Int,
    mode: GameMode = GameMode.UNKNOWN,
    config: GameConfigDto = GameConfigDto(),
): String? {
    val raw = difficultyNameOf(subModeType, config) ?: return null
    if (raw.startsWith("折磨")) return "折磨"
    if (raw == "挑战" || raw == "挑战模式") return "挑战"
    // 只有塔防的默认关才是挑战关；猎场 / 追猎真出现 0 就让它显示为「默认」，别乱认
    if (mode == GameMode.TOWER && raw == "默认") return "挑战"
    return raw
}

/**
 * 分区（Boss）名。`huntingFieldPartitionArea` 是 `id -> 名字`。
 *
 * 和 [OFFICIAL_MAP_NAMES] / [OFFICIAL_DIFFICULTY_NAMES] 是同一个病根：
 * `center.config.list` 只在**有凭证**时才拉得到，没登录又是一张空表，
 * 详情页的「区域用时」于是退化成一串「区域 40101」。
 */
fun partitionNameOf(areaId: String, config: GameConfigDto = GameConfigDto()): String =
    config.huntingFieldPartitionArea[areaId]?.takeIf { it.isNotBlank() }
        ?: areaId.toIntOrNull()?.let { OFFICIAL_PARTITION_AREAS[it] }
        ?: "区域 $areaId"

private val OFFICIAL_PARTITION_AREAS: Map<Int, String> = mapOf(
    40014 to "北欧森林",
    40101 to "下水道",
    40104 to "Z博士",
    40111 to "下水道",
    40112 to "博物馆",
    40113 to "工厂",
    40121 to "下水道",
    40122 to "博物馆",
    40123 to "工厂",
    40124 to "Z博士",
    40131 to "下水道",
    40132 to "博物馆",
    40133 to "工厂",
    40134 to "Z博士",
    40141 to "下水道",
    40142 to "博物馆",
    40143 to "工厂",
    40144 to "Z博士",
    40151 to "下水道",
    40152 to "博物馆",
    40153 to "工厂",
    40154 to "Z博士",
    40211 to "巴黎1区",
    40212 to "巴黎2区",
    40213 to "红磨坊",
    40221 to "巴黎1区",
    40222 to "巴黎2区",
    40223 to "红磨坊",
    40224 to "凯旋门",
    40231 to "巴黎1区",
    40232 to "巴黎2区",
    40233 to "红磨坊",
    40234 to "凯旋门",
    40235 to "死亡骑士",
    40241 to "巴黎1区",
    40242 to "巴黎2区",
    40243 to "红磨坊",
    40244 to "凯旋门",
    40245 to "死亡骑士",
    40251 to "巴黎1区",
    40252 to "巴黎2区",
    40253 to "红磨坊",
    40254 to "凯旋门",
    40255 to "死亡骑士",
    40411 to "海岸鸟居",
    40412 to "城墙",
    40413 to "日式街道",
    40414 to "洞穴",
    40415 to "城堡",
    40421 to "海岸鸟居",
    40422 to "城墙",
    40423 to "日式街道",
    40424 to "洞穴",
    40425 to "城堡",
    40431 to "海岸鸟居",
    40432 to "城墙",
    40433 to "日式街道",
    40434 to "洞穴",
    40435 to "城堡",
    40441 to "海岸鸟居",
    40442 to "城墙",
    40443 to "日式街道",
    40444 to "洞穴",
    40445 to "城堡",
    40514 to "北欧森林",
    40521 to "北欧城市",
    40523 to "地下实验室B",
    40524 to "北欧森林",
    40529 to "地下实验室",
    40531 to "北欧城市",
    40532 to "地下实验室A",
    40533 to "地下实验室B",
    40534 to "北欧森林",
    40535 to "英灵殿",
    40539 to "地下实验室",
    40541 to "北欧城市",
    40542 to "地下实验室A",
    40543 to "地下实验室B",
    40544 to "北欧森林",
    40545 to "英灵殿",
    40549 to "地下实验室",
    40551 to "北欧城市",
    40552 to "地下实验室A",
    40553 to "地下实验室B",
    40554 to "北欧森林",
    40555 to "英灵殿",
    40559 to "地下实验室",
    40561 to "北欧城市",
    40562 to "地下实验室A",
    40563 to "地下实验室B",
    40564 to "北欧森林",
    40565 to "英灵殿",
    40569 to "地下实验室",
    40611 to "漂流",
    40612 to "神庙",
    40621 to "漂流",
    40622 to "神庙",
    40623 to "蛇王",
    40624 to "食人花",
    40631 to "漂流",
    40632 to "神庙",
    40633 to "蛇王",
    40634 to "食人花",
    40635 to "缇娜一阶段",
    40641 to "漂流",
    40642 to "神庙",
    40643 to "蛇王",
    40644 to "食人花",
    40645 to "缇娜一阶段",
    40646 to "缇娜二阶段",
    40711 to "龙顶冰川",
    40712 to "九层妖塔",
    40721 to "龙顶冰川",
    40722 to "九层妖塔",
    40724 to "魔国祭坛",
    40731 to "龙顶冰川",
    40732 to "九层妖塔",
    40733 to "记忆之城",
    40734 to "魔国祭坛",
    40735 to "逆转时间",
    40741 to "龙顶冰川",
    40742 to "九层妖塔",
    40743 to "记忆之城",
    40744 to "魔国祭坛",
    40745 to "逆转时间",
    40811 to "蚁后",
    40812 to "白骆驼",
    40813 to "黑蛇",
    40821 to "蚁后",
    40822 to "白骆驼",
    40823 to "黑蛇",
    40824 to "主教祭司",
    40825 to "奴隶主",
    40831 to "蚁后",
    40832 to "白骆驼",
    40833 to "黑蛇",
    40834 to "主教祭司",
    40835 to "奴隶主",
    40836 to "精绝女王",
    40841 to "蚁后",
    40842 to "白骆驼",
    40843 to "黑蛇",
    40844 to "主教祭司",
    40845 to "奴隶主",
    40846 to "精绝女王",
    40847 to "关卡3",
)

/**
 * 筛选器里的难度选项（不区分模式时用这个）。
 *
 * 优先用配置下发的难度表（按 id 升序），配置还没拉到时用内置顺序兜底——
 * 内置顺序来自需求里点名的那七个难度，不是拍脑袋排的。
 */
fun difficultyOptions(config: GameConfigDto): List<String> {
    val fromConfig = config.difficultyInfo.values
        // 官方表里还有 默认 / 引导 / 练习 / 最大值，混进筛选下拉框只会让列表变得没意义
        .filter { it.name.isNotBlank() && difficultyGroupOf(it.name) in COUNTED_DIFFICULTIES }
        .sortedBy { it.id }
        .map { it.name }
    return fromConfig.ifEmpty { FALLBACK_DIFFICULTIES }
}

/**
 * **每个模式**可选的难度档位 —— 官方前端的 `Gs` 表，原样搬过来。
 *
 * ```js
 * Gs = {
 *   僵尸猎场: [全部难度, 超限, 折磨, 炼狱, 英雄, 困难, 普通],
 *   塔防:     [全部难度, 挑战, 折磨, 炼狱, 英雄, 困难, 普通, 练习, 新手关, 训练场],
 *   时空追猎: [全部难度, 超限, 折磨, 炼狱, 英雄, 困难, 普通],
 * }
 * ```
 *
 * 为什么必须按模式分：**塔防没有超限、但有练习 / 新手关 / 训练场**；
 * 猎场和追猎反过来，有超限、没有练习。用一张全局表的话，
 * 选了塔防再想筛「练习」永远选不到，选了追猎却能选一个根本不存在的「练习」。
 *
 * 顺序照官方：难度从高到低（**不含**「全部难度」，那是筛选器的空选项，由 UI 自己加）。
 */
private val MODE_DIFFICULTIES: Map<GameMode, List<String>> = mapOf(
    GameMode.HUNT to listOf("超限", "折磨", "炼狱", "英雄", "困难", "普通"),
    GameMode.TOWER to listOf(
        "挑战", "折磨", "炼狱", "英雄", "困难", "普通", "练习", "新手关", "训练场",
    ),
    GameMode.TIME_HUNT to listOf("超限", "折磨", "炼狱", "英雄", "困难", "普通"),
)

/** 没选模式时的并集（官方 `Ks()` 的兜底分支）。 */
private val ALL_MODE_DIFFICULTIES: List<String> =
    listOf("超限", "挑战", "折磨", "炼狱", "英雄", "困难", "普通", "练习", "新手关", "训练场")

/** 任何模式下都不该出现在筛选器里的档位。 */
private val NON_COUNTED_DIFFICULTIES = setOf("默认", "引导", "最大值")

/**
 * 指定模式的难度选项。`mode == null`（全部模式）时给并集。
 *
 * 表里没写到、但配置里确实下发了的档位（官方哪天加新难度）补在末尾，
 * 免得用户看得见数据却筛不出来。
 */
fun difficultyOptionsForMode(mode: GameMode?, config: GameConfigDto): List<String> {
    val base = if (mode == null) ALL_MODE_DIFFICULTIES else MODE_DIFFICULTIES[mode] ?: ALL_MODE_DIFFICULTIES
    val extra = config.difficultyInfo.values
        .map { it.name }
        .filter { it.isNotBlank() }
        .map(::difficultyGroupOf)
        .filter { it !in base && it !in NON_COUNTED_DIFFICULTIES }
        .distinct()
        .sortedByDescending(::difficultyRank)
    return base + extra
}

/**
 * 官方 `难度名 -> 权重`。同一来源的表：数字越大越难，用它给难度排序，
 * 免得把顺序写死在代码里（`50` 和 `500` 谁在前这种事不该靠人记）。
 *
 * 练习 / 训练场 / 引导同权重、`新手关` / `默认` 同 0，所以这张表只能
 * 名→值单向用，反过来查会撞车，`difficultyNameOf` 也因此仍然只认服务端配置。
 */
private val DIFFICULTY_WEIGHTS: Map<String, Int> = mapOf(
    "超限" to 70,
    "挑战" to 60,
    "折磨" to 50,
    "炼狱" to 40,
    "英雄" to 30,
    "困难" to 20,
    "普通" to 10,
    "练习" to 5,
    "训练场" to 5,
    "引导" to 5,
    "新手关" to 0,
    "默认" to 0,
)

/** 筛选器只展示这七个正式难度，练习场那几个混进来会让列表变得没意义。 */
private val COUNTED_DIFFICULTIES = setOf("超限", "挑战", "折磨", "炼狱", "英雄", "困难", "普通")

/**
 * 难度列的次序：数字小的排前面（普通 -> 超限）。
 *
 * 地图分布页的卡片上，同一张图的几个难度要按**难度**递增排，而不是按场次多少排 ——
 * 每张卡都按场次排的话，「炼狱」在最左、「普通」在最右，横着扫一列根本比不出来。
 * 表里没有的名字（配置下发了新难度）排到最后。
 */
fun difficultyRank(name: String): Int = DIFFICULTY_WEIGHTS[difficultyGroupOf(name)] ?: Int.MAX_VALUE

/**
 * 难度名归一化到"难度档位"：折磨I ~ 折磨VI -> 折磨，挑战模式 -> 挑战，其余原样。
 *
 * 两个用处，都是被官方的命名坑出来的：
 * - **排序**：拿「折磨VI」这种原名去查 [DIFFICULTY_WEIGHTS] 会落空、被当成未知难度
 *   排到最后，卡片上那一排难度列的顺序就乱了。
 * - **筛选**：没登录时下拉框给的是内置那七个（「折磨」「挑战」），
 *   而数据里的名字是「折磨I」「挑战模式」—— 直接判等的话按「折磨」筛永远筛不出东西。
 *
 * 对已经归一化的名字是幂等的，所以当配置拉到、下拉框里就是「折磨I」时，筛选仍然精确。
 */
fun difficultyGroupOf(name: String): String = when {
    name.startsWith("折磨") -> "折磨"
    name.startsWith("挑战") -> "挑战"
    else -> name
}

/**
 * 一条对局的难度名是否命中筛选条件。
 *
 * 为什么不能简单判等：没拉到配置时下拉框给的是内置那七个笼统档位（「折磨」「挑战」），
 * 而数据里的名字是分级名（「折磨I」「挑战模式」）—— 逐字判等的话选「折磨」一条都出不来。
 *
 * 反过来也不能一律按档位比：配置拉到之后下拉框里就是「折磨I」「折磨VI」这些分级名了，
 * 那时选「折磨I」就该只出折磨I。所以规则是 **笼统档位按组匹配、分级名精确匹配**。
 */
fun difficultyMatches(name: String?, filter: String): Boolean {
    if (name == null) return false
    if (name == filter) return true
    // `filter` 本身就是档位名（而不是「折磨I」这种分级名）时才放宽到整档
    return difficultyGroupOf(filter) == filter && difficultyGroupOf(name) == filter
}

private val FALLBACK_DIFFICULTIES: List<String> = DIFFICULTY_WEIGHTS
    .filterKeys { it in COUNTED_DIFFICULTIES }
    .entries
    .sortedByDescending { it.value }
    .map { it.key }
