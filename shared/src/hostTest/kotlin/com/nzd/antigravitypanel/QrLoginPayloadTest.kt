package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.qrlogin.QrLoginPayload
import com.nzd.antigravitypanel.data.qrlogin.parseQrLoginPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 真实微信区 cookie 的字段构成（值已替换为等长假串，结构一个不少）。 */
private const val WECHAT_COOKIE =
    "openid=oVFkE7rHydhvBDUCk06Av0KjWdjk; acctype=mini; " +
        "appid=wx4e8cbe4fb0eca54c; unionid=oVLDO63lgn0x_XsaOUBZy3Rsqa2Y; " +
        "ieg_ams_token=dbd78ca864f727b8b9ca9bb89514f58d; ieg_ams_token_time=1788957117; " +
        "ieg_ams_token_v2=ad5c1ca1dd722a66b48f8f687c001f80; " +
        "ieg_ams_session_token=76ffdda2ebbd12cffbc1a8e7001f273c1dab7e66771ab825c1e51308c7a0b570ea89"

/**
 * 扫码内容的认领规则。
 *
 * 这些用例真正的价值在**协议还没定死**的时候：PC 端说要改成放地址的那天，
 * 改完 `parseQrLoginPayload` 跑一遍这里的断言，就能确认 Cookie 那条老路没被改坏。
 *
 * [PC端JSON是正式格式] 里那条是对着 PC 端 v1.8.9 的 `CookieQrModal` 抄的真实结构，
 * 字段一个都不能臆造。（PC 端 2026-09 之后把没人读的 `fields` / `ts` 去掉了，
 * 断言里还留着是因为**客户端本来就该忽略不认识的字段**，老二维码不能因此失效。）
 *
 * 微信区的用例（[微信区没有access_token也认成CookieText] 起）锁的是另一件事：
 * 归属判定必须按「哪个区的 token」放宽，而不是写死 `access_token`。
 */
class QrLoginPayloadTest {

    private val cookie = "openid=ABC123; acctype=qc; appid=1112451898; " +
        "access_token=DEF456; verifysession=h018de"

    /** PC 端 v1.8.9 的 `CookieQrModal` 编的就是这个结构。 */
    private val pcJson = "{\"v\":1,\"platform\":\"\",\"openid\":\"ABC123\"," +
        "\"cookie\":\"openid=ABC123; acctype=qc; appid=1112451898; access_token=DEF456\"," +
        "\"fields\":[\"openid\",\"acctype\"],\"ts\":1758523200000}"

    @Test
    fun PC端JSON是正式格式() {
        val payload = parseQrLoginPayload(pcJson)
        assertTrue(payload is QrLoginPayload.CookieText)
        // openid 直接用 JSON 里那个，不用去解析 cookie
        assertEquals("ABC123", payload.openid)
        // raw 必须是 cookie 字段本身：拿整段 JSON 去当凭证，保存时会解析失败，
        // 而且「账号详细信息」里回显出来是一坨 JSON，用户没法用
        assertEquals(
            "openid=ABC123; acctype=qc; appid=1112451898; access_token=DEF456",
            payload.raw,
        )
    }

    @Test
    fun JSON里没有openid就退回解析cookie() {
        val payload = parseQrLoginPayload(
            "{\"v\":1,\"openid\":\"\",\"cookie\":\"openid=ZZZ999; access_token=TOK\"}"
        )
        assertTrue(payload is QrLoginPayload.CookieText)
        assertEquals("ZZZ999", payload.openid)
    }

    @Test
    fun JSON没有cookie字段不算凭证() {
        // 别的 app 的 JSON 二维码不该被当成登录码
        assertTrue(parseQrLoginPayload("{\"v\":1,\"openid\":\"A\"}") is QrLoginPayload.Unknown)
        assertTrue(parseQrLoginPayload("{\"v\":1,\"cookie\":\"\"}") is QrLoginPayload.Unknown)
    }

    @Test
    fun 坏掉的JSON归Unknown() {
        assertTrue(parseQrLoginPayload("{\"v\":1,\"cookie\":") is QrLoginPayload.Unknown)
    }

    @Test
    fun cookie原文认成CookieText并解出openid() {
        val payload = parseQrLoginPayload(cookie)
        assertTrue(payload is QrLoginPayload.CookieText)
        assertEquals("ABC123", payload.openid)
        // 回传的必须是原文：拿解析后的字段拼回去会丢掉我们不认识的键
        assertEquals(cookie, payload.raw)
    }

    @Test
    fun 带Cookie前缀也能认() {
        val payload = parseQrLoginPayload("Cookie: $cookie")
        assertTrue(payload is QrLoginPayload.CookieText)
        assertEquals("ABC123", payload.openid)
    }

