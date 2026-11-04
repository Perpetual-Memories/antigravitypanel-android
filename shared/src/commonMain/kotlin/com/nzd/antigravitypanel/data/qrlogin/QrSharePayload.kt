package com.nzd.antigravitypanel.data.qrlogin

import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.credential.WechatMiniCredential
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 「反重力登录」二维码的协议版本。
 *
 * 和 PC 端 `CookieQrModal` 的 `v` 是同一个东西：现在两端都只认 1。
 * 以后结构真变了就按 `v` 分派，别拿"有没有某个字段"去猜。
 */
const val QR_SHARE_PROTOCOL_VERSION = 1

/**
 * 「反重力登录」二维码里的内容。
 *
 * 字段和顺序**逐字照抄 PC 端 v1.8.9 的 `CookieQrModal`**：
 * `EO({platform, openid, cookie})` 先按值去重 cookie，再 `JSON.stringify` 出
 * `{"v":1,"platform":"","openid":"","cookie":"..","fields":[...],"ts":<毫秒>}`。
 *
 * 为什么必须一模一样：这张码的消费方是**另一端已经装好的反重力数据面板**，
 * 它读的就是这套字段。多一个少一个都会让对面认不出来——所以别在这里"优化"命名。
 */
@Serializable
data class QrSharePayload(
    val v: Int = QR_SHARE_PROTOCOL_VERSION,
    /** `qq` / `wechat`。PC 端自己叫 loginType，扫码侧目前不读它，但保持有值便于排查。 */
    val platform: String = "",
    val openid: String = "",
    /** 去重后的 cookie 原文，形如 `openid=..; acctype=..; access_token=..`。 */
    val cookie: String = "",
    /** cookie 里出现过的键名，顺序与 [cookie] 一致。 */
    val fields: List<String> = emptyList(),
    /** 毫秒时间戳（PC 端用的是 `Date.now()`，不是秒）。 */
    val ts: Long = 0,
)

/** 这张码是给机器读的，缩进只会让二维码更密、更难扫，所以不 prettyPrint。 */
private val QrShareJson = Json {
    encodeDefaults = true
}

/**
 * 用当前账号拼出二维码里的 JSON 文本。
 *
 * @param nowSec 当前 epoch **秒**（走 [com.nzd.antigravitypanel.util.currentEpochSeconds]，
 *   不要直接用系统时钟，单测要能固定时间）。函数内部乘 1000 转成毫秒。
 */
fun encodeQrSharePayload(
    credential: MiniProgramCredential,
    nowSec: Long,
): String {
    val cookie = normalizeShareCookie(credential.raw)
    return QrShareJson.encodeToString(
        QrSharePayload(
            platform = platformOf(credential),
            openid = credential.openid,
            cookie = cookie,
            fields = shareFieldsOf(cookie),
            ts = nowSec * 1000L,
        ),
    )
}

/**
 * 和 PC 端 `TO()` 完全一致的去重规则：
 * 按 `;` 切开、trim、丢掉空段，**按 value 去重**（value 相同的后面那些键整段丢掉），
 * 最后用 `"; "` 拼回去。
 *
 * 为什么按 value 而不是按 key：抓包下来的 cookie 常出现同一个值挂在两个键名上
 * （`access_token` 与 `accesstoken` 之类），按 value 去重能把这类重复整段清掉，
 * 而按 key 去重会留下两条同值记录，白白把二维码撑大。
 *
 * ⚠️ 空 value 的段**保留**、也不进去重集合（PC 端 `o !== "" && n.has(o)` 同理）：
 * 那是"这个键存在但是空"的信息，删掉就可能让对面把可选字段当缺失。
 */
fun normalizeShareCookie(raw: String): String {
    val seenValues = HashSet<String>()
    val kept = ArrayList<String>()
    for (part in raw.split(';')) {
        // 用户可能整条 `Cookie: a=b; c=d` 粘进来，第一段会带上前缀
        val piece = part.trim().removePrefix("Cookie:").trim()
        if (piece.isEmpty()) continue
        val eq = piece.indexOf('=')
        if (eq < 0) {
            kept += piece
            continue
        }
        val value = piece.substring(eq + 1)
        if (value.isNotEmpty() && !seenValues.add(value)) continue
        kept += piece
    }
    return kept.joinToString("; ")
}

/** cookie 里出现过的键名（保留原大小写、只 trim），顺序和 cookie 一致。 */
fun shareFieldsOf(cookie: String): List<String> = cookie
    .split(';')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .mapNotNull { piece -> piece.substringBefore('=').takeIf { it.isNotBlank() } }

/** 分区名照 PC 端的 loginType 叫法。 */
private fun platformOf(credential: MiniProgramCredential): String = when (credential) {
    is WechatMiniCredential -> "wechat"
    is NzCookie -> "qq"
}
