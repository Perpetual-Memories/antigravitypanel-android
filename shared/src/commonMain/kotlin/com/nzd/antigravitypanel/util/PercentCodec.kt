package com.nzd.antigravitypanel.util

private const val MAX_ROUNDS = 3

/**
 * 反复做百分号解码，直到解不动为止。
 *
 * 官方接口的编码层数并不统一：
 * - 昵称 `%E9%94%8B...` 编了一层
 * - `list[].avatar` 编了一层，解出来是 `https://...`
 * - `loginUserDetail.avatar` 编了**两**层，解出来还是 `http%3A%2F%2F...`
 *
 * 只解一次的话头像会拿到一串带 `%3A` 的死链，所以这里循环解，最多三轮。
 * 已经不含 `%` 的字符串第一轮就会原样返回，不会误伤正常 URL。
 */
fun String.percentDecoded(): String {
    var current = this
    repeat(MAX_ROUNDS) {
        if (!current.contains('%')) return current
        val next = decodeOnce(current)
        if (next == current) return current
        current = next
    }
    return current
}

private fun decodeOnce(src: String): String {
    val bytes = ArrayList<Byte>(src.length)
    var i = 0
    while (i < src.length) {
        val c = src[i]
        if (c == '%' && i + 2 < src.length) {
            val value = src.substring(i + 1, i + 3).toIntOrNull(16)
            if (value != null) {
                bytes.add(value.toByte())
                i += 3
                continue
            }
        }
        // 非编码字符按 UTF-8 原样收，和上面解出来的字节拼在一起再整体解码，
        // 否则 `%E9%94%8B` 这种 3 字节的汉字会被拆成 3 个乱码字符。
        val literal = if (c == '+') " " else c.toString()
        for (b in literal.encodeToByteArray()) bytes.add(b)
        i++
    }
    return bytes.toByteArray().decodeToString()
}
