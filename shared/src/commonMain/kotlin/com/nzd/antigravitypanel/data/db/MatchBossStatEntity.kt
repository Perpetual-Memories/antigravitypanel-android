package com.nzd.antigravitypanel.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一局对局的 Boss 伤害口径缓存。
 *
 * 存在的理由：历史页那两个标签（实力局 / 带飞局）要看**全队**的 Boss 伤害，
 * 而这个数只存在于逐局详情接口 `center.game.detail`（列表接口一个字都没有），
 * 历史页又不可能为了每一行都现打一次接口。所以拉到一次就落库，之后标签读本地。
 *
 * 三个数的分工：
 * - [selfBossDamage] 自己打的，算占比的分子
 * - [teamBossDamage] 全队合计（**含自己**），算占比的分母
 * - [rivalBossDamage] **除自己以外**打得最多的那个，算"是不是第二名的两倍"
 *
 * ⚠️ **一行存在就等于"这局试过了"**：拉失败、或者这局压根没有 Boss 口径
 * （塔防 / 时空追猎）时写一条全 0 的行。补拉逻辑靠"有没有这一行"去重，
 * 不这么做的话每次进历史页都会把拉不动的老对局重试一遍。
 */
@Entity(tableName = "match_boss_stats")
data class MatchBossStatEntity(
    @PrimaryKey
    @ColumnInfo(name = "roomId")
    val roomId: String,
    @ColumnInfo(name = "selfBossDamage")
    val selfBossDamage: Long = 0,
    @ColumnInfo(name = "teamBossDamage")
    val teamBossDamage: Long = 0,
    @ColumnInfo(name = "rivalBossDamage")
    val rivalBossDamage: Long = 0,
    @ColumnInfo(name = "updatedAtSec")
    val updatedAtSec: Long = 0,
    /**
     * 这一局详情里**其余**能拿到的东西，序列化成 JSON 存（见 [CachedMatchDetail]）。
     *
     * 金币 / 小怪伤害 / 分区用时 / 配装这些和上面三个数同源，但只有导出会用到，
     * 给它们各开一列意味着每加一个数就要写一次迁移 —— 整块存是一种妥协：
     * 字段只增不删、全部带默认值，老行照样能解出来。
     *
     * null = 这份详情是老版本写的（升级前拉到的），或者当时就没存成功。
     */
    @ColumnInfo(name = "payloadJson")
    val payloadJson: String? = null,
)
