package com.nzd.antigravitypanel.data.xinyue

import com.nzd.antigravitypanel.data.remote.IdeJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/**
 * 拆 `agw.xinyue.qq.com` 的响应壳：`{ ret, msg, data }`。
 *
 * 和游戏中心那套 `{ code, data }` 形状不同但判断顺序一样：**先看 `ret`，非 0 就是错误**。
 * 抽出来是为了单测——这一步错了整个功能只会显示"没拿到"。
 */
object XinyueEnvelopeCodec {
    fun <T> decode(
        body: String,
        json: Json = IdeJson,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val root = runCatching { json.parseToJsonElement(body) }
            .getOrElse { throw XinyueProtocolException("响应不是合法 JSON：${it.message}") }
        return decode(root, json, deserializer)
    }

    fun <T> decode(
        root: JsonElement,
        json: Json = IdeJson,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val obj = root.jsonObject
        val ret = obj["ret"]?.jsonPrimitive?.intOrNull
        if (ret != null && ret != 0) {
            throw XinyueApiException(ret, obj["msg"]?.jsonPrimitive?.content)
        }
        val data = obj["data"]
            ?: throw XinyueProtocolException("响应缺少 data 节点")
        return runCatching { json.decodeFromJsonElement(deserializer, data) }
            .getOrElse { throw XinyueProtocolException("data 解不出来：${it.message}") }
    }
}

/** 便捷版。写成顶层函数而不是 `XinyueEnvelopeCodec` 上同名的扩展，理由同 `unwrapQq`。 */
internal inline fun <reified T> unwrapXy(
    body: String,
    json: Json = IdeJson,
): T = XinyueEnvelopeCodec.decode(body, json, json.serializersModule.serializer<T>())
