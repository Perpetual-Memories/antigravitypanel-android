package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.credential.WechatMiniCredential
import com.nzd.antigravitypanel.data.credential.partitionLabelOf
import com.nzd.antigravitypanel.data.remote.dto.GameDetailDto
import com.nzd.antigravitypanel.data.remote.dto.PlayerDetailDto
import com.nzd.antigravitypanel.data.repo.accountProfileOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 概览页顶栏那颗账号胶囊要用到的两个纯函数。
 *
 * 这两个都是"只在真正的网络返回上才会跑到"的逻辑，靠手试要等一轮同步，
 * 固定成断言省事得多 —— 尤其是 URL 编码那一层：
 * `nickname` / `avatar` 是服务端 percent-encode 过的，忘了解码顶栏会直接显示一串百分号。
 */
class TopBarAccountTest {
    private fun detail(nickname: String = "", avatar: String = "") = GameDetailDto(
        loginUserDetail = PlayerDetailDto(nickname = nickname, avatar = avatar),
    )

    private fun qqCookie() = NzCookie(
        openid = "137700727749128",
        acctype = "qc",
        appid = NzCookie.REQUIRED_APPID,
        accessToken = "token",
        verifysession = "",
    )

    @Test
    fun `昵称和头像要先解码再显示`() {
        val profile = accountProfileOf(
            details = listOf(
                detail(
                    nickname = "%E5%93%A6%E5%AF%B9%E4%BA%86",
                    avatar = "http%3A%2F%2Fa%2Eb%2Fc%2Favatar",
                ),
            ),
            openid = "137700727749128",
        )
        assertEquals("哦对了", profile?.nickname)
        // 顺带验一下 http 要被升成 https（另一条测试专门讲为什么）
        assertEquals("https://a.b/c/avatar", profile?.avatarUrl)
        // openid 要跟着存：换号之后靠它判定这张名片是不是别人的
        assertEquals("137700727749128", profile?.openid)
    }

    @Test
    fun `头像地址统一升成 https`() {
        // 官方下发的是 http 打头的 thirdqq / thirdwx CDN，而 app 没开明文流量：
        // 原样交给 Coil 会静默失败 —— 表现就是"ID 和分区都出来了，头像那格是空的"
        val profile = accountProfileOf(
            details = listOf(
                detail(nickname = "甲", avatar = "http://thirdqq.qlogo.cn/ek_qqapp/abc/132"),
            ),
            openid = "1",
        )
        assertEquals("https://thirdqq.qlogo.cn/ek_qqapp/abc/132", profile?.avatarUrl)

        // 已经是 https 的不动；相对 / 畸形地址也不该被改坏
        assertEquals("https://a.b/c", accountProfileOf(listOf(detail(avatar = "https://a.b/c")))?.avatarUrl)
        assertEquals("ftp://a.b/c", accountProfileOf(listOf(detail(avatar = "ftp://a.b/c")))?.avatarUrl)
    }

    @Test
    fun `第一份带回 loginUserDetail 的详情就够用`() {
        // 同一个号打出来的局，昵称头像都一样，没必要逐局比对
        val profile = accountProfileOf(
            details = listOf(detail("甲"), detail("乙")),
            openid = "1",
        )
        assertEquals("甲", profile?.nickname)
    }

    @Test
    fun `自己不在名单里的详情要跳过`() {
        assertNull(accountProfileOf(listOf(GameDetailDto()), "1"))
        assertNull(accountProfileOf(emptyList(), "1"))
    }

    @Test
    fun `昵称和头像全空时才换下一份详情`() {
        // 只缺头像不该被判成"这份没用" —— 顶栏那边头像有兜底样式
        assertNull(accountProfileOf(listOf(detail("", "")), "1"))
        val avatarOnly = accountProfileOf(listOf(detail("", "https://a/b")), "1")
        assertEquals("", avatarOnly?.nickname)
        assertEquals("https://a/b", avatarOnly?.avatarUrl)
    }

    @Test
    fun `分区名跟着凭证类型走`() {
        assertEquals("QQ区", partitionLabelOf(qqCookie()))
        assertEquals(
            "微信区",
            partitionLabelOf(
                WechatMiniCredential(
                    openid = "1",
                    appid = "wx123",
                    tokenKey = "ieg_ams_token",
                    unionid = "",
                    raw = "",
                ),
            ),
        )
        // 没凭证是"不知道"，不能笼统报成 QQ 区
        assertEquals("", partitionLabelOf(null))
    }
}