    @Test
    fun 键名大小写混写也认() {
        val payload = parseQrLoginPayload("OPENID=ABC; AcCtYpE=qc; ACCESS_TOKEN=DEF")
        assertTrue(payload is QrLoginPayload.CookieText)
    }

    @Test
    fun 解析不出openid也仍然是CookieText() {
        // 归属只看文本长什么样，不看能不能解析成功：
        // 解析失败要交给保存那一步去报错，这里判成 Unknown 会让错误文案变成"不是登录码"
        val payload = parseQrLoginPayload("openid=; access_token=DEF")
        assertTrue(payload is QrLoginPayload.CookieText)
        assertNull(payload.openid)
    }

    @Test
    fun 局域网地址认成Endpoint() {
        val payload = parseQrLoginPayload("http://192.168.1.7:9527/qrlogin?session=abc")
        assertTrue(payload is QrLoginPayload.Endpoint)
        assertEquals("http://192.168.1.7:9527/qrlogin?session=abc", payload.url)
    }

    @Test
    fun 自定义scheme也算Endpoint() {
        // 桌面应用爱用自定义 scheme，不该被当成"看不懂的文本"
        val payload = parseQrLoginPayload("agpanel://login?token=abc")
        assertTrue(payload is QrLoginPayload.Endpoint)
    }

    @Test
    fun 普通文本和空串归Unknown() {
        assertTrue(parseQrLoginPayload("这是一句普通的话") is QrLoginPayload.Unknown)
        assertTrue(parseQrLoginPayload("   ") is QrLoginPayload.Unknown)
        assertTrue(parseQrLoginPayload("") is QrLoginPayload.Unknown)
    }

    @Test
    fun 只有openid没有token不算凭证() {
        // 少了 token 的串拿去登录必然失败，与其让用户点完确认才报错，
        // 不如直接说"这码不对"。
        // 注意判据是「有没有 token **键**」而不是「有没有 access_token」——
        // 微信区压根没有 access_token，写成后者会把整个微信区误杀。
        assertTrue(parseQrLoginPayload("openid=ABC; acctype=qc") is QrLoginPayload.Unknown)
    }

    // ---------------- 微信区 ----------------

    @Test
    fun 微信区没有access_token也认成CookieText() {
        // 这是微信区之前扫不动的直接原因：判定条件要求 access_token，
        // 而微信区给的是 ieg_ams_token 一族，于是整条被判成"不是登录码"。
        val payload = parseQrLoginPayload(WECHAT_COOKIE)
        assertTrue(payload is QrLoginPayload.CookieText)
        assertEquals("oVFkE7rHydhvBDUCk06Av0KjWdjk", payload.openid)
        assertEquals(WECHAT_COOKIE, payload.raw)
    }

    @Test
    fun PC端JSON里的微信cookie也认() {
        // PC 端二维码里包的是 JSON，cookie 字段换成微信区那串后同样要能过
        val json = "{\"v\":1,\"platform\":\"\",\"openid\":\"oVFkE7rHydhvBDUCk06Av0KjWdjk\"," +
            "\"cookie\":\"$WECHAT_COOKIE\"}"
        val payload = parseQrLoginPayload(json)
        assertTrue(payload is QrLoginPayload.CookieText)
        assertEquals("oVFkE7rHydhvBDUCk06Av0KjWdjk", payload.openid)
        assertEquals(WECHAT_COOKIE, payload.raw)
    }

    @Test
    fun 只有session_token的微信串也认() {
        // 三个 token 键里只带最后一个的情况（小程序不同版本写进去的字段不完全一样）
        val payload = parseQrLoginPayload(
            "openid=ABC; acctype=mini; appid=wx4e8cbe4fb0eca54c; ieg_ams_session_token=T"
        )
        assertTrue(payload is QrLoginPayload.CookieText)
        assertEquals("ABC", payload.openid)
    }

    @Test
    fun 别家的ieg_ams前缀键不算token() {
        // 认的是三个确定的键名，不是 ieg_ams_ 前缀——
        // 否则任何带这个前缀的键都会被当成凭证，把无关二维码放进来
        assertTrue(
            parseQrLoginPayload("openid=ABC; acctype=mini; ieg_ams_something=X")
                is QrLoginPayload.Unknown
        )
    }

    @Test
    fun 前后空白不影响判定() {
        val payload = parseQrLoginPayload("  $cookie  ")
        assertTrue(payload is QrLoginPayload.CookieText)
        assertEquals(cookie, payload.raw)
    }
}
