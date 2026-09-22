package com.nzd.antigravitypanel.ui.signin

import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.data.xinyue.XinyueApiException
import com.nzd.antigravitypanel.data.xinyue.XinyueCardCacheCodec
import com.nzd.antigravitypanel.data.xinyue.XinyueCardStatus
import com.nzd.antigravitypanel.data.xinyue.XinyueClaimOutcome
import com.nzd.antigravitypanel.data.xinyue.XinyueClubApi
import com.nzd.antigravitypanel.data.xinyue.XinyueCredential
import com.nzd.antigravitypanel.data.xinyue.XinyueCredentialException
import com.nzd.antigravitypanel.data.xinyue.XinyueGatewayException
import com.nzd.antigravitypanel.data.xinyue.XinyueProtocolException
import com.nzd.antigravitypanel.data.xinyue.XinyueRepository
import com.nzd.antigravitypanel.data.xinyue.XinyueRequestException
import com.nzd.antigravitypanel.data.xinyue.toStatus
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

data class XinyueUiState(
    val status: XinyueCardStatus = XinyueCardStatus(),
    /** 有没有填过心悦凭证。没填时整块 UI 是"去绑定"的引导，不是错误。 */
    val bound: Boolean = false,
    /** 手上有一份能看的数字（本次拉到的或缓存）。 */
    val available: Boolean = false,
    val loading: Boolean = false,
    val claiming: Boolean = false,
    val error: String? = null,
    /** 领取后的结果提示，显示完由 UI 调 [XinyueViewModel.consumeNotice] 清掉。 */
    val notice: String? = null,
    /**
     * 已保存的凭证原文，弹层里回显用。
     *
     * 和 QQ 那边同理：弹层要"打开即显示当前值"，让 UI 自己走 suspend 去读存储的话
     * 第一帧是空的，用户会以为凭证丢了。
     */
    val credentialRaw: String = "",
)

/**
 * 心悦俱乐部悦享卡的每日礼包。
 *
 * 和另两块签到是**三套独立的东西**：小程序那套走 AMS、游戏中心那套走 kuikly-msr，
 * 这套走 `agw.xinyue.qq.com` + 两个 `T-*` 请求头。所以单独一个 ViewModel——
 * 混在一起会让"没绑心悦"、"没绑 QQ"、"没填小程序 cookie"三种状态互相污染。
 */
class XinyueViewModel(
    private val store: KeyValueStore,
    api: XinyueClubApi = XinyueClubApi(),
    /** 冷启动同步读到的缓存原文。走 suspend 的话第一帧早就画完了。 */
    cachedSeed: String? = null,
) {
    private val repository = XinyueRepository(api, store)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val seeded = cachedSeed?.let { XinyueCardCacheCodec.decode(it) }?.toStatus()

    private val _state = MutableStateFlow(
        seeded?.let { XinyueUiState(status = it, available = true) } ?: XinyueUiState(),
    )
    val state: StateFlow<XinyueUiState> = _state.asStateFlow()

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
        _state.value = XinyueUiState(
            status = cache?.toStatus() ?: XinyueCardStatus(),
            bound = credential != null,
            available = cache != null,
            credentialRaw = credential?.raw.orEmpty(),
        )
    }

    /**
     * 拉状态。开了自动领取就顺手领——但要先看去重标记，同一天只试一次。
     *
     * 没绑凭证时什么都不做（也不报错）：这块是可选功能，不该在没配置的时候刷存在感。
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
            val mark = "$today|${credential.openId}"
            val shouldClaim = autoClaim && repository.autoMark() != mark

            try {
                val outcome = if (shouldClaim) repository.claim(credential) else null
                if (outcome != null) {
                    apply(outcome.status)
                    // 只在真跑完这一轮才记标记：失败了下次开 app 还能再试
                    repository.markAutoDone(mark)
                    _state.value = _state.value.copy(
                        notice = if (outcome is XinyueClaimOutcome.Claimed) {
                            "已领取悦享卡每日礼包：${outcome.status.rewardText.ifBlank { "奖励" }}"
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
                    error = describeXinyueError(e),
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
                        is XinyueClaimOutcome.Claimed ->
                            "已领取：${outcome.status.rewardText.ifBlank { "奖励" }}"

                        is XinyueClaimOutcome.NothingToClaim -> xinyueNothingText(outcome.status)
                    },
                )
                repository.markAutoDone("${serverDateKey(currentEpochSeconds())}|${credential.openId}")
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    claiming = false,
                    error = describeXinyueError(e),
                )
            }
        }
    }

    private suspend fun apply(status: XinyueCardStatus) {
        _state.value = _state.value.copy(
            status = status,
            available = true,
            loading = false,
            claiming = false,
            error = null,
        )
        repository.writeCache(status)
    }

    /** 保存心悦凭证并立刻拉一次。返回 null 表示解析失败。 */
    suspend fun saveCredential(raw: String): XinyueCredential? {
        val credential = runCatching { repository.saveCredential(raw) }.getOrNull()
        if (credential == null) {
            _state.value = _state.value.copy(error = "没认出来，要一段含 T-OPENID 和 T-ACCESS-TOKEN 的请求头")
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
        _state.value = XinyueUiState()
    }

    fun consumeNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        const val CACHE_KEY = StoreKey.XINYUE_CARD_CACHE
    }
}

/** 「领不了」的具体原因。没卡和已过期是两回事，别都糊成一句"现在不能领"。 */
internal fun xinyueNothingText(status: XinyueCardStatus): String = when {
    !status.hasCard -> "这个心悦账号下没有逆战未来的悦享卡"
    status.expired -> "悦享卡已经过期了${status.endDate.takeIf { it.isNotBlank() }?.let { "（$it 到期）" } ?: ""}"
    else -> "今天已经领过了"
}

/**
 * 错误文案。心悦这边的 token 比另两套更容易过期（重新登录心悦就失效），
 * 所以过期要说清楚该去干什么，不能只说"请求失败"。
 */
internal fun describeXinyueError(e: Throwable): String = when (e) {
    is XinyueCredentialException -> "心悦凭证不对：${e.message}"
    is XinyueApiException -> {
        val hint = if (e.ret in 100_000..100_999 ||
            e.msg.orEmpty().contains("token", ignoreCase = true) ||
            e.msg.orEmpty().contains("auth", ignoreCase = true)
        ) {
            "（多半是心悦登录态过期了，重新抓一次凭证）"
        } else {
            ""
        }
        "心悦返回 ret=${e.ret}：${e.msg ?: "没有说明"}$hint"
    }

    is XinyueGatewayException -> "心悦网关没接住这个接口（${e.attempts.firstOrNull() ?: "未知"}）" +
        "——多半是官方换了域名，等更新"

    is XinyueProtocolException -> "接口结构变了，可能官方升级了：${e.message}"
    is XinyueRequestException -> e.message ?: "网络请求失败"
    else -> e.message?.takeIf { it.isNotBlank() } ?: "没拿到心悦的数据"
}
