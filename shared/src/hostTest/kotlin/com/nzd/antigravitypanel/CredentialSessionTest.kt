package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.credential.CookieParseException
import com.nzd.antigravitypanel.data.credential.CredentialSession
import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.settings.UserSettings
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SAMPLE_OPENID = "0A1B2C3D4E5F6G7H8I9J"
private const val SECOND_OPENID = "SECONDACCOUNTOPENID"

private const val SAMPLE_COOKIE =
    "openid=$SAMPLE_OPENID; acctype=qc; appid=1112451898; " +
        "access_token=ABCDEF0123456789; verifysession=xyz"

private const val SECOND_SAMPLE_COOKIE =
    "openid=$SECOND_OPENID; acctype=qc; appid=1112451898; " +
        "access_token=SECONDACCOUNTTOKEN; verifysession=abc"

class CredentialSessionTest {

    private class FakeStore(
        initial: Map<String, String> = emptyMap(),
    ) : KeyValueStore {
        val map = LinkedHashMap<String, String>(initial)
        var reads = 0

        override suspend fun read(key: String): String? {
            reads++
            return map[key]
        }

        override suspend fun write(key: String, value: String) {
            map[key] = value
        }

        override suspend fun remove(key: String) {
            map.remove(key)
        }

        override fun peek(key: String): String? = map[key]
    }

    @Test
    fun 首次启动没有凭证时ready仍为true() = runBlocking {
        val session = CredentialSession(FakeStore())
        assertFalse(session.ready.value, "读之前不该是 ready")

        session.restore()

        assertTrue(session.ready.value)
        assertNull(session.cookie.value)
    }

    @Test
    fun 保存后重新进入能恢复() = runBlocking {
        val store = FakeStore()
        val first = CredentialSession(store)
        first.save(SAMPLE_COOKIE)

        val second = CredentialSession(store)
        second.restore()

        assertEquals("0A1B2C3D4E5F6G7H8I9J", second.cookie.value?.openid)
        // 存的是原文，恢复时按 acctype 重新分派：这条是 QQ 区的，appid 依旧被改写
        assertEquals(NzCookie.REQUIRED_APPID, (second.cookie.value as NzCookie).appid)
    }

    @Test
    fun 两个账号可保存切换并在重启后恢复() = runBlocking {
        val store = FakeStore()
        val session = CredentialSession(store)
        session.save(SAMPLE_COOKIE)
        session.save(SECOND_SAMPLE_COOKIE)

        assertEquals(setOf(SAMPLE_OPENID, SECOND_OPENID), session.accounts.value.map { it.openid }.toSet())
        assertEquals(SECOND_OPENID, session.activeOpenId.value)
        assertEquals(SECOND_SAMPLE_COOKIE, store.map[StoreKey.COOKIE_RAW])

        assertTrue(session.switchTo(SAMPLE_OPENID))
        assertEquals(SAMPLE_OPENID, session.activeOpenId.value)
        assertEquals(SAMPLE_OPENID, session.cookie.value?.openid)
        assertEquals(SAMPLE_COOKIE, store.map[StoreKey.COOKIE_RAW])
        assertEquals(SAMPLE_COOKIE, session.accounts.value.single { it.openid == SAMPLE_OPENID }.cookieRaw)
        assertFalse(session.switchTo("not-exists"))

        val reopened = CredentialSession(store)
        reopened.restore()
        assertEquals(setOf(SAMPLE_OPENID, SECOND_OPENID), reopened.accounts.value.map { it.openid }.toSet())
        assertEquals(SAMPLE_OPENID, reopened.activeOpenId.value)
        assertEquals(SAMPLE_COOKIE, reopened.cookie.value?.raw)
    }

