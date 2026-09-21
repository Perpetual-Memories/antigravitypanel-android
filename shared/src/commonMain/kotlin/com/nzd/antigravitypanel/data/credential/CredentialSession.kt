package com.nzd.antigravitypanel.data.credential

import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前登录态。整个 app 只有一个实例，UI 直接观察 [cookie] / [ready]。
 *
 * 存的是用户粘贴的**原文**，不是解析后的字段——解析规则以后还会变（2026-09 就比
 * 早期源码多了 verifysession），存原文才能让老数据在新规则下重新解释。
 */
class CredentialSession(
    private val store: KeyValueStore,
) {
    private val _cookie = MutableStateFlow<NzCookie?>(null)
    val cookie: StateFlow<NzCookie?> = _cookie.asStateFlow()

    /** 是否已完成首次读取。UI 要等它变成 true 才能决定弹不弹引导。 */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** 从存储恢复。重复调用无副作用（进程重建后 UI 会再走一次）。 */
    suspend fun restore() {
        if (_ready.value) return
        val raw = store.read(StoreKey.COOKIE_RAW)
        // 老数据在当前解析规则下可能解析失败，失败就当没有凭证，让用户重新填
        _cookie.value = raw?.let { runCatching { parseNzCookie(it) }.getOrNull() }
        _ready.value = true
    }

    /**
     * 解析并保存。解析失败**不落盘**，直接把异常抛给 UI 显示错误文案。
     *
     * @throws CookieParseException cookie 缺字段或格式不对
     */
    suspend fun save(raw: String): NzCookie {
        val parsed = parseNzCookie(raw)
        store.write(StoreKey.COOKIE_RAW, raw)
        _cookie.value = parsed
        _ready.value = true
        return parsed
    }

    suspend fun clear() {
        store.remove(StoreKey.COOKIE_RAW)
        _cookie.value = null
    }
}
