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

private const val SAMPLE_COOKIE =
    "openid=0A1B2C3D4E5F6G7H8I9J; acctype=qc; appid=1112451898; " +
        "access_token=ABCDEF0123456789; verifysession=xyz"

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
    fun restore只真正读一次存储() = runBlocking {
        val store = FakeStore(mapOf(StoreKey.COOKIE_RAW to SAMPLE_COOKIE))
        val session = CredentialSession(store)

        session.restore()
        session.restore()
        session.restore()

        assertEquals(1, store.reads)
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
