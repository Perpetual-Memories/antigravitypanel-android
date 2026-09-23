package com.nzd.antigravitypanel.ui.home

import com.nzd.antigravitypanel.data.credential.MiniProgramCredential
import com.nzd.antigravitypanel.data.remote.ApiException
import com.nzd.antigravitypanel.data.remote.CookieExpiredException
import com.nzd.antigravitypanel.data.remote.MissingCredentialException
import com.nzd.antigravitypanel.data.remote.NzException
import com.nzd.antigravitypanel.data.remote.ProtocolException
import com.nzd.antigravitypanel.data.repo.MatchRepository
import com.nzd.antigravitypanel.data.repo.SyncResult
import com.nzd.antigravitypanel.data.settings.UserSettings
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 战绩页的状态持有者。
 *
 * 没用 androidx.lifecycle.ViewModel：commonMain 里拿不到，而且 CMP 下自己管 scope 更省事。
 * 代价是要在组合退出时显式调 [close]。
 */
class HomeViewModel(
    private val repository: MatchRepository,
    private val settings: UserSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val matchCount: StateFlow<Long> = repository.observeCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 最新一局的 roomId，用于"打开最新一局"入口。 */
    val latestRoomId: StateFlow<String?> = repository.observeAll()
        .map { it.firstOrNull()?.roomId }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastResult = MutableStateFlow<SyncResult?>(null)
    val lastResult: StateFlow<SyncResult?> = _lastResult.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun refresh(cookie: MiniProgramCredential?) {
        if (cookie == null || _syncing.value) return
        scope.launch {
            _syncing.value = true
            _error.value = null
            try {
                _lastResult.value = repository.sync(
                    cookie = cookie,
                    nowSec = currentEpochSeconds(),
                    retentionMonths = settings.retentionMonths.value,
                )
            } catch (e: Throwable) {
                _error.value = describeSyncError(e)
            } finally {
                _syncing.value = false
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}

/**
 * 把异常翻译成人话。
 *
 * 服务端会在凭证过期时以 `iRet != 0` 返回，那种情况必须明确引导重新抓 cookie——
 * 只显示"请求失败"的话，用户会以为 app 坏了。
 */
internal fun describeSyncError(e: Throwable): String = when (e) {
    is CookieExpiredException -> "凭证已失效（iRet=${e.iRet}），请重新抓取 Cookie"
    is MissingCredentialException -> "还没有凭证，先填入 Cookie"
    is ApiException -> "接口返回错误 ${e.code}：${e.msg}"
    is ProtocolException -> "接口结构变了，可能官方升级了：${e.message}"
    is NzException -> e.message ?: "请求失败"
    else -> e.message?.takeIf { it.isNotBlank() } ?: "网络请求失败，检查一下网络"
}
