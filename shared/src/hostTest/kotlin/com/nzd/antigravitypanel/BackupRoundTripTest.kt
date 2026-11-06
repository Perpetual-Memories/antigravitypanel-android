package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.backup.BACKUP_FORMAT_VERSION
import com.nzd.antigravitypanel.data.backup.BackupExporter
import com.nzd.antigravitypanel.data.backup.BackupImporter
import com.nzd.antigravitypanel.data.backup.backupFileName
import com.nzd.antigravitypanel.data.backup.looksLikeZip
import com.nzd.antigravitypanel.data.backup.packZip
import com.nzd.antigravitypanel.data.backup.unpackZip
import com.nzd.antigravitypanel.data.db.ConfigCacheDao
import com.nzd.antigravitypanel.data.db.ConfigCacheEntity
import com.nzd.antigravitypanel.data.db.MatchBossStatDao
import com.nzd.antigravitypanel.data.db.MatchBossStatEntity
import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.db.MatchKnownRow
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 数据包（导出数据 / 导入数据）。
 *
 * 守的规则：
 * - 打出来再读回来，对局 / 详情 / 凭证 / 设置 / 配置一样不少；
 * - 导入只认白名单键 —— 包里被人塞了别的键不能写进存储；
 * - 空凭证不覆盖本机已有的（否则"导入一份没凭证的包"就等于退出登录）；
 * - 不是本应用 / 版本太新 / 不是 zip，都要报错而不是默默成功。
 */
private const val OWNER_A = "owner-a"
private const val OWNER_B = "owner-b"

class BackupRoundTripTest {

    private fun match(roomId: String = "1001", ownerOpenId: String = OWNER_A) = MatchEntity(
        roomId = roomId,
        openId = "openid-1",
        eventTime = "2026-09-04 22:15:16",
        eventTimeSec = 1780000000L,
        startTime = "2026-09-04 21:50:00",
        areaId = "1",
        finTime = 1500,
        duration = 1500,
        isWin = true,
        gameMode = 1,
        modeType = 1,
        subModeType = 6,
        mapId = 17,
        score = 123456L,
        seasonId = "4",
        kills = 30,
        deaths = 2,
        assists = 5,
        rankValue = 1,
        finished = true,
        syncedAtSec = 111L,
        ownerOpenId = ownerOpenId,
    )

    private fun detail(roomId: String = "1001", ownerOpenId: String = OWNER_A) = MatchBossStatEntity(
        roomId = roomId,
        selfBossDamage = 500L,
        teamBossDamage = 800L,
        rivalBossDamage = 300L,
        updatedAtSec = 999L,
        payloadJson = "{\"playerCount\":4}",
        ownerOpenId = ownerOpenId,
    )

    private class FakeStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
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

    private class FakeMatchDao : MatchDao {
        val rows = LinkedHashMap<Pair<String, String>, MatchEntity>()
        override suspend fun upsertAll(entities: List<MatchEntity>) {
            entities.forEach { rows[it.ownerOpenId to it.roomId] = it }
        }

        override suspend fun knownStates(ownerOpenId: String, roomIds: List<String>): List<MatchKnownRow> =
            roomIds.mapNotNull { rows[ownerOpenId to it] }.map { MatchKnownRow(it.roomId, it.finished) }

        override suspend fun find(ownerOpenId: String, roomId: String): MatchEntity? = rows[ownerOpenId to roomId]
        override suspend fun previousOf(
            ownerOpenId: String,
            mapId: Int,
            subModeType: Int,
            beforeSec: Long,
        ): MatchEntity? = null
        override suspend fun page(ownerOpenId: String, limit: Int, offset: Int): List<MatchEntity> =
            rows.values.filter { it.ownerOpenId == ownerOpenId }.drop(offset).take(limit)

        override fun observeAll(ownerOpenId: String): Flow<List<MatchEntity>> =
            flowOf(rows.values.filter { it.ownerOpenId == ownerOpenId })
        override suspend fun all(ownerOpenId: String): List<MatchEntity> =
            rows.values.filter { it.ownerOpenId == ownerOpenId }
        override fun observeCount(ownerOpenId: String): Flow<Long> =
            flowOf(rows.values.count { it.ownerOpenId == ownerOpenId }.toLong())
        override suspend fun count(ownerOpenId: String): Long =
            rows.values.count { it.ownerOpenId == ownerOpenId }.toLong()
        override suspend fun oldestTimeSec(ownerOpenId: String): Long? = null
        override suspend fun newestTimeSec(ownerOpenId: String): Long? = null
        override suspend fun deleteOlderThan(ownerOpenId: String, cutoffSec: Long): Int = 0
        override suspend fun clear(ownerOpenId: String) {
            rows.keys.filter { it.first == ownerOpenId }.forEach { rows.remove(it) }
        }
        override suspend fun claimLegacyMatches(ownerOpenId: String): Int = 0
        override suspend fun claimLegacyBossStats(ownerOpenId: String): Int = 0
        override suspend fun deleteUnclaimedMatches(): Int = 0
        override suspend fun deleteUnclaimedBossStats(): Int = 0
    }

