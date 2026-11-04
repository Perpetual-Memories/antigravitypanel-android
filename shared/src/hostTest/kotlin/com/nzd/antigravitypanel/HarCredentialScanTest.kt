package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.credential.HarCredentialScanner
import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.credential.SignInCredentialKind
import com.nzd.antigravitypanel.data.credential.WechatMiniCredential
import com.nzd.antigravitypanel.data.credential.parseNzCookie
import com.nzd.antigravitypanel.data.credential.scanMiniProgramCookie
import com.nzd.antigravitypanel.data.qq.parseQqCredential
import com.nzd.antigravitypanel.data.xinyue.parseXinyueCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 从抓包文件里认凭证。
 *
 * 三条必须钉死的：
 * 1. **不是 HAR 的文件要能被认出来**（用户很容易选错文件）；
 * 2. **认到的 cookie 要能真的过 `parseQqCredential`**——扫出来却存不进去是最糟的失败；
 * 3. **域名优先**：`p_skey` 是全 QQ 通用的登录态，在别处抓的包里也有，
 *    但它换不来游戏中心的签到接口。
 */
class HarCredentialScanTest {

    @Test
    fun 从游戏中心抓的包里认出QQ凭证() {
        val scan = HarCredentialScanner.scan(HAR_GAMECENTER)
        assertTrue(scan.looksLikeHar)
        assertEquals(2, scan.entryCount)

        val raw = scan.raw(SignInCredentialKind.QQ_GIFT)
        check(raw != null) { "应该扫到 cookie" }
        // 扫出来的原文必须能直接被解析器吃下，否则等于白扫
        val credential = parseQqCredential(raw)
        assertEquals("2107338272", credential.uin)
        assertEquals("o2107338272", credential.pUin)
    }

    @Test
    fun 从心悦抓的包里认出两个T头() {
        val scan = HarCredentialScanner.scan(HAR_XINYUE)
        val raw = scan.raw(SignInCredentialKind.XINYUE)
        check(raw != null) { "应该扫到两个 T- 头" }
        val credential = parseXinyueCredential(raw)
        assertEquals("0F79B73019698D54DC51E8F4B656A341", credential.openId)
        assertEquals("0BCFAF031A0F1D5B04C0ED20FF43FFA8", credential.accessToken)
    }

    @Test
    fun 下划线写法的键名也要认() {
        // t_access_token（下划线）和 T-ACCESS-TOKEN（连字符）是同一个键。
        // 先去 T- 前缀再换下划线的话，下划线那套永远匹配不上。
        val scan = HarCredentialScanner.scan(HAR_XINYUE_UNDERSCORE)
        assertEquals(
            "0F79B73019698D54DC51E8F4B656A341",
            scan.xinyueOpenId,
        )
        assertEquals(
            "0BCFAF031A0F1D5B04C0ED20FF43FFA8",
            scan.xinyueAccessToken,
        )
    }

    @Test
    fun 域名对得上的那条优先() {
        // 两份都带 p_skey，但 game center 那条才带得动签到接口
        val scan = HarCredentialScanner.scan(HAR_MIXED_HOSTS)
        assertTrue(
            scan.qqCookie.orEmpty().contains("GAMECENTER-ONLY"),
            "应该取 gamecenter 那条，实际取到：${scan.qqCookie}",
        )
    }

    @Test
    fun 没有我们要的请求时什么也认不到() {
        val scan = HarCredentialScanner.scan(HAR_NO_CREDENTIAL)
        assertTrue(scan.looksLikeHar, "是 HAR，只是抓错了页面")
        assertNull(scan.qqCookie)
        assertNull(scan.xinyueOpenId)
        assertNull(scan.raw(SignInCredentialKind.QQ_GIFT))
    }

    @Test
    fun 不是HAR的文件要能认出来() {
        val scan = HarCredentialScanner.scan("这不是 JSON，就是一段随手打的")
        assertFalse(scan.looksLikeHar)
        assertEquals(0, scan.entryCount)
    }

