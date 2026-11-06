package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.remote.ApiException
import com.nzd.antigravitypanel.data.remote.CookieExpiredException
import com.nzd.antigravitypanel.data.remote.ProtocolException
import com.nzd.antigravitypanel.data.repo.tolerateUnlessExpired
import com.nzd.antigravitypanel.ui.overview.CookieStatus
import com.nzd.antigravitypanel.ui.overview.cookieStatusAfterFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 「凭证过期了，概览状态卡还写已识别」这个 bug 的回归。
 *
 * 根因不在 UI：`OverviewRepository.load` 给每一路都套了 `runCatching`，
 * 连 `CookieExpiredException` 也一起吞了 —— 于是 `refresh` 永远走成功分支，
 * 无条件把状态写成 OK。签到页那边报「凭证失效」、状态卡这边报「已识别」，
 * 两张卡说的是相反的话。
 *
 * 这两条规则（怎么容错、失败之后状态怎么变）现在都是纯函数，钉在这里。
 */
class OverviewExpiryTest {

    // ---------------- 逐项容错 ----------------

    @Test
    fun `普通错误吞掉，这一项用默认值`() {
        // 网络抖动 / 接口结构变了都只是"这一项没拉到"，不该把整页打空
        assertNull(tolerateUnlessExpired<Int> { throw ProtocolException("timeout") })
        assertNull(tolerateUnlessExpired<Int> { throw ApiException(500, "internal") })
    }

    @Test
    fun `拿到了值就原样返回`() {
        assertEquals(42, tolerateUnlessExpired { 42 })
        // null 也是正常结果（比如 firstWinLeftCount 拿不到），别被当成失败
        assertNull(tolerateUnlessExpired<Int?> { null })
    }

    @Test
    fun `凭证失效必须往上抛`() {
        // 这一条就是 bug 本身：吞掉它 = 状态卡永远不知道凭证已经死了
        val e = assertFailsWith<CookieExpiredException> {
            tolerateUnlessExpired<Int> { throw CookieExpiredException(3001, "not login") }
        }
        assertEquals(3001, e.iRet)
    }

    // ---------------- 失败之后状态卡显示什么 ----------------

    @Test
    fun `凭证失效把已识别改成过期`() {
        assertEquals(
            CookieStatus.EXPIRED,
            cookieStatusAfterFailure(CookieStatus.OK, CookieExpiredException(3001, "not login")),
        )
    }

    @Test
    fun `普通失败不改状态`() {
        // 网络挂了不代表凭证过期，把绿卡改成黄卡属于诬告
        assertEquals(
            CookieStatus.OK,
            cookieStatusAfterFailure(CookieStatus.OK, ProtocolException("timeout")),
        )
        assertEquals(
            CookieStatus.IMPORTED_JSON,
            cookieStatusAfterFailure(CookieStatus.IMPORTED_JSON, ApiException(500, "internal")),
        )
    }

    @Test
    fun `已经过期时再失败也还是过期`() {
        // 过期之后再怎么抖都不会自己变回「已识别」—— 那个只有服务端回 iRet=0 才能翻回来
        assertEquals(
            CookieStatus.EXPIRED,
            cookieStatusAfterFailure(CookieStatus.EXPIRED, ProtocolException("timeout")),
        )
    }
}
