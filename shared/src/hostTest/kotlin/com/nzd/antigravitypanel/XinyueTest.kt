package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.remote.IdeJson
import com.nzd.antigravitypanel.data.xinyue.XinyueApiException
import com.nzd.antigravitypanel.data.xinyue.XinyueCardCache
import com.nzd.antigravitypanel.data.xinyue.XinyueCardCacheCodec
import com.nzd.antigravitypanel.data.xinyue.XinyueCardStatus
import com.nzd.antigravitypanel.data.xinyue.XinyueCredentialException
import com.nzd.antigravitypanel.data.xinyue.XinyueEnvelopeCodec
import com.nzd.antigravitypanel.data.xinyue.XinyueProtocolException
import com.nzd.antigravitypanel.data.xinyue.decodeRoleText
import com.nzd.antigravitypanel.data.xinyue.gatewayErrorOf
import com.nzd.antigravitypanel.data.xinyue.dto.XyMyCardListDto
import com.nzd.antigravitypanel.data.xinyue.dto.XyReceiveGiftDto
import com.nzd.antigravitypanel.data.xinyue.parseXinyueCredential
import com.nzd.antigravitypanel.data.xinyue.pickYuexiangCard
import com.nzd.antigravitypanel.data.xinyue.rewardText
import com.nzd.antigravitypanel.data.xinyue.toCache
import com.nzd.antigravitypanel.data.xinyue.toStatus
import com.nzd.antigravitypanel.data.xinyue.yuexiangStatus
import com.nzd.antigravitypanel.ui.signin.xinyueNothingText
import com.nzd.antigravitypanel.ui.signin.xinyueSummaryText
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 心悦俱乐部悦享卡。拿 2026-07-29 抓心悦 App 的真实响应验证。
 *
 * 四件必须钉死的事：
 * 1. **`gift_status` 就是「今天领没领」**：同一天前后两份响应（0→1）对出来的；
 * 2. **「没开通过」和「已过期」必须分开**：过期卡会被服务端挪进 `expire_user_info`，
 *    而抓的那张 2026-08-06 就到期了——过期在实际使用中会是常态；
 * 3. **只认 `card_type == month`**：同一个账号下还挂着黑曜卡（`hmc`），认错了就会拿
 *    黑曜卡的道具清单当悦享卡的；
 * 4. **角色名 / 区服名是 URL-safe base64 且不带 padding**，按标准 base64 解会直接抛。
 */
class XinyueTest {

    // ---------------- 凭证 ----------------

    @Test
    fun 解析抓包里的两个请求头() {
        val credential = parseXinyueCredential(
            "T-OPENID: 0F79B73019698D54DC51E8F4B656A341\n" +
                "T-ACCESS-TOKEN: A8AB8888A215C39B1E7A425D317F3075",
        )
        assertEquals("0F79B73019698D54DC51E8F4B656A341", credential.openId)
        assertEquals("A8AB8888A215C39B1E7A425D317F3075", credential.accessToken)
    }

    @Test
    fun 等号写法和分号分隔也认() {
        val credential = parseXinyueCredential(
            "t-openid=0F79B73019698D54DC51E8F4B656A341;t_access_token=A8AB8888A215C39B1E7A425D317F3075",
        )
        assertEquals("0F79B73019698D54DC51E8F4B656A341", credential.openId)
        assertEquals("A8AB8888A215C39B1E7A425D317F3075", credential.accessToken)
    }

    @Test
    fun 只有两个裸串时按先openid后token收() {
        val credential = parseXinyueCredential("0F79B73019698D54DC51E8F4B656A341 A8AB8888A215C39B1E7A425D317F3075")
        assertEquals("0F79B73019698D54DC51E8F4B656A341", credential.openId)
        assertEquals("A8AB8888A215C39B1E7A425D317F3075", credential.accessToken)
    }

    @Test
    fun 空凭证要给出可执行的提示() {
        assertFailsWith<XinyueCredentialException> { parseXinyueCredential("   ") }
        // 只粘了一半最常见，也要说清楚缺什么
        assertFailsWith<XinyueCredentialException> {
            parseXinyueCredential("T-OPENID: 0F79B73019698D54DC51E8F4B656A341")
        }
    }

    // ---------------- 状态 ----------------

    @Test
    fun 领取前今天可领() {
        val status = decode<XyMyCardListDto>(RESPONSE_MY_CARD_LIST).yuexiangStatus(NOW_IN_CARD)
        assertTrue(status.hasCard)
        assertFalse(status.expired)
        assertTrue(status.canClaim)
        assertEquals(15, status.gotNum)
        assertEquals(30, status.totalNum)
        assertEquals("2026-08-06", status.endDate)
    }

