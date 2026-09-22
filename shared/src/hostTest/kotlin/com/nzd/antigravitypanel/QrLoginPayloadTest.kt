package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.qrlogin.QrLoginPayload
import com.nzd.antigravitypanel.data.qrlogin.parseQrLoginPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 扫码内容的认领规则。
 *
 * 这些用例真正的价值在**协议还没定死**的时候：PC 端说要改成放地址的那天，
 * 改完 `parseQrLoginPayload` 跑一遍这里的断言，就能确认 Cookie 那条老路没被改坏。
 *
 * [PC端JSON是正式格式] 里那条是对着 PC 端 v1.8.9 的 `CookieQrModal` 抄的真实结构，
 * 字段一个都不能臆造。
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
    fun 只有openid没有access_token不算凭证() {
        // 少了 access_token 的串拿去登录必然失败，与其让用户点完确认才报错，
        // 不如直接说"这码不对"
        assertTrue(parseQrLoginPayload("openid=ABC; acctype=qc") is QrLoginPayload.Unknown)
    }

    @Test
    fun 前后空白不影响判定() {
        val payload = parseQrLoginPayload("  $cookie  ")
        assertTrue(payload is QrLoginPayload.CookieText)
        assertEquals(cookie, payload.raw)
    }
}
