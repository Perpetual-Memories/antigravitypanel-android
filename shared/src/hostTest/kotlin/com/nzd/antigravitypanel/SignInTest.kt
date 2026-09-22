package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.config.BUILTIN_CONFIG_JSON
import com.nzd.antigravitypanel.data.config.RemoteConfig
import com.nzd.antigravitypanel.data.remote.IdeChart
import com.nzd.antigravitypanel.data.remote.IdeJson
import com.nzd.antigravitypanel.data.remote.IdeMethod
import com.nzd.antigravitypanel.data.remote.ProtocolException
import com.nzd.antigravitypanel.data.remote.dto.SignInDoDto
import com.nzd.antigravitypanel.data.remote.dto.SignInListDto
import com.nzd.antigravitypanel.data.remote.unwrapIdeResponse
import com.nzd.antigravitypanel.data.signin.SignInCache
import com.nzd.antigravitypanel.data.signin.SignInCacheCodec
import com.nzd.antigravitypanel.data.signin.SignInStatus
import com.nzd.antigravitypanel.data.signin.rewardText
import com.nzd.antigravitypanel.data.signin.toCache
import com.nzd.antigravitypanel.data.signin.toSignInStatus
import com.nzd.antigravitypanel.data.signin.toStatus
import com.nzd.antigravitypanel.util.serverDateKey
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 福利站签到。拿 2026-09-21 抓小程序的真实响应验证。
 *
 * 这一块最容易错的地方是**壳**：福利站的响应是 `jData.welfareStationData.data`，
 * 战绩那 26 个 method 是 `jData.data.data`。壳取错了只会报"加载失败"，
 * 从现象上根本看不出是这一层的问题。
 */
class SignInTest {

    // ---------------- 拆包：两套壳 ----------------

    @Test
    fun 福利站响应走welfareStationData壳() {
        val root = IdeJson.parseToJsonElement(HarFixtures.RESPONSE_SIGNIN_LIST)
        val data = unwrapIdeResponse(root, IdeMethod.SignInList)
        assertEquals(1, decode<SignInListDto>(data).groupList.size)
    }

    @Test
    fun 签到返回也走同一个壳() {
        val root = IdeJson.parseToJsonElement(HarFixtures.RESPONSE_SIGNIN_DO)
        val data = unwrapIdeResponse(root, IdeMethod.SignInDo)
        val dto = decode<SignInDoDto>(data)
        assertEquals("AMS-NZM-0921172619-WMjQzB-98585-98227", dto.signIn?.amsSerialNum)
    }

    @Test
    fun 战绩那组仍然走data壳() {
        // 回归：加了 IdeChart 之后，老 method 的壳不能跟着变
        val root = IdeJson.parseToJsonElement(HarFixtures.RESPONSE_USER_STATS)
        assertTrue(unwrapIdeResponse(root, IdeMethod.UserStats).toString().contains("playtime"))
    }

    @Test
    fun 拿错壳时报协议异常而不是静默返回空() {
        val root = IdeJson.parseToJsonElement(HarFixtures.RESPONSE_SIGNIN_LIST)
        assertFailsWith<ProtocolException> { unwrapIdeResponse(root, IdeMethod.UserStats) }
    }

    // ---------------- 活动号 ----------------

    @Test
    fun 两组活动号分开取() {
        val config = RemoteConfig()
        assertEquals("430662", config.iChartIdOf(IdeChart.Main))
        assertEquals("NoOapI", config.sIdeTokenOf(IdeChart.Main))
        assertEquals("541709", config.iChartIdOf(IdeChart.Welfare))
        assertEquals("VAs3zJ", config.sIdeTokenOf(IdeChart.Welfare))
    }

    @Test
    fun 内置兜底配置里带着福利站那组号() {
        // 远程配置拉不到时用的就是这份，少了它签到会直接失效
        val config = IdeJson.decodeFromString<RemoteConfig>(BUILTIN_CONFIG_JSON)
        assertEquals("541709", config.welfareIChartId)
        assertEquals("VAs3zJ", config.welfareSIdeToken)
    }

    @Test
    fun 福利站那组不发表单顶层的seasonID() {
        assertFalse(IdeChart.Welfare.withSourceParams)
        assertTrue(IdeChart.Main.withSourceParams)
    }

    // ---------------- 看板解析 ----------------

    @Test
    fun 看板解析出连续与累计天数() {
        val status = statusOf(HarFixtures.RESPONSE_SIGNIN_LIST)
        assertEquals("2026-09-21", status.date)
        assertFalse(status.signedToday)
        assertEquals(0, status.continuousDays)
        assertEquals(86, status.totalDays)
        assertEquals(136, status.totalTarget)
        assertEquals(6, status.monthDays)
        assertEquals(30, status.monthTarget)
        assertEquals("2026-05-09", status.periodStart)
        assertEquals("2026-09-21", status.periodEnd)
    }

