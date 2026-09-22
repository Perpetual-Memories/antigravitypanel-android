package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.qq.QqApiException
import com.nzd.antigravitypanel.data.qq.QqCredentialException
import com.nzd.antigravitypanel.data.qq.QqEnvelopeCodec
import com.nzd.antigravitypanel.data.qq.QqGiftCache
import com.nzd.antigravitypanel.data.qq.QqGiftCacheCodec
import com.nzd.antigravitypanel.data.qq.QqProtocolException
import com.nzd.antigravitypanel.data.qq.QqWeeklySignIn
import com.nzd.antigravitypanel.data.qq.bkn
import com.nzd.antigravitypanel.data.qq.dto.QqExchangeResultDto
import com.nzd.antigravitypanel.data.qq.dto.QqFirstScreenDto
import com.nzd.antigravitypanel.data.qq.dto.QqGameUserInfoDto
import com.nzd.antigravitypanel.data.qq.parseQqCredential
import com.nzd.antigravitypanel.data.qq.toCache
import com.nzd.antigravitypanel.data.qq.toStatus
import com.nzd.antigravitypanel.data.qq.weeklySignIn
import com.nzd.antigravitypanel.data.remote.IdeJson
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * QQ 游戏中心礼包。拿 2026-09-21 抓 QQ 游戏中心逆战未来礼包页的真实响应验证。
 *
 * 三件必须钉死的事：
 * 1. **g_tk 算法必须和官方一致**——算错就是全部接口 403，而且看不出是这步错了；
 * 2. **只有 `isSign` 那个礼包才是签到**：同一页有 15 个礼包，另外 13 个是等级礼/启动礼/CDK 礼；
 * 3. **`canGot` 是唯一可信的"今天领没领"信号**：`weekItems[].got` 刚领完还是 false。
 */
class QqGiftTest {

    // ---------------- g_tk ----------------

    @Test
    fun gTk复现抓包里的第一个值() {
        // entry 0：game-detail-v2 那组请求，g_tk=317843728
        assertEquals(317843728, bkn("O4uU4NY7CoDfI3knhoxhc1jWAlTB77MBz4r5-PrmjlI_"))
    }

    @Test
    fun gTk复现抓包里的第二个值() {
        // entry 15/18/21：福利那组请求（p_skey 已经换过一次），g_tk=509270140
        assertEquals(509270140, bkn("nAPMRk35bCgZyyPxj3xTPlUHqStIX0uA5Aqnv4Jbr4U_"))
    }

    @Test
    fun gTk恒为正数() {
        // 结果要清掉符号位，否则会出现负的 g_tk，服务端不认
        assertTrue(bkn("nAPMRk35bCgZyyPxj3xTPlUHqStIX0uA5Aqnv4Jbr4U_") > 0)
        assertTrue(bkn("O4uU4NY7CoDfI3knhoxhc1jWAlTB77MBz4r5-PrmjlI_") > 0)
    }

    // ---------------- 凭证 ----------------

    @Test
    fun 解析抓包里的整条cookie() {
        val credential = parseQqCredential(COOKIE_B)
        assertEquals("2107338272", credential.uin)
        assertEquals("o2107338272", credential.pUin)
        assertEquals("nAPMRk35bCgZyyPxj3xTPlUHqStIX0uA5Aqnv4Jbr4U_", credential.pSkey)
        assertEquals("323", credential.domainId)
        assertEquals(509270140, credential.gTk)
    }

    @Test
    fun 拼回去和抓包里的请求头一字不差() {
        // 这类内部接口可能对字段顺序敏感，重排过就有风险——钉住顺序
        assertEquals(COOKIE_B, parseQqCredential(COOKIE_B).asHeaderValue())
        assertEquals(COOKIE_A, parseQqCredential(COOKIE_A).asHeaderValue())
    }

    @Test
    fun 用户粘进来带Cookie前缀和多余空格也能吃下() {
        val credential = parseQqCredential("Cookie: uin=2107338272; p_skey=nAPMRk35bCgZyyPxj3xTPlUHqStIX0uA5Aqnv4Jbr4U_ ;")
        assertEquals("2107338272", credential.uin)
        assertEquals(509270140, credential.gTk)
        // p_uin 没粘到就按 `o` + uin 补——服务端认的是这个形状
        assertEquals("o2107338272", credential.pUin)
    }

