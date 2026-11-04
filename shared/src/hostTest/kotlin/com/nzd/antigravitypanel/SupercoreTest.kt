package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.credential.HarCredentialScanner
import com.nzd.antigravitypanel.data.credential.SignInCredentialKind
import com.nzd.antigravitypanel.data.supercore.SupercoreApiException
import com.nzd.antigravitypanel.data.supercore.SupercoreCache
import com.nzd.antigravitypanel.data.supercore.SupercoreCacheCodec
import com.nzd.antigravitypanel.data.supercore.SUPERCORE_PLAN_VERSION
import com.nzd.antigravitypanel.data.supercore.giftRecordsOf
import com.nzd.antigravitypanel.data.supercore.SupercoreStatus
import com.nzd.antigravitypanel.data.supercore.extractSupercoreConfig
import com.nzd.antigravitypanel.data.supercore.holdsOf
import com.nzd.antigravitypanel.data.supercore.normalizeSupercoreUrl
import com.nzd.antigravitypanel.data.supercore.parseSupercoreCredential
import com.nzd.antigravitypanel.data.supercore.parseSupercoreEnvelope
import com.nzd.antigravitypanel.data.supercore.parseSupercoreRoot
import com.nzd.antigravitypanel.data.supercore.pointsOf
import com.nzd.antigravitypanel.data.supercore.rewardTextOf
import com.nzd.antigravitypanel.data.supercore.supercorePlanFromConfig
import com.nzd.antigravitypanel.data.supercore.toCache
import com.nzd.antigravitypanel.data.supercore.toStatus
import com.nzd.antigravitypanel.ui.signin.supercoreSummaryText
import com.nzd.antigravitypanel.util.parseServerTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 超核组队活动。拿 2026-11-01 这一期（活动号 31115，11-03 才开）的真配置与真响应校口径。
 *
 * 四件必须钉死的事：
 * 1. **凭证和另三套都不通用**：只能从 WPE 请求头里认，不能拿心悦那对（appid 不同）；
 * 2. **flow 是按活动页配置分类出来的**，不是写死的：签到 9 颗、任务 2 颗、抽奖 1 颗；
 * 3. **业务失败在内层 `data` 里**（活动没开 ret=40006），只看外层 ret=0 会当成成功；
 * 4. **抽奖奖励取 `data.msg`**（页面就是 `msg.replace("恭喜您获得了礼包： ", "")`）。
 */
class SupercoreTest {

    // ---------------- 凭证 ----------------

    @Test
    fun 解析超核请求头并带上appid() {
        val credential = parseSupercoreCredential(
            "T-OPENID: 0123456789ABCDEF0123456789ABCDEF\n" +
                "T-ACCESS-TOKEN: FEDCBA9876543210FEDCBA9876543210\n" +
                "T-APPID: 101813972",
        )
        assertEquals("0123456789ABCDEF0123456789ABCDEF", credential.openId)
        assertEquals("FEDCBA9876543210FEDCBA9876543210", credential.accessToken)
        assertEquals("101813972", credential.appId)
    }

    @Test
    fun 没有appid时用超核活动页的缺省值() {
        val credential = parseSupercoreCredential(
            "t_openid=0123456789ABCDEF0123456789ABCDEF;t_access_token=FEDCBA9876543210FEDCBA9876543210",
        )
        assertEquals("101813972", credential.appId)
    }

    // ---------------- 抓包 ----------------

    @Test
    fun 从超核活动页的HAR里一次认出网址和凭证() {
        val scan = HarCredentialScanner.scan(HAR_SUPERCORE)
        assertTrue(scan.looksLikeHar)
        assertTrue(scan.supercoreActivityUrl.orEmpty().contains("team_inv_code"))

        val raw = scan.raw(SignInCredentialKind.SUPERCORE)
        check(raw != null) { "应该扫到 WPE 请求上的三个头" }
        val credential = parseSupercoreCredential(raw)
        assertEquals("0123456789ABCDEF0123456789ABCDEF", credential.openId)
        assertEquals("101813972", credential.appId)
    }

