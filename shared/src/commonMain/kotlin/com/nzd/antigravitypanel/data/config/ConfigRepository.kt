package com.nzd.antigravitypanel.data.config

import com.nzd.antigravitypanel.data.remote.IdeJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * 协议参数的来源：先用安装包内置的那份，再异步拉远程 JSON 覆盖。
 *
 * 覆盖是**逐字段**的：远程 JSON 里只写变化的那几个字段就行，
 * 没写的继续用内置值。这样远程文件坏了也不会把整个 app 搞挂。
 */
class ConfigRepository(
    private val client: HttpClient = ConfigRepository.createClient(),
) {
    @Volatile
    var current: RemoteConfig = BUILTIN
        private set

    /**
     * 拉取远程配置并合并。任何一步失败都静默退回内置值——
     * 拿不到新参数顶多是 seasonID 过时，总比开不了屏强。
     */
    suspend fun refresh(): RemoteConfig {
        val url = BUILTIN.configUrl
        if (url.isBlank()) {
            current = BUILTIN
            return BUILTIN
        }
        val merged = runCatching {
            val text = client.get(url).bodyAsText()
            val remote = IdeJson.parseToJsonElement(text).jsonObject
            IdeJson.decodeFromJsonElement<RemoteConfig>(JsonObject(BUILTIN_JSON + remote))
        }.getOrNull()

        val result = if (merged == null || merged.seasonID <= 0) BUILTIN else merged
        current = result
        return result
    }

    /** 用完关掉底层 HttpClient，别把连接挂到进程结束。 */
    fun close() {
        runCatching { client.close() }
    }

    companion object {
        private val BUILTIN_JSON: JsonObject by lazy {
            IdeJson.parseToJsonElement(BUILTIN_CONFIG_JSON).jsonObject
        }

        private val BUILTIN: RemoteConfig by lazy {
            IdeJson.decodeFromJsonElement(BUILTIN_JSON)
        }

        fun createClient(): HttpClient = HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 12_000
            }
            expectSuccess = false
        }
    }
}