    @Test
    fun 缺p_skey直接报错而不是静默发出错请求() {
        assertFailsWith<QqCredentialException> { parseQqCredential("uin=2107338272") }
        assertFailsWith<QqCredentialException> { parseQqCredential("") }
        assertFailsWith<QqCredentialException> { parseQqCredential("p_skey=abc") }
    }

    // ---------------- 首页解析 ----------------

    @Test
    fun 挑出周签到而不是别的礼包() {
        val status = decode<QqFirstScreenDto>(QqHarFixtures.RESPONSE_QQ_FIRSTSCREEN).weeklySignIn()
        assertEquals(1, status.day)
        // 一周是 7 天：weekItems 有 8 项，但第 8 项是"七天全签满的额外奖励"，
        // 不是一周里的第 8 天。写成 8 会显示成本周 1/8。
        assertEquals(7, status.totalDays)
        assertTrue(status.canClaim)
        assertEquals("GP点*500", status.todayReward)
        assertEquals("NZ券*100", status.nextReward)
        assertEquals("赛季补给箱*2", status.fullWeekBonus)
        assertEquals(1, status.weekDay)
    }

    @Test
    fun 第8天是满签奖励不算进本周进度() {
        // 满签之后 day 会走到 8，但"本周"仍然是 7/7，不能显示成 8/8
        val status = QqWeeklySignIn(day = 8, totalDays = 7, fullWeekBonus = "赛季补给箱*2")
        assertEquals(8, status.day)
        assertEquals(7, status.weekDay)
    }

    @Test
    fun 可领的非签到礼包不会被当成签到() {
        // 夹具里特意放着一个 canGot=true 但 isSign=false 的【启动】礼。
        // 照 canGot 挑就会把"启动礼"当成签到，天数直接变 0。
        val gifts = decode<QqFirstScreenDto>(QqHarFixtures.RESPONSE_QQ_FIRSTSCREEN)
            .firstGame?.gifts.orEmpty()
        assertTrue(gifts.count { it.canGot } > 1)
        assertEquals(8, gifts.first { it.isSign }.weekItems.size)
    }

    @Test
    fun 领完那一格canGot翻成false() {
        val status = decode<QqFirstScreenDto>(QqHarFixtures.RESPONSE_QQ_FIRSTSCREEN_CLAIMED).weeklySignIn()
        assertFalse(status.canClaim)
        // 天数/奖励还在，"领没领"只由 canGot 表达
        assertEquals(1, status.day)
        assertEquals("GP点*500", status.todayReward)
    }

    @Test
    fun 不能拿weekItems的got判断领没领() {
        // 抓包里 09:37:54 刚领完、09:37:55 再查，got 仍然是 false。
        // 谁拿它判断"今天领了没"，就会天天以为还没领、反复点。
        val sign = decode<QqFirstScreenDto>(QqHarFixtures.RESPONSE_QQ_FIRSTSCREEN_CLAIMED)
            .firstGame?.gifts.orEmpty()
            .first { it.isSign }
        assertFalse(sign.canGot)
        assertFalse(sign.weekItems.first().got)
    }

    @Test
    fun 没有签到礼包时给出空状态而不是崩() {
        val none = """{"code":0,"data":{"firstGame":{"appid":"1110484610","gifts":[]}}}"""
        val status = decode<QqFirstScreenDto>(none).weeklySignIn()
        assertFalse(status.hasData)
        assertEquals(0, status.totalDays)
        assertFalse(status.canClaim)
    }

    // ---------------- 角色 ----------------

    @Test
    fun 角色信息解出领取必需的参数() {
        val info = decode<QqGameUserInfoDto>(QqHarFixtures.RESPONSE_QQ_USER_INFO)
        val area = info.roles.first().areaInfo
        checkNotNull(area)
        assertEquals(1, area.area)
        assertEquals(1, area.platId)
        assertEquals(1, area.partition)
        assertEquals("419175704459784", area.roleId)
        assertEquals("哦对了徐八分钱", area.roleName)
    }

