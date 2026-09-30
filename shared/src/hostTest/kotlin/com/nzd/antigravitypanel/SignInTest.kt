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
import com.nzd.antigravitypanel.data.remote.dto.ScoreRedeemListDto
import com.nzd.antigravitypanel.data.remote.dto.TaskLabelDto
import com.nzd.antigravitypanel.data.remote.dto.TaskRewardDto
import com.nzd.antigravitypanel.data.settings.UserSettings
import com.nzd.antigravitypanel.data.signin.SignInCache
import com.nzd.antigravitypanel.data.signin.SignInCacheCodec
import com.nzd.antigravitypanel.data.signin.SignInStatus
import com.nzd.antigravitypanel.data.signin.WelfareTaskState
import com.nzd.antigravitypanel.data.signin.claimableTasks
import com.nzd.antigravitypanel.data.signin.dailyTask
import com.nzd.antigravitypanel.data.signin.rewardText
import com.nzd.antigravitypanel.data.signin.toCache
import com.nzd.antigravitypanel.data.signin.toSignInStatus
import com.nzd.antigravitypanel.data.signin.toStatus
import com.nzd.antigravitypanel.data.signin.totalScore
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.ui.signin.dailyTaskStateText
import com.nzd.antigravitypanel.ui.signin.signInSummaryText
import com.nzd.antigravitypanel.util.serverDateKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    // ---------------- 积分余额 ----------------

    @Test
    fun 积分总数取scoreList里的totalScore() {
        assertEquals(7800, scoreRedeemListOf(WelfareFixtures.RESPONSE_SCORE_REDEEM_LIST).totalScore())
    }

    @Test
    fun 一个积分项都没有时积分是null而不是0() {
        // 0 会被 UI 显示成"一分没有"，那是另一件事
        val empty = """{"ret":0,"iRet":0,"jData":{"welfareStationData":{"code":0,"data":{"actID":3472,"tasks":[]},"message":""}}}"""
        assertNull(scoreRedeemListOf(empty).totalScore())
    }

    // ---------------- 任务中心 ----------------

    @Test
    fun 每日任务挑的是period为day的那条() {
        val daily = requireNotNull(taskLabelOf(WelfareFixtures.RESPONSE_TASK_LABEL).dailyTask())
        assertEquals(65928, daily.taskId)
        assertEquals(13299, daily.groupId)
        // 名字直接用服务端给的，不在这边写死
        assertEquals("每日完成1局", daily.name)
        assertTrue(daily.finished)
        assertFalse(daily.awarded)
    }

    @Test
    fun 真实抓包里该领的是每日与每周那两个() {
        val ids = taskLabelOf(WelfareFixtures.RESPONSE_TASK_LABEL).claimableTasks().map { it.taskId }
        assertEquals(listOf(65928, 65929), ids)
    }

    @Test
    fun 自动领取只认每日与每周_long与month都不碰() {
        // 订阅小程序是 long、累登是 month，两个都"已完成未领取"，照样不能被领走
        assertEquals(listOf(12), taskLabelOf(TASKS_JSON).claimableTasks().map { it.taskId })
        assertEquals(12, taskLabelOf(TASKS_JSON).dailyTask()?.taskId)
    }

    @Test
    fun 已领过的不再出现在可领列表里() {
        val awarded = TASKS_JSON.replace(
            """"progress":1,"isfinished":true,"isawarded":false,"ext":{"period":"day"}""",
            """"progress":1,"isfinished":true,"isawarded":true,"ext":{"period":"day"}""",
        )
        assertTrue(awarded != TASKS_JSON)
        assertTrue(taskLabelOf(awarded).claimableTasks().isEmpty())
    }

    @Test
    fun 领取成功的文案取amsmsg并去掉前后空格() {
        val dto = decode<TaskRewardDto>(
            unwrapIdeResponse(
                IdeJson.parseToJsonElement(WelfareFixtures.RESPONSE_TASK_REWARD),
                IdeMethod.TaskReward,
            ),
        )
        assertEquals("恭喜您获得了礼包： 500积分", dto.rewardText())
    }

    @Test
    fun 领取返回Ret非0时不算领到() {
        // 仓库靠 Ret 判断要不要记去重标记：记了的话这天剩下的机会就被吞掉了
        val failed = WelfareFixtures.RESPONSE_TASK_REWARD.replace("\"Ret\":0", "\"Ret\":1")
        val dto = decode<TaskRewardDto>(
            unwrapIdeResponse(IdeJson.parseToJsonElement(failed), IdeMethod.TaskReward),
        )
        assertFalse(dto.res.first().Ret == 0)
    }

    // ---------------- 概览小字 ----------------

    @Test
    fun 概览小字的三态说完整() {
        val base = SignInStatus(
            date = "2026-09-29",
            continuousDays = 9,
            monthDays = 15,
            monthTarget = 30,
            dailyTask = WelfareTaskState(name = "每日完成1局", period = "day"),
        )
        assertEquals(
            "连续 9 天 · 本月 15/30 · 每日完成1局 任务未完成",
            signInSummaryText(base, available = true),
        )
        val task = requireNotNull(base.dailyTask)
        assertEquals("奖励待领取", dailyTaskStateText(task.copy(finished = true)))
        assertEquals("奖励已领取", dailyTaskStateText(task.copy(finished = true, awarded = true)))
    }

    @Test
    fun 拿不到每日任务时小字不摆那一段() {
        // 冷启动缓存里刻意不存任务态：昨天的"已领取"摆到今天是假话
        val status = SignInStatus(date = "2026-09-29", continuousDays = 9, monthDays = 15, monthTarget = 30)
        assertEquals("连续 9 天 · 本月 15/30", signInSummaryText(status, available = true))
    }

    @Test
    fun 没拿到数据时小字不谎报连续0天() {
        assertEquals(
            "登录后可见，打开 app 时会自动帮你签到",
            signInSummaryText(SignInStatus(), available = false),
        )
    }

    // ---------------- 缓存与开关 ----------------

    @Test
    fun 积分跟着缓存走且跨天不清空() {
        val cache = statusOf(HarFixtures.RESPONSE_SIGNIN_LIST).copy(totalScore = 7800).toCache(0L)
        assertEquals(7800, cache.toStatus("20260922").totalScore)
        assertEquals(cache, SignInCacheCodec.decode(SignInCacheCodec.encode(cache)))
    }

    @Test
    fun 两个开关默认都是开() = runBlocking {
        val settings = UserSettings(FakeStore())
        settings.restore()
        assertTrue(settings.autoSignIn.value)
        assertTrue(settings.autoClaimWelfareTask.value)
    }

    @Test
    fun 只有写成0才是关() = runBlocking {
        val settings = UserSettings(
            FakeStore(
                mapOf(
                    StoreKey.SIGNIN_AUTO to "0",
                    StoreKey.WELFARE_TASK_AUTO_CLAIM to "0",
                ),
            ),
        )
        settings.restore()
        assertFalse(settings.autoSignIn.value)
        assertFalse(settings.autoClaimWelfareTask.value)
    }

    private class FakeStore(
        initial: Map<String, String> = emptyMap(),
    ) : KeyValueStore {
        val map = LinkedHashMap<String, String>(initial)

        override suspend fun read(key: String): String? = map[key]

        override suspend fun write(key: String, value: String) {
            map[key] = value
        }

        override suspend fun remove(key: String) {
            map.remove(key)
        }

        override fun peek(key: String): String? = map[key]
    }

    /** 四个任务、四种 period，用来验证"该不该自动领"的筛选。 */
    private val TASKS_JSON = """{"ret":0,"iRet":0,"jData":{"welfareStationData":{"code":0,"data":{"taskgroups":{"labelgrouptasks":[{"groupid":13299,"grouptasks":[{"taskid":11,"taskinfo":{"name":"订阅小程序"},"taskdata":{"target":1,"progress":1,"isfinished":true,"isawarded":false,"ext":{"period":"long"}}},{"taskid":12,"taskinfo":{"name":"每日完成1局"},"taskdata":{"target":1,"progress":1,"isfinished":true,"isawarded":false,"ext":{"period":"day"}}},{"taskid":13,"taskinfo":{"name":"每周对局5次"},"taskdata":{"target":5,"progress":5,"isfinished":true,"isawarded":true,"ext":{"period":"week"}}},{"taskid":14,"taskinfo":{"name":"累登10天领取"},"taskdata":{"target":10,"progress":10,"isfinished":true,"isawarded":false,"ext":{"period":"month"}}}]}]},"message":""}}}}"""

    private fun scoreRedeemListOf(raw: String): ScoreRedeemListDto =
        decode(
            unwrapIdeResponse(IdeJson.parseToJsonElement(raw), IdeMethod.ScoreRedeemList),
        )

    private fun taskLabelOf(raw: String): TaskLabelDto =
        decode(
            unwrapIdeResponse(IdeJson.parseToJsonElement(raw), IdeMethod.TaskLabel),
        )

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
