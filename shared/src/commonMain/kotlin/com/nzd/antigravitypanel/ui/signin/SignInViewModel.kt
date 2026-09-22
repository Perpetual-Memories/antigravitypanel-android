package com.nzd.antigravitypanel.ui.signin

import com.nzd.antigravitypanel.data.credential.NzCookie
import com.nzd.antigravitypanel.data.remote.NzApi
import com.nzd.antigravitypanel.data.signin.SignInCacheCodec
import com.nzd.antigravitypanel.data.signin.SignInOutcome
import com.nzd.antigravitypanel.data.signin.SignInRepository
import com.nzd.antigravitypanel.data.signin.SignInStatus
import com.nzd.antigravitypanel.data.signin.toStatus
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.ui.home.describeSyncError
import com.nzd.antigravitypanel.util.currentEpochSeconds
import com.nzd.antigravitypanel.util.serverDateKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SignInUiState(
    val status: SignInStatus = SignInStatus(),
    /**
     * 手上有一份能看的数字（本次拉到的，或冷启动缓存）。
     *
     * 和 `status.date.isEmpty()` 不是一回事：没登录时 status 是全零，
     * 但"全零"和"没拿到"在 UI 上是两种完全不同的话，不能用前者去推后者。
     */
    val available: Boolean = false,
    val loading: Boolean = false,
    /** 正在打签到接口（而不是只拉看板）。按钮要转圈。 */
    val signing: Boolean = false,
    val error: String? = null,
)

/**
 * 签到页 / 概览签到卡的状态。
 *
 * **凭证就绪时自动补签**（`refresh` 里做完），每天只试一次——
 * 不开后台定时任务：Android 的后台管理越来越严，没有自启动权限时周期任务基本不生效，
 * 而"打开 app 顺手签一下"这件事本来就发生在用户会用这个 app 的时刻，漏签概率可接受。
 */
class SignInViewModel(
    api: NzApi,
    private val store: KeyValueStore,
    /**
     * 冷启动时**同步**读到的缓存原文（宿主在 `setContent` 之前预热好存储后取的）。
     * 走 suspend 的话第一帧早就画完了，签到卡会先闪一帧"需要登录"。
     */
    cachedSeed: String? = null,
) {
    private val repository = SignInRepository(api, store)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val seeded = cachedSeed?.let { SignInCacheCodec.decode(it) }
        ?.toStatus(serverDateKey(currentEpochSeconds()))

    private val _state = MutableStateFlow(
        seeded?.let { SignInUiState(status = it, available = true) } ?: SignInUiState(),
    )
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    /** 同步那一路没读到时的兜底。仍然要排在首刷之前。 */
    suspend fun restoreCached() {
        if (seeded != null) return
        val cache = repository.readCached() ?: return
        _state.value = SignInUiState(
            status = cache.toStatus(serverDateKey(currentEpochSeconds())),
            available = true,
        )
    }

    /**
     * 有凭证就拉看板，并且**今天还没自动签过就顺手签到**。
     * 没凭证就把卡片收起（清掉缓存），不要留着上一个号的签到状态。
     */
    fun refresh(cookie: NzCookie?) {
        if (cookie == null) {
            _state.value = SignInUiState()
            scope.launch { repository.clearCache() }
            return
        }
        scope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val today = serverDateKey(currentEpochSeconds())
            val mark = "$today|${cookie.openid}"
            val autoSign = repository.autoMark() != mark

            try {
                val outcome = if (autoSign) repository.sign(cookie) else null
                if (outcome != null) {
                    applyOutcome(outcome)
                    // 只在真的走完这一轮才记标记：失败了下次开 app 还要再试一次
                    repository.markAutoDone(mark)
                } else {
                    // 今天已经自动试过了：只刷看板，不再打签到接口
                    applyOutcome(SignInOutcome.AlreadySigned(repository.load(cookie)))
                }
            } catch (e: Throwable) {
                // 拉失败要留着上一份：一次抖动把卡片打回"需要登录"比慢更难受。
                // 也不写去重标记，下次开 app 还有机会自动签到。
                _state.value = _state.value.copy(
                    loading = false,
                    signing = false,
                    error = describeSyncError(e),
                )
            }
        }
    }

    /** 手动签到。二级页那个按钮走这里，忽略"今天已自动试过"的标记。 */
    fun signNow(cookie: NzCookie?) {
        val active = cookie ?: return
        scope.launch {
            _state.value = _state.value.copy(signing = true, error = null)
            try {
                val outcome = repository.sign(active)
                applyOutcome(outcome)
                repository.markAutoDone("${serverDateKey(currentEpochSeconds())}|${active.openid}")
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    signing = false,
                    error = describeSyncError(e),
                )
            }
        }
    }

    private suspend fun applyOutcome(outcome: SignInOutcome) {
        _state.value = _state.value.copy(
            status = outcome.status,
            available = true,
            loading = false,
            signing = false,
            error = null,
        )
        repository.writeCache(outcome.status)
    }

    /**
     * 概览的下拉刷新也要带上这里：顶栏那个刷新按钮是"整页重拉"，
     * 只重刷概览却让签到卡继续显示旧数字，看着像刷新没生效。
     */
    fun refreshBoardOnly(cookie: NzCookie?) {
        if (cookie == null) return
        scope.launch {
            runCatching { repository.load(cookie) }
                .onSuccess { status ->
                    _state.value = _state.value.copy(
                        status = status,
                        available = true,
                        error = null,
                    )
                    repository.writeCache(status)
                }
        }
    }

    /** 登出：缓存和去重标记一起清，换号登录时才不会跳过自动签到。 */
    fun clear() {
        _state.value = SignInUiState()
        scope.launch { repository.clearCache() }
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        /** 宿主在 `setContent` 之前预热存储后，同步读缓存用的键。 */
        const val CACHE_KEY = StoreKey.SIGNIN_CACHE
    }
}
