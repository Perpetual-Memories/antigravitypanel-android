package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.remote.ApiException
import com.nzd.antigravitypanel.data.remote.CookieExpiredException
import com.nzd.antigravitypanel.data.remote.IdeJson
import com.nzd.antigravitypanel.data.remote.IdeMethod
import com.nzd.antigravitypanel.data.remote.ProtocolException
import com.nzd.antigravitypanel.data.remote.dto.CollectionListDto
import com.nzd.antigravitypanel.data.remote.dto.ConfigListResponseDto
import com.nzd.antigravitypanel.data.remote.dto.GameDetailDto
import com.nzd.antigravitypanel.data.remote.dto.GameListPageDto
import com.nzd.antigravitypanel.data.remote.dto.MapStatsDto
import com.nzd.antigravitypanel.data.remote.dto.UserStatsDto
import com.nzd.antigravitypanel.data.remote.dto.avatarDecoded
import com.nzd.antigravitypanel.data.remote.dto.nicknameDecoded
import com.nzd.antigravitypanel.data.remote.unwrapIdeResponse
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 拿 2026-09-14 真机抓包的原始数据验证接口层。 */
class IdeResponseTest {

    // ---------------- 拆包 ----------------

    @Test
    fun 正常响应拆到最内层() {
        val root = IdeJson.parseToJsonElement(HarFixtures.RESPONSE_USER_STATS)
        val data = unwrapIdeResponse(root, IdeMethod.UserStats)
        val stats = IdeJson.decodeFromJsonElement<UserStatsDto>(data)
        assertEquals(75008L, stats.playtime)
        assertEquals(1912, stats.huntGameCount)
    }

    @Test
    fun iRet非零视为凭证失效() {
        val root = IdeJson.parseToJsonElement(
            """{"ret":1,"iRet":3001,"sMsg":"not login","jData":{"data":{"code":0,"data":{}}}}"""
        )
        val e = assertFailsWith<CookieExpiredException> {
            unwrapIdeResponse(root, IdeMethod.UserStats)
        }
        assertEquals(3001, e.iRet)
    }

    @Test
    fun code非零视为业务错误() {
        val root = IdeJson.parseToJsonElement(
            """{"iRet":0,"jData":{"data":{"code":500,"msg":"internal","data":null}}}"""
        )
        val e = assertFailsWith<ApiException> {
            unwrapIdeResponse(root, IdeMethod.UserStats)
        }
        assertEquals(500, e.code)
    }

    @Test
    fun iRet与code都为0但data为null时给JsonNull() {
        // 用户未同意数据协议就是这个形状
        val root = IdeJson.parseToJsonElement(
            """{"iRet":0,"jData":{"data":{"code":0,"msg":"ok","data":null}}}"""
        )
        assertEquals(JsonNull, unwrapIdeResponse(root, IdeMethod.GameList))
    }

    @Test
    fun 结构不对时抛协议异常() {
        val root = IdeJson.parseToJsonElement("""{"unexpected":1}""")
        assertFailsWith<ProtocolException> { unwrapIdeResponse(root, IdeMethod.UserStats) }
    }

    // ---------------- 对局列表：空串字段 ----------------

    @Test
    fun 对局列表能解析完整记录() {
        val page = decode<GameListPageDto>(HarFixtures.PAYLOAD_GAME_LIST_P1)
        assertEquals(1, page.page)
        assertEquals(10, page.limit)
        assertEquals(10, page.gameList.size)

        val first = page.gameList.first()
        assertEquals("72075042511372298", first.DsRoomId)
        assertEquals("2026-09-04 22:15:16", first.dtEventTime)
        assertEquals(1235, first.iFinTime)
        assertEquals(19665238L, first.iScore)
        assertEquals(475, first.iKills)
        assertEquals(1, first.iDeaths)
        assertEquals(16, first.iMapId)
        assertTrue(first.iIsWin)
        assertTrue(first.finished)
    }

