package com.nzd.antigravitypanel.data.qq

/**
 * QQ（含 TIM）的登录凭证。
 *
 * **和小程序那套 cookie 没有交集**：那边是 `openid / access_token / verifysession`，
 * 这边是 `uin / p_uin / p_skey`。两边都要各自的抓包，不能互相推出来。
 *
 * 抓包实测的请求头顺序是 `p_uin;p_skey;uin;o_cookie;domain_id`，按原顺序拼回去——
 * 这类内部接口有时会对字段做顺序敏感的处理，别自己重排。
 */
data class QqCredential(
    val uin: String,
    val pUin: String,
    val pSkey: String,
    val oCookie: String = "",
    val domainId: String = "",
    /** 用户粘贴的原文。回显给用户看要用它，拼回去会漏掉我们不认识的键。 */
    val raw: String = "",
) {
    /**
     * `g_tk`。腾讯那套 bkn 算法：**起点是 5381，每轮 `h += (h << 5) + c`，最后取 31 位**。
     *
     * 2026-09-21 抓包里有两组不同 cookie，g_tk 分别是 317843728 与 509270140，
     * 用这里的实现对各自的 p_skey 算一遍**两个都对得上**（见 `QqCredentialTest`）。
     */
    val gTk: Int get() = bkn(pSkey)

    fun asHeaderValue(): String = buildString {
        append("p_uin=").append(pUin)
        append(";p_skey=").append(pSkey)
        append(";uin=").append(uin)
        if (oCookie.isNotBlank()) append(";o_cookie=").append(oCookie)
        if (domainId.isNotBlank()) append(";domain_id=").append(domainId)
    }
}

class QqCredentialException(message: String) : Exception(message)

/** `h = 5381; h += (h << 5) + c` —— 溢出不处理（Int 自然回绕），最后清掉符号位。 */
internal fun bkn(skey: String): Int {
    var hash = 5381
    for (ch in skey) hash += (hash shl 5) + ch.code
    return hash and 0x7FFFFFFF
}

/**
 * 解析用户粘进来的 QQ cookie。容错规则和小程序那边的 `parseNzCookie` 一致：
 * 整条 `Cookie: a=b; c=d`、大小写混写、多余空格都吃下。
 *
 * `p_uin` 抓包里是 `o` + uin 的形式（`o2107338272`），但用户可能只粘到 `uin`，
 * 缺了就按这个规则补一个——服务端认的是这个形状。
 */
fun parseQqCredential(raw: String): QqCredential {
    val text = raw.trim()
    if (text.isBlank()) throw QqCredentialException("cookie 是空的")

    val pairs = HashMap<String, String>()
    for (part in text.split(';')) {
        val piece = part.trim().removePrefix("Cookie:").trim()
        if (piece.isBlank()) continue
        val eq = piece.indexOf('=')
        if (eq <= 0) continue
        val key = piece.substring(0, eq).trim().lowercase()
        val value = piece.substring(eq + 1).trim().trim('"').trim('\'')
        if (key.isNotBlank()) pairs[key] = value
    }

    val uin = pairs["uin"]?.takeIf { it.isNotBlank() }
        ?: pairs["o_cookie"]?.takeIf { it.isNotBlank() }
        ?: throw QqCredentialException("cookie 里找不到 uin")
    val pSkey = pairs["p_skey"]?.takeIf { it.isNotBlank() }
        ?: throw QqCredentialException("cookie 里找不到 p_skey")
    val pUin = pairs["p_uin"]?.takeIf { it.isNotBlank() } ?: "o$uin"

    return QqCredential(
        uin = uin,
        pUin = pUin,
        pSkey = pSkey,
        oCookie = pairs["o_cookie"].orEmpty(),
        domainId = pairs["domain_id"].orEmpty(),
        raw = text,
    )
}
