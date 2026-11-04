package com.nzd.antigravitypanel.data.yyb

/** 微信返回的二维码图片和本地会话标识。图片不能由 UUID 自行重建。 */
data class YybQrSession(
    val id: String,
    val imageBytes: ByteArray,
)

data class YybPollResult(
    val status: String,
    val errCode: Int? = null,
)

/**
 * GoMobile 协议层边界。这里有意只暴露一次性 code，不把 login_buffer、刷新令牌、
 * PSK 或应用宝 Cookie 交给 Compose/UI。
 */
interface YybLoginEngine {
    suspend fun createQr(): YybQrSession
    suspend fun poll(sessionId: String): YybPollResult
    suspend fun confirmAndGetCode(sessionId: String, appId: String): String
    fun cancel(sessionId: String)
    fun close()
}

expect fun createYybLoginEngine(): YybLoginEngine

enum class YybLoginStage {
    IDLE,
    LOADING_QR,
    WAITING_SCAN,
    SCANNED,
    AUTHORIZING,
    EXCHANGING,
    SUCCESS,
    EXPIRED,
    CANCELLED,
    FAILED,
}

data class YybLoginUiState(
    val stage: YybLoginStage = YybLoginStage.IDLE,
    val qr: YybQrSession? = null,
    val error: String? = null,
    /** 仅在成功后短暂存在；宿主保存并关闭浮层后立即清空。 */
    val resultCookie: String? = null,
)
