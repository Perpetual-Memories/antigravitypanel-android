package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.db.CachedEquipment
import com.nzd.antigravitypanel.data.db.CachedItem
import com.nzd.antigravitypanel.data.db.CachedMatchDetail
import com.nzd.antigravitypanel.data.db.MatchBossStatEntity
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.db.cachedDetailOf
import com.nzd.antigravitypanel.data.db.decodeCachedDetail
import com.nzd.antigravitypanel.data.db.encodeCachedDetail
import com.nzd.antigravitypanel.data.export.MatchExportFormat
import com.nzd.antigravitypanel.data.export.csvCell
import com.nzd.antigravitypanel.data.export.exportEntries
import com.nzd.antigravitypanel.data.export.matchExportFileName
import com.nzd.antigravitypanel.data.export.resultOf
import com.nzd.antigravitypanel.data.export.toCsv
import com.nzd.antigravitypanel.data.export.toJson
import com.nzd.antigravitypanel.data.remote.dto.GameDetailDto
import com.nzd.antigravitypanel.data.remote.dto.HuntingDetailsDto
import com.nzd.antigravitypanel.data.remote.dto.PartitionDetailDto
import com.nzd.antigravitypanel.data.remote.dto.PlayerDetailDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 导出历史战绩。
 *
 * 守的是三条最容易改坏的规则：
 * - 列表那半段必须完整（击杀 / 死亡 / 助攻 / 积分 / 排名 / 时长 / 地图 / 胜负）；
 * - 没缓存过详情的局，后半段**必须留空**，不能拿 0 充数（0 和"没数据"在统计里是两回事）；
 * - 导出内容里不能出现中文。
 */
class MatchExportTest {

    private fun match(
        roomId: String = "12345678901234567",
        isWin: Boolean = true,
        finished: Boolean = true,
        mapId: Int = 17,
    ) = MatchEntity(
        roomId = roomId,
        openId = "openid-1",
        eventTime = "2026-09-04 22:15:16",
        eventTimeSec = 1780000000L,
        startTime = "2026-09-04 21:50:00",
        areaId = "1",
        finTime = if (finished) 1500 else 0,
        duration = 1500,
        isWin = isWin,
        gameMode = 1,
        modeType = 1,
        subModeType = 6,
        mapId = mapId,
        score = 123456L,
        seasonId = "4",
        kills = 30,
        deaths = 2,
        assists = 5,
        rankValue = 1,
        finished = finished,
        syncedAtSec = 0L,
    )

    private fun cached(payload: CachedMatchDetail) = MatchBossStatEntity(
        roomId = "12345678901234567",
        selfBossDamage = payload.bossDamage,
        teamBossDamage = payload.teamBossDamage,
        rivalBossDamage = payload.rivalBossDamage,
        updatedAtSec = 1L,
        payloadJson = encodeCachedDetail(payload),
    )

    @Test
    fun 胜负三态() {
        assertEquals("WIN", resultOf(match(isWin = true)))
        assertEquals("LOSS", resultOf(match(isWin = false)))
        // 没打完不是输：胜率统计要把这批排除掉，混进 LOSS 会把胜率算低
        assertEquals("UNFINISHED", resultOf(match(isWin = false, finished = false)))
    }

    @Test
    fun 列表字段进CSV() {
        val csv = toCsv(exportEntries(listOf(match()), emptyMap()))
        val header = csv.lines().first()
        val row = csv.lines()[1].split(",")
        for (column in listOf("kills", "deaths", "assists", "score", "rank", "duration_sec", "map_id", "result")) {
            assertTrue(header.contains(column), "表头缺少 $column")
        }
        val index = header.split(",").indexOf("result")
        assertEquals("WIN", row[index])
        assertEquals("HUNT", row[header.split(",").indexOf("mode")])
    }

    @Test
    fun 没有详情缓存的局后半段留空() {
        val csv = toCsv(exportEntries(listOf(match()), emptyMap()))
        val rows = csv.lines()
        val header = rows.first().split(",")
        val cells = rows[1].split(",")
        for (column in listOf("detail_coin", "detail_boss_damage", "detail_weapon_ids")) {
            assertEquals("", cells[header.indexOf(column)], "$column 应该留空")
        }
    }

