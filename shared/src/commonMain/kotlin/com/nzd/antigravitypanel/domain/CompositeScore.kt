package com.nzd.antigravitypanel.domain

import kotlin.math.sqrt

/**
 * 综合分：把「打出去多少伤害」和「这些伤害花得值不值」压成一个 0~1 的数。
 *
 * 详情页原来那格「经济转化」= Boss 伤害 ÷ 金币，毛病很直白：金币攒得越多、
 * 攻击力档位越高的人，同样一万金币本来就能换到更多伤害，于是"省着花的人"
 * 反而分数低。综合分先把伤害按档位还原成"同档位下能打多少"，再乘上他在
 * 全队里的伤害占比，最后开方 —— 取值落在 0~1。
 *
 * 下标 i 指某一个玩家，j 遍历本局全部玩家：
 *
 * - L(G)：金币 → 攻击力档位，查表（[ATTACK_TIERS]）
 * - E_i = B_i ÷ L_i：档位修正效率分
 * - P_i = B_i ÷ Σ B_j：伤害占比
 * - S_i = √(P_i × E_i ÷ max E_j)：综合分
 *
 * 全是纯函数：这个数铺在每一张玩家卡上，写错一次就是满屏错，必须能单测。
 */

/** 一个人的输入：Boss 伤害 B 与金币 G（都是服务端 huntingDetails 里的原值）。 */
data class CompositeScoreInput(
    val bossDamage: Long,
    val coin: Long,
)

/**
 * 综合分的完整算式。弹「这个数怎么来的」那个对话框要的就是这些中间值 ——
 * 只给一个结果数字，用户没法判断它算得对不对。
 *
 * @param tierPercent 攻击力档位 L，百分数（200 表示 200%）。
 * @param efficiency E = B ÷ L，L 这里取**倍数**（200% 记作 2.0）。
 *   用倍数还是百分数不影响 S：E_i ÷ max E_j 是比值，L 整体放大多少倍都会被约掉。
 * @param score 综合分 S，0~1。null = 算不出来。
 */
data class CompositeScoreBreakdown(
    val bossDamage: Long,
    val coin: Long,
    /** 全队 Boss 伤害合计，也就是算占比的那个分母。 */
    val teamBossDamage: Long,
    val tierPercent: Int,
    val efficiency: Double,
    /** 全队最高的 E，S 的分母。 */
    val maxEfficiency: Double,
    /** P = B ÷ ΣB，0~1。 */
    val damageShare: Double,
    val score: Double?,
)

/**
 * 金币 → 攻击力档位的对照表。**升序**，[AttackTier.minCoin] 是这一档的门槛（含）。
 *
 * 这份表是游戏里的数值，服务端不提供，只能客户端自带（凡是"只有官方才有的名字表 /
 * 数值表"都自己存一份，难度名、区域名、难度号段已经踩过三次）。
 * 改档位只改这里，别在别处再抄一份。
 */
private class AttackTier(val minCoin: Long, val percent: Int)

/** 表里最低一档的门槛。金币不到这个数就按第一档算，UI 要就这个边界给一句说明。 */
const val ATTACK_TIER_MIN_COIN = 400L

private val ATTACK_TIERS = listOf(
    AttackTier(ATTACK_TIER_MIN_COIN, 25),
    AttackTier(900L, 50),
    AttackTier(1_500L, 75),
    AttackTier(2_300L, 100),
    AttackTier(3_300L, 125),
    AttackTier(4_500L, 150),
    AttackTier(6_000L, 175),
    AttackTier(7_800L, 200),
    AttackTier(9_900L, 225),
    AttackTier(12_400L, 250),
    AttackTier(15_300L, 275),
    AttackTier(18_600L, 300),
    AttackTier(22_400L, 325),
    AttackTier(26_700L, 350),
    AttackTier(31_500L, 375),
    AttackTier(36_900L, 400),
    AttackTier(42_900L, 425),
    AttackTier(49_500L, 450),
    AttackTier(56_900L, 475),
    AttackTier(65_100L, 500),
    AttackTier(74_300L, 550),
    AttackTier(84_500L, 600),
    AttackTier(95_900L, 650),
    AttackTier(108_500L, 700),
    AttackTier(123_500L, 750),
    AttackTier(143_300L, 800),
    AttackTier(183_100L, 1_000),
    AttackTier(242_900L, 1_200),
    AttackTier(322_700L, 1_500),
)

/**
 * 查档位：从高往低找第一档"够得着"的。
 *
 * 一档都够不着（G < 400，表里最低门槛）时**按第一档 25% 兜底**，不是返回 0 ——
 * 返回 0 会让 E 变成除零，那一格就永远是个「—」。
 */
fun attackTierPercentOf(coin: Long): Int {
    for (index in ATTACK_TIERS.lastIndex downTo 0) {
        if (coin >= ATTACK_TIERS[index].minCoin) return ATTACK_TIERS[index].percent
    }
    return ATTACK_TIERS.first().percent
}

/**
 * 一局里所有人的综合分。返回顺序与 [inputs] 一致，方便按下标取回。
 *
 * 必须**整局一起算**：占比的分母是全队伤害，效率的分母是全队最高效率，
 * 两者都只有拿到所有人之后才知道，一个人一个人的算算不出来。
 */
fun compositeScores(inputs: List<CompositeScoreInput>): List<CompositeScoreBreakdown> {
    val teamBossDamage = inputs.sumOf { it.bossDamage.coerceAtLeast(0L) }
    val efficiencies = inputs.map(::efficiencyOf)
    val maxEfficiency = efficiencies.maxOrNull() ?: 0.0
    return inputs.mapIndexed { index, input ->
        val boss = input.bossDamage.coerceAtLeast(0L)
        val damageShare = if (teamBossDamage > 0L) {
            boss.toDouble() / teamBossDamage.toDouble()
        } else {
            0.0
        }
        val score = when {
            // 和经济转化一个口径：Boss 伤害或金币没有就别摆一个 0 上去
            input.bossDamage <= 0L || input.coin <= 0L -> null
            teamBossDamage <= 0L || maxEfficiency <= 0.0 -> null
            else -> sqrt((damageShare * (efficiencies[index] / maxEfficiency)).coerceAtLeast(0.0))
        }
        CompositeScoreBreakdown(
            bossDamage = input.bossDamage,
            coin = input.coin,
            teamBossDamage = teamBossDamage,
            tierPercent = attackTierPercentOf(input.coin),
            efficiency = efficiencies[index],
            maxEfficiency = maxEfficiency,
            damageShare = damageShare,
            score = score,
        )
    }
}

/** E = B ÷ L。L 用倍数（25% → 0.25）。 */
private fun efficiencyOf(input: CompositeScoreInput): Double {
    val percent = attackTierPercentOf(input.coin)
    if (percent <= 0) return 0.0
    return input.bossDamage.coerceAtLeast(0L).toDouble() / (percent / 100.0)
}
