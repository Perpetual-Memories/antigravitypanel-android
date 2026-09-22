package com.nzd.antigravitypanel.ui.signin

import com.nzd.antigravitypanel.data.qq.QqApiException
import com.nzd.antigravitypanel.data.qq.QqClaimOutcome
import com.nzd.antigravitypanel.data.qq.QqCredential
import com.nzd.antigravitypanel.data.qq.QqCredentialException
import com.nzd.antigravitypanel.data.qq.QqGameCenterApi
import com.nzd.antigravitypanel.data.qq.QqGiftCacheCodec
import com.nzd.antigravitypanel.data.qq.QqGiftRepository
import com.nzd.antigravitypanel.data.qq.QqNoBoundRoleException
import com.nzd.antigravitypanel.data.qq.QqProtocolException
import com.nzd.antigravitypanel.data.qq.QqRequestException
import com.nzd.antigravitypanel.data.qq.QqWeeklySignIn
import com.nzd.antigravitypanel.data.qq.toStatus
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
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

data class QqGiftUiState(
    val status: QqWeeklySignIn = QqWeeklySignIn(),
    /** 有没有填过 QQ 凭证。没填时整块 UI 是"去绑定"的引导，不是错误。 */
    val bound: Boolean = false,
    /** 手上有一份能看的数字（本次拉到的或缓存）。 */
    val available: Boolean = false,
    val loading: Boolean = false,
    /** 正在打领取接口。 */
    val claiming: Boolean = false,
    val error: String? = null,
    /** 领取后的结果提示，显示完由 UI 调 [QqGiftViewModel.consumeNotice] 清掉。 */
    val notice: String? = null,
    /**
     * 已保存的凭证原文，弹层里回显用。
     *
     * 单独存一份而不是让 UI 再去读存储：弹层要"打开即显示当前值"，
     * 走 suspend 的话第一帧是空的，用户会以为凭证丢了。
     */
    val credentialRaw: String = "",
)

/**
 * 游戏中心周签到礼包。
 *
 * 和小程序签到是**两套独立的东西**：另一张凭证（QQ 登录 cookie）、另一个域名、
 * 另一套接口。所以单独一个 ViewModel，不塞进 `SignInViewModel`——
 * 混在一起会让"没绑 QQ"和"没填小程序 cookie"这两种状态互相污染。
 */
