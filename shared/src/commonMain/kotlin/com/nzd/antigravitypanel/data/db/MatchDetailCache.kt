package com.nzd.antigravitypanel.data.db

import com.nzd.antigravitypanel.data.remote.dto.GameDetailDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 单局详情的缓存内容，序列化成 JSON 存在 `match_boss_stats.payloadJson` 这一列里。
 *
 * 为什么是「一整块 JSON」而不是给每个数开一列：
 * - 这些数字**只有** `center.game.detail` 会给，而那个接口一次只能查一局、配额有限，
 *   能存下来的都必须存——开列的话每加一个数就要写一次迁移；
 * - 导出行数最多几千，导出时一次性解码，开销可以忽略；
 * - 设备上的老数据在升级后仍然要能读出来，所以字段全部带默认值，
 *   配合 [DetailCacheJson] 的 `ignoreUnknownKeys`：新版本加字段不会让老行解码失败。
 *
 * ⚠️ 这里**只放英文数字**：导出文件要求全 ASCII，中文名（昵称 / 武器名 / 地图名）
 * 一律不进缓存，需要名字时拿 id 去 [com.nzd.antigravitypanel.domain] 那几张表反查。
 */
@Serializable
data class CachedMatchDetail(
    /** 这一局名单上有多少人（含自己）。 */
    val playerCount: Int = 0,
    /** 肉鸽模式的局。 */
    val isRogueMatch: Boolean = false,
    /** 金币。塔防 / 时空追猎没有这个口径，恒为 0。 */
    val coin: Long = 0,
    /** 自己打在 Boss 上的伤害。 */
    val bossDamage: Long = 0,
    /** 自己打在小怪上的伤害。 */
    val mobDamage: Long = 0,
    /** 全队 Boss 伤害（含自己）。 */
    val teamBossDamage: Long = 0,
    /** 除自己以外打得最多的那个人。 */
    val rivalBossDamage: Long = 0,
    /** 打过几个分区（Boss）。 */
    val partitionCount: Int = 0,
    /** 各分区用时合计，秒。 */
    val partitionTotalSec: Int = 0,
    /** 配装。通常一到两组（主 / 副武器），每组一把枪加若干插件。 */
    val equipment: List<CachedEquipment> = emptyList(),
)

@Serializable
data class CachedEquipment(
    val weaponId: Long = 0,
    val weaponQuality: Int = 0,
    val plugins: List<CachedItem> = emptyList(),
)

@Serializable
data class CachedItem(
    val itemId: Long = 0,
    val quality: Int = 0,
)

/**
 * 从单局详情里抽出要缓存的那些数。纯函数，方便单测。
 *
 * @return null 表示**这份详情不完整**（没有 `loginUserDetail`，或者名单是空的），
 *   调用方拿到 null 就不该写库 —— 那不是"这局没有数据"，写一条空行会永久挡掉补拉。
 */
fun cachedDetailOf(detail: GameDetailDto): CachedMatchDetail? {
    val self = detail.loginUserDetail ?: return null
    val players = detail.list
    if (players.isEmpty()) return null

    // 认队友时按 openId 排除自己；openId 是空串时（服务端偶尔这么给）退回到
    // "排除同一个对象"，否则 `"" != ""` 会把所有人都当成自己，第二名永远是 0
    val selfId = self.baseDetail?.vOpenID.orEmpty()
    val rivals = if (selfId.isBlank()) {
        players.filter { it !== self }
    } else {
        players.filter { it.baseDetail?.vOpenID.orEmpty() != selfId }
    }
    val hunting = self.huntingDetails
    val partitions = hunting?.partitionDetails.orEmpty()

    return CachedMatchDetail(
        playerCount = players.size,
        isRogueMatch = detail.isRogueMatch,
        coin = hunting?.totalCoin ?: 0L,
        bossDamage = hunting?.damageTotalOnBoss ?: 0L,
        mobDamage = hunting?.damageTotalOnMobs ?: 0L,
        teamBossDamage = players.sumOf { it.huntingDetails?.damageTotalOnBoss ?: 0L },
        rivalBossDamage = rivals.maxOfOrNull { it.huntingDetails?.damageTotalOnBoss ?: 0L } ?: 0L,
        partitionCount = partitions.size,
        partitionTotalSec = partitions.sumOf { it.usedTime },
        equipment = self.equipmentScheme.map { scheme ->
            CachedEquipment(
                weaponId = scheme.weaponID,
                weaponQuality = scheme.quality,
                plugins = scheme.commonItems.map { CachedItem(it.itemID, it.quality) },
            )
        },
    )
}

/** [CachedMatchDetail] 专用的 [Json]。老数据缺字段 / 多字段都不能让它解码失败。 */
val DetailCacheJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

fun encodeCachedDetail(detail: CachedMatchDetail): String =
    DetailCacheJson.encodeToString(detail)

/** 解不出来返回 null（当这局没缓存处理），绝不抛——导出不能因为一行脏数据整体失败。 */
fun decodeCachedDetail(raw: String?): CachedMatchDetail? {
    if (raw.isNullOrBlank()) return null
    return runCatching { DetailCacheJson.decodeFromString<CachedMatchDetail>(raw) }.getOrNull()
}