    @Test
    fun 没打完的对局空串字段不炸且标记未完成() {
        // 先确认原始 JSON 里确实是空串，而不是被反序列化悄悄改写了
        val raw = IdeJson.parseToJsonElement(HarFixtures.PAYLOAD_GAME_LIST_P1)
            .jsonObject["gameList"]!!.jsonArray[1].jsonObject
        assertEquals("", raw["iFinTime"]!!.jsonPrimitive.content)
        assertEquals("", raw["iScore"]!!.jsonPrimitive.content)
        assertEquals("", raw["iIsWin"]!!.jsonPrimitive.content)

        val unfinished = decode<GameListPageDto>(HarFixtures.PAYLOAD_GAME_LIST_P1).gameList[1]
        assertEquals(0, unfinished.iFinTime)
        assertEquals(0L, unfinished.iScore)
        assertFalse(unfinished.iIsWin)
        assertFalse(unfinished.finished)
        // 未完成的局 KDA 仍然有值，别被一起清掉
        assertEquals(1, unfinished.iKills)
        assertEquals(20, unfinished.iDuration)
    }

    // ---------------- 配置下发 ----------------

    @Test
    fun 配置三张表都解析出来() {
        val config = decode<ConfigListResponseDto>(HarFixtures.PAYLOAD_CONFIG_LIST).config
            ?: error("没有 config")
        assertEquals(29, config.mapInfo.size)
        assertEquals(16, config.difficultyInfo.size)
        assertEquals(145, config.huntingFieldPartitionArea.size)

        val map = config.mapInfo["1002"] ?: error("缺少 mapInfo[1002]")
        assertEquals(1002, map.id)
        assertEquals("凯旋之地", map.name)
        assertTrue(map.supportStatistics)

        // 用 supportStatistics 决定是否计入统计，而不是硬编码排除
        assertTrue(config.mapInfo.values.any { it.supportStatistics })
        assertTrue(config.mapInfo.values.any { !it.supportStatistics })

        assertEquals("北欧森林", config.huntingFieldPartitionArea["40014"])
    }

    // ---------------- 详情：URL 编码 ----------------

    @Test
    fun 详情昵称与头像能解码() {
        val detail = decode<GameDetailDto>(HarFixtures.PAYLOAD_GAME_DETAIL)
        assertEquals(4, detail.list.size)

        val member = detail.list.first()
        assertEquals("锋矢之影灬辉灬", member.nicknameDecoded)
        assertTrue(member.avatarDecoded.startsWith("https://thirdwx.qlogo.cn/"))

        // 本人头像编了两层，一层解码会剩下 %3A%2F%2F
        val me = detail.loginUserDetail ?: error("没有 loginUserDetail")
        assertEquals("哦对了徐八分钱", me.nicknameDecoded)
        assertTrue(me.avatarDecoded.startsWith("http"))
        assertFalse(me.avatarDecoded.contains("%3A"))
    }

    @Test
    fun 详情的分区与配装能解析() {
        val detail = decode<GameDetailDto>(HarFixtures.PAYLOAD_GAME_DETAIL)
        val hunting = detail.list.first().huntingDetails ?: error("没有 huntingDetails")
        assertEquals(110952L, hunting.totalCoin)
        assertEquals(2511736L, hunting.damageTotalOnBoss)
        assertEquals(6, hunting.partitionDetails.size)
        assertEquals("40843", hunting.partitionDetails.first().areaId)
        assertEquals(114, hunting.partitionDetails.first().usedTime)

        val weapon = detail.list.first().equipmentScheme.first()
        assertEquals("飓风之龙-心动旋律", weapon.weaponName)
        assertEquals(4, weapon.quality)
        assertTrue(weapon.commonItems.isNotEmpty())
    }

    // ---------------- 地图统计 ----------------

    @Test
    fun 地图统计的mapModeData能解析() {
        val list: List<MapStatsDto> = IdeJson.decodeFromJsonElement(
            ListSerializer(MapStatsDto.serializer()),
            IdeJson.parseToJsonElement(HarFixtures.PAYLOAD_MAP_STATS),
        )
        assertEquals(9, list.size)
        val first = list.first()
        assertEquals(17, first.map_id)
        assertEquals(500, first.total)
        assertEquals(270, first.mapModeData["2004085"])
    }

    // ---------------- 图鉴 ----------------

    @Test
    fun 武器图鉴能解析() {
        val list = decode<CollectionListDto>(HarFixtures.PAYLOAD_COLLECTION_WEAPON)
        assertTrue(list.list.isNotEmpty())
        val first = list.list.first()
        assertEquals("玄凌飞刃", first.displayName)
        assertEquals(20119000001L, first.anyId)
        assertTrue(first.displayIcon.startsWith("https://"))
        assertTrue(first.owned)
    }

    private inline fun <reified T> decode(raw: String): T =
        IdeJson.decodeFromJsonElement(IdeJson.parseToJsonElement(raw))
}
