package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.build.BUILD_PERKS
import com.nzd.antigravitypanel.data.build.BUILD_WEAPONS
import com.nzd.antigravitypanel.data.build.BuildPlan
import com.nzd.antigravitypanel.data.build.BuildPlanCodec
import com.nzd.antigravitypanel.data.build.PERK_SLOT_COUNT
import com.nzd.antigravitypanel.data.build.PlannedWeapon
import com.nzd.antigravitypanel.data.build.neededPerks
import com.nzd.antigravitypanel.data.build.PERK_RARITY_LEGENDARY
import com.nzd.antigravitypanel.data.build.perksForSlot
import com.nzd.antigravitypanel.data.build.pickablePerksForSlot
import com.nzd.antigravitypanel.data.build.weaponsByProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Build 计划的统计口径。
 *
 * 这里守的是两块最容易被改坏的东西：
 * - 顶部总览的"还差几个"必须**跨武器累加、并扣掉已获得的**，否则用户照着它刷会刷多；
 * - 概览页那三把必须**最接近完成的排前面**，否则"我现在该刷哪个"就答错了。
 */
class BuildPlanTest {

    private fun weapon(
        name: String,
        perks: List<String>,
        obtained: Set<Int> = emptySet(),
    ) = PlannedWeapon(weaponName = name, perks = perks, obtained = obtained)

    @Test
    fun 同一个插件跨武器累加() {
        val needs = neededPerks(
            listOf(
                weapon("梦魇", listOf("超频", "", "", "")),
                weapon("死亡猎手", listOf("", "超频", "", "")),
            ),
        )
        assertEquals(1, needs.size)
        assertEquals("超频", needs[0].name)
        assertEquals(2, needs[0].count)
    }

    @Test
    fun 标记获得后总数减少() {
        val needs = neededPerks(
            listOf(
                weapon("梦魇", listOf("超频", "", "", ""), obtained = setOf(0)),
                weapon("死亡猎手", listOf("", "超频", "", "")),
            ),
        )
        assertEquals(1, needs.size)
        assertEquals(1, needs[0].count)
    }

    @Test
    fun 空格子不算需求() {
        // 「待填入」还没决定装什么，算进来会让总览凭空多出几条
        assertTrue(neededPerks(listOf(weapon("梦魇", listOf("", "", "", "")))).isEmpty())
        val needs = neededPerks(listOf(weapon("梦魇", listOf("超频", "", "", ""))))
        assertEquals(listOf("超频"), needs.map { it.name })
    }

    @Test
    fun 需求按数量降序() {
        val needs = neededPerks(
            listOf(
                weapon("甲", listOf("弹匣扩容", "", "", "")),
                weapon("乙", listOf("超频", "", "", "")),
                weapon("丙", listOf("超频", "", "", "")),
            ),
        )
        assertEquals(listOf("超频", "弹匣扩容"), needs.map { it.name })
        assertEquals(listOf(2, 1), needs.map { it.count })
    }

    @Test
    fun 最接近完成的排最前() {
        val done = weapon("梦魇", listOf("超频", "超载", "", ""), obtained = setOf(0, 1))
        val half = weapon("死亡猎手", listOf("超频", "超载", "", ""), obtained = setOf(0))
        val none = weapon("火神炎帝", listOf("超频", "超载", "穿甲", "爆伤"))
        assertEquals(listOf("梦魇", "死亡猎手", "火神炎帝"), weaponsByProgress(listOf(none, half, done)).map { it.weaponName })
    }

    @Test
    fun 进度相同时顺序稳定() {
        // 三把都是"填了俩、拿到零个"：不该每次重组成都换位置，
        // 所以最后按名字兜底 —— 而且是**按 Unicode 码点**排，不是「甲乙丙」的笔画顺序：
        // 丙 U+4E19 < 乙 U+4E59 < 甲 U+7532
        val list = listOf(
            weapon("丙", listOf("超频", "超载", "", "")),
            weapon("甲", listOf("超频", "超载", "", "")),
            weapon("乙", listOf("超频", "超载", "", "")),
        )
        val expected = listOf("丙", "乙", "甲")
        assertEquals(expected, weaponsByProgress(listOf(list[2], list[0], list[1])).map { it.weaponName })
        // 换个输入顺序，结果必须一样
        assertEquals(expected, weaponsByProgress(listOf(list[1], list[2], list[0])).map { it.weaponName })
    }