    @Test
    fun 心悦的MyCardList不会被当成超核凭证() {
        // 同一台 xinyue 域名下、同样的 T- 头，只有 WPEIndex 那条才算超核
        val scan = HarCredentialScanner.scan(HAR_XINYUE_ONLY)
        assertNull(scan.raw(SignInCredentialKind.SUPERCORE))
        assertTrue(scan.xinyueOpenId != null, "心悦那条还是该被认出来")
    }

    // ---------------- 活动配置 ----------------

    @Test
    fun 从appjs里抠出活动配置() {
        val config = extractSupercoreConfig(
            "var x=1;e.exports=JSON.parse('{\"activity_id\":31115,\"ast\":\"supercore\"}')",
        )
        check(config != null)
        assertEquals("31115", config["activity_id"].toString())
    }

    @Test
    fun 按配置认出签到九颗任务两颗和抽奖按钮() {
        val config = extractSupercoreConfig("JSON.parse('$CONFIG_31115')")
        check(config != null)
        val plan = supercorePlanFromConfig(URL, config, 1_800_000_000)
        check(plan != null)

        assertEquals(31115, plan.activityId)
        assertEquals("1471", plan.gid)
        assertEquals("nzm", plan.gameCode)
        assertEquals("supercore", plan.bizId)
        assertEquals("逆战：未来S4季中更新·组队赢好礼", plan.title)
        assertEquals(524512, plan.holdsFlow)
        assertEquals(524511, plan.pointsFlow)
        assertEquals(524505, plan.lotteryFlow)
        // 队伍 flow 也从组队组件配置里读；没成团时全靠它拦住"网络繁忙"
        assertEquals(524509, plan.teamInfoFlow)
        assertEquals(524507, plan.confirmTeamFlow)
        assertEquals("kMH1PYJD", plan.inviteCode)
        assertEquals(SUPERCORE_PLAN_VERSION, plan.planVersion)
        assertEquals(listOf(524502, 524503), plan.taskFlows)
        // 按 1..9 天排序，不是按配置里的顺序
        assertEquals(
            listOf(524504, 524514, 524515, 524516, 524517, 524518, 524519, 524520, 524521),
            plan.signFlows,
        )
    }

    // ---------------- 接口响应 ----------------

    @Test
    fun 活动没开时从内层ret认出来() {
        val e = assertFailsWith<SupercoreApiException> {
            parseSupercoreEnvelope(
                """{"ret":0,"msg":"活动将于11月3日00:00开启，请届时再来",""" +
                    """"data":"{\"ret\":40006,\"msg\":\"活动将于11月3日00:00开启，请届时再来\"}"}""",
            )
        }
        assertEquals(40006, e.ret)
        assertTrue(e.notStarted)
        assertFalse(e.alreadyDone)
    }

    @Test
    fun 登录态和appid校验失败要认得出来() {
        val e = assertFailsWith<SupercoreApiException> {
            parseSupercoreEnvelope(
                """{"ret":7001,"msg":"login status verification failed: appid校验不通过"}""",
            )
        }
        assertTrue(e.loginExpired)
    }

    @Test
    fun 读得出剩余次数和积分() {
        val holds = parseSupercoreEnvelope(
            """{"ret":0,"msg":"","data":"{\"ret\":0,\"holdList\":""" +
                """{\"524505\":{\"remain\":27,\"total\":27},\"524504\":{\"remain\":0,\"total\":1}}}"}""",
        )
        val map = holdsOf(holds)
        assertEquals(27, map[524505]?.remain)
        assertEquals(0, map[524504]?.remain)

        val points = pointsOf(
            parseSupercoreEnvelope("""{"ret":0,"msg":"","data":"{\"ret\":0,\"remain\":12,\"total\":27}"}"""),
        )
        assertEquals(12, points.remain)
        assertEquals(27, points.total)
    }

