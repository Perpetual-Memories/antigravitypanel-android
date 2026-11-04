package com.nzd.antigravitypanel.data.supercore

import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 超核活动的本地账本：活动网址、凭证、解析出来的 flow 计划、今日状态、抽奖历史。
 *
 * 单独一个类而不是塞进 ViewModel：这份东西有**两套生命周期**——
 * 计划和状态跟活动走，凭证跟 QQ 号走；以后要支持多期活动时只改这里。
 */
class SupercoreRepository(
    private val store: KeyValueStore,
    private val api: SupercoreApi = SupercoreApi(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // ---------------- 活动网址 ----------------

    suspend fun readUrl(): String = runCatching {
        store.read(StoreKey.SUPERCORE_ACTIVITY_URL)
    }.getOrNull().orEmpty()

    suspend fun saveUrl(url: String) {
        store.write(StoreKey.SUPERCORE_ACTIVITY_URL, url)
    }

    // ---------------- 凭证 ----------------

    suspend fun readCredential(): SupercoreCredential? = runCatching {
        store.read(StoreKey.SUPERCORE_CREDENTIAL_RAW)
    }.getOrNull()?.let { raw ->
        runCatching { parseSupercoreCredential(raw) }.getOrNull()
    }

    suspend fun saveCredential(raw: String): SupercoreCredential {
        val credential = parseSupercoreCredential(raw)
        store.write(StoreKey.SUPERCORE_CREDENTIAL_RAW, credential.raw)
        return credential
    }

    suspend fun clearCredential() {
        runCatching { store.remove(StoreKey.SUPERCORE_CREDENTIAL_RAW) }
        runCatching { store.remove(StoreKey.SUPERCORE_ROLE_KEY) }
        runCatching { store.remove(StoreKey.SUPERCORE_CACHE) }
    }

    // ---------------- flow 计划 ----------------

    /**
     * 缓存里的计划。网址对不上（用户换了一期）或者超过 [MAX_PLAN_AGE_SEC] 就当没有——
     * 活动页偶尔会换按钮位置，第二天重新抓一次比一直用旧坐标稳。
     */
    suspend fun readPlan(
        url: String,
        nowSec: Long = currentEpochSeconds(),
        maxAgeSec: Long = MAX_PLAN_AGE_SEC,
    ): SupercorePlan? {
        val raw = runCatching { store.read(StoreKey.SUPERCORE_PLAN_CACHE) }.getOrNull() ?: return null
        val plan = runCatching { json.decodeFromString<SupercorePlan>(raw) }.getOrNull() ?: return null
        if (plan.url != url) return null
        // 旧版本缓存没有队伍 flow，解出来会让"成团检查"静默跳过
        if (plan.planVersion < SUPERCORE_PLAN_VERSION) return null
        if (plan.fetchedAtSec > 0 && nowSec - plan.fetchedAtSec > maxAgeSec) return null
        return plan
    }

    suspend fun loadPlan(
        url: String,
        nowSec: Long = currentEpochSeconds(),
        force: Boolean = false,
    ): SupercorePlan {
        if (!force) {
            readPlan(url, nowSec)?.takeIf { it.ready }?.let { return it }
        }
        // 抓页面失败时退回**旧缓存**（哪怕过期）：官方换版 / 临时断网时，
        // 上一份 flow 计划通常还能用，总比整块功能直接躺平好
        val stale = readPlan(url, nowSec, maxAgeSec = Long.MAX_VALUE)?.takeIf { it.ready }
        val plan = try {
            api.loadPlan(url, nowSec)
        } catch (t: Throwable) {
            stale?.let { return it }
            throw t
        }
        runCatching { store.write(StoreKey.SUPERCORE_PLAN_CACHE, json.encodeToString(plan)) }
        return plan
    }

    // ---------------- 状态 ----------------

    suspend fun readCachedStatus(): SupercoreStatus? = runCatching {
        store.read(StoreKey.SUPERCORE_CACHE)
    }.getOrNull()?.let { SupercoreCacheCodec.decode(it) }?.toStatus()

    suspend fun writeStatus(status: SupercoreStatus) {
        runCatching {
            store.write(
                StoreKey.SUPERCORE_CACHE,
                SupercoreCacheCodec.encode(status.toCache(currentEpochSeconds())),
            )
        }
    }

    suspend fun clearStatus() {
        runCatching { store.remove(StoreKey.SUPERCORE_CACHE) }
    }

    // ---------------- 角色 ----------------

    suspend fun readRoleKey(): String =
        runCatching { store.read(StoreKey.SUPERCORE_ROLE_KEY) }.getOrNull().orEmpty()

    suspend fun saveRoleKey(key: String) {
        runCatching { store.write(StoreKey.SUPERCORE_ROLE_KEY, key) }
    }

    companion object {
        /** 计划缓存 12 小时。活动页白天不会换版本，一天抓两次已经足够。 */
        const val MAX_PLAN_AGE_SEC = 12 * 3600L
    }
}