    @Test
    fun 同号保存更新cookie并保留资料() = runBlocking {
        val store = FakeStore()
        val session = CredentialSession(store)
        session.save(SAMPLE_COOKIE)
        session.updateAccountProfile(
            openid = SAMPLE_OPENID,
            nickname = "昵称",
            avatarUrl = "https://example.test/avatar.png",
            seasonLevel = 12,
        )
        val updatedRaw = SAMPLE_COOKIE.replace("ABCDEF0123456789", "NEWTOKEN") + "; custom=keep"

        session.save(updatedRaw)

        val account = session.accounts.value.single()
        assertEquals(updatedRaw, account.cookieRaw)
        assertEquals("昵称", account.nickname)
        assertEquals("https://example.test/avatar.png", account.avatarUrl)
        assertEquals(12, account.seasonLevel)
        assertEquals(updatedRaw, store.map[StoreKey.COOKIE_RAW])
    }

    @Test
    fun 旧单账号cookie会迁移成首个账号() = runBlocking {
        val store = FakeStore(mapOf(StoreKey.COOKIE_RAW to SAMPLE_COOKIE))
        val session = CredentialSession(store)

        session.restore()

        assertEquals(SAMPLE_OPENID, session.accounts.value.single().openid)
        assertEquals(SAMPLE_COOKIE, session.accounts.value.single().cookieRaw)
        assertEquals(SAMPLE_OPENID, session.activeOpenId.value)
        assertEquals(SAMPLE_OPENID, store.map[StoreKey.ACTIVE_ACCOUNT_OPENID])
        assertTrue(store.map.containsKey(StoreKey.ACCOUNTS_V1))

        val reopened = CredentialSession(store)
        reopened.restore()
        assertEquals(1, reopened.accounts.value.size)
        assertEquals(SAMPLE_OPENID, reopened.cookie.value?.openid)
    }

    @Test
    fun 空账号表不会从旧cookie复活账号() = runBlocking {
        val store = FakeStore(
            mapOf(
                StoreKey.ACCOUNTS_V1 to "[]",
                StoreKey.COOKIE_RAW to SAMPLE_COOKIE,
                StoreKey.ACTIVE_ACCOUNT_OPENID to SAMPLE_OPENID,
            )
        )
        val session = CredentialSession(store)

        session.restore()

        assertTrue(session.accounts.value.isEmpty())
        assertNull(session.activeOpenId.value)
        assertNull(session.cookie.value)
        assertEquals("[]", store.map[StoreKey.ACCOUNTS_V1])
    }

    @Test
    fun clear只删除当前账号且不自动切换() = runBlocking {
        val store = FakeStore()
        val session = CredentialSession(store)
        session.save(SAMPLE_COOKIE)
        session.save(SECOND_SAMPLE_COOKIE)

        session.clear()

        assertEquals(listOf(SAMPLE_OPENID), session.accounts.value.map { it.openid })
        assertNull(session.activeOpenId.value)
        assertNull(session.cookie.value)
        assertNull(store.map[StoreKey.ACTIVE_ACCOUNT_OPENID])
        assertNull(store.map[StoreKey.COOKIE_RAW])

        val reopened = CredentialSession(store)
        reopened.restore()
        assertEquals(listOf(SAMPLE_OPENID), reopened.accounts.value.map { it.openid })
        assertNull(reopened.activeOpenId.value)
        assertNull(reopened.cookie.value)
    }

    @Test
    fun 资料更新只修改传入字段并持久化() = runBlocking {
        val store = FakeStore()
        val session = CredentialSession(store)
        session.save(SAMPLE_COOKIE)

        session.updateAccountProfile(SAMPLE_OPENID, nickname = "新昵称")
        var account = session.accounts.value.single()
        assertEquals("新昵称", account.nickname)
        assertEquals("", account.avatarUrl)
        assertNull(account.seasonLevel)

        session.updateAccountProfile(SAMPLE_OPENID, avatarUrl = "avatar", seasonLevel = 8)
        account = session.accounts.value.single()
        assertEquals("新昵称", account.nickname)
        assertEquals("avatar", account.avatarUrl)
        assertEquals(8, account.seasonLevel)

        val reopened = CredentialSession(store)
        reopened.restore()
        assertEquals(account, reopened.accounts.value.single())
    }

