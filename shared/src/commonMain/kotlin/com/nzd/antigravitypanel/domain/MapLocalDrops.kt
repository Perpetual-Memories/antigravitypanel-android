package com.nzd.antigravitypanel.domain

import com.nzd.antigravitypanel.data.remote.dto.CollectionItemDto
import com.nzd.antigravitypanel.data.remote.dto.MapItemDto

/**
 * 官方 `center.map.item.list` **不下发**的那几件隐藏掉落物，客户端自己补。
 *
 * 照官方 PC 端「反重力数据面板」2.0.0 的做法（`mapCollection` 组装函数里那三段
 * `if (String(t) === '19' && !s.find(...))`）：三张图各补一件，名字与顺序都跟它一致，
 * 图标和「已解锁」不写死，从 `collection.pendant.list`（挂件图鉴）里按名字捞 ——
 * 那件东西在服务端其实是「装扮 / 挂件」，所以只有挂件图鉴带 `owned`。
 *
 * 为什么不放进 `MapItemDto` 的兜底表：这条数据是**账号相关**的（owned 跟着号走），
 * 静态表只能给名字，给不了进度；而进度正是这一页要显示的东西。
 *
 * @param mapId 地图 id（配置里的 `mapInfo.id`）：17 精绝古城 / 19 樱之渊 / 13 樱之城。
 * @param tokens 认这件东西的关键词。服务端和挂件图鉴两边的名字可能用 `·` 或 `•`，
 *   所以匹配前统一把间隔号换成 `-`，再逐个 `contains`。
 */
internal data class LocalDropSpec(
    val itemID: String,
    val mapId: Int,
    val name: String,
    val tokens: List<String>,
    val quality: Int = 4,
)

/** 三件隐藏掉落物。顺序就是卡片上从左到右的顺序（PC 端也是插在最前面）。 */
internal val LOCAL_DROP_SPECS: List<LocalDropSpec> = listOf(
    // 樱之渊
    LocalDropSpec("local_aijingling_jy", 19, "AI精灵-晶耀", listOf("AI精灵", "晶耀")),
    // 精绝古城
    LocalDropSpec("local_baiyibeibao", 17, "白蚁背包-幻巢", listOf("白蚁背包", "幻巢")),
    // 樱之城
    LocalDropSpec("local_aijingling", 13, "AI精灵-鎏光", listOf("AI精灵", "鎏光")),
)

/** 名字里那两种间隔号统一成 `-` 再比：官方两边一个用 `·` 一个用 `•` 的情况都见过。 */
private fun normalizeDropName(name: String): String = name.replace('·', '-').replace('•', '-')

private fun MapItemDto.matches(tokens: List<String>): Boolean {
    val name = normalizeDropName(this.name)
    return tokens.all { name.contains(it) }
}

private fun CollectionItemDto.matches(tokens: List<String>): Boolean {
    // 挂件的名字字段是 `name`（PC 端也只读它）；`displayName` 只是兜其余图鉴的命名差异
    val name = normalizeDropName(this.name.ifBlank { displayName })
    return tokens.all { name.contains(it) }
}

/**
 * 挂件图标。**先 `icon` 再 `pic`**：PC 端取的是 `icon`，而 `CollectionItemDto.displayIcon`
 * 是反过来的（先 pic）—— 挂件这两个字段都有值时，顺序错了会画出一张不相干的图。
 */
private fun CollectionItemDto.dropIcon(): String = icon.ifBlank { pic }

/**
 * 把三件隐藏掉落物并进 `center.map.item.list` 的结果。
 *
 * 规则（与 PC 端一致，只是多了"没拉到挂件图鉴"这一支）：
 * - 官方已经下发了同名的那件 → 不补，免得卡片上出现两个一模一样的格子。
 *   **判重按名字**，不看 `itemID`：服务端那件的 id 是它自己分配的，跟本地的 `local_*` 对不上。
 * - 补的时候插在这张图列表的**最前面**，品质固定 4（传说框）。
 * - 图标与 `owned` 依次退：挂件图鉴 → 上次补过的缓存 → 空 / 未解锁。
 * - **挂件图鉴没拉到、也没有上次的缓存**时整条跳过：宁可少一格，
 *   也不要凭空把一件可能已经拿到的东西报成"未解锁"。
 *
 * @param drops 官方接口的原始结果（**不含**本函数补的这三件）。
 * @param pendants `collection.pendant.list` 的 `list`。
 * @param previous 上一次的 `drops`（可能来自落盘缓存），用来在挂件接口失败时保住图标和进度。
 * @param pendantsLoaded 挂件图鉴这一发有没有成功。失败时不吃"没有匹配"这条结论。
 */
fun mergeLocalDrops(
    drops: Map<Int, List<MapItemDto>>,
    pendants: List<CollectionItemDto>,
    previous: Map<Int, List<MapItemDto>> = emptyMap(),
    pendantsLoaded: Boolean = true,
): Map<Int, List<MapItemDto>> {
    var result = drops
    for (spec in LOCAL_DROP_SPECS) {
        // 官方这份里没有这张图 = 这张图压根没有收集品数据，别硬造一格出来
        val current = drops[spec.mapId] ?: continue
        if (current.any { it.matches(spec.tokens) }) continue

        val pendant = pendants.firstOrNull { it.matches(spec.tokens) }
        val cached = previous[spec.mapId]?.firstOrNull { it.itemID == spec.itemID }
        if (pendant == null && cached == null && !pendantsLoaded) continue

        val item = MapItemDto(
            mapID = spec.mapId.toString(),
            itemID = spec.itemID,
            name = spec.name,
            quality = spec.quality,
            icon = pendant?.dropIcon() ?: cached?.icon.orEmpty(),
            owned = pendant?.owned ?: cached?.owned ?: false,
        )
        result = result + (spec.mapId to (listOf(item) + current))
    }
    return result
}

/**
 * 这张图算不算「毕业」（掉落物全解锁）。
 *
 * `null` = 判不了：这张图没有收集品数据，或者掉落物还没拉回来 ——
 * 这两种情况都**不能**当成"已毕业"从页面上抹掉。
 */
fun Map<Int, List<MapItemDto>>.allDropsOwned(mapId: Int): Boolean? {
    val list = this[mapId] ?: return null
    if (list.isEmpty()) return null
    return list.all { it.owned }
}

/**
 * 「仅显示未毕业的地图」：按 [allDropsOwned] 把已毕业的卡剔掉。
 *
 * 一整节都毕业了就整节不摆（赛季小标题下面空着比小标题也消失更奇怪）。
 */
fun filterCompletedSections(
    sections: List<MapSection>,
    drops: Map<Int, List<MapItemDto>>,
): List<MapSection> = sections.mapNotNull { section ->
    val kept = section.entries.filter { drops.allDropsOwned(it.mapId) != true }
    if (kept.isEmpty()) null else section.copy(entries = kept)
}
