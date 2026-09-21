package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.remote.dto.UserStatsDto
import com.nzd.antigravitypanel.data.repo.OverviewCache
import com.nzd.antigravitypanel.data.repo.OverviewCacheCodec
import com.nzd.antigravitypanel.data.repo.RecentFive
import com.nzd.antigravitypanel.domain.ActivityEvent
import com.nzd.antigravitypanel.ui.overview.CookieStatus
import com.nzd.antigravitypanel.ui.overview.restoreOverviewFromCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 概览缓存的落盘 / 读回。
 *
 * 这份缓存是"冷启动先把上一帧摆回去"的唯一依据，所以最该锁住的是**往返不丢字段**：
 * 近五场那几个可空项（Boss 伤害 / 金币）在 JSON 里就是 `null`，
 * 一旦编解码哪一侧把它们吞成 0，页面上就会凭空多出两格假数据。
 */
class OverviewCacheTest {
    private fun sample() = OverviewCache(
        cookieStatus = "OK",
        stats = UserStatsDto(playtime = 3661, huntGameCount = 12),
        recent = RecentFive(
            sampleCount = 5,
            mvpCount = 2,
            avgScore = 8800,
            avgBossDamage = 123456,
            avgCoin = 700,
        ),
        activities = listOf(
            ActivityEvent(
                title = "双倍掉落",
                startSec = 1000,
                endSec = 2000,
                description = "活动说明",
                extParam = "09.10-09.24",
            ),
        ),
        savedAtSec = 1_700_000_000,
    )

    @Test
    fun `编解码往返后每一项都还在`() {
        val decoded = OverviewCacheCodec.decode(OverviewCacheCodec.encode(sample()))
        assertEquals(sample(), decoded)
    }

    @Test
    fun `近五场里可空的 Boss 伤害和金币不会被吞成 0`() {
        // 本地库算出来的近五场没有这两项，null 是有意义的："这一格整个不显示"
        val cache = sample().copy(
            recent = RecentFive(
                sampleCount = 3,
                mvpCount = 1,
                avgScore = 500,
                avgKills = 12,
                winCount = 2,
                localOnly = true,
            ),
        )
        val decoded = OverviewCacheCodec.decode(OverviewCacheCodec.encode(cache))
        assertEquals(null, decoded?.recent?.avgBossDamage)
        assertEquals(null, decoded?.recent?.avgCoin)
        assertEquals(12, decoded?.recent?.avgKills)
        assertEquals(true, decoded?.recent?.localOnly)
    }

    @Test
    fun `解析不了的缓存返回 null 而不是抛异常`() {
        assertNull(OverviewCacheCodec.decode(""))
        assertNull(OverviewCacheCodec.decode("{ 这不是 json"))
        // 写到一半进程被杀掉留下的半截 JSON
        assertNull(OverviewCacheCodec.decode("""{"cookieStatus":"OK","stats":{"playtim"""))
    }

    @Test
    fun `认不出的状态名等不到任何枚举就当没缓存`() {
        // 注意解码器开了 isLenient，数字会被宽容地收成字符串（"123"），
        // 所以这里不是靠"解析失败"兜住的，而是靠 OverviewViewModel 里
        // `CookieStatus.entries.firstOrNull { it.name == ... }` 匹配不上直接返回。
        // 这条断言把那个契约写死：状态名对不上时，缓存整体不生效。
        val decoded = OverviewCacheCodec.decode("""{"cookieStatus":123}""")
        assertEquals("123", decoded?.cookieStatus)
        assertEquals(null, CookieStatus.entries.firstOrNull { it.name == decoded?.cookieStatus })
    }

    @Test
    fun `缓存的状态是已识别时冷启动直接带上同步中标记`() {
        val raw = OverviewCacheCodec.encode(sample().copy(cookieStatus = "OK"))
        // 这一条就是"冷启动第一帧该长什么样"：状态是已识别，且盖着同步中遮罩，
        // 不能是默认的「未输入」——那一帧闪一下用户就会以为凭证丢了
        val restored = restoreOverviewFromCache(raw, 1_700_000_100)
        assertEquals(CookieStatus.OK, restored?.cookieStatus)
        assertEquals(true, restored?.cookieVerifying)
        assertEquals(12, restored?.stats?.huntGameCount)
        assertEquals(5, restored?.recent?.sampleCount)
    }

    @Test
    fun `缓存的状态是未输入时不显示同步中`() {
        // 上一次就没登录过，这次也不该装作正在同步
        val raw = OverviewCacheCodec.encode(sample().copy(cookieStatus = "MISSING"))
        val restored = restoreOverviewFromCache(raw, 1_700_000_100)
        assertEquals(CookieStatus.MISSING, restored?.cookieStatus)
        assertEquals(false, restored?.cookieVerifying)
    }

    @Test
    fun `恢复时滤掉缓存里已经结束的活动`() {
        val cache = sample().copy(
            activities = listOf(
                ActivityEvent(title = "已结束", startSec = 1000, endSec = 2000),
                ActivityEvent(title = "还在跑", startSec = 1500, endSec = 9999),
            ),
        )
        // 时间轴也放进断言里：endSec 是相对"现在"判断的，写个 1.7e9 会把两条都滤掉
        val restored = restoreOverviewFromCache(OverviewCacheCodec.encode(cache), 5000L)
        assertEquals(listOf("还在跑"), restored?.activities?.map { it.title })
    }

    @Test
    fun `新版本写的多余字段被忽略`() {
        // 老版本 app 读到新版本（或未来版本）写进去的缓存时，不认识的直接丢，
        // 认识的字段照常读出来——否则一次升级就等于缓存全废
        val raw = """{"cookieStatus":"OK","stats":{"playtime":60},"futureField":{"a":1}}"""
        val decoded = OverviewCacheCodec.decode(raw)
        assertEquals("OK", decoded?.cookieStatus)
        assertEquals(60L, decoded?.stats?.playtime)
    }
}