class QqGiftViewModel(
    private val store: KeyValueStore,
    api: QqGameCenterApi = QqGameCenterApi(),
    /**
     * 冷启动同步读到的缓存原文。走 suspend 的话第一帧早就画完了，
     * 概览那块会先闪一帧"未绑定"。
     */
    cachedSeed: String? = null,
) {
    private val repository = QqGiftRepository(api, store)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val seeded = cachedSeed?.let { QqGiftCacheCodec.decode(it) }?.toStatus()

    private val _state = MutableStateFlow(
        seeded?.let { QqGiftUiState(status = it, available = true) } ?: QqGiftUiState(),
    )
    val state: StateFlow<QqGiftUiState> = _state.asStateFlow()

    /** 同步那一路没读到时的兜底。仍然要排在首刷之前。 */
    suspend fun restore() {
        if (seeded != null && _state.value.bound) return
        val credential = repository.readCredential()
        if (seeded != null) {
            _state.value = _state.value.copy(
                bound = credential != null,
                credentialRaw = credential?.raw.orEmpty(),
            )
            return
        }
        val cache = repository.readCached()
        _state.value = QqGiftUiState(
            status = cache?.toStatus() ?: QqWeeklySignIn(),
            bound = credential != null,
            available = cache != null,
            credentialRaw = credential?.raw.orEmpty(),
        )
    }

    /**
     * 拉状态。开了自动领取就顺手领——但要先看去重标记，同一天只试一次。
     *
     * 没绑凭证时什么都不做（也不报错）：这块是实验性功能，不该在没配置的时候刷存在感。
     */
    fun refresh(autoClaim: Boolean) {
        scope.launch {
            val credential = repository.readCredential()
            if (credential == null) {
                _state.value = _state.value.copy(bound = false, loading = false, error = null)
                return@launch
            }
            _state.value = _state.value.copy(bound = true, loading = true, error = null)

            val today = serverDateKey(currentEpochSeconds())
            val mark = "$today|${credential.uin}"
            val shouldClaim = autoClaim && repository.autoMark() != mark

            try {
                val outcome = if (shouldClaim) repository.claim(credential) else null
                if (outcome != null) {
                    apply(outcome.status)
                    // 只在真跑完这一轮才记标记：失败了下次开 app 还能再试
                    repository.markAutoDone(mark)
                    _state.value = _state.value.copy(
                        notice = if (outcome is QqClaimOutcome.Claimed) {
                            "已领取周签到礼包：${outcome.status.todayReward.ifBlank { "奖励" }}"
                        } else {
                            null
                        },
                    )
                } else {
                    apply(repository.load(credential))
                }
            } catch (e: Throwable) {
                // 拉失败要留着上一份：一次抖动把这块打回"未绑定"比慢更难受
                _state.value = _state.value.copy(
                    loading = false,
                    claiming = false,
                    error = describeQqError(e),
                )
            }
        }
    }

    /** 手动领取。忽略自动领取的开关与去重标记——用户点了就是要点。 */
    fun claim() {
        scope.launch {
            val credential = repository.readCredential() ?: return@launch
            _state.value = _state.value.copy(claiming = true, error = null)
            try {
                val outcome = repository.claim(credential)
                apply(outcome.status)
                _state.value = _state.value.copy(
                    notice = when (outcome) {
                        is QqClaimOutcome.Claimed ->
                            "已领取：${outcome.status.todayReward.ifBlank { "奖励" }}"

                        is QqClaimOutcome.NothingToClaim -> "现在没有可领的周签到礼包"
                    },
                )
                repository.markAutoDone("${serverDateKey(currentEpochSeconds())}|${credential.uin}")
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    claiming = false,
                    error = describeQqError(e),
                )
            }
        }
    }

    private suspend fun apply(status: QqWeeklySignIn) {
        _state.value = _state.value.copy(
            status = status,
            available = true,
            loading = false,
            claiming = false,
            error = null,
        )
        repository.writeCache(status)
    }

    /** 保存 QQ 凭证并立刻拉一次。返回 null 表示解析失败。 */
    suspend fun saveCredential(raw: String): QqCredential? {
        val credential = runCatching { repository.saveCredential(raw) }.getOrNull()
        if (credential == null) {
            _state.value = _state.value.copy(error = "这段 cookie 里找不到 uin 或 p_skey")
            return null
        }
        _state.value = _state.value.copy(
            bound = true,
            error = null,
            credentialRaw = credential.raw,
        )
        refresh(autoClaim = false)
        return credential
    }

    suspend fun clearCredential() {
        repository.clearCredential()
        _state.value = QqGiftUiState()
    }

    fun consumeNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        const val CACHE_KEY = StoreKey.QQ_GIFT_CACHE
    }
}

/**
 * 错误文案。QQ 这边的凭证比小程序那串更容易过期——重新登录 QQ 就会失效，
 * 所以过期要说清楚该去干什么，不能只说"请求失败"。
 */
internal fun describeQqError(e: Throwable): String = when (e) {
    is QqCredentialException -> "QQ 凭证不对：${e.message}"
    is QqNoBoundRoleException -> e.message ?: "还没绑定游戏角色"
    is QqApiException -> "游戏中心返回错误 ${e.code}：${e.msg ?: "没有说明"}" +
        if (e.code == 4 || e.code == 10000) "（多半是 QQ 登录态过期了，重新抓一次）" else ""

    is QqProtocolException -> "接口结构变了，可能官方升级了：${e.message}"
    is QqRequestException -> e.message ?: "网络请求失败"
    else -> e.message?.takeIf { it.isNotBlank() } ?: "没拿到游戏中心的数据"
}