    private class FakeBossStatDao : MatchBossStatDao {
        val rows = LinkedHashMap<Pair<String, String>, MatchBossStatEntity>()
        override suspend fun upsert(item: MatchBossStatEntity) {
            rows[item.ownerOpenId to item.roomId] = item
        }

        override suspend fun upsertAll(items: List<MatchBossStatEntity>) {
            items.forEach { rows[it.ownerOpenId to it.roomId] = it }
        }

        override fun observeAll(ownerOpenId: String): Flow<List<MatchBossStatEntity>> =
            flowOf(rows.values.filter { it.ownerOpenId == ownerOpenId })
        override suspend fun all(ownerOpenId: String): List<MatchBossStatEntity> =
            rows.values.filter { it.ownerOpenId == ownerOpenId }
        override suspend fun knownRoomIds(ownerOpenId: String): List<String> =
            rows.values.filter { it.ownerOpenId == ownerOpenId }.map { it.roomId }
        override suspend fun deleteOrphans(ownerOpenId: String): Int = 0
        override suspend fun clear(ownerOpenId: String) {
            rows.keys.filter { it.first == ownerOpenId }.forEach { rows.remove(it) }
        }
    }

    private class FakeConfigDao : ConfigCacheDao {
        val rows = LinkedHashMap<String, ConfigCacheEntity>()
        override suspend fun get(key: String): ConfigCacheEntity? = rows[key]
        override suspend fun put(entity: ConfigCacheEntity) {
            rows[entity.cacheKey] = entity
        }

        override suspend fun remove(key: String) {
            rows.remove(key)
        }

        override suspend fun clear() = rows.clear()
        override suspend fun all(): List<ConfigCacheEntity> = rows.values.toList()
    }

    private class Fixture(
        val store: FakeStore = FakeStore(),
        val matches: FakeMatchDao = FakeMatchDao(),
        val details: FakeBossStatDao = FakeBossStatDao(),
        val config: FakeConfigDao = FakeConfigDao(),
    ) {
        val exporter = BackupExporter(store, matches, details, config)
        val importer = BackupImporter(store, matches, details, config)
    }

    @Test
    fun 导出再导入对局详情凭证设置配置都在() = runBlocking {
        val source = Fixture(
            store = FakeStore(
                mapOf(
                    StoreKey.COOKIE_RAW to "openid=abc; access_token=xyz",
                    StoreKey.QQ_COOKIE_RAW to "uin=123; p_skey=skey",
                    StoreKey.SUPERCORE_ACTIVITY_URL to "https://act.example/2026",
                    StoreKey.PINNED_ROOMS to "1001,1002",
                    StoreKey.SCORE_WAN_TARGETS to "",
                    StoreKey.OVERVIEW_CACHE to "{\"x\":1}",
                ),
            ),
        )
        source.matches.rows[OWNER_A to "1001"] = match()
        source.details.rows[OWNER_A to "1001"] = detail()
        source.config.rows["game_config"] = ConfigCacheEntity("game_config", "{\"map\":[]}", 5L)

        val file = source.exporter.build(nowSec = 1780000000L, appVersion = "0.2.4", ownerOpenId = OWNER_A)
        assertTrue(file.includesCredentials)
        assertEquals(1, file.matchCount)
        assertEquals(1, file.detailCount)
        assertEquals("nzm_backup_20260529.zip", file.fileName)

        val target = Fixture()
        val result = target.importer.import(file.bytes, OWNER_A)

        assertEquals(BACKUP_FORMAT_VERSION, result.formatVersion)
        assertEquals(1, result.matchCount)
        assertEquals(1, result.detailCount)
        assertEquals(3, result.credentialCount)
        // PINNED_ROOMS + SCORE_WAN_TARGETS（空串也算一项）
        assertEquals(2, result.settingCount)
        assertEquals(1, result.configCount)

        assertEquals(match(), target.matches.rows[OWNER_A to "1001"])
        assertEquals(detail(), target.details.rows[OWNER_A to "1001"])
        assertEquals("{\"map\":[]}", target.config.rows["game_config"]?.json)
        assertEquals("openid=abc; access_token=xyz", target.store.map[StoreKey.COOKIE_RAW])
        assertEquals("1001,1002", target.store.map[StoreKey.PINNED_ROOMS])
        // 空串是有含义的（三个页面都显示完整数字），不能被当成"没值"丢掉
        assertEquals("", target.store.map[StoreKey.SCORE_WAN_TARGETS])
        assertEquals("{\"x\":1}", target.store.map[StoreKey.OVERVIEW_CACHE])
    }