    // ---------------- 领取 ----------------

    @Test
    fun 领取是批量的一返回就是三个礼包() {
        val result = decode<QqExchangeResultDto>(QqHarFixtures.RESPONSE_QQ_EXCHANGE)
        assertEquals(3, result.gifts.size)
        val names = result.gifts.mapNotNull { it.gift?.name }
        assertTrue(names.contains("周签到礼包"))
        // 另外两个【启动】礼是官方自己顺手一起领的——不是我们只领了签到
        assertTrue(names.any { it.contains("启动") })
    }

    @Test
    fun 领取后签到那格已经不能再领() {
        val sign = decode<QqExchangeResultDto>(QqHarFixtures.RESPONSE_QQ_EXCHANGE)
            .gifts.mapNotNull { it.gift }
            .first { it.isSign }
        assertFalse(sign.canGot)
        // 另外两个还开着，说明服务端不是简单地"领完就全关"
        assertTrue(
            decode<QqExchangeResultDto>(QqHarFixtures.RESPONSE_QQ_EXCHANGE)
                .gifts.mapNotNull { it.gift }
                .any { !it.isSign && it.canGot },
        )
    }

    // ---------------- 壳 ----------------

    @Test
    fun code非0时抛业务异常() {
        val failure = assertFailsWith<QqApiException> {
            decode<QqFirstScreenDto>("""{"code":10001,"data":null,"message":"请先登录"}""")
        }
        assertEquals(10001, failure.code)
        assertTrue(failure.message.orEmpty().contains("请先登录"))
    }

    @Test
    fun 缺data节点时抛协议异常() {
        assertFailsWith<QqProtocolException> { decode<QqFirstScreenDto>("""{"code":0}""") }
        assertFailsWith<QqProtocolException> { decode<QqFirstScreenDto>("不是 JSON") }
    }

    // ---------------- 缓存 ----------------

    @Test
    fun 缓存不保存canClaim() {
        // 冷启动拿出来的旧数据说"可以领"是误导：昨天能领不等于今天还能领
        val status = decode<QqFirstScreenDto>(QqHarFixtures.RESPONSE_QQ_FIRSTSCREEN)
            .weeklySignIn()
            .copy(roleName = "哦对了徐八分钱")
        assertTrue(status.canClaim)

        val restored = status.toCache(1789982779L).toStatus()
        assertFalse(restored.canClaim)
        assertEquals(1, restored.day)
        assertEquals(7, restored.totalDays)
        assertEquals("GP点*500", restored.todayReward)
        assertEquals("赛季补给箱*2", restored.fullWeekBonus)
        assertEquals("哦对了徐八分钱", restored.roleName)
    }

    @Test
    fun 缓存编解码往返不丢字段() {
        val cache = QqGiftCache(
            day = 3,
            totalDays = 7,
            todayReward = "复活币*1",
            nextReward = "武器插件-惯性制退*1",
            fullWeekBonus = "赛季补给箱*2",
            roleName = "哦对了徐八分钱",
            savedAtSec = 1789982779L,
        )
        assertEquals(cache, QqGiftCacheCodec.decode(QqGiftCacheCodec.encode(cache)))
    }

    @Test
    fun 坏掉的缓存返回null而不是抛异常() {
        assertNull(QqGiftCacheCodec.decode("这不是 JSON"))
    }

    private inline fun <reified T> decode(body: String): T =
        QqEnvelopeCodec.decode(body, IdeJson, IdeJson.serializersModule.serializer<T>())

    companion object {
        /** entry 0 那组：`g_tk=317843728`。 */
        private const val COOKIE_A =
            "p_uin=o2107338272;p_skey=O4uU4NY7CoDfI3knhoxhc1jWAlTB77MBz4r5-PrmjlI_;" +
                "uin=2107338272;o_cookie=2107338272;domain_id=323"

        /** entry 15/18/21 那组：`g_tk=509270140`。 */
        private const val COOKIE_B =
            "p_uin=o2107338272;p_skey=nAPMRk35bCgZyyPxj3xTPlUHqStIX0uA5Aqnv4Jbr4U_;" +
                "uin=2107338272;o_cookie=2107338272;domain_id=323"
    }
}
