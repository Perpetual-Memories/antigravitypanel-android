package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.util.minusCalendarMonths
import com.nzd.antigravitypanel.util.parseServerTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 时间解析与保留期裁剪。
 *
 * 这里的期望值是用 Python 的 datetime 独立算出来的，不是拿实现跑一遍抄的，
 * 否则实现错了测试也会跟着错。
 */
class ServerTimeTest {

    @Test
    fun 解析服务端时间字符串() {
        // 2026-09-04 22:15:16 UTC+8
        assertEquals(1788531316L, parseServerTime("2026-09-04 22:15:16"))
        assertEquals(1767196800L, parseServerTime("2026-01-01 00:00:00"))
    }

    @Test
    fun 解析不出来返回null而不是抛异常() {
        // 未完成对局可能有空字段，一条脏数据不能让整页入库失败
        assertNull(parseServerTime(""))
        assertNull(parseServerTime("2026-09-04"))
        assertNull(parseServerTime("2026-13-04 22:15:16"))
        assertNull(parseServerTime("2026-09-04 25:15:16"))
        assertNull(parseServerTime("abc"))
    }

    @Test
    fun 往前推自然月() {
        val now = 1788531316L // 2026-09-04 22:15:16 UTC+8
        assertEquals(1785852916L, minusCalendarMonths(now, 1)) // 2026-08-04
        assertEquals(1772633716L, minusCalendarMonths(now, 6)) // 2026-03-04
        assertEquals(1756995316L, minusCalendarMonths(now, 12)) // 2025-09-04
    }

    @Test
    fun 保留期为0或不合法时表示永久保留() {
        val now = 1788531316L
        assertEquals(now, minusCalendarMonths(now, 0))
        assertEquals(now, minusCalendarMonths(now, -1))
    }

    @Test
    fun 跨年往前推() {
        // 2026-01-15 → 往前 1 个月是 2025-12-15，年份要退回去
        val jan = parseServerTime("2026-01-15 08:00:00")!!
        val dec = parseServerTime("2025-12-15 08:00:00")!!
        assertEquals(dec, minusCalendarMonths(jan, 1))
    }

    @Test
    fun 月末往前推时按目标月的天数收窄() {
        // 3-31 往前一个月是 2 月，2026 不是闰年，只能收到 2-28
        val mar31 = parseServerTime("2026-03-31 12:00:00")!!
        val feb28 = parseServerTime("2026-02-28 12:00:00")!!
        assertEquals(feb28, minusCalendarMonths(mar31, 1))
    }
}