    @Test
    fun 领取后今天不能再领() {
        val status = decode<XyMyCardListDto>(RESPONSE_MY_CARD_LIST_CLAIMED).yuexiangStatus(NOW_IN_CARD)
        assertTrue(status.hasCard)
        assertFalse(status.canClaim)
        // 次数是服务端算的：15 → 16
        assertEquals(16, status.gotNum)
    }

    @Test
    fun 角色名和区服名要解出来() {
        val status = decode<XyMyCardListDto>(RESPONSE_MY_CARD_LIST).yuexiangStatus(NOW_IN_CARD)
        assertEquals("我好菜的喵", status.roleName)
        assertEquals("默认服务器", status.partitionName)
    }

    @Test
    fun 没开过悦享卡不是错误() {
        val status = decode<XyMyCardListDto>(RESPONSE_MY_CARD_LIST_NO_CARD).yuexiangStatus(NOW_IN_CARD)
        assertFalse(status.hasCard)
        assertFalse(status.expired)
        assertFalse(status.canClaim)
    }

    @Test
    fun 过期卡在另一个列表里也能认出来() {
        val status = decode<XyMyCardListDto>(RESPONSE_MY_CARD_LIST_EXPIRED).yuexiangStatus(NOW_IN_CARD)
        assertTrue(status.hasCard)
        assertTrue(status.expired)
    }

    @Test
    fun 到期时间过了就是过期() {
        val list = decode<XyMyCardListDto>(RESPONSE_MY_CARD_LIST)
        // 卡 2026-08-06 23:59:59 到期，过了这一秒才算
        assertFalse(list.yuexiangStatus(1786031999L).expired)
        assertTrue(list.yuexiangStatus(1786032000L).expired)
    }

    @Test
    fun 到期时间缺失时不判过期() {
        // end_time 是 0 表示"不知道"，不是"已经过期了"
        val status = XinyueCardStatus(hasCard = true, endDate = "")
        assertFalse(status.expired)
    }

    @Test
    fun 当前线上那张卡要能读出来() {
        // 2026-09-22 重新抓的：旧卡过期后换了一张新的（record_id 12910939）
        val list = decode<XyMyCardListDto>(RESPONSE_MY_CARD_LIST_CURRENT)
        assertEquals("12910939", list.pickYuexiangCard()?.recordId)

        val status = list.yuexiangStatus(NOW_CURRENT_CARD)
        assertTrue(status.hasCard)
        assertFalse(status.expired)
        assertTrue(status.canClaim)
        assertEquals(16, status.gotNum)
        assertEquals(30, status.totalNum)
        assertEquals("2026-10-05", status.endDate)
        assertEquals("我好菜的喵", status.roleName)
    }

    // ---------------- 网关 ----------------

    @Test
    fun 网关404要认出来() {
        assertEquals("404 Route Not Found", gatewayErrorOf(RESPONSE_GATEWAY_404))
    }

    @Test
    fun 有ret的一律不是网关错() {
        // 业务响应一定有 ret。认错的话会把"卡类型不存在"当成路由错去换域名重试
        assertNull(gatewayErrorOf(RESPONSE_MY_CARD_LIST))
        assertNull(gatewayErrorOf("""{"ret":-9999,"msg":"卡类型不存在","data":null}"""))
    }

    @Test
    fun 解不动的不算网关错() {
        // 网关以外的怪东西交给协议错误那条路，别把两种混成一句话
        assertNull(gatewayErrorOf("not json"))
        assertNull(gatewayErrorOf("""{"foo":1}"""))
    }

    @Test
    fun 黑曜卡不会被当成悦享卡() {
        // 生效列表里 month 排在 hmc 前面，但 gid 相同的 hmc 也在——
        // 挑错的话拿到的会是黑曜卡那张（record_id 为空、month 为 null）
        val card = decode<XyMyCardListDto>(RESPONSE_MY_CARD_LIST).pickYuexiangCard()
        assertEquals("month", card?.cardType)
        assertEquals(1471, card?.gid)
        assertEquals("11737452", card?.recordId)
        assertEquals("yxk188", card?.cardId)
    }

    // ---------------- base64 ----------------

    @Test
    fun urlsafe且无padding的base64要能解() {
        assertEquals("我好菜的喵", decodeRoleText("5oiR5aW96I-c55qE5Za1"))
        assertEquals("默认服务器", decodeRoleText("6buY6K6k5pyN5Yqh5Zmo"))
    }

