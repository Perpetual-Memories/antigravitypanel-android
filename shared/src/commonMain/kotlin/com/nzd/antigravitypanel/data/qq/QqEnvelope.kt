package com.nzd.antigravitypanel.data.qq

import com.nzd.antigravitypanel.data.remote.IdeJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/**
 * 拆 `ssr.gamecenter.qq.com` 的响应壳：`{ code, data }`。
 *
 * 比 AMS 那四层壳浅，但**判断顺序一样**：先看 `code`，非 0 就是业务错误。
 * 抽出来是为了单测——这一步错了整个功能只会显示"没拿到"。
 */
object QqEnvelopeCodec {
    fun <T> decode(
        body: String,
        json: Json = IdeJson,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val root = runCatching { json.parseToJsonElement(body) }
            .getOrElse { throw QqProtocolException("响应不是合法 JSON：${it.message}") }
        return decode(root, json, deserializer)
    }

    fun <T> decode(
        root: JsonElement,
        json: Json = IdeJson,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val obj = root.jsonObject
        val code = obj["code"]?.jsonPrimitive?.intOrNull
        if (code != null && code != 0) {
            throw QqApiException(code, obj["message"]?.jsonPrimitive?.content ?: obj["msg"]?.jsonPrimitive?.content)
        }
        val data = obj["data"]
            ?: throw QqProtocolException("响应缺少 data 节点")
        return runCatching { json.decodeFromJsonElement(deserializer, data) }
            .getOrElse { throw QqProtocolException("data 解不出来：${it.message}") }
    }
}

/**
 * 便捷版。Json 上没有单参的 `decodeFromJsonElement`，所以这里自己取 serializer。
 *
 * 写成顶层函数而不是 [QqEnvelopeCodec] 上同名（也两参）的扩展：
 * 那样会和三参的 `decode` 混成一堆重载，少传一个参数时编译器报的错
 * 落在"另一个重载"上，很难看出到底缺了什么。
 */
internal inline fun <reified T> unwrapQq(
    body: String,
    json: Json = IdeJson,
): T = QqEnvelopeCodec.decode(body, json, json.serializersModule.serializer<T>())
