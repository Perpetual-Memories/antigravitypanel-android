package com.nzd.antigravitypanel.data.store

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nzd.antigravitypanel.requireContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [KeyValueStore] 的 Android 实现：EncryptedSharedPreferences。
 *
 * 值是 AES256-GCM 加密的，key 也加密（这是它比普通 SharedPreferences 慢的原因，
 * 但我们一秒读写不了几次）。
 *
 * 初始化要走磁盘（生成/读取 MasterKey），所以第一次拿到实例可能耗时几十毫秒，
 * 用 [Mutex] 串行化懒加载，避免并发时创建两遍。
 */
private const val PREF_FILE_NAME = "antigravity_secure"

private val lock = Mutex()
private val blockingLock = Any()

@Volatile
private var prefs: SharedPreferences? = null

private fun buildPreferences(): SharedPreferences {
    val context = requireContext()
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        PREF_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}

private suspend fun preferences(): SharedPreferences = prefs ?: lock.withLock {
    prefs ?: withContext(Dispatchers.IO) { buildPreferences().also { prefs = it } }
}

/**
 * 预热加密存储：把实例建好，之后 [KeyValueStore.peek] 才能同步读到东西。
 *
 * 必须在 `setContent` **之前**调用（`MainActivity.onCreate` 里），也就是启动画面
 * 还盖着的时候——这一下要读磁盘（MasterKey + 解密整个 prefs 文件），
 * 放进合成阶段就会卡住第一帧。
 *
 * 失败只是退化成"缓存读不到"，概览会走回异步那条路，不会崩，所以吞掉异常。
 */
fun primeKeyValueStore() {
    if (prefs != null) return
    synchronized(blockingLock) {
        if (prefs == null) prefs = runCatching { buildPreferences() }.getOrNull()
    }
}

actual fun createKeyValueStore(): KeyValueStore = AndroidKeyValueStore()

private class AndroidKeyValueStore : KeyValueStore {
    /**
     * 同步读。没预热过（[primeKeyValueStore] 还没跑或失败了）就返回 null——
     * 不能在这里临时去建实例，那会把磁盘 I/O 搬进合成阶段。
     */
    override fun peek(key: String): String? {
        val target = prefs ?: return null
        return runCatching { target.getString(key, null) }.getOrNull()
    }

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        preferences().getString(key, null)
    }

    override suspend fun write(key: String, value: String) {
        withContext(Dispatchers.IO) {
            preferences().edit().putString(key, value).apply()
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            preferences().edit().remove(key).apply()
        }
    }
}
