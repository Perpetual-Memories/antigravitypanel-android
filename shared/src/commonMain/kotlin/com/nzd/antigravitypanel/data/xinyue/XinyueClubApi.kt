package com.nzd.antigravitypanel.data.xinyue

import com.nzd.antigravitypanel.data.remote.IdeJson
import com.nzd.antigravitypanel.data.xinyue.dto.XyMyCardListDto
import com.nzd.antigravitypanel.data.xinyue.dto.XyReceiveGiftDto
import com.nzd.antigravitypanel.data.xinyue.dto.XyRoleDto
import com.nzd.antigravitypanel.data.xinyue.dto.XyUserCardDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * `agw.xinyue.qq.com` 的薄封装。
 *
 * **第三套完全独立的协议**：和小程序那套（AMS，表单 + `NzCookie`）、
 * 游戏中心那套（`kuikly-msr`，`g_tk` 在 query 上）都不一样——
 * 这边凭证走请求头（`T-OPENID` / `T-ACCESS-TOKEN`），body 是 JSON，域名是心悦的。
 * 唯一共用的东西是那份宽松 Json 配置。
 *
 * 只用两个接口：
 * - [myCardList] 读状态，**角色信息也在这里面**，所以不用再单独查角色；
 * - [receiveGift] 领当日礼包。
 *
 * 抓包里还有个 `GetCardInfo`，它返回的 `month.base_info` 和 `MyCardList` 是同一份，
 * 而调用它得先有 `role` 和 `record_id`（只有 MyCardList 给）——
 * 对我们没有新增信息，只是多一次请求，所以不接。
 */
