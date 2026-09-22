package com.nzd.antigravitypanel.util

/**
 * 服务端时间字符串 ↔ epoch 秒。
 *
 * 接口给的时间形如 `2026-09-04 22:15:16`，不带时区标记。游戏服务端按北京时间出数，
 * 所以统一按 UTC+8 解释。这里没有引入 kotlinx-datetime：我们只需要「解析 + 按月裁剪」
 * 两件事，用十几行纯整数运算就够，而且结果可单测、不依赖任何平台时钟。
 *
 * 所有需要「当前时间」的函数都把 `nowSec` 作为参数传入，不在内部读时钟，
 * 这样同步逻辑能被确定性地测试。
 */
private const val SECONDS_PER_DAY = 24L * 3600L

/** 服务端所在时区相对 UTC 的偏移（北京时间）。 */
internal const val SERVER_UTC_OFFSET_SECONDS = 8L * 3600L

/**
 * 解析 `yyyy-MM-dd HH:mm:ss`。解析不出来返回 null，绝不抛异常——
 * 列表里的未完成对局可能有空字段，统计时不能因为一条脏数据整页失败。
 */
fun parseServerTime(text: String): Long? {
    if (text.length < 19) return null
    val year = text.substring(0, 4).toIntOrNull() ?: return null
    val month = text.substring(5, 7).toIntOrNull() ?: return null
    val day = text.substring(8, 10).toIntOrNull() ?: return null
    val hour = text.substring(11, 13).toIntOrNull() ?: return null
    val minute = text.substring(14, 16).toIntOrNull() ?: return null
    val second = text.substring(17, 19).toIntOrNull() ?: return null
    if (text[4] != '-' || text[7] != '-' || text[10] != ' ') return null
    if (text[13] != ':' || text[16] != ':') return null
    if (month !in 1..12 || day !in 1..31) return null
    if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return null

    val days = daysFromCivil(year, month, day)
    val timeOfDay = hour * 3600L + minute * 60L + second
    return days * SECONDS_PER_DAY + timeOfDay - SERVER_UTC_OFFSET_SECONDS
}

/**
 * 往前推 [months] 个自然月，返回对应的 epoch 秒。
 *
 * 走日历而不是「按月 = 30 天」：跨年、跨闰月时 30 天近似会漂几天，
 * 保留期裁剪差几天就会出现"昨天还在、今天没了"的困惑。
 * [months] <= 0 时原样返回。
 */
fun minusCalendarMonths(epochSec: Long, months: Int): Long {
    if (months <= 0) return epochSec

    // 先挪到服务端本地时钟空间再做日历运算，保证"月份"是按北京时间算的
    val local = epochSec + SERVER_UTC_OFFSET_SECONDS
    val days = Math.floorDiv(local, SECONDS_PER_DAY)
    val timeOfDay = local - days * SECONDS_PER_DAY

    val (year, month, day) = civilFromDays(days)
    var targetYear = year
    var targetMonth = month - months
    while (targetMonth <= 0) {
        targetMonth += 12
        targetYear -= 1
    }
    val targetDay = minOf(day, daysInMonth(targetYear, targetMonth))

    return daysFromCivil(targetYear, targetMonth, targetDay) * SECONDS_PER_DAY +
        timeOfDay - SERVER_UTC_OFFSET_SECONDS
}

/**
 * 服务端所在时区的当天零点（epoch 秒）。
 *
 * 「今天」必须按北京时间切，不然东八区用户在 UTC 时间跨天后看到的"今天"会错位 8 小时。
 */
fun startOfServerDay(epochSec: Long): Long {
    val local = epochSec + SERVER_UTC_OFFSET_SECONDS
    val days = Math.floorDiv(local, SECONDS_PER_DAY)
    return days * SECONDS_PER_DAY - SERVER_UTC_OFFSET_SECONDS
}

/**
 * 服务端所在时区的日期键，形如 `20260921`。
 *
 * 用来判断"今天"这种按天去重的事（比如自动签到一天只试一次）。必须按北京时间切，
 * 不能拿 UTC 日期或者设备本地时区——改了系统时区就会差一天。
 */
fun serverDateKey(epochSec: Long): String {
    val local = epochSec + SERVER_UTC_OFFSET_SECONDS
    val (year, month, day) = civilFromDays(Math.floorDiv(local, SECONDS_PER_DAY))
    return buildString {
        append(year)
        append(month.toString().padStart(2, '0'))
        append(day.toString().padStart(2, '0'))
    }
}

/** Howard Hinnant 的 days_from_civil：把年月日转成 1970-01-01 起的天数。 */
internal fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = Math.floorDiv(y, 400)
    val yearOfEra = y - era * 400 // [0, 399]
    val dayOfYear = (153 * (month + if (month > 2) -3 else 9) + 2) / 5 + day - 1 // [0, 365]
    val dayOfEra =
        yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear // [0, 146096]
    return era * 146097 + dayOfEra - 719468
}

/** days_from_civil 的逆运算。返回 Triple(year, month, day)。 */
internal fun civilFromDays(days: Long): Triple<Int, Int, Int> {
    val z = days + 719468
    val era = Math.floorDiv(z, 146097)
    val dayOfEra = z - era * 146097 // [0, 146096]
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365 // [0, 399]
    val y = yearOfEra + era * 400
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100) // [0, 365]
    val mp = (5 * dayOfYear + 2) / 153 // [0, 11]
    val day = (dayOfYear - (153 * mp + 2) / 5 + 1).toInt() // [1, 31]
    val month = (mp + if (mp < 10) 3 else -9).toInt() // [1, 12]
    return Triple((y + if (month <= 2) 1 else 0).toInt(), month, day)
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