    @Test
    fun 老数据槽位不足会补齐() {
        val fixed = weapon("梦魇", listOf("超频"), obtained = setOf(0, 1, 2)).sanitized()
        assertEquals(PERK_SLOT_COUNT, fixed.perks.size)
        // 1、2 号槽是空的，"拿到了"的标记要跟着丢，不然会算出一个不存在的进度
        assertEquals(setOf(0), fixed.obtained)
        assertEquals(0, fixed.remaining)
    }

    @Test
    fun 槽位被换成空时清掉已获得() {
        val fixed = weapon("梦魇", listOf("超频", ""), obtained = setOf(0, 1)).sanitized()
        assertEquals(setOf(0), fixed.obtained)
    }

    @Test
    fun 编解码往返一致() {
        val plan = BuildPlan(
            listOf(
                weapon("梦魇", listOf("超频", "超载", "", ""), obtained = setOf(1)),
                weapon("死亡猎手", listOf("", "", "", "")),
            ),
        )
        assertEquals(plan, BuildPlanCodec.decode(BuildPlanCodec.encode(plan)))
    }

    @Test
    fun 坏掉的JSON当没有计划() {
        assertNull(BuildPlanCodec.decode(null))
        assertNull(BuildPlanCodec.decode(""))
        assertNull(BuildPlanCodec.decode("{ 这不是 json"))
    }

    @Test
    fun 老版本只有两个槽位也能读() {
        val raw = """{"weapons":[{"weaponName":"梦魇","perks":["超频","超载"],"obtained":[0]}]}"""
        val plan = BuildPlanCodec.decode(raw)
        val weapon = plan?.weapons?.singleOrNull()
        assertEquals(PERK_SLOT_COUNT, weapon?.perks?.size)
        assertEquals("超频", weapon?.perks?.get(0))
        assertEquals("", weapon?.perks?.get(3))
    }

    @Test
    fun 选插件只列本槽位的() {
        // 目录是真实的 wiki 数据：一号槽 103 个，不会出现别的槽位的插件
        val first = perksForSlot(1)
        assertTrue(first.isNotEmpty())
        assertTrue(first.all { it.slot == 1 })
        assertTrue(perksForSlot(4).all { it.slot == 4 })
        // 每个槽位加起来应当覆盖整张目录
        assertEquals(BUILD_PERKS.size, (1..PERK_SLOT_COUNT).sumOf { perksForSlot(it).size })
    }

    @Test
    fun 槽位号不合法返回空() {
        assertTrue(perksForSlot(0).isEmpty())
        assertTrue(perksForSlot(99).isEmpty())
    }

    @Test
    fun 候选插件只留传说() {
        // 界面上"为方便快速选择"把稀有 / 史诗都剔掉了，只剩传说。
        // wiki 的稀有度是 1 稀有 / 2 史诗 / 3 传说。
        val candidates = (1..PERK_SLOT_COUNT).flatMap { pickablePerksForSlot(it) }
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { it.rarity >= PERK_RARITY_LEGENDARY })
        // 确实是"剔掉了一批"而不是"原样返回"：目录里本来有非传说的
        assertTrue(candidates.size < BUILD_PERKS.size)
        // 但每个槽位都得还有东西可选，不能某个槽位被筛空
        (1..PERK_SLOT_COUNT).forEach { slot ->
            assertTrue(pickablePerksForSlot(slot).isNotEmpty(), "槽位 $slot 被筛空了")
        }
    }

    @Test
    fun 目录里没有重复项() {
        // 这两条是**崩溃回归**：列表的 key 用的是「武器名」「插件名#槽位」，
        // 撞车时 LazyColumn 会直接抛 IllegalArgumentException 把整个 app 带崩。
        // 线上就出过一次：wiki 的 slot-3 里「伤害属性」有 4 个同名不同 id 的文件，
        // 生成目录时没去重，key 变成 4 个「伤害属性#3」。
        // 去重是 `tools/gen_build_data.py` 的职责，这里守的是"别再退回去"。
        assertEquals(BUILD_WEAPONS.size, BUILD_WEAPONS.distinctBy { it.name }.size)
        assertEquals(BUILD_PERKS.size, BUILD_PERKS.distinctBy { it.name to it.slot }.size)
    }
}