    @Test
    fun RoleSrv的平铺响应要能解出角色() {
        // RoleSrv 是 `{ret,msg,roles}`，没有 data 字段；按 WPE 的两层信封解会得到空对象，
        // 结果领取请求里的 role 全是空字符串（实测踩过）。
        val root = parseSupercoreRoot(
            """{"ret":0,"msg":"","roles":[{"game_open_id":"G","game_app_id":"A","area_id":1,""" +
                """"plat_id":1,"partition_id":1,"partition_name":"UQ==","role_id":"R",""" +
                """"role_name":"Tg==","device":"android","flag":0}]}""",
        )
        val roles = root["roles"] as? kotlinx.serialization.json.JsonArray
        check(roles != null)
        assertEquals(1, roles.size)
        assertEquals("G", (roles[0] as kotlinx.serialization.json.JsonObject)["game_open_id"].toString().trim('"'))
    }

    @Test
    fun 抽奖奖励取data里的msg() {
        val data = parseSupercoreEnvelope(
            """{"ret":0,"msg":"","data":"{\"ret\":0,\"msg\":\"恭喜您获得了礼包： NZ券×30\"}"}""",
        )
        assertEquals("NZ券×30", rewardTextOf(data))
    }

    // ---------------- 缓存 / 历史 ----------------

    @Test
    fun 缓存跨天只收走今天已签到() {
        val yesterday = parseServerTime("2026-11-01 23:50:00")!!
        val today = parseServerTime("2026-11-02 00:10:00")!!
        val cache = SupercoreCache(
            points = 12,
            signClaimed = 3,
            signTotal = 9,
            tasksClaimed = 2,
            tasksTotal = 2,
            signedToday = true,
            savedAtSec = yesterday,
        )
        val stale = cache.toStatus(today)
        assertTrue(stale.stale)
        assertFalse(stale.signedToday, "跨天的「今天已签到」不能当今天")
        assertEquals(12, stale.points, "积分是累计值，跨天要留着")
        assertEquals(3, stale.signClaimed)

        val fresh = cache.toStatus(yesterday + 60)
        assertFalse(fresh.stale)
        assertTrue(fresh.signedToday)
    }

    @Test
    fun 官方领奖记录解析() {
        // 实测响应：records 里 gift_name 已经是 "NZ券*30"，gift_time 是服务端格式化好的
        val root = parseSupercoreRoot(
            """{"ret":0,"msg":"succ","records":[""" +
                """{"gift_name":"NZ券*30","gift_time":"2026-11-04 07:52:31","package_real_flag":"0"},""" +
                """{"gift_name":"实物手办","gift_time":"2026-11-04 07:52:29","package_real_flag":"1"}""" +
                """],"total":2}""",
        )
        val records = giftRecordsOf(root)
        assertEquals(2, records.size)
        assertEquals("NZ券*30", records[0].giftName)
        assertEquals("2026-11-04 07:52:31", records[0].giftTime)
        assertFalse(records[0].realGift)
        assertTrue(records[1].realGift)
    }

    @Test
    fun 状态缓存能往返() {
        val status = SupercoreStatus(points = 7, signClaimed = 1, signTotal = 9, signedToday = true)
        val raw = SupercoreCacheCodec.encode(status.toCache(1_800_000_000))
        val back = SupercoreCacheCodec.decode(raw)
        check(back != null)
        assertEquals(7, back.points)
        assertEquals(1, back.signClaimed)
    }

    // ---------------- 文案 / 网址 ----------------

    @Test
    fun 只认超核活动页的网址() {
        checkNotNull(normalizeSupercoreUrl(URL))
        assertNull(normalizeSupercoreUrl("https://act.supercore.qq.com/other/31115"))
        assertNull(normalizeSupercoreUrl("https://example.com/supercore/act/abc/index.html"))
        // 不带 scheme 也认
        checkNotNull(normalizeSupercoreUrl("act.supercore.qq.com/supercore/act/ac6/index.html"))
    }

    @Test
    fun 概览那行先说没配置再说没开始() {
        assertEquals(
            "需要活动网址和凭证",
            supercoreSummaryText(SupercoreStatus(), available = false, bound = false),
        )
        assertEquals(
            "活动将于11月3日00:00开启，请届时再来",
            supercoreSummaryText(
                SupercoreStatus(notStarted = true, message = "活动将于11月3日00:00开启，请届时再来"),
                available = true,
                bound = true,
            ),
        )
        assertEquals(
            "今天已签到 · 积分 12 · 签到 3/9 · 抽奖 7/27",
            supercoreSummaryText(
                SupercoreStatus(
                    points = 12,
                    signClaimed = 3,
                    signTotal = 9,
                    drawsRemain = 20,
                    drawsTotal = 27,
                    signedToday = true,
                ),
                available = true,
                bound = true,
            ),
        )
    }
}

