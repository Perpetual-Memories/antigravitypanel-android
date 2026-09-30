package com.nzd.antigravitypanel.data.credential

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 弹层要的是哪一套凭证。两块签到各要各的，不能混。 */
enum class SignInCredentialKind {
    /** QQ 游戏中心周签到礼包：要 `uin` + `p_skey` 那条 cookie。 */
    QQ_GIFT,

    /** 心悦悦享卡：要 `T-OPENID` + `T-ACCESS-TOKEN` 两个请求头。 */
    XINYUE,
}

/**
 * 一份 HAR 里扫出来的凭证。
 *
 * [entryCount] 单独留着是为了把「不是 HAR」和「是 HAR 但没有我们要的请求」分开说——
 * 用户选错文件时给"文件不对"，抓错页面时给"要在游戏中心里抓"，两句话指向的动作完全不同。
 */
data class HarCredentialScan(
    val entryCount: Int = 0,
    val qqCookie: String? = null,
    val xinyueOpenId: String? = null,
    val xinyueAccessToken: String? = null,
) {
    /** 看起来是一份 HAR（有 entries 节点）。 */
    val looksLikeHar: Boolean get() = entryCount > 0

    /** 取某一套凭证的原文，可以直接交给对应的 `parseXxxCredential`。 */
    fun raw(kind: SignInCredentialKind): String? = when (kind) {
        SignInCredentialKind.QQ_GIFT -> qqCookie
        SignInCredentialKind.XINYUE -> if (xinyueOpenId != null && xinyueAccessToken != null) {
            "T-OPENID: $xinyueOpenId\nT-ACCESS-TOKEN: $xinyueAccessToken"
        } else {
            null
        }
    }
}

/**
 * 从抓包文件（reqable / Charles / Chrome 导出的 HAR）里认签到凭证。
 *
 * 为什么需要它：让用户手抄一段几百字符的 cookie 是不现实的——复制粘贴会漏字符、
 * 漏了之后的表现是"凭证不对"，用户根本不知道是哪里错了。而抓包他本来就要做一遍。
 *
 * 只认**请求头**，不看响应体、不看 query：
 * 凭证是客户端带上来的，落在请求侧，扫响应侧只会误伤（响应里的 Set-Cookie 是新的，
 * 而且有些页面会把别处的 cookie 塞进响应做同步）。
 *
 * **优先取域名对得上的那条**：`p_skey` 是全 QQ 通用的登录态 cookie，
 * 在 mail.qq.com / qun.qq.com 的抓包里也有，但它拿不到游戏中心的签到接口
 * （服务端还会校验 uin 与业务的绑定关系）。先按域名挑，挑不到再退而求其次——
 * 用户偶尔会用别的方式抓，宁可给一条可能不行的也不要直接说"没找到"。
 */
object HarCredentialScanner {
    private val Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun scan(text: String): HarCredentialScan {
        if (text.isBlank()) return HarCredentialScan()
        val root = runCatching { Json.decodeFromString<HarRoot>(text) }.getOrNull()
        val entries = root?.log?.entries
        // entryCount 也要区分：文件根本不是 JSON 时返回 0，别把"解析失败"说成"没找到凭证"
        if (entries == null) return HarCredentialScan()

        var qqHit: String? = null
        var qqFallback: String? = null
        var openIdHit: String? = null
        var openIdFallback: String? = null
        var tokenHit: String? = null
        var tokenFallback: String? = null

        for (entry in entries) {
            val host = hostOf(entry.request?.url).orEmpty()
            val qqMatched = host.contains("gamecenter.qq.com")
            val xinyueMatched = host.contains("xinyue.qq.com")

            // headers 和 cookies 都要扫：reqable 会把 cookie 拆进 `cookies` 数组，
            // 而有的工具只留整条 `Cookie` 头。两个都看，谁先命中算谁的。
            val pairs = ArrayList<Pair<String, String>>()
            for (h in entry.request?.headers.orEmpty()) {
                val name = h.name ?: continue
                val value = h.value ?: continue
                pairs += name to value
            }
            for (c in entry.request?.cookies.orEmpty()) {
                val name = c.name ?: continue
                val value = c.value ?: continue
                pairs += name to value
            }

            for ((name, value) in pairs) {
                val key = normalize(name)
                if (key == "cookie") {
                    if (!value.contains("p_skey", ignoreCase = true)) continue
                    if (qqMatched) qqHit = qqHit ?: value else qqFallback = qqFallback ?: value
                    continue
                }
                if (key == "openid" || key == "access-token") {
                    val v = value.trim()
                    if (v.isBlank()) continue
                    if (key == "openid") {
                        if (xinyueMatched) openIdHit = openIdHit ?: v else openIdFallback = openIdFallback ?: v
                    } else {
                        if (xinyueMatched) tokenHit = tokenHit ?: v else tokenFallback = tokenFallback ?: v
                    }
                }
            }
        }

        return HarCredentialScan(
            entryCount = entries.size,
            qqCookie = qqHit ?: qqFallback,
            xinyueOpenId = openIdHit ?: openIdFallback,
            xinyueAccessToken = tokenHit ?: tokenFallback,
        )
    }

    /**
     * 键名归一：小写、去 `T-` 前缀、`_` 当 `-`。
     *
     * 和 [com.nzd.antigravitypanel.data.xinyue.parseXinyueCredential] 里那套规则保持一致——
     * 两边对同一个键名的理解不一样的话，会出现"扫到了但存的时候说不认识"。
     */
    private fun normalize(name: String): String {
        val text = name.trim().lowercase()
        // 顺序不能反：`t_access_token` 要先换成 `t-access-token`，`T-` 前缀才去得掉。
        // 先去前缀的话下划线那套永远剩一个 `t-` 在头上，匹配不上。
        return text.replace('_', '-').removePrefix("t-")
    }

    private fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val noScheme = url.substringAfter("://", url)
        return noScheme.substringBefore('/').substringBefore(':').takeIf { it.isNotBlank() }
    }
}

@Serializable
private class HarRoot(
    @SerialName("log") val log: HarLog? = null,
)

@Serializable
private class HarLog(
    @SerialName("entries") val entries: List<HarEntry>? = null,
)

@Serializable
private class HarEntry(
    @SerialName("request") val request: HarRequest? = null,
)

@Serializable
private class HarRequest(
    @SerialName("url") val url: String? = null,
    @SerialName("headers") val headers: List<HarNameValue>? = null,
    @SerialName("cookies") val cookies: List<HarNameValue>? = null,
)

@Serializable
private class HarNameValue(
    @SerialName("name") val name: String? = null,
    @SerialName("value") val value: String? = null,
)
