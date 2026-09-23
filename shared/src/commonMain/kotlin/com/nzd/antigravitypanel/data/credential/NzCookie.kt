package com.nzd.antigravitypanel.data.credential

/**
 * 请求用的凭证。**QQ 区与微信区是两套不同的字段**，这里按 [acctype] 分派：
 *
 * - QQ 区（`acctype=qc`）走 [NzCookie]：`openid / acctype / appid / access_token / verifysession`
 *   五件套，且官方做了 appid 强绑定校验（详见该类注释）。
 * - 微信区（`acctype=mini`）走 [WechatMiniCredential]：**没有 `access_token`**，
 *   换成 `ieg_ams_token` / `ieg_ams_token_v2` / `ieg_ams_session_token` 这一族，
 *   并且 appid 就是微信小程序的 `wx...`，**不能改写**。
 *
 * 这里用接口而不是给 [NzCookie] 加几个可空字段，是因为两条分支的**必填项不一样**：
 * 微信区没有 `access_token` 是常态，QQ 区没有它就是坏数据。塞进同一个 data class
 * 就只能把 `accessToken` 变成可空，然后每个调用点都得记住"它什么时候可能是空的"。
 */
sealed interface MiniProgramCredential {
    /** 账号唯一标识。UI 与二维码确认卡都只认这个。 */
    val openid: String

    /** 用户粘贴的原文。回显给用户时必须用它，拼回去会漏掉不认识的键值对。 */
    val raw: String

    /** 拼出请求头里的 `Cookie:` 值。 */
    fun asHeaderValue(): String
}

/**
 * QQ 区的五件套凭证。
 *
 * 关于 `appid`：官方做了 appid 强绑定校验，早期开源实现里就有一句正则替换，
 * 把 cookie 里的 appid 字段整体改写成 [REQUIRED_APPID]。这里沿用同样的做法——
 * 无论用户粘进来的是什么，一律改写，并把原始值留在 [appidInCookie] 里供 UI 提示。
 */
