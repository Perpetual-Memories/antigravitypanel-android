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

/**
 * 分均经济：**金币（万） ÷ 分钟**，也就是"平均每分钟攒了多少万金币"。
 *
 * ⚠️ 只返回数字（`1.5`），单位「万」由 UI 写在**标签**里（`分均经济(万)`）：
 * 这一列的数值本来就是小数，再把单位挂在数后面会比别的格子长出一截，扫不齐。
 *
 * 金币为 0（塔防这类没有金币口径的模式）或时长为 0 时返回 `—` ——
 * 除不出来就别摆一个 0 上去，那会被读成"这一局一分钟没赚钱"。
 */
fun formatCoinPerMinute(coin: Long, durationSec: Int): String {
    if (coin <= 0 || durationSec <= 0) return "—"
    return trimDecimal(coin / 10_000.0 / (durationSec / 60.0))
}

/**
 * 经济转化：**Boss 伤害（万） ÷ 金币（万）**，也就是"每花一万金币打出多少万伤害"。
 *
 * ⚠️ **两边都要折成万**，单位被约掉之后这就是一个比值：
 * Boss 伤害 443.7 万 ÷ 金币 31.2 万 ≈ 14.2。
 * 只把金币折成万、伤害留原值是错的 —— 那算出来的是"每万金币造成多少点伤害"
 * （166,666），既读不出来也和「转化」这个词对不上。
 *
 * 正因为单位约掉了，**不要给它补单位**：这是个比值，再挂「万」就是双重单位。
 *
 * 金币或伤害为 0 时返回 `—`，理由同 [formatCoinPerMinute]。
 */
fun formatDamagePerWanCoin(bossDamage: Long, coin: Long): String {
    if (bossDamage <= 0 || coin <= 0) return "—"
    return trimDecimal(bossDamage.toDouble() / coin.toDouble())
}

/**
 * 秒数折成 `HH:mm:ss`，给倒计时用。
 *
 * 和 [formatDuration] 的区别：那个是"多久"的中文口语（`12分34秒`），这个是"还剩多少"的
 * 时钟写法 —— 倒计时每格都在跳，用中文单位会导致整行宽度忽长忽短。
 *
 * 小时不封顶：24 小时以上的等待照实写成 `25:00:00`，不绕成"1天1小时"。
 * 负数（已经到了但还没刷到数据）按 0 处理，别显示 `-00:00:01`。
 */
fun formatCountdownClock(totalSeconds: Long): String {
    val rest = totalSeconds.coerceAtLeast(0L)
    val hours = rest / 3600L
    val minutes = rest % 3600L / 60L
    val seconds = rest % 60L
    return buildString {
        append(hours.toString().padStart(2, '0'))
        append(':')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
    }
}

/** 胜率等百分比。`total == 0` 时返回 `—`，别显示 NaN%。 */
fun formatPercent(win: Int, total: Int): String =
    if (total <= 0) "—" else "${win * 100 / total}%"
