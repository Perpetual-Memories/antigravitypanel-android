package com.nzd.antigravitypanel.data.supercore

import com.nzd.antigravitypanel.data.xinyue.XinyueCredentialException
import com.nzd.antigravitypanel.data.xinyue.parseXinyueCredential

/**
 * 超核活动页（`act.supercore.qq.com`）的凭证。
 *
 * 请求侧是三个头：`T-OPENID` / `T-ACCESS-TOKEN` / `T-APPID`。
 *
 * ⚠️ **和另三套签到都不通用**（2026-11-01 拿抓包实测）：
 * - QQ 游戏中心那串 `uin / p_skey` cookie → WPE 网关不认；
 * - 心悦悦享卡那对 `T-OPENID / T-ACCESS-TOKEN`（appid 101484782）
 *   → 拿同一对值换成 101813972 才返回 0，换回 101484782 直接
 *   `ret=7001 login status verification failed: appid校验不通过`；
 * - 活动页自己的 cookie（`IED_LOG_INFO_NEW` 里那个 openid）也不是这一对 ——
 *   登录插件是拿 QQ 互联的 `qcCodeToOpenId` 换出来的，再塞进请求头。
 *
 * 所以只能让用户从**超核活动页**的抓包里导入，不能从别处推。
 */
data class SupercoreCredential(
    val openId: String,
    val accessToken: String,
    /** 超核活动的 QQ 互联 appid。抓包里就是 `T-APPID`，缺省 101813972。 */
    val appId: String = DEFAULT_SUPERCORE_APP_ID,
    /** 用户粘贴的原文。回显用。 */
    val raw: String = "",
)

/** 超核活动页的 QQ 互联 appid（`supercore.qq.com` 登录链路里那个 101813972）。 */
const val DEFAULT_SUPERCORE_APP_ID = "101813972"

class SupercoreCredentialException(message: String) : Exception(message)

/**
 * 解析用户粘进来的请求头。
 *
 * 复用心悦那套"键名归一"的解析（`T-OPENID` / `t_openid` / `openid=` 都认），
 * 只是多收一个 `T-APPID`。`T-APPID` 缺省不报错 —— 绝大多数抓包里都有，
 * 没有的话用活动页固定的 101813972。
 */
fun parseSupercoreCredential(raw: String): SupercoreCredential {
    val text = raw.trim()
    if (text.isBlank()) throw SupercoreCredentialException("凭证是空的")

    val base = try {
        parseXinyueCredential(text)
    } catch (e: XinyueCredentialException) {
        throw SupercoreCredentialException(
            e.message ?: "没认出来，要一段含 T-OPENID 和 T-ACCESS-TOKEN 的请求头",
        )
    }

    return SupercoreCredential(
        openId = base.openId,
        accessToken = base.accessToken,
        appId = appIdOf(text),
        raw = text,
    )
}

/** 从同一段原文里找 `T-APPID`。找不到就用活动页固定值。 */
private fun appIdOf(text: String): String {
    for (part in text.replace('\n', ';').replace('\r', ';').split(';', ',', '|')) {
        val piece = part.trim()
        if (piece.isEmpty()) continue
        val sep = listOf(':', '=').map { piece.indexOf(it) }.filter { it > 0 }.minOrNull()
            ?: continue
        // 和 parseXinyueCredential 同一套归一规则，逐字对齐，
        // 不然会出现"扫到了但存的时候说不认识"
        val key = piece.substring(0, sep).trim()
            .lowercase()
            .replace("_", "-")
            .removePrefix("t-")
            .replace("-", "")
        if (key != "appid") continue
        val value = piece.substring(sep + 1).trim().trim('"').trim('\'')
        if (value.isNotBlank()) return value
    }
    return DEFAULT_SUPERCORE_APP_ID
}
