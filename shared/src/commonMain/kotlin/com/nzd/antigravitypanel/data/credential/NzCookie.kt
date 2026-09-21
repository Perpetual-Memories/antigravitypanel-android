package com.nzd.antigravitypanel.data.credential

/**
 * 请求用的凭证。五个字段必须**成套**出现，缺一个服务端就认不出来。
 *
 * 关于 `appid`：官方做了 appid 强绑定校验，早期开源实现里就有一句正则替换，
 * 把 cookie 里的 appid 字段整体改写成 [REQUIRED_APPID]。这里沿用同样的做法——
 * 无论用户粘进来的是什么，一律改写，并把原始值留在 [appidInCookie] 里供 UI 提示。
 */
data class NzCookie(
    val openid: String,
    val acctype: String,
    val appid: String,
    val accessToken: String,
    val verifysession: String,
    val appidInCookie: String = appid,
    val appidReplaced: Boolean = false,
    /**
     * 用户粘贴的原文。「账号详细信息」里要把整条 Cookie 回显给用户看，
     * 用解析后的字段拼回去会漏掉我们不认识的键值对，也会把改写过的 appid 掺进去。
     */
    val raw: String = "",
) {
    fun asHeaderValue(): String = buildString {
        append("openid=").append(openid)
        append("; acctype=").append(acctype)
        append("; appid=").append(appid)
        append("; access_token=").append(accessToken)
        if (verifysession.isNotBlank()) append("; verifysession=").append(verifysession)
    }

    companion object {
        const val REQUIRED_APPID = "1112451898"
    }
}

class CookieParseException(message: String) : Exception(message)

/**
 * 解析用户粘贴进来的 cookie。
 *
 * 容错范围：整条 `Cookie: a=b; c=d` 请求头、带不带引号、大小写混写、
 * 多余空格都能吃下。只要求 `openid` 与 `access_token` 在，其余缺失给默认值。
 */
fun parseNzCookie(raw: String): NzCookie {
    val text = raw.trim()
    if (text.isBlank()) throw CookieParseException("cookie 是空的")

    val pairs = HashMap<String, String>()
    for (part in text.split(';')) {
        val piece = part.trim().removePrefix("Cookie:").trim()
        if (piece.isBlank()) continue
        val eq = piece.indexOf('=')
        if (eq <= 0) continue
        val key = piece.substring(0, eq).trim()
        val value = piece.substring(eq + 1).trim().trim('"').trim('\'')
        if (key.isNotBlank()) pairs[key.lowercase()] = value
    }

    // 有的抓包工具会把 openid 写成大写，统一按小写 key 取
    val openid = pairs["openid"]?.takeIf { it.isNotBlank() }
        ?: throw CookieParseException("cookie 里找不到 openid")
    val accessToken = pairs["access_token"]?.takeIf { it.isNotBlank() }
        ?: pairs["accesstoken"]?.takeIf { it.isNotBlank() }
        ?: throw CookieParseException("cookie 里找不到 access_token")

    val originalAppid = pairs["appid"].orEmpty()
    val replaced = originalAppid.isNotBlank() && originalAppid != NzCookie.REQUIRED_APPID

    return NzCookie(
        openid = openid,
        acctype = pairs["acctype"]?.takeIf { it.isNotBlank() } ?: "qc",
        appid = NzCookie.REQUIRED_APPID,
        accessToken = accessToken,
        verifysession = pairs["verifysession"].orEmpty(),
        appidInCookie = originalAppid,
        appidReplaced = replaced,
        raw = text,
    )
}
