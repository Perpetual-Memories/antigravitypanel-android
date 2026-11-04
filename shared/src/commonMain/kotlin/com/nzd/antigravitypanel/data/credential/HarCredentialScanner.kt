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

    /**
     * 超核组队活动：要 `T-OPENID` + `T-ACCESS-TOKEN` + `T-APPID`。
     *
     * ⚠️ 和 [XINYUE] 的键名一样，但**不是同一对值**：超核是 QQ 互联 appid 101813972，
     * 心悦悦享卡是 101484782，服务端会 `appid校验不通过`。
     * 所以这里不能按域名认，必须按**请求路径**认（`WPESrv/WPEIndex`）。
     */
    SUPERCORE,
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
    val supercoreOpenId: String? = null,
    val supercoreAccessToken: String? = null,
    val supercoreAppId: String? = null,
    /** 抓包里那页活动页的网址（带分享参数的那条优先）。 */
    val supercoreActivityUrl: String? = null,
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

        SignInCredentialKind.SUPERCORE ->
            if (supercoreOpenId != null && supercoreAccessToken != null) {
                buildString {
                    append("T-OPENID: ").append(supercoreOpenId).append('\n')
                    append("T-ACCESS-TOKEN: ").append(supercoreAccessToken)
                    if (!supercoreAppId.isNullOrBlank()) {
                        append('\n').append("T-APPID: ").append(supercoreAppId)
                    }
                }
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
/**
 * 一份 HAR 里扫出来的**小程序凭证**（账号登录用的那一套）。
 *
 * [entryCount] 和 [HarCredentialScan] 同用途：把「不是 HAR」和「是 HAR 但没有我们要的请求」
 * 分开说，两句话指向的动作完全不同。
 */
data class MiniProgramHarScan(
    val entryCount: Int = 0,
    val cookie: String? = null,
) {
    /** 看起来是一份 HAR（有 entries 节点）。 */
    val looksLikeHar: Boolean get() = entryCount > 0
}

/**
 * 从抓包文件（reqable / Charles / Chrome 导出的 HAR）里认**登录凭证**。
 *
 * 和 [HarCredentialScanner.scan] 认的是完全不同的两样东西：那个扫的是签到用的
 * `p_skey` / `T-OPENID`，这里扫的是 `openid + access_token`（或微信区的 `ieg_ams_*`）。
 * 分开两个函数而不是加参数，是因为命中之后要做的事（存哪、刷什么）一条都不重合。
 *
 * 三处都看，因为不同导出工具落点不同：
 * 1. `Cookie` 请求头（大多数情况）；
 * 2. `request.cookies` 数组 —— reqable 会把 cookie 拆成一个个键值对，整条头反而没有；
 * 3. URL 的 query —— 小程序的部分接口把凭证当查询参数带上去了。
 *
 * 只要**键在**就算命中（值空不空由后面的 `parseNzCookie` 判），这样文件选对了但
 * 那一条请求缺字段时，报的是"cookie 里找不到 xxx"而不是含糊的"没找到凭证"。
 */
fun scanMiniProgramCookie(text: String): MiniProgramHarScan {
    if (text.isBlank()) return MiniProgramHarScan()
    val root = runCatching { Json.decodeFromString<HarRoot>(text) }.getOrNull()
    val entries = root?.log?.entries ?: return MiniProgramHarScan()

    for (entry in entries) {
        for (candidate in cookieCandidates(entry.request)) {
            if (looksLikeMiniProgramCookie(parseCookiePairs(candidate))) {
                return MiniProgramHarScan(entryCount = entries.size, cookie = candidate.trim())
            }
        }
    }
    return MiniProgramHarScan(entryCount = entries.size)
}

/** 一条请求里所有"可能装着凭证"的原文，按可信度从高到低排。 */
private fun cookieCandidates(request: HarRequest?): List<String> {
    if (request == null) return emptyList()
    val out = ArrayList<String>()

    for (h in request.headers.orEmpty()) {
        val name = h.name?.trim() ?: continue
        if (!name.equals("cookie", ignoreCase = true)) continue
        val value = h.value?.takeIf { it.isNotBlank() } ?: continue
        out += value
    }

    // cookies 数组是拆开的，拼回 `k=v; k=v` 才能交给 parseCookiePairs 统一处理
    val pairs = request.cookies.orEmpty().mapNotNull { c ->
        val name = c.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val value = c.value ?: return@mapNotNull null
        "$name=$value"
    }
    if (pairs.isNotEmpty()) out += pairs.joinToString("; ")

    val query = request.url?.substringAfter('?', "")?.substringBefore('#').orEmpty()
    if (query.isNotBlank()) {
        // query 用 `&` 分隔，换成 `;` 才能复用同一套切分逻辑
        out += query.split('&').filter { it.isNotBlank() }.joinToString("; ")
    }
    return out
}

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
        // 超核：只认 WPE 那条请求上的头，不能和心悦的 MyCardList 混
        var superCoreOpenId: String? = null
        var superCoreToken: String? = null
        var superCoreAppId: String? = null
        var superCoreUrlHit: String? = null
        var superCoreUrlFallback: String? = null

        for (entry in entries) {
            val url = entry.request?.url.orEmpty()
            val host = hostOf(url).orEmpty()
            val qqMatched = host.contains("gamecenter.qq.com")
            val xinyueMatched = host.contains("xinyue.qq.com")
            // 超核活动页自己那条 HTML 请求：拿它当"活动网址"回填，用户就不用手抄了
            if (host.equals("act.supercore.qq.com", ignoreCase = true) &&
                url.contains("/supercore/act/") &&
                (url.contains(".html") || url.endsWith("/"))
            ) {
                if (url.contains("team_inv_code")) {
                    superCoreUrlHit = superCoreUrlHit ?: url
                } else {
                    superCoreUrlFallback = superCoreUrlFallback ?: url
                }
            }
            val wpeMatched = xinyueMatched &&
                (url.contains("WPEIndex") || url.contains("ActivitySrv/Run"))

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
                    if (wpeMatched) {
                        if (key == "openid") {
                            superCoreOpenId = superCoreOpenId ?: v
                        } else {
                            superCoreToken = superCoreToken ?: v
                        }
                    }
                }
                if (key == "appid" && wpeMatched) {
                    val v = value.trim()
                    if (v.isNotBlank()) superCoreAppId = superCoreAppId ?: v
                }
            }
        }

        // 有些 HAR 的活动页是缓存命中的，**没有单独的 index.html 请求**，
        // 但地址会出现在响应体/日志/Referer 里。从原文兜底扫一遍，
        // 用户就不用手动填网址了（11/4 的 HAR 就是这样）。
        if (superCoreUrlHit == null && superCoreUrlFallback == null) {
            val urls = Regex(
                """https://act\.supercore\.qq\.com/supercore/act/[A-Za-z0-9]+/index\.html[^"'\s\\]*""",
            ).findAll(text).map { it.value }.toList()
            superCoreUrlHit = urls.firstOrNull { it.contains("team_inv_code") }
            superCoreUrlFallback = urls.firstOrNull()
        }

        return HarCredentialScan(
            entryCount = entries.size,
            qqCookie = qqHit ?: qqFallback,
            xinyueOpenId = openIdHit ?: openIdFallback,
            xinyueAccessToken = tokenHit ?: tokenFallback,
            supercoreOpenId = superCoreOpenId,
            supercoreAccessToken = superCoreToken,
            supercoreAppId = superCoreAppId,
            supercoreActivityUrl = superCoreUrlHit ?: superCoreUrlFallback,
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
