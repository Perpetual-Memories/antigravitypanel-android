package com.nzd.antigravitypanel.data.credential

import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 本机保存的一个小程序账号；分区在展示时由 [cookieRaw] 重新解析派生。 */
@Serializable
data class SavedAccount(
    val openid: String,
    val cookieRaw: String,
    val nickname: String = "",
    val avatarUrl: String = "",
    val seasonLevel: Int? = null,
    val lastActiveSec: Long = 0,
)

private val AccountJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * 当前登录态。整个 app 只有一个实例，UI 直接观察 [cookie] / [ready]。
 *
 * 存的是用户粘贴的**原文**，不是解析后的字段——解析规则以后还会变（2026-09 就比
 * 早期源码多了 verifysession，2026-10 又加了微信区），存原文才能让老数据在新规则下
 * 重新解释。微信区的 cookie 尤其依赖这一点：它的鉴权字段是一族 `ieg_ams_*`，
 * 现在还没定死哪个才是服务端认的，原样存着才好换规则重试。
 */
class CredentialSession(
    private val store: KeyValueStore,
) {
    private val _cookie = MutableStateFlow<MiniProgramCredential?>(null)
    val cookie: StateFlow<MiniProgramCredential?> = _cookie.asStateFlow()

    private val _accounts = MutableStateFlow<List<SavedAccount>>(emptyList())
    val accounts: StateFlow<List<SavedAccount>> = _accounts.asStateFlow()

    private val _activeOpenId = MutableStateFlow<String?>(null)
    val activeOpenId: StateFlow<String?> = _activeOpenId.asStateFlow()

    /** 是否已完成首次读取。UI 要等它变成 true 才能决定弹不弹引导。 */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        // 存储已在 setContent 前预热。同步确定当前 owner，供各页面在合成阶段读取正确账号的缓存；
        // ready 仍保持 false，完整账号表与损坏数据处理交给 restore()。
        val raw = runCatching { store.peek(StoreKey.COOKIE_RAW) }.getOrNull()
        val parsed = raw?.let { parseCookieOrNull(it) }
        val active = runCatching { store.peek(StoreKey.ACTIVE_ACCOUNT_OPENID) }.getOrNull()
        val legacy = runCatching { store.peek(StoreKey.ACCOUNTS_V1) }.getOrNull() == null
        if (parsed != null && (parsed.openid == active || (active == null && legacy))) {
            _activeOpenId.value = parsed.openid
            _cookie.value = parsed
        }
    }

    /** 从存储恢复。重复调用无副作用（进程重建后 UI 会再走一次）。 */
    suspend fun restore() {
        if (_ready.value) return
        reload()
    }

    /**
     * 无视"已经读过"的守卫，**重新**从存储读一次。
     *
     * 只给「导入数据」用：那一刻存储里的账号表或旧 cookie 已经被数据包换掉了，而 [restore]
     * 上的守卫是为了冷启动只读一次 —— 不绕过它的话，新账号要等杀进程重开才生效。
     */
    suspend fun reload() {
        val rawAccounts = store.read(StoreKey.ACCOUNTS_V1)
        if (rawAccounts == null) {
            migrateLegacyCookie()
            _ready.value = true
            return
        }

        // 键存在就代表迁移已经完成；即使内容损坏或是空数组，也绝不从旧 cookie 复活账号。
        val decoded = runCatching {
            AccountJson.decodeFromString<List<SavedAccount>>(rawAccounts)
        }.getOrDefault(emptyList())
        val accounts = decoded.normalized()
        val activeOpenId = store.read(StoreKey.ACTIVE_ACCOUNT_OPENID)
        val active = accounts.firstOrNull { it.openid == activeOpenId }

        _accounts.value = accounts
        _activeOpenId.value = active?.openid
        _cookie.value = active?.let { parseCookieOrNull(it.cookieRaw) }
        _ready.value = true
    }

    /**
     * 解析并保存。解析失败**不落盘**，直接把异常抛给 UI 显示错误文案。
     * 同号保存只换 cookie 和活跃时间，已经填写的资料保持不变。
     *
     * @throws CookieParseException cookie 缺字段或格式不对
     */
    suspend fun save(raw: String): MiniProgramCredential {
        val parsed = parseNzCookie(raw)
        ensureLoaded()

        val previous = _accounts.value.firstOrNull { it.openid == parsed.openid }
        val account = previous?.copy(
            cookieRaw = raw,
            lastActiveSec = currentEpochSeconds(),
        ) ?: SavedAccount(
            openid = parsed.openid,
            cookieRaw = raw,
            lastActiveSec = currentEpochSeconds(),
        )
        val updated = (_accounts.value.filterNot { it.openid == parsed.openid } + account).normalized()

        store.write(StoreKey.ACCOUNTS_V1, AccountJson.encodeToString(updated))
        store.write(StoreKey.ACTIVE_ACCOUNT_OPENID, parsed.openid)
        store.write(StoreKey.COOKIE_RAW, raw)
        _accounts.value = updated
        _activeOpenId.value = parsed.openid
        _cookie.value = parsed
        _ready.value = true
        return parsed
    }

    /** 切换到已保存账号，并恢复该账号当初保存的完整 cookie 原文。 */
    suspend fun switchTo(openid: String): Boolean {
        ensureLoaded()
        val account = _accounts.value.firstOrNull { it.openid == openid } ?: return false
        val parsed = parseCookieOrNull(account.cookieRaw) ?: return false
        val switched = account.copy(lastActiveSec = currentEpochSeconds())
        val updated = (_accounts.value.filterNot { it.openid == openid } + switched).normalized()

        store.write(StoreKey.ACCOUNTS_V1, AccountJson.encodeToString(updated))
        store.write(StoreKey.ACTIVE_ACCOUNT_OPENID, openid)
        store.write(StoreKey.COOKIE_RAW, account.cookieRaw)
        _accounts.value = updated
        _activeOpenId.value = openid
        _cookie.value = parsed
        return true
    }

    /** 更新已保存资料；传 null 的参数保持原值，账号不存在时不做任何事情。 */
    suspend fun updateAccountProfile(
        openid: String,
        nickname: String? = null,
        avatarUrl: String? = null,
        seasonLevel: Int? = null,
    ) {
        ensureLoaded()
        val account = _accounts.value.firstOrNull { it.openid == openid } ?: return
        val updatedAccount = account.copy(
            nickname = nickname ?: account.nickname,
            avatarUrl = avatarUrl ?: account.avatarUrl,
            seasonLevel = seasonLevel ?: account.seasonLevel,
        )
        val updated = _accounts.value.map { if (it.openid == openid) updatedAccount else it }
        store.write(StoreKey.ACCOUNTS_V1, AccountJson.encodeToString(updated))
        _accounts.value = updated
    }

    /** 退出只删除当前账号；其他账号保留，且不会自动切换。 */
    suspend fun clear() {
        ensureLoaded()
        val activeOpenId = _activeOpenId.value
        if (activeOpenId != null) {
            val updated = _accounts.value.filterNot { it.openid == activeOpenId }
            store.write(StoreKey.ACCOUNTS_V1, AccountJson.encodeToString(updated))
            _accounts.value = updated
        }
        store.remove(StoreKey.ACTIVE_ACCOUNT_OPENID)
        store.remove(StoreKey.COOKIE_RAW)
        _activeOpenId.value = null
        _cookie.value = null
    }

    private suspend fun ensureLoaded() {
        if (!_ready.value) reload()
    }

    /** 只有账号表键不存在时才允许走这里；先写账号表，成功后再写当前账号。 */
    private suspend fun migrateLegacyCookie() {
        val raw = store.read(StoreKey.COOKIE_RAW)
        val parsed = raw?.let { parseCookieOrNull(it) }
        if (raw == null || parsed == null) {
            _accounts.value = emptyList()
            _activeOpenId.value = null
            _cookie.value = null
            return
        }

        val account = SavedAccount(
            openid = parsed.openid,
            cookieRaw = raw,
            lastActiveSec = currentEpochSeconds(),
        )
        val accounts = listOf(account)
        store.write(StoreKey.ACCOUNTS_V1, AccountJson.encodeToString(accounts))
        store.write(StoreKey.ACTIVE_ACCOUNT_OPENID, account.openid)
        store.write(StoreKey.COOKIE_RAW, raw)
        _accounts.value = accounts
        _activeOpenId.value = account.openid
        _cookie.value = parsed
    }

    private fun parseCookieOrNull(raw: String): MiniProgramCredential? =
        runCatching { parseNzCookie(raw) }.getOrNull()

    private fun List<SavedAccount>.normalized(): List<SavedAccount> =
        distinctBy { it.openid }.sortedByDescending { it.lastActiveSec }
}