    @Test
    fun reload会读取导入后的旧cookie并迁移() = runBlocking {
        val store = FakeStore()
        val session = CredentialSession(store)
        session.restore()
        store.map[StoreKey.COOKIE_RAW] = SAMPLE_COOKIE

        session.reload()

        assertEquals(SAMPLE_OPENID, session.accounts.value.single().openid)
        assertEquals(SAMPLE_OPENID, session.activeOpenId.value)
        assertEquals(SAMPLE_OPENID, session.cookie.value?.openid)
    }

    @Test
    fun 解析失败时不落盘() = runBlocking {
        val store = FakeStore()
        val session = CredentialSession(store)

        assertFailsWith<CookieParseException> { session.save("openid=abc") }

        assertNull(store.map[StoreKey.COOKIE_RAW], "解析失败不该写入存储")
        assertNull(session.cookie.value)
    }

    @Test
    fun 清除后恢复为空() = runBlocking {
        val store = FakeStore()
        val session = CredentialSession(store)
        session.save(SAMPLE_COOKIE)
        session.clear()

        assertNull(session.cookie.value)
        assertNull(store.map[StoreKey.COOKIE_RAW])

        val reopened = CredentialSession(store)
        reopened.restore()
        assertNull(reopened.cookie.value)
    }

    @Test
    fun 存储里的老数据解析不出来时当作没有凭证() = runBlocking {
        // 场景：以后解析规则变严，老用户升级后不该崩，而是重新引导
        val store = FakeStore(mapOf(StoreKey.COOKIE_RAW to "garbage"))
        val session = CredentialSession(store)

        session.restore()

        assertTrue(session.ready.value, "解析失败也要结束引导判定，否则永远卡在加载")
        assertNull(session.cookie.value)
    }

    @Test
    fun 预热后在restore前就能同步确定当前owner() {
        val store = FakeStore(mapOf(StoreKey.COOKIE_RAW to SAMPLE_COOKIE))
        val session = CredentialSession(store)

        assertFalse(session.ready.value)
        assertEquals(SAMPLE_OPENID, session.activeOpenId.value)
        assertEquals(SAMPLE_OPENID, session.cookie.value?.openid)
    }

    @Test
    fun restore只真正读一次存储() = runBlocking {
        val store = FakeStore(mapOf(StoreKey.COOKIE_RAW to SAMPLE_COOKIE))
        val session = CredentialSession(store)

        session.restore()
        val readsAfterFirstRestore = store.reads
        session.restore()
        session.restore()

        assertEquals(readsAfterFirstRestore, store.reads)
    }

    // ---------------- 保留策略 ----------------

    @Test
    fun 保留策略默认是6个月() = runBlocking {
        val settings = UserSettings(FakeStore())
        settings.restore()
        assertEquals(6, settings.retentionMonths.value)
    }

    @Test
    fun 保留策略能持久化() = runBlocking {
        val store = FakeStore()
        UserSettings(store).setRetentionMonths(UserSettings.UNLIMITED)

        val reopened = UserSettings(store)
        reopened.restore()
        assertEquals(0, reopened.retentionMonths.value)
    }

    @Test
    fun 保留策略遇到越界值回退默认() = runBlocking {
        // 存储被改坏、或选项列表以后调整过
        val store = FakeStore(mapOf(StoreKey.RETENTION_MONTHS to "999"))
        val settings = UserSettings(store)
        settings.restore()
        assertEquals(UserSettings.DEFAULT_RETENTION_MONTHS, settings.retentionMonths.value)
    }

    @Test
    fun 无上限显示为无上限文案() {
        assertEquals("无上限", UserSettings.labelOf(UserSettings.UNLIMITED))
        assertEquals("3 个月", UserSettings.labelOf(3))
    }
}
