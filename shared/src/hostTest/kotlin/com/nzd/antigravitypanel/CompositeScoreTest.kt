package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.domain.ATTACK_TIER_MIN_COIN
import com.nzd.antigravitypanel.domain.CompositeScoreInput
import com.nzd.antigravitypanel.domain.attackTierPercentOf
import com.nzd.antigravitypanel.domain.compositeScores
import com.nzd.antigravitypanel.ui.format.formatCompositeScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 综合分（详情页「经济转化」那一格的替换口径）。
 *
 * 档位表是手抄的游戏数值，错一个门槛就是某一档金币的人分数全错，
 * 所以边界（含下不含上）必须一条条钉死。
 */
class CompositeScoreTest {

    @Test
    fun tierTableUsesLowerBoundInclusive() {
        // 表里最低一档从 400 起，没到 400 的按第一档兜底（不能返回 0，会除零）
        assertEquals(25, attackTierPercentOf(0))
        assertEquals(25, attackTierPercentOf(ATTACK_TIER_MIN_COIN - 1))
        assertEquals(25, attackTierPercentOf(ATTACK_TIER_MIN_COIN))
        assertEquals(25, attackTierPercentOf(899))
        assertEquals(50, attackTierPercentOf(900))
        assertEquals(75, attackTierPercentOf(1_500))
        assertEquals(100, attackTierPercentOf(2_300))
        assertEquals(125, attackTierPercentOf(3_300))
        assertEquals(150, attackTierPercentOf(4_500))
        assertEquals(175, attackTierPercentOf(6_000))
        assertEquals(200, attackTierPercentOf(7_800))
        assertEquals(225, attackTierPercentOf(9_900))
        assertEquals(250, attackTierPercentOf(12_400))
        assertEquals(275, attackTierPercentOf(15_300))
        assertEquals(300, attackTierPercentOf(18_600))
        assertEquals(325, attackTierPercentOf(22_400))
        assertEquals(350, attackTierPercentOf(26_700))
        assertEquals(375, attackTierPercentOf(31_500))
        assertEquals(400, attackTierPercentOf(36_900))
        assertEquals(425, attackTierPercentOf(42_900))
        assertEquals(450, attackTierPercentOf(49_500))
        assertEquals(475, attackTierPercentOf(56_900))
        assertEquals(500, attackTierPercentOf(65_100))
        assertEquals(550, attackTierPercentOf(74_300))
        assertEquals(600, attackTierPercentOf(84_500))
        assertEquals(650, attackTierPercentOf(95_900))
        assertEquals(700, attackTierPercentOf(108_500))
        assertEquals(750, attackTierPercentOf(123_500))
        assertEquals(800, attackTierPercentOf(143_300))
        assertEquals(1_000, attackTierPercentOf(183_100))
        assertEquals(1_200, attackTierPercentOf(242_900))
        assertEquals(1_500, attackTierPercentOf(322_700))
        // 顶档之上不再往上跳
        assertEquals(1_500, attackTierPercentOf(10_000_000))
    }

    /** 单人时占比 1、效率比 1，综合分就是 1。 */
    @Test
    fun soloPlayerScoresOne() {
        val result = compositeScores(listOf(CompositeScoreInput(bossDamage = 800_000, coin = 10_000)))
        assertEquals(1.0, result[0].score!!, 1e-9)
        assertEquals(800_000L, result[0].teamBossDamage)
    }

    /**
     * 两个人伤害一样多，但金币差一大截：低档位那个人"同样的伤害花得更少"，
     * 综合分必须更高 —— 这正是综合分相对「经济转化」要修的那件事。
     */
    @Test
    fun cheaperPlayerScoresHigher() {
        val (cheap, rich) = compositeScores(
            listOf(
                CompositeScoreInput(bossDamage = 1_000_000, coin = 10_000), // L = 225%
                CompositeScoreInput(bossDamage = 1_000_000, coin = 100_000), // L = 650%
            ),
        )
        // 占比各 0.5；便宜那个人 E 最高，所以 E/maxE = 1
        assertEquals(0.5, cheap.damageShare, 1e-9)
        assertEquals(1_000_000L / 2.25, cheap.efficiency, 1e-6)
        assertEquals(cheap.efficiency, cheap.maxEfficiency, 1e-9)
        assertEquals(1_000_000L / 6.5, rich.efficiency, 1e-6)
        assertEquals(0.707, cheap.score!!, 1e-3)
        assertEquals(0.416, rich.score!!, 1e-3)
    }

    /** 算不出来的就给 null，别摆一个 0 上去（和经济转化一个规矩）。 */
    @Test
    fun uncomputableScoresAreNull() {
        val (noDamage, noCoin) = compositeScores(
            listOf(
                CompositeScoreInput(bossDamage = 0, coin = 50_000),
                CompositeScoreInput(bossDamage = 500_000, coin = 0),
            ),
        )
        assertNull(noDamage.score)
        assertNull(noCoin.score)
        // 全队伤害为 0 时占比不能是 NaN
        assertEquals(0.0, noDamage.damageShare, 1e-9)
    }

    /** 展示：两位小数，算不出来是「—」。 */
    @Test
    fun scoreFormatsToTwoDecimals() {
        assertEquals("0.71", formatCompositeScore(0.7071))
        assertEquals("1.00", formatCompositeScore(1.0))
        assertEquals("0.00", formatCompositeScore(0.0))
        assertEquals("—", formatCompositeScore(null))
    }
}