    @Test
    fun 空串不炸() {
        assertFalse(HarCredentialScanner.scan("").looksLikeHar)
        assertFalse(HarCredentialScanner.scan("   ").looksLikeHar)
    }

    // ------------------------------------------------------------ 登录凭证（账号详细信息里的导入）

    @Test
    fun 从整条Cookie头里认出登录凭证() {
        val scan = scanMiniProgramCookie(HAR_LOGIN_COOKIE_HEADER)
        assertTrue(scan.looksLikeHar)
        val raw = scan.cookie
        check(raw != null) { "应该扫到 cookie" }
        // 扫出来的原文必须能直接过解析器 —— 否则账号弹层里点导入只会得到一句"cookie 里找不到 xxx"
        val credential = parseNzCookie(raw)
        assertEquals("137700727749128", credential.openid)
        assertEquals(NzCookie.REQUIRED_APPID, (credential as NzCookie).appid)
    }

    @Test
    fun reqable把cookie拆成数组时也能拼回来() {
        // reqable 导出的 HAR 里，cookie 常常只落在 request.cookies 数组里，
        // 整条 Cookie 头反而是空的。只看头的话这份包会一句"没有登录请求"都认不出来。
        val raw = scanMiniProgramCookie(HAR_LOGIN_COOKIE_ARRAY).cookie
        check(raw != null) { "应该拼出 cookie" }
        assertEquals("137700727749128", parseNzCookie(raw).openid)
        assertTrue(raw.contains("access_token="))
    }

    @Test
    fun 凭证在URL查询参数里也认() {
        // 小程序有一部分接口把凭证拼在 query 上，不在头里
        val raw = scanMiniProgramCookie(HAR_LOGIN_QUERY).cookie
        check(raw != null) { "应该认出 query 里的凭证" }
        assertEquals("137700727749128", parseNzCookie(raw).openid)
    }

    @Test
    fun 微信区的登录凭证也认() {
        val raw = scanMiniProgramCookie(HAR_LOGIN_WECHAT).cookie
        check(raw != null) { "微信区没有 access_token，不能只认那一个键" }
        assertTrue(parseNzCookie(raw) is WechatMiniCredential)
    }

    @Test
    fun 抓错页面的HAR要说清楚是没抓到而不是文件坏了() {
        // 是 HAR 但没有登录请求 → 引导用户去小程序里再抓一次；
        // 不是 HAR → 让他换文件。两句话指向的动作不同，所以两个字段都要能区分
        val wrongPage = scanMiniProgramCookie(HAR_NO_CREDENTIAL)
        assertTrue(wrongPage.looksLikeHar)
        assertNull(wrongPage.cookie)

        val notHar = scanMiniProgramCookie("这不是 JSON")
        assertFalse(notHar.looksLikeHar)
        assertNull(notHar.cookie)
    }
}

// ---------------------------------------------------------------- 夹具

private const val HAR_GAMECENTER = """
{
  "log": {
    "version": "1.2",
    "entries": [
      {
        "startedDateTime": "2026-09-24T14:10:35.853Z",
        "request": {
          "method": "POST",
          "url": "https://ssr.gamecenter.qq.com/kuikly-msr/v1/cgi-bin/game-detail-v2?g_tk=1052959842",
          "headers": [
            {"name": "Host", "value": "ssr.gamecenter.qq.com"},
            {"name": "Content-Type", "value": "application/json"},
            {"name": "Cookie", "value": "p_uin=o2107338272;p_skey=*8sDhzi8tDTLldgF6B65smopcc0ESyO*sdJnz3cRrP8_;uin=2107338272;o_cookie=2107338272;domain_id=323"}
          ],
          "postData": {"mimeType": "application/json", "text": "{\"appid\":\"1110484610\"}"}
        }
      },
      {
        "startedDateTime": "2026-09-24T14:10:36.000Z",
        "request": {
          "method": "GET",
          "url": "https://qh.qlogo.cn/g?b=qq&s=100",
          "headers": []
        }
      }
    ]
  }
}
"""

