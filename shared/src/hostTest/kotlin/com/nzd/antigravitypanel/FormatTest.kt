package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.ui.format.formatCompact
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

    @Test
    fun percentHandlesZeroTotal() {
        assertEquals("—", formatPercent(0, 0))
        assertEquals("50%", formatPercent(1, 2))
        assertEquals("33%", formatPercent(1, 3))
        assertEquals("100%", formatPercent(4, 4))
    }
}