    @Test
    fun 连续和累计取自不同分支不能混() {
        // 抓包这份里两者差得远（0 vs 86），取错一支会显示成另一个含义完全不同的数
        val status = statusOf(HarFixtures.RESPONSE_SIGNIN_LIST)
        assertTrue(status.totalDays > status.continuousDays)
        // 连续那支的三个窗口实测全是 0，累计那支的月窗口是 6
        assertEquals(0, status.continuousDays)
        assertEquals(6, status.monthDays)
    }

    @Test
    fun 今日奖励的标题与条目() {
        val status = statusOf(HarFixtures.RESPONSE_SIGNIN_LIST)
        assertEquals("100积分", status.todayGiftName)
        assertEquals(listOf("福利中心-兑换币 x100"), status.todayGiftItems)
    }

    @Test
    fun 已签到时signedToday为真() {
        val signed = HarFixtures.RESPONSE_SIGNIN_LIST.replace(
            "\"isSignIn\":false",
            "\"isSignIn\":true",
        )
        assertTrue(statusOf(signed).signedToday)
    }

    @Test
    fun 空groupList给出空状态而不是崩() {
        val empty = """{"ret":0,"iRet":0,"jData":{"welfareStationData":{"code":0,"data":{"groupList":[]},"message":""}}}"""
        val status = statusOf(empty)
        assertEquals("", status.date)
        assertFalse(status.signedToday)
        assertEquals(0, status.totalDays)
    }

    @Test
    fun 签到返回的奖励文案() {
        val root = IdeJson.parseToJsonElement(HarFixtures.RESPONSE_SIGNIN_DO)
        val dto = decode<SignInDoDto>(unwrapIdeResponse(root, IdeMethod.SignInDo))
        assertEquals("100积分", dto.rewardText())
    }

    // ---------------- 缓存 ----------------

    @Test
    fun 同一天的缓存仍显示已签() {
        val cache = statusOf(HarFixtures.RESPONSE_SIGNIN_LIST)
            .copy(signedToday = true)
            .toCache(0L)
        assertTrue(cache.toStatus("20260921").signedToday)
    }

    @Test
    fun 跨天后缓存不再算已签但数字留着() {
        // 昨天签过 → 今天不该显示"已签到"，但连续天数要留着等新数据覆盖
        val cache = statusOf(HarFixtures.RESPONSE_SIGNIN_LIST)
            .copy(signedToday = true)
            .toCache(0L)
        val restored = cache.toStatus("20260922")
        assertFalse(restored.signedToday)
        assertEquals(86, restored.totalDays)
    }

    @Test
    fun 缓存编解码往返不丢字段() {
        val cache = SignInCache(
            date = "2026-09-21",
            signedToday = true,
            continuousDays = 3,
            totalDays = 86,
            totalTarget = 136,
            monthDays = 6,
            monthTarget = 30,
            todayGiftName = "100积分",
            savedAtSec = 1789982779L,
        )
        val decoded = SignInCacheCodec.decode(SignInCacheCodec.encode(cache))
        assertEquals(cache, decoded)
    }

    @Test
    fun 坏掉的缓存返回null而不是抛异常() {
        assertEquals(null, SignInCacheCodec.decode("这不是 JSON"))
    }

    // ---------------- 按北京时间切天 ----------------

    @Test
    fun 日期键按北京时间切天() {
        // 2026-09-21 17:26:19 +08:00
        assertEquals("20260921", serverDateKey(1789982779L))
        // 0 点刚过也算新的一天
        assertEquals("20260921", serverDateKey(1789921800L))
        // 前一天晚上 23:30 还是前一天：UTC 那边已经跨天了，不能跟着跨
        assertEquals("20260920", serverDateKey(1789918200L))
        // 边界：北京时间 00:00（= UTC 前一天 16:00）前一秒还是前一天
        assertEquals("20260920", serverDateKey(1789919999L))
        assertEquals("20260921", serverDateKey(1789920000L))
    }

    private fun statusOf(raw: String): SignInStatus =
        decode<SignInListDto>(
            unwrapIdeResponse(IdeJson.parseToJsonElement(raw), IdeMethod.SignInList),
        ).toSignInStatus()

    /**
     * 注意：Json 上只有两参的 `decodeFromJsonElement(deserializer, element)`，
     * 没有单参的便捷重载（和 `IdeClient` 里那条注释同一个坑）。
     */
    private inline fun <reified T> decode(element: JsonElement): T =
        IdeJson.decodeFromJsonElement(serializer<T>(), element)
}
