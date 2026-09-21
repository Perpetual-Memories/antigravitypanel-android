package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.credential.CookieParseException
import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.credential.parseNzCookie
import com.nzd.antigravitypanel.util.percentDecoded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialTest {

    @Test
    fun 解析标准cookie并强制appid() {
        val c = parseNzCookie(
            "openid=ABC123; acctype=qc; appid=1112451898; " +
                "access_token=DEF456; verifysession=h018de"
        )
        assertEquals("ABC123", c.openid)
        assertEquals("qc", c.acctype)
        assertEquals(NzCookie.REQUIRED_APPID, c.appid)
        assertEquals("DEF456", c.accessToken)
        assertEquals("h018de", c.verifysession)
        assertFalse(c.appidReplaced)
    }

    @Test
    fun appid不一致时改写并标记() {
        val c = parseNzCookie("openid=A; appid=999999; access_token=B")
        assertEquals(NzCookie.REQUIRED_APPID, c.appid)
        assertEquals("999999", c.appidInCookie)
        assertTrue(c.appidReplaced)
    }

    @Test
    fun 整条请求头也能吃下() {
        val c = parseNzCookie(
            "Cookie: openid=A; acctype=qc; appid=1112451898; access_token=B; verifysession=C"
        )
        assertEquals("A", c.openid)
        assertEquals("B", c.accessToken)
    }

    @Test
    fun 缺access_token要报错() {
        assertFailsWith<CookieParseException> { parseNzCookie("openid=A") }
    }

    @Test
    fun 空串要报错() {
        assertFailsWith<CookieParseException> { parseNzCookie("   ") }
    }

    @Test
    fun 拼出的header是五件套() {
        val header = parseNzCookie(
            "openid=A; acctype=qc; appid=1112451898; access_token=B; verifysession=C"
        ).asHeaderValue()
        assertEquals(
            "openid=A; acctype=qc; appid=1112451898; access_token=B; verifysession=C",
            header,
        )
    }

    @Test
    fun 两层编码的头像能解干净() {
        // 实测：list 里的头像编了一层，loginUserDetail 里的编了两层
        val single = "https%3A%2F%2Fthirdwx.qlogo.cn%2Fmmopen%2Fvi_32"
        assertEquals("https://thirdwx.qlogo.cn/mmopen/vi_32", single.percentDecoded())

        val double = "http%3A%2F%2Fthirdqq%2Eqlogo%2Ecn%2Fek%5Fqqthird%2FAQKCpjBCP3XewPuK9rdXeA"
        val once = double.percentDecoded()
        assertFalse(once.contains("%3A"))
        assertEquals(once, once.percentDecoded(), "解干净之后再解应该不变")
    }

    @Test
    fun 中文昵称能解出来() {
        assertEquals("锋矢之影灬辉灬", "%E9%94%8B%E7%9F%A2%E4%B9%8B%E5%BD%B1%E7%81%AC%E8%BE%89%E7%81%AC".percentDecoded())
        assertEquals("哦对了徐八分钱", "%E5%93%A6%E5%AF%B9%E4%BA%86%E5%BE%90%E5%85%AB%E5%88%86%E9%92%B1".percentDecoded())
    }

    @Test
    fun 不含百分号的字符串原样返回() {
        val plain = "https://thirdwx.qlogo.cn/mmopen/vi_32?a=1&b=2"
        assertEquals(plain, plain.percentDecoded())
    }
}
