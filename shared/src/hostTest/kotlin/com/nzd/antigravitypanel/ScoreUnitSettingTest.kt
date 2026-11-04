package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.settings.ScoreUnitTarget
import com.nzd.antigravitypanel.ui.format.formatScoreWithUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 「积分显示单位」的两个纯逻辑：存盘往返 + 显示换算。
 *
 * 最要紧的是 [ScoreUnitTarget.decode] 里那条分叉：**键不存在 ≠ 空串**。
 * 混在一起的话，用户把三个勾全取消（想全看完整数字）之后会立刻被默认值改回去，
 * 表现为"这个设置关不掉"，而代码看起来一切正常。
 */
class ScoreUnitSettingTest {

    @Test
    fun 默认只有历史与概览用万() {
        assertEquals(
            setOf(ScoreUnitTarget.HISTORY, ScoreUnitTarget.OVERVIEW_RECENT),
            ScoreUnitTarget.DEFAULT,
        )
        // 详情页默认是完整数字：用户点进去多半是为了核对某一局，4.5万 会丢信息
        assertTrue(ScoreUnitTarget.MATCH_DETAIL !in ScoreUnitTarget.DEFAULT)
    }

    @Test
    fun 存盘往返() {
        val targets = setOf(ScoreUnitTarget.MATCH_DETAIL, ScoreUnitTarget.HISTORY)
        assertEquals(targets, ScoreUnitTarget.decode(ScoreUnitTarget.encode(targets)))
        // 顺序固定按枚举来，不是按集合插入顺序 —— 否则存出来的字符串每次都不一样
        assertEquals("MATCH_DETAIL,HISTORY", ScoreUnitTarget.encode(targets))
    }

    @Test
    fun 空集合表示全部显示完整数字() {
        assertEquals(emptySet(), ScoreUnitTarget.decode(""))
        assertEquals("", ScoreUnitTarget.encode(emptySet()))
    }

    @Test
    fun 从没设过才用默认值() {
        assertEquals(ScoreUnitTarget.DEFAULT, ScoreUnitTarget.decode(null))
        // 认不出来的名字（改过枚举 / 老数据）直接丢掉，不能整个炸掉
        assertEquals(setOf(ScoreUnitTarget.HISTORY), ScoreUnitTarget.decode("HISTORY,GHOST"))
    }

    @Test
    fun 完整数字带千分位而以万为单位会折起来() {
        assertEquals("45,678", formatScoreWithUnit(45_678, useWan = false))
        assertEquals("4.5万", formatScoreWithUnit(45_678, useWan = true))
        // 不到一万时两种写法一致：不该出现"0.8万"
        assertEquals("6789", formatScoreWithUnit(6_789, useWan = true))
    }
}
