package com.nzd.antigravitypanel.ui.format

/**
 * 展示用的数字 / 时间格式化。
 *
 * 全部是纯函数：这些字符串会出现在列表每一行里，写错一次就会满屏难看，
 * 所以宁可抽出来单测，也不要在 Composable 里现拼。
 */

/** `iDuration`（秒）转成 `12分34秒`。超过一小时也照实写，不换成小时——对局时长没那么长。 */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "—"
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes <= 0) "${rest}秒" else "${minutes}分${rest.toString().padStart(2, '0')}秒"
}

/**
 * `2026-09-04 22:15:16` -> `09-04 22:15`。
 *
 * 年份在战绩列表里是噪音：本地库可能存着半年前的对局，但用户扫列表时只想看"哪天几点"。
 */
fun formatDateTimeShort(eventTime: String): String {
    if (eventTime.length < 16) return eventTime
    return eventTime.substring(5, 16)
}

/** `2026-09-04 22:15:16` -> `09-04`。详情页「数据总览」日期那行只要月日。 */
fun formatDateShort(eventTime: String): String =
    if (eventTime.length >= 10) eventTime.substring(5, 10) else eventTime

/** `2026-09-04 22:15:16` -> `22:15`。 */
fun formatTimeShort(eventTime: String): String =
    if (eventTime.length >= 16) eventTime.substring(11, 16) else eventTime

/** 大数字加千分位。伤害、积分这类数字不分位根本读不出来。 */
fun formatScore(value: Long): String {
    if (value == 0L) return "0"
    val sign = if (value < 0) "-" else ""
    var digits = if (value < 0) (0 - value).toString() else value.toString()
    val out = StringBuilder()
    while (digits.length > 3) {
        val cut = digits.length - 3
        out.insert(0, ",${digits.substring(cut)}")
        digits = digits.substring(0, cut)
    }
    return sign + digits + out
}

/**
 * 伤害这种动辄上亿的量级，列宽有限时用 `1.2亿` / `3456.7万`。
 * 小于一万原样显示，避免"0.8万"这种别扭写法。
 */
fun formatCompact(value: Long): String {
    if (value < 0) return "-" + formatCompact(-value)
    if (value < 10_000L) return value.toString()
    if (value < 100_000_000L) {
        val wan = value / 10_000.0
        return "${trimDecimal(wan)}万"
    }
    val yi = value / 100_000_000.0
    return "${trimDecimal(yi)}亿"
}

private fun trimDecimal(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

/** 胜率等百分比。`total == 0` 时返回 `—`，别显示 NaN%。 */
fun formatPercent(win: Int, total: Int): String =
    if (total <= 0) "—" else "${win * 100 / total}%"
