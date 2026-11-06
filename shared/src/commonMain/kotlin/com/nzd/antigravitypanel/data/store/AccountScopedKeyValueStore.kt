package com.nzd.antigravitypanel.data.store

/**
 * 某个固定账号的 KV 视图。owner 在构造时固定，不会在旧请求迟到时意外写到新账号。
 *
 * 应用设置与注册表仍走原键；账号资料、凭证和缓存进入 `account:v1:<openid>:` 命名空间。
 * 没有当前账号时保留旧的未归属读写路径，供空机导入旧 JSON / 备份使用。
 */
class AccountScopedKeyValueStore(
    private val delegate: KeyValueStore,
    private val ownerOpenId: String?,
) : KeyValueStore {
    override suspend fun read(key: String): String? = delegate.read(resolve(key))

    override suspend fun write(key: String, value: String) = delegate.write(resolve(key), value)

    override suspend fun remove(key: String) = delegate.remove(resolve(key))

    override fun peek(key: String): String? = delegate.peek(resolve(key))

    private fun resolve(key: String): String = when {
        key in GLOBAL_KEYS -> key
        ownerOpenId.isNullOrBlank() -> key
        else -> accountScopedKey(ownerOpenId, key)
    }

    companion object {
        /** 设备级设置、账号注册表及跨账号一致的 UI 偏好。 */
        private val GLOBAL_KEYS = setOf(
            StoreKey.COOKIE_RAW,
            StoreKey.ACCOUNTS_V1,
            StoreKey.ACTIVE_ACCOUNT_OPENID,
            StoreKey.ACCOUNT_CACHE_MIGRATED,
            StoreKey.RETENTION_MONTHS,
            StoreKey.COLOR_MODE,
            StoreKey.AUTO_REFRESH_MINUTES,
            StoreKey.PREDICTIVE_BACK_TRANSLATION,
            StoreKey.SIGNIN_AUTO,
            StoreKey.WELFARE_TASK_AUTO_CLAIM,
            StoreKey.QQ_GIFT_AUTO_CLAIM,
            StoreKey.XINYUE_AUTO_CLAIM,
            StoreKey.SUPERCORE_AUTO,
            StoreKey.MAP_SEASON_FILTER_HUNT,
            StoreKey.MAP_SEASON_FILTER_TOWER,
            StoreKey.MAP_HIDE_COMPLETED,
            StoreKey.TOP_BAR_ACCOUNT,
            StoreKey.WIKI_PROXY_MODE,
            StoreKey.COMPOSITE_SCORE,
            StoreKey.SCORE_WAN_TARGETS,
        )
    }
}

internal fun accountScopedKey(ownerOpenId: String, key: String): String =
    "account:v1:$ownerOpenId:$key"

/**
 * 升级前没有账号命名空间的键。只含本组件会接管的值，不含设备级设置和注册表。
 */
private val LEGACY_ACCOUNT_KEYS = listOf(
    StoreKey.PINNED_ROOMS,
    StoreKey.FAVORITE_ROOMS,
    StoreKey.JSON_IMPORT_SUMMARY,
    StoreKey.BUILD_PLAN,
    StoreKey.OVERVIEW_CACHE,
    StoreKey.SIGNIN_CACHE,
    StoreKey.SIGNIN_AUTO_MARK,
    StoreKey.WELFARE_TASK_AUTO_MARK,
    StoreKey.MAP_STATS_CACHE,
    StoreKey.MAP_DROPS_CACHE,
    StoreKey.QQ_COOKIE_RAW,
    StoreKey.QQ_GIFT_CACHE,
    StoreKey.QQ_GIFT_AUTO_MARK,
    StoreKey.XINYUE_CREDENTIAL_RAW,
    StoreKey.XINYUE_CARD_CACHE,
    StoreKey.XINYUE_AUTO_MARK,
    StoreKey.SUPERCORE_ACTIVITY_URL,
    StoreKey.SUPERCORE_CREDENTIAL_RAW,
    StoreKey.SUPERCORE_PLAN_CACHE,
    StoreKey.SUPERCORE_CACHE,
    StoreKey.SUPERCORE_DRAW_HISTORY,
    StoreKey.SUPERCORE_ROLE_KEY,
)

/**
 * 首次升级时把旧全局数据交给当时的当前账号。标记存在后不再复制，避免新账号拿到旧号缓存。
 * 旧键保留作只读备份；目标键已存在时不覆盖较新的账号数据。
 */
suspend fun migrateLegacyAccountDataOnce(store: KeyValueStore, ownerOpenId: String?) {
    if (ownerOpenId.isNullOrBlank()) return
    if (store.read(StoreKey.ACCOUNT_CACHE_MIGRATED) == "1") return
    copyLegacyAccountData(store, ownerOpenId)
    store.write(StoreKey.ACCOUNT_CACHE_MIGRATED, "1")
}

/** 空机导入旧备份后显式认领其中的账号缓存；不修改首次升级标记。 */
suspend fun copyLegacyAccountData(store: KeyValueStore, ownerOpenId: String) {
    if (ownerOpenId.isBlank()) return
    LEGACY_ACCOUNT_KEYS.forEach { key ->
        val legacy = store.read(key) ?: return@forEach
        val target = accountScopedKey(ownerOpenId, key)
        if (store.read(target) == null) store.write(target, legacy)
    }
}
