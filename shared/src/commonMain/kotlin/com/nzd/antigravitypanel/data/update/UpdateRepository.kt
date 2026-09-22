package com.nzd.antigravitypanel.data.update

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 安装包的下载页。APK 放在蓝奏云上而不是 GitHub Release 的 asset：
 * `objects.githubusercontent.com` 在国内基本下不动（实测必超时），
 * 所以 Release 只用来**发版本号和更新日志**，真正的包走网盘。
 *
 * 代价是蓝奏云要提取码，所以打开页面前得先把码复制好。
 */
const val DOWNLOAD_PAGE_URL = "https://wwbxs.lanzouu.com/b011mhp3wd"

/** 蓝奏云的提取码。跟着上面的地址一起改。 */
const val DOWNLOAD_EXTRACT_CODE = "g6ks"

private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/Perpetual-Memories/antigravitypanel-android/releases/latest"

/** 版本号只比三段（x.y.z），和 HyperIsland 一致。 */
private const val VERSION_PART_COUNT = 3

/** 一次「确实有新版」的结论。 */
data class AppUpdate(
    /** 最新版本号，不带 `v` 前缀。 */
    val version: String,
    /** Release 的正文（更新日志），直接是 GitHub 上写的 markdown 原文。 */
    val changelog: String,
    val downloadUrl: String = DOWNLOAD_PAGE_URL,
)

/**
 * 问 GitHub 要最新 Release。
 *
 * 参照 HyperIsland 的 `UpdateService`：只认 `releases/latest` 一个接口，
 * 不列全部 release —— 我们只要"有没有比现在新的"这一个答案，
 * 拿列表回来还要自己排一次序，多一次请求也多一处出错的地方。
 */
class UpdateRepository(
    private val client: HttpClient = createClient(),
) {

    /**
     * 拉最新 Release，比当前版本新才返回，否则 null。
     *
     * **网络异常不在这里吞**：调用方自己 `runCatching`，才能把「没有新版本」和
     * 「没查成」分开——前者弹"已是最新"，后者得说"检查失败"，混在一起用户会以为
     * 自己已经是最新版了。
     */
    suspend fun fetchIfNewer(currentVersion: String): AppUpdate? {
        val response = client.get(LATEST_RELEASE_API) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            // GitHub 的 API 要求带 User-Agent，空 UA 会被直接拒（403）
            header(HttpHeaders.UserAgent, "AntigravityPanel/$currentVersion")
        }
        // 仓库一个 release 都没发过时是 404，不是错误
        if (!response.status.isSuccess()) return null

        val release = runCatching { Json.parseToJsonElement(response.bodyAsText()) }
            .getOrNull() as? JsonObject ?: return null
        // 标签是 `v0.2.0` 这种，去掉前缀再比
        val remote = (release["tag_name"] as? JsonPrimitive)?.content
            ?.removePrefix("v")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (!isNewerVersion(remote, currentVersion)) return null

        val changelog = (release["body"] as? JsonPrimitive)?.content.orEmpty()
        return AppUpdate(version = remote, changelog = changelog)
    }

    fun close() {
        runCatching { client.close() }
    }

    companion object {
        fun createClient(): HttpClient = HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 12_000
            }
            expectSuccess = false
        }
    }
}

/**
 * 远端版本是否比当前新。三段数字逐段比，没有的段算 0。
 *
 * 只认数字前缀（`1.2.0-beta` 当成 `1.2.0`）：预发布后缀不参与比较，
 * 否则 `0.2.0` 会被 `0.2.0-beta` 判成"有新版"，而实际上后者更早。
 */
fun isNewerVersion(remote: String, current: String): Boolean {
    // 当前版本读不出来时一律**不提示**。空串会被解析成 0.0.0，
    // 于是任何版本都"比它新"——版本名一次取不到，用户就会每次打开都被弹一次更新。
    // 漏报一次更新无所谓，误报是打扰。
    if (current.isBlank() || current.none(Char::isDigit)) return false
    val remoteParts = versionParts(remote)
    val currentParts = versionParts(current)
    repeat(VERSION_PART_COUNT) { index ->
        if (remoteParts[index] > currentParts[index]) return true
        if (remoteParts[index] < currentParts[index]) return false
    }
    return false
}

private fun versionParts(version: String): List<Int> = version
    .removePrefix("v")
    .split('.')
    .take(VERSION_PART_COUNT)
    .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    .let { parts -> List(VERSION_PART_COUNT) { index -> parts.getOrElse(index) { 0 } } }