    @Test
    fun 有详情缓存的局补齐单局数据() {
        val payload = CachedMatchDetail(
            playerCount = 4,
            coin = 88000L,
            bossDamage = 1200000L,
            mobDamage = 300000L,
            teamBossDamage = 4000000L,
            rivalBossDamage = 900000L,
            partitionCount = 3,
            partitionTotalSec = 420,
            equipment = listOf(
                CachedEquipment(
                    weaponId = 1001L,
                    weaponQuality = 3,
                    plugins = listOf(CachedItem(2001L, 2), CachedItem(2002L, 1)),
                ),
            ),
        )
        val entries = exportEntries(listOf(match()), mapOf("12345678901234567" to cached(payload)))
        val detail = entries.single().detail
        assertEquals(4, detail?.playerCount)
        assertEquals(88000L, detail?.coin)
        assertEquals(1200000L, detail?.bossDamage)
        assertEquals("1001", detail?.weaponIds)
        assertEquals("3", detail?.weaponQualities)
        assertEquals("2001|2002", detail?.pluginIds)
        assertEquals("2|1", detail?.pluginQualities)
    }

    @Test
    fun CSV转义逗号和引号() {
        assertEquals("abc", csvCell("abc"))
        assertEquals("\"a,b\"", csvCell("a,b"))
        assertEquals("\"a\"\"b\"", csvCell("a\"b"))
    }

    @Test
    fun 导出内容不含中文() {
        val entries = exportEntries(
            listOf(match()),
            mapOf("12345678901234567" to cached(CachedMatchDetail(playerCount = 4))),
        )
        val all = toCsv(entries) + toJson(entries)
        assertTrue(all.none { it in '一'..'鿿' }, "导出内容里出现了中文")
    }

    @Test
    fun JSON里没有详情的局是null() {
        val entries = exportEntries(listOf(match()), emptyMap())
        assertTrue(toJson(entries).contains("\"detail\": null"))
        assertNull(entries.single().detail)
    }

    @Test
    fun 文件名带服务端日期和扩展名() {
        // 1780000000 = 2026-05-29 14:26:40 北京时间
        assertEquals("matches_20260529.csv", matchExportFileName(MatchExportFormat.CSV, 1780000000L))
        assertEquals("matches_20260529.json", matchExportFileName(MatchExportFormat.JSON, 1780000000L))
    }

    @Test
    fun 详情缺自己时不写缓存() {
        val detail = GameDetailDto(
            loginUserDetail = null,
            list = listOf(PlayerDetailDto(nickname = "a")),
        )
        assertNull(cachedDetailOf(detail))
        assertNull(cachedDetailOf(GameDetailDto(loginUserDetail = PlayerDetailDto(), list = emptyList())))
    }

    @Test
    fun 分区用时合计与全队口径() {
        val self = PlayerDetailDto(
            baseDetail = com.nzd.antigravitypanel.data.remote.dto.GameRecordDto(vOpenID = "me"),
            huntingDetails = HuntingDetailsDto(
                totalCoin = 9000L,
                damageTotalOnBoss = 500L,
                damageTotalOnMobs = 700L,
                partitionDetails = listOf(PartitionDetailDto("40101", 120), PartitionDetailDto("40104", 180)),
            ),
        )
        val mate = PlayerDetailDto(
            baseDetail = com.nzd.antigravitypanel.data.remote.dto.GameRecordDto(vOpenID = "mate"),
            huntingDetails = HuntingDetailsDto(damageTotalOnBoss = 300L),
        )
        val payload = cachedDetailOf(
            GameDetailDto(loginUserDetail = self, list = listOf(self, mate)),
        )
        assertEquals(2, payload?.playerCount)
        assertEquals(800L, payload?.teamBossDamage)
        assertEquals(300L, payload?.rivalBossDamage)
        assertEquals(2, payload?.partitionCount)
        assertEquals(300, payload?.partitionTotalSec)
    }

    @Test
    fun 老版本缓存行解不出来就当没缓存() {
        assertNull(decodeCachedDetail(null))
        assertNull(decodeCachedDetail(""))
        assertNull(decodeCachedDetail("not json"))
        // 老数据里缺字段要靠默认值补，不能整条失败
        assertEquals(0, decodeCachedDetail("{\"playerCount\":3}")?.bossDamage)
    }
}
