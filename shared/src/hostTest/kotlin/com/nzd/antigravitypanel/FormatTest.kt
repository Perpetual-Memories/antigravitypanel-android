package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.ui.format.formatCoinPerMinute
import com.nzd.antigravitypanel.ui.format.formatCompact
import com.nzd.antigravitypanel.ui.format.formatCountdownClock
import com.nzd.antigravitypanel.ui.format.formatDamagePerWanCoin
import com.nzd.antigravitypanel.ui.format.formatDateTimeShort
import com.nzd.antigravitypanel.ui.format.formatDuration
import com.nzd.antigravitypanel.ui.format.formatPercent
import com.nzd.antigravitypanel.ui.format.formatScore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 展示层格式化。
 *
 * 这些字符串铺在列表每一行上，一旦写错就是满屏可见，而且改起来要在真机上一屏屏看，
 * 所以全部收成纯函数在这里钉死。
 */
class FormatTest {

    @Test
    fun durationUsesMinutesAndSeconds() {
        assertEquals("—", formatDuration(0))
        assertEquals("45秒", formatDuration(45))
        assertEquals("12分34秒", formatDuration(754))
        assertEquals("1分05秒", formatDuration(65))
    }

    @Test
    fun dateTimeDropsYearAndSeconds() {
        assertEquals("09-04 22:15", formatDateTimeShort("2026-09-04 22:15:16"))
        // 脏数据（时间串被截断）原样返回，不要抛异常
        assertEquals("2026-0", formatDateTimeShort("2026-0"))
    }

    @Test
    fun scoreAddsThousandSeparators() {
        assertEquals("0", formatScore(0))
        assertEquals("123", formatScore(123))
        assertEquals("1,234", formatScore(1234))
        assertEquals("12,345,678", formatScore(12345678))
        assertEquals("-1,234", formatScore(-1234))
    }

    @Test
    fun compactSwitchesToWanAndYi() {
        assertEquals("9999", formatCompact(9999))
        assertEquals("1万", formatCompact(10_000))
        assertEquals("1.2万", formatCompact(12_345))
        assertEquals("1亿", formatCompact(100_000_000))
        assertEquals("1.5亿", formatCompact(150_000_000))
    }

    /** 分均经济 = 金币（万）÷ 分钟。**不带单位**，「万」写在 UI 的标签里。 */
    @Test
    fun coinPerMinuteDividesWanByMinutes() {
        // 30 万金币 / 20 分钟 = 每分钟 1.5 万
        assertEquals("1.5", formatCoinPerMinute(300_000, 1200))
        // 除不尽时截断到一位（和 formatCompact 一个口径）
        assertEquals("1.4", formatCoinPerMinute(300_000, 1260))
        // 金币为 0（塔防这类没金币口径的模式）或时长为 0 都算不出来
        assertEquals("—", formatCoinPerMinute(0, 1200))
        assertEquals("—", formatCoinPerMinute(300_000, 0))
    }

    /** 经济转化 = Boss 伤害（万）÷ 金币（万）：两边的万约掉了，是个比值，不带单位。 */
    @Test
    fun damagePerWanCoinIsARatioOfTwoWanNumbers() {
        // 用户的原话：Boss 伤害 443.7 万 ÷ 金币 31.2 万 ≈ 14
        assertEquals("14.2", formatDamagePerWanCoin(4_437_000, 312_000))
        assertEquals("0.5", formatDamagePerWanCoin(500_000, 1_000_000))
        assertEquals("—", formatDamagePerWanCoin(0, 300_000))
        assertEquals("—", formatDamagePerWanCoin(5_000_000, 0))
    }

    @Test
    fun percentHandlesZeroTotal() {
        assertEquals("—", formatPercent(0, 0))
        assertEquals("50%", formatPercent(1, 2))
        assertEquals("33%", formatPercent(1, 3))
        assertEquals("100%", formatPercent(4, 4))
    }

    @Test
    fun countdownClockPadsEveryField() {
        assertEquals("00:00:00", formatCountdownClock(0))
        assertEquals("00:00:09", formatCountdownClock(9))
        assertEquals("00:01:01", formatCountdownClock(61))
        assertEquals("06:44:44", formatCountdownClock(24_284)) // 6 小时 44 分 44 秒
    }

    @Test
    fun countdownClockDoesNotRollHoursIntoDays() {
        // 倒计时到"明天刷新"最远就是 24 小时整，超过也照实写成 25:01:01，
        // 不绕成"1天1小时" —— 那样这行的宽度会随剩余时间变化
        assertEquals("24:00:00", formatCountdownClock(86_400))
        assertEquals("25:01:01", formatCountdownClock(90_061))
    }

    @Test
    fun countdownClockTreatsNegativeAsZero() {
        // 已经过了刷新点但数据还没刷回来那一瞬间，别显示 -00:00:01
        assertEquals("00:00:00", formatCountdownClock(-1))
    }
}
