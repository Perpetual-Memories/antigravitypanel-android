package com.nzd.antigravitypanel.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 这个接口的两个坏习惯，逼着我们必须自己接管反序列化：
 *
 * 1. **数字也用字符串返回**：`iFinTime="1235"`、`Score="19665238"`。
 * 2. **缺数据时给空串，而不是 null 或省掉字段**：一局没打完时
 *    `iFinTime=""`、`iIsWin=""`、`iScore=""`、`Rank=""` 全都是空串。
 *
 * 直接按 `Int` 反序列化，空串会让整条记录炸掉；一条坏数据会连累整页。
 * 所以统一走下面这组宽松转换：取不到就给零值，绝不抛异常。
 */

private fun Decoder.lenientText(): String? {
    if (this is JsonDecoder) {
        val element = decodeJsonElement()
        return (element as? JsonPrimitive)?.contentOrNull
    }
    return runCatching { decodeString() }.getOrNull()
}

/** 空串 / null / 非数字 → 0。小数先转 Double 再截断，兼容 `"12.0"` 这种。 */
object LenientInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Int {
        val text = decoder.lenientText()?.trim() ?: return 0
        return text.toIntOrNull() ?: text.toDoubleOrNull()?.toInt() ?: 0
    }
}

/** 同上。分数、金币这类能到千万级，必须走 Long。 */
object LenientLong : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Long {
        val text = decoder.lenientText()?.trim() ?: return 0L
        return text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong() ?: 0L
    }
}

object LenientDouble : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Double =
        decoder.lenientText()?.trim()?.toDoubleOrNull() ?: 0.0
}

/** 服务端用 `"1"` / `"0"`，未完成时是空串，一律按 false 处理。 */
object LenientBoolean : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Boolean) =
        encoder.encodeString(if (value) "1" else "0")

    override fun deserialize(decoder: Decoder): Boolean {
        val text = decoder.lenientText()?.trim() ?: return false
        return text == "1" || text.equals("true", ignoreCase = true)
    }
}

/**
 * 有些 id 字段在不同 method 下时而是数字、时而是字符串（`areaId`、`mapID` 都见过），
 * 统一按原始文本收，避免类型漂移。
 */
object LenientString : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String = decoder.lenientText() ?: ""
}

/**
 * 全项目共用的 Json 实例。
 *
 * - `ignoreUnknownKeys`：响应里塞了 `requestID` / `amsSerial` 等一堆我们不需要的字段
 * - `coerceInputValues` + `explicitNulls=false`：`null` 一律落到属性默认值
 */
val IdeJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}
