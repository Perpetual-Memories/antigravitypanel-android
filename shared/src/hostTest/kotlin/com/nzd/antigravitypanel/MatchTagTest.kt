package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.remote.dto.GameDetailDto
import com.nzd.antigravitypanel.data.remote.dto.GameRecordDto
import com.nzd.antigravitypanel.data.remote.dto.HuntingDetailsDto
import com.nzd.antigravitypanel.data.remote.dto.PlayerDetailDto
import com.nzd.antigravitypanel.data.repo.bossStatOf
import com.nzd.antigravitypanel.domain.MatchTag
import com.nzd.antigravitypanel.domain.matchTagOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 「实力局 / 带飞局」两个标签。
 *
 * 规则是用户定的（占比 40% / 两倍于第二名 / 两条都中取带飞），没有第二个可对照的
 * 实现，只能靠测试把边界钉住 —— 尤其"第二名是 0"这条，漏了它单刷局会满屏带飞。
 */
class MatchTagTest {

    /**
     * 自己 100 / 全队 200：占 50%（够实力局）。
     * 第二名 60 时不到两倍 -> 实力局；第二名 50 时正好两倍 -> 带飞局（优先）。
     */
    @Test
    fun carryWinsWhenBothHit() {
        assertEquals(MatchTag.STRONG, matchTagOf(100, 200, 60))
        assertEquals(MatchTag.CARRY, matchTagOf(100, 200, 50))
    }

    /** 40% 是"含"：39% 不给，正好 40% 给。 */
    @Test
    fun strongNeedsFortyPercent() {
        assertNull(matchTagOf(39, 100, 30))
        assertEquals(MatchTag.STRONG, matchTagOf(40, 100, 30))
    }

    /** 两倍也是"含"：99 不到 50 的两倍（且占比不够）-> 没标签；100 正好 -> 带飞。 */
    @Test
    fun carryNeedsDoubleOfRunnerUp() {
        assertNull(matchTagOf(99, 500, 50))
        assertEquals(MatchTag.CARRY, matchTagOf(100, 150, 50))
    }

    /** 第二名不存在（单刷 / 队友全 0）时不能算带飞，否则 `自己 >= 0 × 2` 恒真。 */
    @Test
    fun noRivalMeansNoCarry() {
        // 占比只有 10%，没有第二名 -> 什么都没有
        assertNull(matchTagOf(10, 100, 0))
        // 单刷且占比 100% -> 只能算实力局，不能算带飞
        assertEquals(MatchTag.STRONG, matchTagOf(100, 100, 0))
    }

    @Test
    fun zeroDamageOrZeroTeamHasNoTag() {
        assertNull(matchTagOf(0, 100, 50))
        assertNull(matchTagOf(100, 0, 0))
    }

    @Test
    fun bossStatSeparatesSelfFromRivals() {
        val detail = GameDetailDto(
            loginUserDetail = player("me", 500),
            list = listOf(player("me", 500), player("a", 300), player("b", 120)),
        )
        val stat = bossStatOf("r1", detail, 0)!!
        assertEquals(500L, stat.selfBossDamage)
        assertEquals(920L, stat.teamBossDamage)
        // 第二名是**除自己以外**最高的那个（300），不是全队第二高那个（120）
        assertEquals(300L, stat.rivalBossDamage)
        assertEquals(
            MatchTag.STRONG,
            matchTagOf(stat.selfBossDamage, stat.teamBossDamage, stat.rivalBossDamage),
        )
    }

    /** openId 是空串时按对象身份排除自己，否则所有人都被当成自己、第二名永远是 0。 */
    @Test
    fun bossStatFallsBackToIdentityWhenOpenIdBlank() {
        val self = PlayerDetailDto(
            baseDetail = GameRecordDto(),
            huntingDetails = HuntingDetailsDto(damageTotalOnBoss = 100),
        )
        val other = PlayerDetailDto(
            baseDetail = GameRecordDto(),
            huntingDetails = HuntingDetailsDto(damageTotalOnBoss = 40),
        )
        val stat = bossStatOf("r1", GameDetailDto(loginUserDetail = self, list = listOf(self, other)), 0)
        assertEquals(40L, stat?.rivalBossDamage)
    }

    /** 详情里没有"自己"时返回 null：那份详情不完整，不该被记成"这局没有 Boss 数据"。 */
    @Test
    fun bossStatRequiresLoginUser() {
        assertNull(bossStatOf("r1", GameDetailDto(list = listOf(player("a", 10))), 0))
        assertNull(bossStatOf("r1", GameDetailDto(), 0))
    }

    private fun player(openId: String, boss: Long): PlayerDetailDto = PlayerDetailDto(
        baseDetail = GameRecordDto(vOpenID = openId),
        huntingDetails = HuntingDetailsDto(damageTotalOnBoss = boss),
    )
}