class XinyueClubApi(
    private val json: Json = IdeJson,
    private val http: HttpClient = createHttpClient(),
) {
    suspend fun myCardList(credential: XinyueCredential): XyMyCardListDto =
        post(credential, "XyCard.CardSrv/MyCardList") {
            put("channel", "vip")
            put("pay_channel", "h5")
            put("device", "android")
            put("platform", "tgclubApp")
        }.let { unwrapXy<XyMyCardListDto>(it, json) }

    /**
     * 领一张卡今天的礼包。
     *
     * ⚠️ 它**真的会往游戏角色上发货**（抓包里领到的是 NZ点 x200）。
     * 和游戏中心那个批量接口不同，这个只领传进去的这张卡，
     * 所以自动领取可以默认开——但仍然要能关。
     */
    suspend fun receiveGift(credential: XinyueCredential, card: XyUserCardDto): XyReceiveGiftDto =
        post(credential, "XyCard.CardSrv/ReceiveGift") {
            put("gid", card.gid)
            put("card_group", card.cardGroup)
            put("card_type", card.cardType)
            put("card_id", card.cardId)
            put("channel", "vip")
            put("pay_channel", "h5")
            put("platform", "android")
            putJsonObject("role") { fill(card.role) }
            put("num", 1)
            put("record_id", card.recordId)
            putJsonObject("user_info") {
                put("avatar", "")
                put("nickname", "")
            }
        }.let { unwrapXy<XyReceiveGiftDto>(it, json) }

    /** 角色整块照原样回传，`partition_name` / `role_name` 保持 base64，不解码。 */
    private fun JsonObjectBuilder.fill(role: XyRoleDto?) {
        put("game_open_id", role?.gameOpenId.orEmpty())
        put("game_app_id", role?.gameAppId.orEmpty())
        put("area_id", role?.areaId ?: 0)
        put("plat_id", role?.platId ?: 0)
        put("partition_id", role?.partitionId ?: 0)
        put("partition_name", role?.partitionName.orEmpty())
        put("role_id", role?.roleId.orEmpty())
        put("role_name", role?.roleName.orEmpty())
        put("device", role?.device.orEmpty())
        put("flag", role?.flag ?: 0)
    }

    private suspend fun post(
        credential: XinyueCredential,
        path: String,
        body: JsonObjectBuilder.() -> Unit,
    ): String {
        // 逐个域名试：网关自己返回的错误**也是合法 JSON**（`{"error_msg":"404 Route Not Found"}`），
        // HTTP 状态码还是 200，光看状态码判断不出来，必须看 body 里有没有 `ret`。
        val attempts = ArrayList<String>()
        for (host in HOSTS) {
            val text = try {
                call(host, credential, path, body)
            } catch (t: Throwable) {
                attempts += "$host：${t.message}"
                continue
            }
            val gatewayError = gatewayErrorOf(text)
            if (gatewayError == null) return text
            attempts += "$host：$gatewayError"
        }
        throw XinyueGatewayException(path, attempts)
    }

    private suspend fun call(
        host: String,
        credential: XinyueCredential,
        path: String,
        body: JsonObjectBuilder.() -> Unit,
    ): String {
        return try {
            http.post("https://$host.xinyue.qq.com/$path") {
                contentType(ContentType.Application.Json)
                header("Accept", "*/*")
                header("Origin", "https://xinyue.qq.com")
                header("Referer", "https://xinyue.qq.com/")
                header("User-Agent", USER_AGENT)
                header("T-OPENID", credential.openId)
                header("T-ACCESS-TOKEN", credential.accessToken)
                header("T-APPID", APPID)
                header("T-GID", GID_NZ_FUTURE)
                header("T-MODE", "true")
                header("T-DEVICE-TYPE", "android")
                header("T-CHANNEL-ID", "vip")
                header("T-ACCOUNT-TYPE", "qc")
                header("UP-VIA", "vip")
                header("UP-VIA-SOURCE", "Y")
                header("X-Requested-With", "com.tencent.tgclub")
                setBody(json.encodeToString(buildJsonObject(body)))
            }.bodyAsText()
        } catch (t: Throwable) {
            throw XinyueRequestException("请求 $host/$path 失败：${t.message}", t)
        }
    }

    companion object {
        /**
         * 网关域名。**顺序即优先级**：`bgw` 是当前在用的，`agw` 是 2026-09-22 之前在用的。
         *
         * 那天开始 `agw` 全线返回 `{"error_msg":"404 Route Not Found"}`（HTTP 状态码还是 200），
         * `MyCardList` 和 `ReceiveGift` 都下掉了，换成 `bgw` 才正常。
         * 留着 `agw` 兜底：这类网关域名腾讯时不时互换，见网关错就换下一个再试一次。
         */
        private val HOSTS = listOf("bgw", "agw")

        /**
         * 心悦 App 的 appid。**注意不是游戏的 appid**：
         * 游戏 appid（`1110484610`，和游戏中心那个同号）出现在 body 的 `role.game_app_id` 里，
         * 请求头这个 `T-APPID` 是心悦自己的。两个别混。
         */
        private const val APPID = "101484782"

        /** 逆战未来在心悦的 gid。MyCardList 返回的卡上也带这个号，靠它筛。 */
        const val GID_NZ_FUTURE = "1471"

        /** 照抓包原样带：心悦 App 内嵌 webview 的 UA。 */
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; 25102RKBEC Build/BP2A.250605.031.A3; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.0.0 " +
                "Mobile Safari/537.36"

        fun createHttpClient(serializationJson: Json = IdeJson): HttpClient = HttpClient {
            // 出错时也可能返回 200，靠 body 里的 ret 判断；非 2xx 也要能读到 body
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

/** 网络层挂了。message 里带上 path 便于排查。 */
class XinyueRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 接口返回 ret != 0。凭证失效是这里最常见的一种。 */
class XinyueApiException(val ret: Int, val msg: String?) :
    Exception("心悦返回 ret=$ret${msg?.let { ", $it" } ?: ""}")

/** 响应结构认不出来。 */
class XinyueProtocolException(message: String) : Exception(message)

/**
 * 网关没接住这个 path：`{"error_msg":"404 Route Not Found"}`。
 *
 * 单独一个类型是因为它和"业务报错"的处置完全不同：业务报错（`ret != 0`）重试也没用，
 * 而这个是路由层面的问题，换域名就有可能好。而且它的 HTTP 状态码是 200，
 * 不主动认出来就会被当成正常响应往下走。
 */
class XinyueGatewayException(val path: String, val attempts: List<String>) :
    Exception("$path 没有网关接住（${attempts.joinToString("；")}）")

/**
 * 判网关错误。返回 `error_msg`，不是则返回 null。
 *
 * 判据是**「没有 ret，但有 error_msg」**：业务响应一定有 `ret`，
 * 而网关错误只有 `error_msg`。反过来只看 `error_msg` 是不够的——
 * 万一哪天的业务响应也带这个键，会把正常响应误判成路由错。
 */
internal fun gatewayErrorOf(body: String): String? {
    val root = runCatching { IdeJson.parseToJsonElement(body) }.getOrNull() ?: return null
    val obj = root as? JsonObject ?: return null
    if (obj.containsKey("ret")) return null
    return obj["error_msg"]?.jsonPrimitive?.contentOrNull
}