    @Test
    fun 解不动时退回原文() {
        // 服务端哪天改回明文，不该显示一片乱码
        assertEquals("默认服务器", decodeRoleText("默认服务器"))
        assertEquals("", decodeRoleText(""))
        // 纯 ASCII 的短串会被"解"成一堆随机字节，按控制字符判掉
        assertEquals("server", decodeRoleText("server"))
    }

    // ---------------- 领取 ----------------

    @Test
    fun 奖励文案来自领取响应() {
        assertEquals("NZ点×200", decode<XyReceiveGiftDto>(RESPONSE_RECEIVE_GIFT).rewardText())
    }

    @Test
    fun 没有道具时退回礼包标题() {
        val empty = XyReceiveGiftDto(giftInfo = emptyList())
        assertEquals("", empty.rewardText())
    }

    // ---------------- 响应壳 ----------------

    @Test
    fun ret不为0要抛业务错误() {
        val e = assertFailsWith<XinyueApiException> {
            decode<XyMyCardListDto>(RESPONSE_TOKEN_EXPIRED)
        }
        assertEquals(100001, e.ret)
    }

    @Test
    fun 不是JSON要抛协议错误() {
        assertFailsWith<XinyueProtocolException> { decode<XyMyCardListDto>("not json") }
        assertFailsWith<XinyueProtocolException> { decode<XyMyCardListDto>("""{"ret":0}""") }
    }

    // ---------------- 缓存 ----------------

    @Test
    fun 缓存往返一致() {
        val status = decode<XyMyCardListDto>(RESPONSE_MY_CARD_LIST).yuexiangStatus(NOW_IN_CARD)
        val json = XinyueCardCacheCodec.encode(status.toCache(NOW_IN_CARD))
        val back = XinyueCardCacheCodec.decode(json)?.toStatus()
        assertEquals(status.copy(canClaim = false), back)
    }

    @Test
    fun 缓存里不带今天能不能领() {
        // 冷启动拿出来的那份不能声称"可以领"——昨天缓存的可领，今天可能早就领过了
        val cache = XinyueCardCache(hasCard = true, gotNum = 16, totalNum = 30)
        assertFalse(cache.toStatus().canClaim)
    }

    @Test
    fun 缓存坏了就当没有() {
        assertNull(XinyueCardCacheCodec.decode("{oops"))
    }

    // ---------------- 文案 ----------------

    @Test
    fun 概览那行把三种情况分开说() {
        val noCard = XinyueCardStatus()
        val expired = XinyueCardStatus(hasCard = true, expired = true, endDate = "2026-08-06")
        val claimable = XinyueCardStatus(hasCard = true, gotNum = 15, totalNum = 30, canClaim = true)
        val done = XinyueCardStatus(hasCard = true, gotNum = 16, totalNum = 30)

        assertEquals("需要心悦凭证", xinyueSummaryText(claimable, available = true, bound = false))
        assertEquals("还没拿到，点开看看", xinyueSummaryText(claimable, available = false, bound = true))
        assertEquals("这个号没有悦享卡", xinyueSummaryText(noCard, available = true, bound = true))
        assertEquals("已过期 · 2026-08-06 到期", xinyueSummaryText(expired, available = true, bound = true))
        assertEquals("已领 15/30 · 今天可领", xinyueSummaryText(claimable, available = true, bound = true))
        // 领完不再挂状态词：那件事概览卡右侧的勾已经说了，同一行说两遍纯占地方。
        // 只有"还能领"才值得写出来。
        assertEquals("已领 16/30", xinyueSummaryText(done, available = true, bound = true))
    }

    @Test
    fun 手动领取点下去要说清为什么领不了() {
        assertEquals(
            "这个心悦账号下没有逆战未来的悦享卡",
            xinyueNothingText(XinyueCardStatus()),
        )
        assertEquals(
            "悦享卡已经过期了（2026-08-06 到期）",
            xinyueNothingText(XinyueCardStatus(hasCard = true, expired = true, endDate = "2026-08-06")),
        )
        assertEquals(
            "今天已经领过了",
            xinyueNothingText(XinyueCardStatus(hasCard = true, gotNum = 16)),
        )
    }

    private inline fun <reified T> decode(body: String): T =
        XinyueEnvelopeCodec.decode(body, IdeJson, IdeJson.serializersModule.serializer<T>())

    private companion object {
        /** 07-29 那张卡有效期内的一刻：2026-08-06 23:00（北京时间）。 */
        const val NOW_IN_CARD = 1786030800L

        /** 09-22 那张卡有效期内的一刻：2026-09-27（北京时间）。 */
        const val NOW_CURRENT_CARD = 1790500000L
    }
}
