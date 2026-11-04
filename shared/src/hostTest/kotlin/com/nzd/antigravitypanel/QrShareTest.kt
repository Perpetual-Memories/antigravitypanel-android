package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.credential.parseNzCookie
import com.nzd.antigravitypanel.data.qrlogin.QrLoginPayload
import com.nzd.antigravitypanel.data.qrlogin.QrSharePayload
import com.nzd.antigravitypanel.data.qrlogin.encodeQrSharePayload
import com.nzd.antigravitypanel.data.qrlogin.normalizeShareCookie
import com.nzd.antigravitypanel.data.qrlogin.parseQrLoginPayload
import com.nzd.antigravitypanel.data.qrlogin.shareFieldsOf
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 「反重力登录」二维码的内容。
 *
 * 这张码是给**另一端的反重力数据面板**读的，所以守的是"和 PC 端逐字一致"：
 * - 字段名 / 顺序 / `v` / `ts` 单位（毫秒）；
 * - cookie 先按值去重（PC 端 `TO()` 的行为），空 value 的段要保留；
 * - 我们自己扫码那一侧也要能把它读回来（同一套协议的双向自检）。
 */
class QrShareTest {

    private val qqCookie = "openid=0A1B; acctype=qc; appid=1112451898; " +
        "access_token=TOKEN; verifysession=SESSION"

    @Test
    fun 字段与PC端一致且时间戳是毫秒() {
        val credential = parseNzCookie(qqCookie)
        val text = encodeQrSharePayload(credential, nowSec = 1780000000L)
        val obj = Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject

        assertEquals(1, (obj["v"] as kotlinx.serialization.json.JsonPrimitive).content.toInt())
        assertEquals("qq", (obj["platform"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("0A1B", (obj["openid"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals(
            qqCookie,
            (obj["cookie"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
        assertEquals(
            1780000000000L,
            (obj["ts"] as kotlinx.serialization.json.JsonPrimitive).content.toLong(),
        )
        val fields = (obj["fields"] as kotlinx.serialization.json.JsonArray)
            .map { (it as kotlinx.serialization.json.JsonPrimitive).content }
        assertEquals(listOf("openid", "acctype", "appid", "access_token", "verifysession"), fields)
    }

    @Test
    fun 微信区platform是wechat() {
        val credential = parseNzCookie(
            "openid=wx-1; acctype=mini; appid=wx123; ieg_ams_session_token=T; unionid=u",
        )
        val text = encodeQrSharePayload(credential, nowSec = 1L)
        val obj = Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        assertEquals("wechat", (obj["platform"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun 按值去重且保留空值段() {
        val raw = "Cookie: openid=abc; acctype=qc; access_token=XYZ; accesstoken=XYZ; verifysession="
        val cookie = normalizeShareCookie(raw)

        // 第一段带 "Cookie:" 前缀也要清掉
        assertEquals("openid=abc; acctype=qc; access_token=XYZ; verifysession=", cookie)
        // 值重复的 accesstoken 整段消失，空值的 verifysession 保留
        assertEquals(listOf("openid", "acctype", "access_token", "verifysession"), shareFieldsOf(cookie))
    }

    @Test
    fun 生成的码能被自己这一侧读回来() {
        val credential = parseNzCookie(qqCookie)
        val text = encodeQrSharePayload(credential, nowSec = 1780000000L)

        val parsed = parseQrLoginPayload(text)
        assertTrue(parsed is QrLoginPayload.CookieText, "自己生成的码要能认成 CookieText")
        assertEquals(qqCookie, (parsed as QrLoginPayload.CookieText).raw)
        assertEquals("0A1B", parsed.openid)
    }

    @Test
    fun 序列化不缩进() {
        // 缩进会让二维码更密；这条防的是以后顺手把 QrShareJson 改成 prettyPrint
        val text = encodeQrSharePayload(parseNzCookie(qqCookie), nowSec = 1L)
        assertTrue(!text.contains('\n'), "二维码内容不该有换行")
    }

    /** 只是让 QrSharePayload 的默认构造被编译到：协议版本写死在数据类里。 */
    @Test
    fun 默认协议版本是1() {
        assertEquals(1, QrSharePayload().v)
    }
}
