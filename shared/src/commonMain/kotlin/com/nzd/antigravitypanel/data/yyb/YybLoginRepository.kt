package com.nzd.antigravitypanel.data.yyb

import com.nzd.antigravitypanel.data.config.RemoteConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 微信二维码登录状态机；每次 start 都创建一个全新的内存协议运行时。 */
class YybLoginRepository(
    private val engineFactory: () -> YybLoginEngine,
    private val exchange: GameCodeExchanger,
    private val configProvider: () -> RemoteConfig,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(YybLoginUiState())
    val state: StateFlow<YybLoginUiState> = _state.asStateFlow()

    private var runJob: Job? = null
    private var engine: YybLoginEngine? = null

    fun start() {
        stop()
        _state.value = YybLoginUiState(stage = YybLoginStage.LOADING_QR)
        runJob = scope.launch { run() }
    }

    private suspend fun run() {
        var sessionId: String? = null
        var runningEngine: YybLoginEngine? = null
        try {
            val activeEngine = engineFactory()
            runningEngine = activeEngine
            engine = activeEngine
            val qr = activeEngine.createQr()
            sessionId = qr.id
            _state.value = YybLoginUiState(YybLoginStage.WAITING_SCAN, qr = qr)

            while (currentCoroutineContext().isActive) {
                val poll = activeEngine.poll(qr.id)
                when (poll.status) {
                    "pending" -> Unit
                    "scanned" -> _state.value = _state.value.copy(stage = YybLoginStage.SCANNED)
                    "authorized" -> {
                        _state.value = _state.value.copy(stage = YybLoginStage.AUTHORIZING)
                        val code = activeEngine.confirmAndGetCode(qr.id, configProvider().miniProgramAppId)
                        _state.value = _state.value.copy(stage = YybLoginStage.EXCHANGING)
                        val cookie = exchange.exchange(code, configProvider())
                        _state.value = _state.value.copy(
                            stage = YybLoginStage.SUCCESS,
                            resultCookie = cookie,
                            error = null,
                        )
                        return
                    }
                    "expired" -> {
                        _state.value = _state.value.copy(stage = YybLoginStage.EXPIRED)
                        return
                    }
                    "cancelled" -> {
                        _state.value = _state.value.copy(stage = YybLoginStage.CANCELLED)
                        return
                    }
                    else -> {
                        _state.value = _state.value.copy(
                            stage = YybLoginStage.FAILED,
                            error = "微信返回了未知扫码状态（${poll.errCode ?: poll.status}）",
                        )
                        return
                    }
                }
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            _state.value = _state.value.copy(
                stage = YybLoginStage.FAILED,
                error = t.message?.lineSequence()?.firstOrNull().orEmpty().ifBlank { "登录失败，请重试" },
                resultCookie = null,
            )
        } finally {
            // 用本次运行自己的引用：旧任务迟到的 finally 不能关掉下一次 start 的新引擎。
            sessionId?.let { id -> runningEngine?.cancel(id) }
            runningEngine?.close()
            if (engine === runningEngine) engine = null
        }
    }

    fun cancel() {
        stop()
        _state.value = _state.value.copy(
            stage = YybLoginStage.CANCELLED,
            resultCookie = null,
            error = null,
        )
    }

    fun close() {
        stop()
        _state.value = YybLoginUiState()
    }

    private fun stop() {
        runJob?.cancel()
        runJob = null
        engine?.close()
        engine = null
    }
}
