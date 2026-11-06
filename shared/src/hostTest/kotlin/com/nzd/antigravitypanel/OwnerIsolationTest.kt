package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.db.MatchBossStatDao
import com.nzd.antigravitypanel.data.db.MatchBossStatEntity
import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.db.MatchKnownRow
import com.nzd.antigravitypanel.data.db.toEntity
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.remote.dto.GameRecordDto
import com.nzd.antigravitypanel.data.repo.MatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 不依赖 Android Context 的 owner 契约测试；真正的 Room 迁移仍需设备侧验证。 */
class OwnerIsolationTest {

    @Test
    fun 同roomId双账号共存且DAO读写清理互不影响() = runBlocking {
        val details = FakeBossDao()
        val matches = FakeMatchDao(details)
        val old = match("old", "owner-a", score = 10, eventTime = "2026-09-01 10:00:00")
        val current = match("shared", "owner-a", score = 100)
        val otherAccount = match("shared", "owner-b", score = 200)
        matches.upsertAll(listOf(old, current, otherAccount))

        assertEquals(2L, matches.count("owner-a"))
        assertEquals(1L, matches.count("owner-b"))
        assertEquals(100L, matches.find("owner-a", "shared")?.score)
        assertEquals(200L, matches.find("owner-b", "shared")?.score)
        assertEquals(
            listOf(MatchKnownRow("shared", true)),
            matches.knownStates("owner-a", listOf("shared", "missing")),
        )
        assertEquals(old.roomId, matches.previousOf("owner-a", 0, 0, current.eventTimeSec)?.roomId)
        assertEquals(listOf("shared", "old"), matches.page("owner-a", 10, 0).map { it.roomId })
        assertEquals(listOf(200L), matches.all("owner-b").map { it.score })
        assertEquals(listOf(100L, 10L), matches.observeAll("owner-a").first().map { it.score })
        assertEquals(1L, matches.observeCount("owner-b").first())
        assertEquals(old.eventTimeSec, matches.oldestTimeSec("owner-a"))
        assertEquals(current.eventTimeSec, matches.newestTimeSec("owner-a"))

        details.upsertAll(
            listOf(
                detail("shared", "owner-a", damage = 10),
                detail("shared", "owner-b", damage = 20),
            ),
        )
        assertEquals(listOf(10L), details.all("owner-a").map { it.selfBossDamage })
        assertEquals(listOf(20L), details.observeAll("owner-b").first().map { it.selfBossDamage })
        assertEquals(listOf("shared"), details.knownRoomIds("owner-a"))

        assertEquals(1, matches.deleteOlderThan("owner-a", old.eventTimeSec + 1))
        assertEquals(1L, matches.count("owner-a"))
        assertEquals(1L, matches.count("owner-b"))
        details.clear("owner-b")
        assertEquals(1, details.all("owner-a").size)
        assertEquals(0, details.all("owner-b").size)
        matches.clear("owner-b")
        assertNull(matches.find("owner-b", "shared"))
        assertEquals(100L, matches.find("owner-a", "shared")?.score)
    }

    @Test
    fun 旧owner认领保留冲突行且重复调用幂等() = runBlocking {
        val details = FakeBossDao()
        val matches = FakeMatchDao(details)
        matches.upsertAll(
            listOf(
                match("shared", "", score = 10),
                match("legacy-only", "", score = 30),
                match("shared", "owner-a", score = 100),
                match("shared", "owner-b", score = 200),
            ),
        )
        details.upsertAll(
            listOf(
                detail("shared", "", damage = 1, payload = "legacy-shared"),
                detail("legacy-only", "", damage = 3, payload = "legacy-payload"),
                detail("shared", "owner-a", damage = 100, payload = "current"),
                detail("shared", "owner-b", damage = 200, payload = "other"),
            ),
        )
        val repository = MatchRepository(NzApi(), matches)

        repository.claimLegacyData("owner-a")
        repository.claimLegacyData("owner-a")

        assertTrue(matches.rows.keys.none { it.first.isEmpty() })
        assertTrue(details.rows.keys.none { it.first.isEmpty() })
        assertEquals(100L, matches.find("owner-a", "shared")?.score)
        assertEquals(200L, matches.find("owner-b", "shared")?.score)
        assertEquals(30L, matches.find("owner-a", "legacy-only")?.score)
        assertEquals("current", details.rows.getValue("owner-a" to "shared").payloadJson)
        assertEquals("other", details.rows.getValue("owner-b" to "shared").payloadJson)
        assertEquals("legacy-payload", details.rows.getValue("owner-a" to "legacy-only").payloadJson)
        assertEquals(3, matches.rows.size)
        assertEquals(3, details.rows.size)
    }

    private fun match(
        roomId: String,
        ownerOpenId: String,
        score: Long,
        eventTime: String = "2026-09-04 22:15:16",
    ): MatchEntity = GameRecordDto(
        DsRoomId = roomId,
        dtEventTime = eventTime,
        iScore = score,
        iFinTime = 1,
        vOpenID = "dto-id-not-owner",
    ).toEntity(ownerOpenId = ownerOpenId)

    private fun detail(
        roomId: String,
        ownerOpenId: String,
        damage: Long,
        payload: String? = null,
    ) = MatchBossStatEntity(
        roomId = roomId,
        selfBossDamage = damage,
        teamBossDamage = damage * 2,
        payloadJson = payload,
        ownerOpenId = ownerOpenId,
    )

    private class FakeBossDao : MatchBossStatDao {
        val rows = LinkedHashMap<Pair<String, String>, MatchBossStatEntity>()

