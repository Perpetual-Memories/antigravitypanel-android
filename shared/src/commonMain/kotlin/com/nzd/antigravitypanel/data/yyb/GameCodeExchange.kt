package com.nzd.antigravitypanel.data.yyb

import com.nzd.antigravitypanel.data.config.RemoteConfig
import com.nzd.antigravitypanel.data.remote.IdeClient
import com.nzd.antigravitypanel.data.remote.ProtocolException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private const val CODE_TO_SESSION_URL = "https://comm.ams.game.qq.com/ide/"

fun interface GameCodeExchanger {
    suspend fun exchange(code: String, config: RemoteConfig): String
}

/** PC Rust 侧的 codeToSession：一次性 wx.login code → 微信区业务 Cookie。 */
class GameCodeExchange(
    private val http: HttpClient = IdeClient.createHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : GameCodeExchanger {
    override suspend fun exchange(code: String, config: RemoteConfig): String {
        if (code.isBlank()) throw ProtocolException("微信登录 code 为空")
        val text = try {
            http.get(CODE_TO_SESSION_URL) {
                parameter("action", "codeToSession")
                parameter("appid", config.miniProgramAppId)
                parameter("wxcode", code)
                header("User-Agent", config.scanLoginUserAgent)
                header(
                    "Referer",
                    "https://servicewechat.com/${config.miniProgramAppId}/" +
                        "${config.scanLoginPageFrameVersion}/page-frame.html",
                )
            }.bodyAsText()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            throw ProtocolException("请求登录换票接口失败：${t.message}", t)
        }
        return parseCodeToSessionCookie(text, config.miniProgramAppId, json)
    }
}

internal fun parseCodeToSessionCookie(
    text: String,
    appId: String,
    json: Json = Json,
): String {
    val root = try {
        json.parseToJsonElement(text)
    } catch (t: Throwable) {
        throw ProtocolException("登录换票接口返回的不是 JSON", t)
    }
    val iRet = root.findPrimitive("iRet")?.intOrNull
        ?: root.findPrimitive("ret")?.intOrNull
    if (iRet != null && iRet != 0) {
        val message = root.findString("sMsg") ?: root.findString("msg") ?: "未知错误"
        throw ProtocolException("登录换票失败（$iRet）：$message")
    }

    val openid = root.requiredString("openid")
    val accessToken = root.requiredString("access_token")
    val sessionToken = root.requiredString("session_token")
    val tokenV2 = root.findString("token_v2")
    val tokenTime = root.findString("lastaccesstime")
    val unionid = root.findString("unionid")

    return buildList {
        add("openid=$openid")
        add("acctype=mini")
        add("appid=$appId")
        add("ieg_ams_token=$accessToken")
        if (!tokenV2.isNullOrBlank()) add("ieg_ams_token_v2=$tokenV2")
        add("ieg_ams_session_token=$sessionToken")
        if (!tokenTime.isNullOrBlank()) add("ieg_ams_token_time=$tokenTime")
        if (!unionid.isNullOrBlank()) add("unionid=$unionid")
    }.joinToString("; ")
}

private fun JsonElement.requiredString(key: String): String =
    findString(key)?.takeIf { it.isNotBlank() }
        ?: throw ProtocolException("登录换票响应缺少 $key")

private fun JsonElement.findString(key: String): String? =
    findPrimitive(key)?.takeIf { !it.isString || it.content.isNotBlank() }?.content

private fun JsonElement.findPrimitive(key: String): JsonPrimitive? = when (this) {
    is JsonPrimitive -> null
    is JsonObject -> this[key] as? JsonPrimitive
        ?: values.firstNotNullOfOrNull { it.findPrimitive(key) }
    else -> null
}