data class NzCookie(
    override val openid: String,
    val acctype: String,
    val appid: String,
    val accessToken: String,
    val verifysession: String,
    val appidInCookie: String = appid,
    val appidReplaced: Boolean = false,
    override val raw: String = "",
) : MiniProgramCredential {
    override fun asHeaderValue(): String = buildString {
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

/**
 * 微信区的凭证。
 *
 * 和 QQ 区最关键的两点差别：
 *
 * 1. **没有 `access_token`**。微信小程序给的是 `ieg_ams_*` 一族，识别时[任一即可][WECHAT_TOKEN_KEYS]。
 * 2. **不改写 appid，也不重建请求头**。理由不是"不确定"，而是**实测就只能是原样转发**：
 *    同一份新鲜 cookie 逐步削减后打 `center.user.game.list`，只有原样那条返回
 *    `iRet=0`，去掉 `ieg_ams_session_token` / `session_token` 立刻退回
 *    `iRet=101 请先登录`。也就是说服务端认的是 **`ieg_ams_session_token`**，
 *    而不是看起来更"正式"的 `ieg_ams_token_v2`——按直觉挑一个 token 重建请求头
 *    会把真正的鉴权字段丢掉，照样登不上。另外 appid 也参与鉴权（见 [parseNzCookie]）。
 *
 * @param appid 只用于展示与排查，**不参与拼请求头**，因此允许缺失。
 */
data class WechatMiniCredential(
    override val openid: String,
    val appid: String,
    /** 命中的那个 token 键名，比如 `ieg_ams_token_v2`，用于出错时提示"认到了哪个"。 */
    val tokenKey: String,
    val unionid: String,
    override val raw: String,
) : MiniProgramCredential {
    override fun asHeaderValue(): String = raw

    companion object {
        /**
         * 微信区可能出现的 token 键，按"看起来最像主 token"的顺序排列。
         *
         * 这里只用来**认领**（判断这段文本是不是微信凭证），不用来挑字段拼请求头——
         * 请求头一律走 [raw]。别看到 `ieg_ams_token_v2` 排在第一个就以为它是鉴权字段：
         * 实测真正管用的是排在最后的 `ieg_ams_session_token`（见类注释）。
         */
        val WECHAT_TOKEN_KEYS = listOf(
            "ieg_ams_token_v2",
            "ieg_ams_token",
            "ieg_ams_session_token",
        )
    }
}

/** 微信区标记。`acctype=mini` 是唯一的权威判别依据。 */
const val ACCTYPE_WECHAT = "mini"

/** QQ 区标记，也是 [parseNzCookie] 缺省时的回退值。 */
const val ACCTYPE_QQ = "qc"

/**
 * 微信小程序的 appid 前缀。除 [ACCTYPE_WECHAT] 之外，实践中还会遇到
 * cookie 里没有 `acctype`、但 appid 明显是微信小程序的抓包，一并按微信区处理。
 */
private const val WECHAT_APPID_PREFIX = "wx"

class CookieParseException(message: String) : Exception(message)

/** 把 `k=v; k2=v2` 拆成 map，键统一小写。整条 `Cookie:` 请求头也能吃下。 */
internal fun parseCookiePairs(raw: String): HashMap<String, String> {
    val pairs = HashMap<String, String>()
    for (part in raw.trim().split(';')) {
        val piece = part.trim().removePrefix("Cookie:").trim()
        if (piece.isBlank()) continue
        val eq = piece.indexOf('=')
        if (eq <= 0) continue
        val key = piece.substring(0, eq).trim()
        val value = piece.substring(eq + 1).trim().trim('"').trim('\'')
        if (key.isNotBlank()) pairs[key.lowercase()] = value
    }
    return pairs
}

/**
 * 这段文本像不像一条**小程序凭证**。判据：有 `openid` 键，且有任一区的 token 键。
 *
 * 二维码入参的归属判定（[com.nzd.antigravitypanel.data.qrlogin.parseQrLoginPayload]）
 * 和真正解析用的是**同一个函数**，避免两处规则各写一份、改一处漏一处：
 * 以前那里写的是 `openid && access_token`，微信区就因为少这一个字段被整条判成"未知二维码"。
 *
 * 只看**键在不在**，不看值空不空：`openid=` 这种烂数据仍然算"长得像凭证"，
 * 这样保存那一步才能报出"cookie 里找不到 openid"，而不是含糊地说"这不是登录码"。
 */
internal fun looksLikeMiniProgramCookie(pairs: Map<String, String>): Boolean {
    if (!pairs.containsKey("openid")) return false
    if (pairs.containsKey("access_token")) return true
    return WechatMiniCredential.WECHAT_TOKEN_KEYS.any { pairs.containsKey(it) }
}

/**
 * 解析用户粘贴进来的 cookie，按 `acctype` 分派到 QQ 区或微信区。
 *
 * 容错范围：整条 `Cookie: a=b; c=d` 请求头、带不带引号、大小写混写、
 * 多余空格都能吃下。必填项按区不同——QQ 区要 `access_token`，微信区要 `ieg_ams_*` 之一。
 *
 * @throws CookieParseException 缺 `openid`，或者缺该区必需的 token。
 */
fun parseNzCookie(raw: String): MiniProgramCredential {
    val text = raw.trim()
    if (text.isBlank()) throw CookieParseException("cookie 是空的")

    val pairs = parseCookiePairs(text)

    // 有的抓包工具会把 openid 写成大写，统一按小写 key 取
    val openid = pairs["openid"]?.takeIf { it.isNotBlank() }
        ?: throw CookieParseException("cookie 里找不到 openid")

    val acctype = pairs["acctype"]?.takeIf { it.isNotBlank() } ?: ACCTYPE_QQ
    val appidInCookie = pairs["appid"].orEmpty()
    val isWechat = acctype.equals(ACCTYPE_WECHAT, ignoreCase = true) ||
        appidInCookie.startsWith(WECHAT_APPID_PREFIX, ignoreCase = true)

    if (isWechat) {
        val tokenKey = WechatMiniCredential.WECHAT_TOKEN_KEYS
            .firstOrNull { !pairs[it].isNullOrBlank() }
            ?: throw CookieParseException(
                "cookie 里找不到微信区的 token（${WechatMiniCredential.WECHAT_TOKEN_KEYS.joinToString(" / ")}）"
            )
        return WechatMiniCredential(
            openid = openid,
            // 微信区的 appid 就是小程序自己的，保持原值，不做 [NzCookie.REQUIRED_APPID] 改写。
            // 两处实测：换成 QQ 的 1112451898 之后服务端从 101 改判成"访问人数太多"（iRet=-108），
            // 说明 appid 确实参与鉴权；而且微信请求头里的鉴权字段是 ieg_ams_session_token 那一族，
            // 跟 QQ 区的 access_token 完全不是一回事，改写 appid 之外再重建字段只会更糟。
            appid = appidInCookie,
            tokenKey = tokenKey,
            unionid = pairs["unionid"].orEmpty(),
            raw = text,
        )
    }

    val accessToken = pairs["access_token"]?.takeIf { it.isNotBlank() }
        ?: pairs["accesstoken"]?.takeIf { it.isNotBlank() }
        ?: throw CookieParseException("cookie 里找不到 access_token")

    val appidReplaced = appidInCookie.isNotBlank() && appidInCookie != NzCookie.REQUIRED_APPID

    return NzCookie(
        openid = openid,
        acctype = acctype,
        appid = NzCookie.REQUIRED_APPID,
        accessToken = accessToken,
        verifysession = pairs["verifysession"].orEmpty(),
        appidInCookie = appidInCookie,
        appidReplaced = appidReplaced,
        raw = text,
    )
}