        override suspend fun upsert(item: MatchBossStatEntity) {
            rows[item.ownerOpenId to item.roomId] = item
        }

        override suspend fun upsertAll(items: List<MatchBossStatEntity>) = items.forEach { upsert(it) }

        override fun observeAll(ownerOpenId: String): Flow<List<MatchBossStatEntity>> =
            flowOf(rows.values.filter { it.ownerOpenId == ownerOpenId })

        override suspend fun all(ownerOpenId: String): List<MatchBossStatEntity> =
            rows.values.filter { it.ownerOpenId == ownerOpenId }

        override suspend fun knownRoomIds(ownerOpenId: String): List<String> =
            all(ownerOpenId).map { it.roomId }

        override suspend fun deleteOrphans(ownerOpenId: String): Int = 0

        override suspend fun clear(ownerOpenId: String) {
            rows.keys.filter { it.first == ownerOpenId }.forEach { rows.remove(it) }
        }

        fun claimLegacy(ownerOpenId: String): Int {
            val legacy = rows.filterKeys { it.first.isEmpty() }
            var claimed = 0
            for ((key, row) in legacy) {
                val target = ownerOpenId to row.roomId
                if (target in rows) continue
                rows.remove(key)
                rows[target] = row.copy(ownerOpenId = ownerOpenId)
                claimed++
            }
            return claimed
        }

        fun deleteLegacy(): Int {
            val keys = rows.keys.filter { it.first.isEmpty() }
            keys.forEach { rows.remove(it) }
            return keys.size
        }
    }

    private class FakeMatchDao(
        private val bossDao: FakeBossDao,
    ) : MatchDao {
        val rows = LinkedHashMap<Pair<String, String>, MatchEntity>()

        override suspend fun upsertAll(entities: List<MatchEntity>) {
            entities.forEach { rows[it.ownerOpenId to it.roomId] = it }
        }

        override suspend fun knownStates(ownerOpenId: String, roomIds: List<String>): List<MatchKnownRow> =
            roomIds.mapNotNull { id ->
                rows[ownerOpenId to id]?.let { MatchKnownRow(id, it.finished) }
            }

        override suspend fun find(ownerOpenId: String, roomId: String): MatchEntity? = rows[ownerOpenId to roomId]

        override suspend fun previousOf(
            ownerOpenId: String,
            mapId: Int,
            subModeType: Int,
            beforeSec: Long,
        ): MatchEntity? = rows.values
            .filter {
                it.ownerOpenId == ownerOpenId && it.mapId == mapId && it.subModeType == subModeType &&
                    it.eventTimeSec > 0 && it.eventTimeSec < beforeSec
            }
            .maxWithOrNull(compareBy<MatchEntity> { it.eventTimeSec }.thenBy { it.roomId })

        override suspend fun page(ownerOpenId: String, limit: Int, offset: Int): List<MatchEntity> =
            rows.values.filter { it.ownerOpenId == ownerOpenId }
                .sortedWith(compareByDescending<MatchEntity> { it.eventTimeSec }.thenByDescending { it.roomId })
                .drop(offset).take(limit)

        override fun observeAll(ownerOpenId: String): Flow<List<MatchEntity>> =
            flowOf(rows.values.filter { it.ownerOpenId == ownerOpenId }
                .sortedWith(compareByDescending<MatchEntity> { it.eventTimeSec }.thenByDescending { it.roomId }))

        override suspend fun all(ownerOpenId: String): List<MatchEntity> = page(ownerOpenId, Int.MAX_VALUE, 0)

        override fun observeCount(ownerOpenId: String): Flow<Long> = flowOf(rows.values.count { it.ownerOpenId == ownerOpenId }.toLong())

        override suspend fun count(ownerOpenId: String): Long = rows.values.count { it.ownerOpenId == ownerOpenId }.toLong()

        override suspend fun oldestTimeSec(ownerOpenId: String): Long? =
            rows.values.filter { it.ownerOpenId == ownerOpenId }.minOfOrNull { it.eventTimeSec }

        override suspend fun newestTimeSec(ownerOpenId: String): Long? =
            rows.values.filter { it.ownerOpenId == ownerOpenId }.maxOfOrNull { it.eventTimeSec }

        override suspend fun deleteOlderThan(ownerOpenId: String, cutoffSec: Long): Int {
            val keys = rows.filter { (key, row) ->
                key.first == ownerOpenId && row.eventTimeSec > 0 && row.eventTimeSec < cutoffSec
            }.keys
            keys.forEach { rows.remove(it) }
            return keys.size
        }

        override suspend fun clear(ownerOpenId: String) {
            rows.keys.filter { it.first == ownerOpenId }.forEach { rows.remove(it) }
        }

        override suspend fun claimLegacyMatches(ownerOpenId: String): Int {
            val legacy = rows.filterKeys { it.first.isEmpty() }
            var claimed = 0
            for ((key, row) in legacy) {
                val target = ownerOpenId to row.roomId
                if (target in rows) continue
                rows.remove(key)
                rows[target] = row.copy(ownerOpenId = ownerOpenId)
                claimed++
            }
            return claimed
        }

        override suspend fun claimLegacyBossStats(ownerOpenId: String): Int = bossDao.claimLegacy(ownerOpenId)

        override suspend fun deleteUnclaimedMatches(): Int {
            val keys = rows.keys.filter { it.first.isEmpty() }
            keys.forEach { rows.remove(it) }
            return keys.size
        }

        override suspend fun deleteUnclaimedBossStats(): Int = bossDao.deleteLegacy()
    }
}
