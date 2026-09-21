package com.nzd.antigravitypanel.data.remote

import com.nzd.antigravitypanel.data.config.RemoteConfig
import com.nzd.antigravitypanel.data.credential.NzCookie
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer

private const val ENDPOINT = "https://comm.ams.game.qq.com/ide/"

/**
 * `POST https://comm.ams.game.qq.com/ide/` 的薄封装。
 *
 * 请求是表单，响应是 JSON，包了四层：
 * ```
 * { ret, iRet, sMsg, sAmsSerial,
 *   jData: { data: { code, data: <真正的业务数据>, msg, requestID, amsSerial } } }
 * ```
 * 这里统一拆到最内层，调用方直接拿到 `T`。
 */
class IdeClient(
    val json: Json = IdeJson,
    private val http: HttpClient = createHttpClient(),
) {
    /**
     * 发请求并拆包，返回最内层的业务数据节点。
     * 只管取数，不做类型转换——转换交给下面的 postIde，好让它保持 inline。
     */
    suspend fun fetchPayload(
        method: IdeMethod,
        param: JsonObject,
        cookie: NzCookie,
        config: RemoteConfig,
    ): JsonElement {
        val text = try {
            http.submitForm(
                url = ENDPOINT,
                formParameters = parameters {
                    append("iChartId", config.iChartId)
                    append("iSubChartId", config.iChartId)
                    append("sIdeToken", config.sIdeToken)
                    append("eas_url", method.page.easUrl)
                    append("method", method.apiName)
                    append("from_source", "2")
                    // seasonID 在表单顶层和 param 里各出现一次，两处都要发
                    append("seasonID", config.seasonID.toString())
                    append("param", param.toString())
                },
            ) {
                // 带 charset=utf-8：param 里可能含中文（比如 map_mode=猎场）
                contentType(ContentType.Application.FormUrlEncoded.withCharset(Charsets.UTF_8))
                header("Referer", config.referer)
                header("User-Agent", config.userAgent)
                header("Cookie", cookie.asHeaderValue())
            }.bodyAsText()
        } catch (t: Throwable) {
            throw ProtocolException("请求 ${method.apiName} 失败：${t.message}", t)
        }

        val root = try {
            json.parseToJsonElement(text)
        } catch (t: Throwable) {
            throw ProtocolException("响应不是合法 JSON：${t.message}", t)
        }
        return unwrapIdeResponse(root, method)
    }

    suspend fun <T> postIde(
        method: IdeMethod,
        param: JsonObject,
        cookie: NzCookie,
        config: RemoteConfig,
        deserializer: DeserializationStrategy<T>,
    ): T = json.decodeFromJsonElement(deserializer, fetchPayload(method, param, cookie, config))

    /**
     * 注意：Json 上只有两参的 `decodeFromJsonElement(deserializer, element)`，
     * 没有 `decodeFromJsonElement<T>(element)` 这种单参便捷版本，所以这里得自己取 serializer。
     */
    suspend inline fun <reified T> postIde(
        method: IdeMethod,
        param: JsonObject,
        cookie: NzCookie,
        config: RemoteConfig,
    ): T {
        val strategy: KSerializer<T> = serializer()
        return postIde(method, param, cookie, config, strategy)
    }

    companion object {
        // 参数别叫 json：ContentNegotiation 的配置函数就叫 json()，同名的构造参数会把它遮蔽掉
        fun createHttpClient(serializationJson: Json = IdeJson): HttpClient = HttpClient {
            // 出错时服务端也返回 200，靠 iRet/code 判断；非 2xx 也要能读到 body
            expectSuccess = false

            install(ContentNegotiation) { json(serializationJson) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 25_000
            }
            install(Logging) { level = LogLevel.NONE }
        }
    }
}