private const val URL =
    "https://act.supercore.qq.com/supercore/act/ac6ceaba320e34a8284fe46ecfd7b46b5/index.html" +
        "?team_inv_code=kMH1PYJD&share_type=default_share"

/** 这期配置里所有 `c-button-reward` 的位置与 flowID（其余字段对分类没用）。 */
private const val CONFIG_31115 = """
{"activity_id":31115,"ast":"supercore","gid":"1471","game_code":"nzm","activity_name":"逆战：未来S4季中更新·组队赢好礼",
"config":{"configs":{"XbTEFmGSTVAB0qAakDb7g":{"canvasConfig":{"quaFlowId":524512,"pageMeta":{"title":"逆战：未来S4季中更新·组队赢好礼"}},
"componentConfig":[
{"component":"c-button-reward","configurationData":{"flowID":524505},"styles":{"base":{"left":"269px","top":"3355px","width":"213px","height":"214px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524500},"styles":{"base":{"left":"185px","top":"2780px","width":"381px","height":"74px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524504},"styles":{"base":{"left":"34px","top":"4650px","width":"204px","height":"47px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524503},"styles":{"base":{"left":"516px","top":"4090px","width":"176px","height":"47px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524502},"styles":{"base":{"left":"516px","top":"3976px","width":"176px","height":"47px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524515},"styles":{"base":{"left":"512px","top":"4650px","width":"204px","height":"47px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524514},"styles":{"base":{"left":"273px","top":"4650px","width":"204px","height":"47px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524520},"styles":{"base":{"left":"273px","top":"5310px","width":"204px","height":"47px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524518},"styles":{"base":{"left":"512px","top":"4980px","width":"204px","height":"47px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524517},"styles":{"base":{"left":"273px","top":"4980px","width":"204px","height":"47px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524516},"styles":{"base":{"left":"34px","top":"4980px","width":"204px","height":"47px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524519},"styles":{"base":{"left":"34px","top":"5310px","width":"204px","height":"47px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524521},"styles":{"base":{"left":"512px","top":"5310px","width":"204px","height":"47px"}}},
{"component":"c-button-reward","configurationData":{"flowID":524524},"styles":{"base":{"left":"163px","top":"1720px","width":"425px","height":"87px"}}},
{"component":"c-group-team-new","configurationData":{"queryFlowIdConfirmTeam":524507,"queryFlowIdCreateTeam":524501,"queryFlowIdInviteInfo":524509,"queryFlowIdTeamInfo":524508}},
{"component":"c-text-dynamic","configurationData":{"flowID":524511,"prependText":"当前积分：","targetText":"{{remain}}"}}
]}}}}
"""

private const val HAR_SUPERCORE = """
{
  "log": {
    "entries": [
      {
        "request": {
          "method": "GET",
          "url": "https://act.supercore.qq.com/supercore/act/ac6ceaba320e34a8284fe46ecfd7b46b5/index.html?team_inv_code=kMH1PYJD&via_source=C31115",
          "headers": [{"name": "Accept", "value": "text/html"}]
        }
      },
      {
        "request": {
          "method": "POST",
          "url": "https://bgw.xinyue.qq.com/amp2.WPESrv/WPEIndex?flowId=524512&actId=31115",
          "headers": [
            {"name": "T-OPENID", "value": "0123456789ABCDEF0123456789ABCDEF"},
            {"name": "T-ACCESS-TOKEN", "value": "FEDCBA9876543210FEDCBA9876543210"},
            {"name": "T-APPID", "value": "101813972"},
            {"name": "Content-Type", "value": "application/json"}
          ]
        }
      }
    ]
  }
}
"""

private const val HAR_XINYUE_ONLY = """
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
            {"name": "T-APPID", "value": "101484782"}
          ]
        }
      }
    ]
  }
}
"""
