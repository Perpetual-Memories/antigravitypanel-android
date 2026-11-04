package com.nzd.antigravitypanel

import com.nzd.antigravitypanel.data.config.RemoteConfig
import com.nzd.antigravitypanel.data.remote.ProtocolException
import com.nzd.antigravitypanel.data.yyb.GameCodeExchanger
import com.nzd.antigravitypanel.data.yyb.YybLoginEngine
import com.nzd.antigravitypanel.data.yyb.YybLoginRepository
import com.nzd.antigravitypanel.data.yyb.YybLoginStage
import com.nzd.antigravitypanel.data.yyb.YybPollResult
import com.nzd.antigravitypanel.data.yyb.YybQrSession
import com.nzd.antigravitypanel.data.yyb.parseCodeToSessionCookie
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class YybLoginTest {
    @Test
    fun codeToSessionBuildsPcCompatibleWechatCookie() {
        val response = """
            {"iRet":0,"jData":{"data":{
              "openid":"open-id","access_token":"access","token_v2":"token-v2",
              "session_token":"session","lastaccesstime":1234567890,"unionid":"union"
            }}}
        """.trimIndent()
        assertEquals(
            "openid=open-id; acctype=mini; appid=wx4e8cbe4fb0eca54c; " +
                "ieg_ams_token=access; ieg_ams_token_v2=token-v2; " +
                "ieg_ams_session_token=session; ieg_ams_token_time=1234567890; unionid=union",
            parseCodeToSessionCookie(response, "wx4e8cbe4fb0eca54c"),
        )
    }

    @Test
    fun codeToSessionRejectsBusinessErrorAndMissingSession() {
        assertFailsWith<ProtocolException> {
            parseCodeToSessionCookie("""{"iRet":101,"sMsg":"请先登录"}""", "app")
        }
        assertFailsWith<ProtocolException> {
            parseCodeToSessionCookie(
                """{"jData":{"openid":"open-id","access_token":"access"}}""",
                "app",
            )
        }
    }

    @Test
    fun repositoryRunsAuthorizedSessionThroughCodeExchange() = runBlocking {
        val engine = FakeEngine()
        val repository = YybLoginRepository(
            engineFactory = { engine },
            exchange = GameCodeExchanger { code, config ->
                assertEquals("one-time-code", code)
                assertEquals("wx4e8cbe4fb0eca54c", config.miniProgramAppId)
                "openid=open-id; acctype=mini; appid=wx4e8cbe4fb0eca54c; ieg_ams_session_token=session"
            },
            configProvider = { RemoteConfig() },
            scope = this,
        )
        repository.start()
        val result = withTimeout(2_000) {
            repository.state.first { it.stage == YybLoginStage.SUCCESS }
        }
        assertEquals("session", result.resultCookie?.substringAfterLast("="))
        assertEquals("wx4e8cbe4fb0eca54c", engine.requestedAppId)
        assertTrue(engine.closed)
        repository.close()
    }

    private class FakeEngine : YybLoginEngine {
        var requestedAppId: String? = null
        var closed: Boolean = false

        override suspend fun createQr(): YybQrSession =
            YybQrSession("qr-session", byteArrayOf(1, 2, 3))

        override suspend fun poll(sessionId: String): YybPollResult =
            YybPollResult("authorized", 405)

        override suspend fun confirmAndGetCode(sessionId: String, appId: String): String {
            requestedAppId = appId
            return "one-time-code"
        }

        override fun cancel(sessionId: String) = Unit

        override fun close() {
            closed = true
        }
    }
}
