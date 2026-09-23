package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.credential.CookieParseException
import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.credential.WechatMiniCredential
import com.nzd.antigravitypanel.data.credential.parseNzCookie
import com.nzd.antigravitypanel.util.percentDecoded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 真实微信区 cookie 的字段构成（值已替换为等长假串，结构一个不少）。 */
private const val WECHAT_COOKIE =
    "openid=oVFkE7rHydhvBDUCk06Av0KjWdjk; acctype=mini; " +
        "appid=wx4e8cbe4fb0eca54c; unionid=oVLDO63lgn0x_XsaOUBZy3Rsqa2Y; " +
        "ieg_ams_token=dbd78ca864f727b8b9ca9bb89514f58d; ieg_ams_token_time=1788957117; " +
        "ieg_ams_token_v2=ad5c1ca1dd722a66b48f8f687c001f80; " +
        "ieg_ams_session_token=76ffdda2ebbd12cffbc1a8e7001f273c1dab7e66771ab825c1e51308c7a0b570ea89"

class CredentialTest {

    @Test
    fun 解析标准cookie并强制appid() {
        val c = parseNzCookie(
            "openid=ABC123; acctype=qc; appid=1112451898; " +
                "access_token=DEF456; verifysession=h018de"
        )
        assertTrue(c is NzCookie)
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
        assertTrue(c is NzCookie)
        assertEquals(NzCookie.REQUIRED_APPID, c.appid)
        assertEquals("999999", c.appidInCookie)
        assertTrue(c.appidReplaced)
    }

    @Test
    fun 整条请求头也能吃下() {
        val c = parseNzCookie(
            "Cookie: openid=A; acctype=qc; appid=1112451898; access_token=B; verifysession=C"
        )
        assertTrue(c is NzCookie)
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

    // ---------------- 微信区 ----------------

    @Test
    fun 微信区没有access_token也能解析() {
        val c = parseNzCookie(WECHAT_COOKIE)
        assertTrue(c is WechatMiniCredential, "acctype=mini 应走微信区分支")
        assertEquals("oVFkE7rHydhvBDUCk06Av0KjWdjk", c.openid)
        assertEquals("ieg_ams_token_v2", c.tokenKey)
        assertEquals("oVLDO63lgn0x_XsaOUBZy3Rsqa2Y", c.unionid)
        assertEquals("wx4e8cbe4fb0eca54c", c.appid)
    }

    @Test
    fun 微信区不改写appid() {
        // 实测：把微信的 appid 换成 QQ 的 1112451898 之后，服务端从
        // 「请先登录」(101) 改判成「访问人数太多」(-108)，说明 appid 确实参与鉴权。
        // 无条件改写等于把这条 cookie 弄坏——这正是当初微信区登不上的原因之一。
        val c = parseNzCookie(WECHAT_COOKIE)
        assertFalse(c.asHeaderValue().contains(NzCookie.REQUIRED_APPID))
        assertTrue(c.asHeaderValue().contains("appid=wx4e8cbe4fb0eca54c"))
    }

    @Test
    fun 微信区原样转发整条cookie() {
        // 这条是**回归的主要防线**：实测真正管用的鉴权字段是 ieg_ams_session_token
        // （去掉它、或只留看起来更"正式"的 ieg_ams_token_v2，服务端都退回 101），
        // 而且还有 token_v2 / session_token 这些我们没解析的别名。
        // 只有原样转发能保证一个都不丢——重建请求头必然丢掉鉴权字段。
        assertEquals(WECHAT_COOKIE, parseNzCookie(WECHAT_COOKIE).asHeaderValue())
    }

    @Test
    fun 微信区认领时按顺序取第一个命中的token键() {
        // tokenKey 只用于日志/排查。**别把它当成"服务端认的字段"**：
        // 排第一的 ieg_ams_token_v2 实测过不了鉴权，真正的字段是 ieg_ams_session_token。
        val onlyV1 = parseNzCookie("openid=A; acctype=mini; appid=wx1; ieg_ams_token=T1")
        assertEquals("ieg_ams_token", (onlyV1 as WechatMiniCredential).tokenKey)

        val all = parseNzCookie(
            "openid=A; acctype=mini; appid=wx1; ieg_ams_session_token=T3; " +
                "ieg_ams_token=T1; ieg_ams_token_v2=T2"
        )
        assertEquals("ieg_ams_token_v2", (all as WechatMiniCredential).tokenKey)
    }

    @Test
    fun 微信区缺token仍要报错() {
        val e = assertFailsWith<CookieParseException> {
            parseNzCookie("openid=A; acctype=mini; appid=wx4e8cbe4fb0eca54c")
        }
        assertTrue(e.message!!.contains("微信"), "错误文案要指明是微信区的 token：${e.message}")
    }

    @Test
    fun 没有acctype但appid是微信的也按微信区处理() {
        // 有的抓包工具不会带上 acctype，靠 appid 兜底
        val c = parseNzCookie("openid=A; appid=wx4e8cbe4fb0eca54c; ieg_ams_token=T")
        assertTrue(c is WechatMiniCredential)
    }

    @Test
    fun 没有acctype也没有微信appid时仍按QQ区处理() {
        // 老行为不能变：QQ 区的 cookie 偶尔就是只有 openid + access_token
        val c = parseNzCookie("openid=A; access_token=B")
        assertTrue(c is NzCookie)
        assertEquals(NzCookie.REQUIRED_APPID, (c as NzCookie).appid)
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
