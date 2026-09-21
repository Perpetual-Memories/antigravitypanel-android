package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.imports.parseImportedMatches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 反重力数据面板导出的 `nzm_matches.json` 的解析。
 *
 * 样本取自真实导出文件（2226 条里挑了 3 条改的），字段顺序和缺失情况都照原样保留：
 * 这个文件是外部产物，格式说变就变，靠单测钉住比靠文档靠谱。
 */
class JsonImportTest {

    private val sample = """
        [
          {
            "DsRoomId": 72075042511372298,
            "dtEventTime": "2026-09-04 22:15:16",
            "iMapId": 16,
            "iIsWin": 1,
            "iScore": 19665238,
            "iSubModeType": 6,
            "openid": "936104BF122DFB5E1094CBD6FDBE303A",
            "vOpenID": 137700727749128,
            "iKills": 475,
            "iDeaths": 1,
            "iAssists": 0,
            "iDuration": 1235,
            "iGameMode": 3,
            "iModeType": 134,
            "AreaID": 1,
            "Rank": 1,
            "iFinTime": 1235,
            "dtGameStartTime": "2026-09-04 21:52:29",
            "SeasonId": ""
          },
          {
            "DsRoomId": 72075042511372299,
            "dtEventTime": "2026-09-04 23:01:02",
            "iMapId": 16,
            "iIsWin": 2,
            "iScore": 100,
            "iSubModeType": 6,
            "openid": "936104BF122DFB5E1094CBD6FDBE303A",
            "vOpenID": 137700727749128,
            "iKills": 10,
            "iDeaths": 3,
            "iAssists": 1,
            "iDuration": 600,
            "iGameMode": 3,
            "iModeType": 134,
            "AreaID": 1,
            "Rank": 4,
            "iFinTime": 600,
            "dtGameStartTime": "2026-09-04 22:50:00",
            "SeasonId": ""
          },
          {
            "DsRoomId": 72075042511372300,
            "dtEventTime": "2026-09-05 00:00:00",
            "iMapId": 17,
            "iIsWin": 0,
            "iScore": 0,
            "iSubModeType": 6,
            "openid": "936104BF122DFB5E1094CBD6FDBE303A",
            "vOpenID": 137700727749128,
            "iKills": 2,
            "iDeaths": 1,
            "iAssists": 0,
            "iDuration": 120,
            "iGameMode": 3,
            "iModeType": 134,
            "AreaID": 1,
            "Rank": null,
            "iFinTime": null,
            "dtGameStartTime": "2026-09-04 23:58:00",
            "SeasonId": ""
          }
        ]
    """.trimIndent()

    @Test
    fun 字段映射成实体() {
        val entities = parseImportedMatches(sample, syncedAtSec = 111L)
        assertEquals(3, entities.size)

        val first = entities[0]
        // 数字主键要变成字符串：库里 roomId 是 String，Ui 也直接拿它当导航参数
        assertEquals("72075042511372298", first.roomId)
        assertEquals("936104BF122DFB5E1094CBD6FDBE303A", first.openId)
        assertEquals("1", first.areaId)
        assertEquals(16, first.mapId)
        assertEquals(19665238L, first.score)
        assertEquals(1235, first.finTime)
        assertEquals(1235, first.duration)
        assertTrue(first.isWin)
        assertTrue(first.finished)
        assertEquals(1, first.rankValue)
        assertEquals(111L, first.syncedAtSec)
        assertTrue(first.eventTimeSec > 0, "时间要解析成 epoch 秒，否则排序全沉底")
    }

    @Test
    fun 三态胜负只有胜利算赢() {
        val entities = parseImportedMatches(sample)
        assertFalse(entities[1].isWin, "iIsWin=2 是失败")
        assertTrue(entities[1].finished, "失败也是打完了")
        assertFalse(entities[2].isWin, "iIsWin=0 是未完成")
        assertFalse(entities[2].finished, "iFinTime 为空 = 没打完，不能算进胜率")
        assertEquals(0, entities[2].rankValue, "Rank 为 null 时落 0")
    }

    @Test
    fun 包一层对象也能找到数组() {
        val entities = parseImportedMatches("""{"data": $sample}""")
        assertEquals(3, entities.size)
    }

    @Test
    fun 同文件内重复房间号只留一条() {
        val raw = """[{"DsRoomId": 1, "dtEventTime": "", "iIsWin": 1},""" +
            """ {"DsRoomId": 1, "dtEventTime": "", "iIsWin": 2},""" +
            """ {"DsRoomId": 2, "dtEventTime": "", "iIsWin": 1}]"""
        assertEquals(2, parseImportedMatches(raw).size)
    }

    @Test
    fun 房间号为零的脏数据被丢掉() {
        val raw = """[{"DsRoomId": 0, "iIsWin": 1}, {"DsRoomId": 5, "iIsWin": 1}]"""
        val entities = parseImportedMatches(raw)
        assertEquals(1, entities.size)
        assertEquals("5", entities[0].roomId)
    }

    @Test
    fun 只给了必填字段也能解析() {
        val raw = """[{"DsRoomId": 9}]"""
        val entity = parseImportedMatches(raw).single()
        assertEquals("9", entity.roomId)
        assertEquals(0, entity.mapId)
        assertEquals(0L, entity.eventTimeSec)
        assertFalse(entity.finished)
    }

    @Test
    fun 宽松模式下没加引号的键和字符串也认() {
        // 导出方早期版本手拼过 JSON，键和字符串值都没加引号
        val raw = """[{DsRoomId: 9, openid: abc, iIsWin: 1, iFinTime: 30}]"""
        val entity = parseImportedMatches(raw).single()
        assertEquals("9", entity.roomId)
        assertEquals("abc", entity.openId)
        assertTrue(entity.isWin)
        assertTrue(entity.finished)
    }

    @Test
    fun 不是JSON或没有数组时报错() {
        assertFailsWith<IllegalArgumentException> { parseImportedMatches("这不是 json") }
        assertFailsWith<IllegalArgumentException> {
            parseImportedMatches("""{"code": 0, "msg": "ok"}""")
        }
    }
}