private const val HAR_XINYUE = """
{
  "log": {
    "entries": [
      {
        "request": {
          "method": "POST",
          "url": "https://bgw.xinyue.qq.com/XyCard/CardSrv/MyCardList",
          "headers": [
            {"name": "T-OPENID", "value": "0F79B73019698D54DC51E8F4B656A341"},
            {"name": "T-ACCESS-TOKEN", "value": "0BCFAF031A0F1D5B04C0ED20FF43FFA8"},
            {"name": "Content-Type", "value": "application/json"}
          ]
        }
      }
    ]
  }
}
"""

private const val HAR_XINYUE_UNDERSCORE = """
{
  "log": {
    "entries": [
      {
        "request": {
          "url": "https://bgw.xinyue.qq.com/XyCard/CardSrv/MyCardList",
          "headers": [
            {"name": "t_openid", "value": "0F79B73019698D54DC51E8F4B656A341"},
            {"name": "t_access_token", "value": "0BCFAF031A0F1D5B04C0ED20FF43FFA8"}
          ]
        }
      }
    ]
  }
}
"""

private const val HAR_MIXED_HOSTS = """
{
  "log": {
    "entries": [
      {
        "request": {
          "url": "https://mail.qq.com/cgi-bin/frame_html",
          "headers": [
            {"name": "Cookie", "value": "p_uin=o0001;p_skey=MAIL-ONLY-SKEY;uin=0001"}
          ]
        }
      },
      {
        "request": {
          "url": "https://ssr.gamecenter.qq.com/kuikly-msr/v1/cgi-bin/game-detail-v2",
          "headers": [
            {"name": "Cookie", "value": "p_uin=o2107338272;p_skey=GAMECENTER-ONLY;uin=2107338272"}
          ]
        }
      }
    ]
  }
}
"""

private const val HAR_NO_CREDENTIAL = """
{
  "log": {
    "entries": [
      {
        "request": {
          "url": "https://qh.qlogo.cn/g?b=qq",
          "headers": [{"name": "User-Agent", "value": "Mozilla/5.0"}]
        }
      }
    ]
  }
}
"""

private const val HAR_LOGIN_COOKIE_HEADER = """
{
  "log": {
    "entries": [
      {
        "request": {
          "url": "https://comm.ams.game.qq.com/ide/?serviceId=95&method=center.user.game.list",
          "headers": [
            {"name": "Host", "value": "comm.ams.game.qq.com"},
            {"name": "Cookie", "value": "openid=137700727749128; acctype=qc; appid=1112451898; access_token=ABCDEF123456; verifysession=VS"}
          ]
        }
      }
    ]
  }
}
"""

private const val HAR_LOGIN_COOKIE_ARRAY = """
{
  "log": {
    "entries": [
      {
        "request": {
          "url": "https://comm.ams.game.qq.com/ide/?serviceId=95&method=center.user.game.list",
          "headers": [{"name": "Host", "value": "comm.ams.game.qq.com"}],
          "cookies": [
            {"name": "openid", "value": "137700727749128"},
            {"name": "acctype", "value": "qc"},
            {"name": "appid", "value": "1112451898"},
            {"name": "access_token", "value": "ABCDEF123456"}
          ]
        }
      }
    ]
  }
}
"""

private const val HAR_LOGIN_QUERY = """
{
  "log": {
    "entries": [
      {
        "request": {
          "url": "https://comm.ams.game.qq.com/ide/?openid=137700727749128&acctype=qc&appid=1112451898&access_token=ABCDEF123456",
          "headers": []
        }
      }
    ]
  }
}
"""

private const val HAR_LOGIN_WECHAT = """
{
  "log": {
    "entries": [
      {
        "request": {
          "url": "https://comm.ams.game.qq.com/ide/?serviceId=95&method=center.user.game.list",
          "headers": [
            {"name": "Cookie", "value": "openid=oWeChatOpenId; acctype=mini; appid=wx1234567890; ieg_ams_session_token=SESSION-TOKEN"}
          ]
        }
      }
    ]
  }
}
"""
