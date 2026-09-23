package com.nzd.antigravitypanel.data.qrlogin

import com.nzd.antigravitypanel.data.credential.looksLikeMiniProgramCookie
import com.nzd.antigravitypanel.data.credential.parseCookiePairs
import com.nzd.antigravitypanel.data.credential.parseNzCookie
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 扫到的二维码内容，按"它是什么"分成几类。
 *
 * **这一层存在的唯一理由就是协议还没定死。** 扫码页只负责把**文本**交回来，
 * 认它是哪种活儿由这里干；以后协议变了（比如 PC 端改成给个地址让手机去 POST 凭证），
 * 改这个文件加一条分支就行，扫码页和 UI 都不用动。
 *
 * 反过来，如果把"扫到的东西就是 Cookie"这个假设写进扫码页或者 AppContent，
 * 协议一改就得从相机那层一路改到 UI，那才是真的麻烦。
 */
sealed interface QrLoginPayload {

    /**
     * Cookie 原文。
     *
     * 两种来源都归到这一类：PC 端 v1.8.9 起的 JSON 包装（[parseQrLoginPayload] 会
     * 把 `cookie` 字段取出来），以及直接把 Cookie 串编成码的老式二维码。
     *
     * @param raw **可用来登录的那条 Cookie 串**。JSON 情况下取的是里面的 `cookie`
     *   字段而不是整段 JSON —— 输入框要回显给用户看，摆一坨 JSON 在那里没法用。
     *   QQ 区与微信区都归这里，两者的字段不一样（微信区没有 `access_token`），
     *   区分交给 [parseNzCookie]。
     * @param openid 给确认卡显示用。JSON 里带 `openid` 就直接用，没有再退回解析 Cookie。
     *   解析不出来是 null，但**不影响它仍然是 CookieText**：判定归属只看文本长什么样，
     *   不看能不能解析成功。
     */
    data class CookieText(val raw: String, val openid: String?) : QrLoginPayload

    /**
     * 一个地址（预留）。将来 PC 端改成"二维码里放局域网地址 / 一次性授权链接"时走这条：
     * 手机拿到地址后自己去沟通，而不是直接把凭证吞进来。
     *
     * 现在先认下来但不处理，UI 上给一句"这种二维码还不支持"，总比当成未知文本强。
     */
    data class Endpoint(val url: String) : QrLoginPayload

    /** 既不像 Cookie 也不是地址，比如普通文本、网址、别的 app 的二维码。 */
    data class Unknown(val text: String) : QrLoginPayload
}

/**
 * 认领一段扫码文本。
 *
 * 判定走"看起来像什么"，不做严格校验：校验是保存那一步的事（[parseNzCookie] 会抛），
 * 这里提前判死只会把"格式小变一下"的场景误杀成未知。
 */
fun parseQrLoginPayload(text: String): QrLoginPayload {
    val value = text.trim()
    if (value.isBlank()) return QrLoginPayload.Unknown(text)

    // 地址：http(s) 之外也认自定义 scheme，PC 端桌面应用很爱用（比如 agpanel://）。
    // 局域网那段（192.168 / 10. / 172.16-31）也只是备注，判定上和其他地址一视同仁。
    if (value.contains("://")) return QrLoginPayload.Endpoint(value)

    // PC 端 v1.8.9 起的正式格式：二维码里是一段 JSON
    if (value.startsWith("{")) {
        parsePcCookiePayload(value)?.let { return it }
        // JSON 解析不出来就继续往下走：万一哪天 PC 端改回裸 Cookie 串，
        // 而那个串恰好以 { 开头，也不至于直接判成未知
    }

    // 判据和真正解析时**共用同一个函数**（见 looksLikeMiniProgramCookie）：
    // 以前这里写死 `openid && access_token`，微信区的 cookie 没有 access_token，
    // 于是整条被判成"未知二维码"，连输入框都进不去。
    if (looksLikeMiniProgramCookie(parseCookiePairs(value))) {
        val openid = runCatching { parseNzCookie(value).openid }.getOrNull()
        return QrLoginPayload.CookieText(raw = value, openid = openid)
    }

    return QrLoginPayload.Unknown(value)
}

/**
 * PC 端「Cookie 二维码」的格式（`CookieQrModal`）：
 *
 * ```json
 * {"v":1,"platform":"","openid":"","cookie":"openid=..; acctype=..; access_token=..",
 *  "fields":["openid","acctype"],"ts":1758523200000}
 * ```
 *
 * 字段来源：`payload = { v, platform, openid, cookie, fields, ts }`，二维码编的是
 * `JSON.stringify(payload)`。`cookie` 在编码前还过了一遍去重（按 value 去重，
 * 重复值的键会被丢掉），所以拿到的已经是一条干净的 `k=v; k2=v2`。
 *
 * `v` 是**协议版本**：现在是 1，以后 PC 端要是再改结构，按 `v` 分派即可，
 * 别拿"有没有某个字段"去猜——那正是这里写死的坑。
 *
 * @return 认出来的 Cookie；不是这个格式（缺 `cookie`、或者根本不是 JSON）返回 null。
 */
private fun parsePcCookiePayload(text: String): QrLoginPayload.CookieText? {
    val obj = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        ?: return null
    // 只认字符串：万一以后 cookie 变成对象/数组，宁可判成未知也别把它的 toString 当凭证
    val cookie = (obj["cookie"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        ?: return null
    val openid = (obj["openid"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        ?: runCatching { parseNzCookie(cookie).openid }.getOrNull()
    return QrLoginPayload.CookieText(raw = cookie, openid = openid)
}
