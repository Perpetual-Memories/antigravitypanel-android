package com.nzd.antigravitypanel.data.remote

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 拆开响应的四层壳，返回最内层的业务数据节点。
 *
 * 抽成顶层函数是为了能直接单测——这层判断错一点，整个 app 就只会报"加载失败"。
 *
 * @throws CookieExpiredException iRet != 0，凭证失效
 * @throws ApiException           code != 0，业务报错
 * @throws ProtocolException      结构不认识
 */
fun unwrapIdeResponse(root: JsonElement, method: IdeMethod): JsonElement {
    val obj = root.jsonObject
    val iRet = obj["iRet"]?.jsonPrimitive?.intOrNull
        ?: obj["ret"]?.jsonPrimitive?.intOrNull
    if (iRet != null && iRet != 0) {
        throw CookieExpiredException(iRet, obj["sMsg"]?.jsonPrimitive?.content)
    }

    val inner = obj["jData"]?.jsonObject?.get("data")?.jsonObject
        ?: throw ProtocolException("响应缺少 jData.data（method=${method.apiName}）")

    val code = inner["code"]?.jsonPrimitive?.intOrNull
    if (code != null && code != 0) {
        throw ApiException(code, inner["msg"]?.jsonPrimitive?.content)
    }

    // 用户未同意数据协议时，iRet 与 code 都是 0，但 data 是 null
    return inner["data"] ?: JsonNull
}