    @Test
    fun 同roomId双账号导出隔离且旧包导入当前owner() = runBlocking {
        val source = Fixture()
        source.matches.rows[OWNER_A to "shared"] = match("shared", OWNER_A).copy(score = 100)
        source.matches.rows[OWNER_B to "shared"] = match("shared", OWNER_B).copy(score = 200)
        source.details.rows[OWNER_A to "shared"] = detail("shared", OWNER_A).copy(selfBossDamage = 10)
        source.details.rows[OWNER_B to "shared"] = detail("shared", OWNER_B).copy(selfBossDamage = 20)

        val file = source.exporter.build(nowSec = 1780000000L, ownerOpenId = OWNER_A)
        assertEquals(1, file.matchCount)
        assertEquals(1, file.detailCount)

        val target = Fixture()
        target.importer.import(file.bytes, OWNER_B)
        assertEquals(setOf(OWNER_B to "shared"), target.matches.rows.keys)
        assertEquals(setOf(OWNER_B to "shared"), target.details.rows.keys)
        assertEquals(100L, target.matches.rows.getValue(OWNER_B to "shared").score)
        assertEquals(10L, target.details.rows.getValue(OWNER_B to "shared").selfBossDamage)
        assertEquals("{\"playerCount\":4}", target.details.rows.getValue(OWNER_B to "shared").payloadJson)
    }

    @Test
    fun 白名单之外的键不写() = runBlocking {
        val source = Fixture(store = FakeStore(mapOf(StoreKey.COOKIE_RAW to "c")))
        val file = source.exporter.build(nowSec = 1780000000L)

        // 手工改包：往设置里塞一个一次性标记（那是本机的运行态，不该跨设备搬）
        val tampered = tamperKeyValues(
            bytes = file.bytes,
            entry = "settings.json",
            json = "{\"entries\":{\"${StoreKey.WIKI_PROXY_MODE}\":\"1\",\"evil\":\"x\"}}",
        )

        val target = Fixture()
        val result = target.importer.import(tampered)

        assertEquals(0, result.settingCount)
        assertNull(target.store.map[StoreKey.WIKI_PROXY_MODE])
        assertNull(target.store.map["evil"])
    }

    @Test
    fun 空凭证不覆盖本机已有的() = runBlocking {
        val source = Fixture()
        val file = source.exporter.build(nowSec = 1780000000L)

        val tampered = tamperKeyValues(
            bytes = file.bytes,
            entry = "credentials.json",
            json = "{\"entries\":{\"${StoreKey.COOKIE_RAW}\":\"\"}}",
        )

        val target = Fixture(store = FakeStore(mapOf(StoreKey.COOKIE_RAW to "本机原来的")))
        val result = target.importer.import(tampered)

        assertEquals(0, result.credentialCount)
        assertEquals("本机原来的", target.store.map[StoreKey.COOKIE_RAW])
    }

    @Test
    fun 不是本应用的包要报错() = runBlocking {
        val source = Fixture()
        val file = source.exporter.build(nowSec = 1780000000L)
        val tampered = tamperKeyValues(
            bytes = file.bytes,
            entry = "manifest.json",
            json = "{\"app\":\"other-app\",\"formatVersion\":1}",
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            Fixture().importer.import(tampered)
        }
        assertTrue(failure.message.orEmpty().contains("反重力数据面板"))
    }

    @Test
    fun 格式版本太新要报错() = runBlocking {
        val source = Fixture()
        val file = source.exporter.build(nowSec = 1780000000L)
        val tampered = tamperKeyValues(
            bytes = file.bytes,
            entry = "manifest.json",
            json = "{\"app\":\"antigravity-panel\",\"formatVersion\":99}",
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            Fixture().importer.import(tampered)
        }
        assertTrue(failure.message.orEmpty().contains("升级"))
    }

    @Test
    fun 不是zip直接报错() = runBlocking {
        assertFalse(looksLikeZip("{\"data\":[]}".encodeToByteArray()))
        assertFailsWith<IllegalArgumentException> {
            Fixture().importer.import("{\"data\":[]}".encodeToByteArray())
        }
        Unit
    }

    @Test
    fun zip往返保留字节() {
        val entries = listOf(
            "a.json" to "{\"中文\":1}".encodeToByteArray(),
            "b.bin" to byteArrayOf(0, 1, 2, 3, -1),
        )
        val packed = packZip(entries.map { (name, bytes) ->
            com.nzd.antigravitypanel.data.backup.ZipOutEntry(name, bytes)
        })
        assertTrue(looksLikeZip(packed))

        val unpacked = unpackZip(packed)
        assertTrue(entries[0].second.contentEquals(unpacked["a.json"]))
        assertTrue(entries[1].second.contentEquals(unpacked["b.bin"]))
    }

    /** 解包 → 换掉其中一个文件 → 重新打包，模拟"用户手改过的包"。 */
    private fun tamperKeyValues(bytes: ByteArray, entry: String, json: String): ByteArray {
        val files = unpackZip(bytes).toMutableMap()
        files[entry] = json.encodeToByteArray()
        return packZip(
            files.map { (name, content) ->
                com.nzd.antigravitypanel.data.backup.ZipOutEntry(name, content)
            },
        )
    }
}
