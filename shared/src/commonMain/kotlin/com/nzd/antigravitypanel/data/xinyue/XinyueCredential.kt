package com.nzd.antigravitypanel.data.xinyue

/**
 * 心悦俱乐部（`agw.xinyue.qq.com`）的凭证。
 *
 * **第三套完全独立的凭证**：小程序战绩那串是 `openid / access_token / verifysession`，
 * QQ 游戏中心那串是 `uin / p_uin / p_skey`，这边只要两个**请求头**——
 * `T-OPENID` 和 `T-ACCESS-TOKEN`。三者没有任何交集，不能互相推出来，各抓各的。
 *
 * 好消息是**角色信息不用用户填**：`XyCard.CardSrv/MyCardList` 会把绑在卡上的
 * 角色（`role`，含 `game_open_id` / `role_id` / 区服）一起返回，
 * 领礼包时照原样回传即可。之前那版 Python 脚本要用户手填十来个角色字段，
 * 就是因为没走这个接口。
 */
data class XinyueCredential(
    val openId: String,
    val accessToken: String,
    /** 用户粘贴的原文。回显用，拼回去会漏掉我们不认识的键。 */
    val raw: String = "",
)

class XinyueCredentialException(message: String) : Exception(message)

/**
 * 解析用户粘进来的心悦凭证头。
 *
 * 吃两种写法：
 * 1. 请求头原文（`T-OPENID: xxx` 一行一个，或 `;` 分隔），键名忽略大小写、
 *    忽略 `T-` 前缀、`-` 和 `_` 一视同仁；
 * 2. 手上只有两个裸串时，按「先 openid 后 token」的顺序收——两个都是 32 位十六进制，
 *    没有别的办法区分，所以这只作为兜底，文档里也只教第一种写法。
 */
fun parseXinyueCredential(raw: String): XinyueCredential {
    val text = raw.trim()
    if (text.isBlank()) throw XinyueCredentialException("凭证是空的")

    val pairs = HashMap<String, String>()
    for (part in text.replace('\n', ';').replace('\r', ';').split(';', ',', '|')) {
        val piece = part.trim()
        if (piece.isBlank()) continue
        // 冒号优先（HTTP 头写法），其次等号（ini / query 写法）
        val sep = listOf(':', '=').map { piece.indexOf(it) }.filter { it > 0 }.minOrNull()
        if (sep == null) continue
        // 先把 `_` 归一成 `-` 再去掉 `T-` 前缀：`t_access_token` 和 `T-ACCESS-TOKEN`
        // 是同一个键，只处理后一种会让前一种变成 `taccesstoken`，永远匹配不上。
        val key = piece.substring(0, sep).trim()
            .lowercase()
            .replace("_", "-")
            .removePrefix("t-")
            .replace("-", "")
        val value = piece.substring(sep + 1).trim().trim('"').trim('\'')
        if (key.isNotBlank() && value.isNotBlank()) pairs[key] = value
    }

    val openId = pairs["openid"]
    val accessToken = pairs["accesstoken"] ?: pairs["token"]

    if (openId != null && accessToken != null) {
        return XinyueCredential(openId = openId, accessToken = accessToken, raw = text)
    }

    // 兜底：一个键名都没认出来、且正好有两个串时，按「先 openid 后 token」收。
    // 必须限定在 pairs 为空——只粘了 `T-OPENID: xxx` 一半的情况也满足"两个串"，
    // 但那是要报错的缺失，不是另一种写法。
    if (pairs.isEmpty()) {
        val bare = text.split(' ', '\t', '\n', '\r', ',', ';', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (bare.size >= 2) {
            return XinyueCredential(openId = bare[0], accessToken = bare[1], raw = text)
        }
    }

    throw XinyueCredentialException(
        "没认出来。要一段含 T-OPENID 和 T-ACCESS-TOKEN 的请求头，两行各一个",
    )
}
