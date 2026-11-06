package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.store.AccountScopedKeyValueStore
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.data.store.accountScopedKey
import com.nzd.antigravitypanel.data.store.copyLegacyAccountData
import com.nzd.antigravitypanel.data.store.migrateLegacyAccountDataOnce
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountScopedKeyValueStoreTest {
    private class FakeStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
        val values = LinkedHashMap<String, String>(initial)

        override suspend fun read(key: String): String? = values[key]
        override suspend fun write(key: String, value: String) {
            values[key] = value
        }
        override suspend fun remove(key: String) {
            values.remove(key)
        }
        override fun peek(key: String): String? = values[key]
    }

    @Test
    fun 同键在两个账号间隔离而设备设置共享() = runBlocking {
        val raw = FakeStore()
        val a = AccountScopedKeyValueStore(raw, "account-a")
        val b = AccountScopedKeyValueStore(raw, "account-b")

        a.write(StoreKey.OVERVIEW_CACHE, "overview-a")
        b.write(StoreKey.OVERVIEW_CACHE, "overview-b")
        a.write(StoreKey.TOP_BAR_ACCOUNT, "0")

        assertEquals("overview-a", a.read(StoreKey.OVERVIEW_CACHE))
        assertEquals("overview-b", b.read(StoreKey.OVERVIEW_CACHE))
        assertEquals("0", b.read(StoreKey.TOP_BAR_ACCOUNT))
        assertEquals("0", raw.values[StoreKey.TOP_BAR_ACCOUNT])
    }

    @Test
    fun 旧缓存只认领一次且不会落到第二个账号() = runBlocking {
        val raw = FakeStore(
            mapOf(
                StoreKey.OVERVIEW_CACHE to "legacy-overview",
                StoreKey.MAP_STATS_CACHE to "legacy-map",
            ),
        )

        migrateLegacyAccountDataOnce(raw, "account-a")
        migrateLegacyAccountDataOnce(raw, "account-b")

        assertEquals("legacy-overview", raw.values[accountScopedKey("account-a", StoreKey.OVERVIEW_CACHE)])
        assertEquals("legacy-map", raw.values[accountScopedKey("account-a", StoreKey.MAP_STATS_CACHE)])
        assertNull(raw.values[accountScopedKey("account-b", StoreKey.OVERVIEW_CACHE)])
        assertNull(raw.values[accountScopedKey("account-b", StoreKey.MAP_STATS_CACHE)])
    }

    @Test
    fun 空机旧包导入后显式认领且不覆盖新缓存() = runBlocking {
        val raw = FakeStore(mapOf(StoreKey.SIGNIN_CACHE to "from-backup"))
        val imported = AccountScopedKeyValueStore(raw, null)
        imported.write(StoreKey.OVERVIEW_CACHE, "from-backup")

        copyLegacyAccountData(raw, "account-a")
        copyLegacyAccountData(raw, "account-a")

        assertEquals("from-backup", raw.values[accountScopedKey("account-a", StoreKey.SIGNIN_CACHE)])
        assertEquals("from-backup", raw.values[accountScopedKey("account-a", StoreKey.OVERVIEW_CACHE)])

        raw.values[accountScopedKey("account-a", StoreKey.OVERVIEW_CACHE)] = "newer"
        copyLegacyAccountData(raw, "account-a")
        assertEquals("newer", raw.values[accountScopedKey("account-a", StoreKey.OVERVIEW_CACHE)])
    }
}
