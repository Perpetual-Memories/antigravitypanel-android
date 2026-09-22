package com.nzd.antigravitypanel.data.qq

import com.nzd.antigravitypanel.data.qq.dto.QqAreaInfoDto
import com.nzd.antigravitypanel.data.qq.dto.QqExchangeResultDto
import com.nzd.antigravitypanel.data.qq.dto.QqFirstScreenDto
import com.nzd.antigravitypanel.data.qq.dto.QqGameUserInfoDto
import com.nzd.antigravitypanel.data.remote.IdeJson
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
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * `ssr.gamecenter.qq.com` 的薄封装。
 *
 * 和 AMS 那套（`comm.ams.game.qq.com/ide/`）**完全是另一回事**：
 * 不同域名、不同凭证（QQ 登录 cookie）、JSON body 而不是表单、g_tk 放在 query 上。
 * 唯一共用的东西是那份宽松 Json 配置。
 *
 * @param NZM_APPID 逆战未来在游戏中心的 appid。静态配置
 *   `static.mie.qq.com/common/lib/zero/idip/1110484610.json` 里 `game_name` 就是 `Nzm`，
 *   确认过是这个号。
 */
class QqGameCenterApi(
    private val json: Json = IdeJson,
    private val http: HttpClient = createHttpClient(),
) {
    /**
     * 查已绑定的游戏角色。返回结果里的 `areaInfo` 是领取礼包必需的参数。
     *
     * 抛 [QqNoBoundRoleException]：账号在游戏中心没绑过逆战未来的角色，
     * 这时没法领——服务端不知道往哪个角色发货。
     */
    suspend fun gameUserInfo(credential: QqCredential): QqAreaInfoDto =
        post(credential, "welfare-new/get-game-user-info", PAGE_GIFT_GAME, PAGE_GIFT_GAME) {
            // JsonArrayBuilder.add 收的是 JsonElement，不能直接塞 String
            putJsonArray("appids") { add(JsonPrimitive(NZM_APPID)) }
        }.let { body ->
            val roles = unwrapQq<QqGameUserInfoDto>(body, json).roles
            roles.firstOrNull { it.appid == NZM_APPID }?.areaInfo
                ?: roles.firstOrNull()?.areaInfo
                ?: throw QqNoBoundRoleException()
        }

    /** 礼包首页。`gifts` 里带每个礼包的 `canGot` / `signDate`。 */
    suspend fun firstScreen(credential: QqCredential): QqFirstScreenDto =
        post(credential, "welfare-new/single-game-firstscreen", PAGE_GIFT_GAME, PAGE_GIFT_GAME) {
            put("appid", NZM_APPID)
            put("source", "")
        }.let { unwrapQq<QqFirstScreenDto>(it, json) }

    /**
     * 领取当前能领的礼包。
     *
     * ⚠️ 它是**批量**的：服务端把"它认为能领的"一起领掉，抓包里返回了三个
     * （周签到礼包 + 两个【启动】礼包）。没在抓包里找到只领某一个的接口，
     * 所以调用它等于点官方那个「全部领取」按钮，不是"只签到"。
     */
    suspend fun exchangeAllGifts(credential: QqCredential, area: QqAreaInfoDto): QqExchangeResultDto =
        post(credential, "welfare-new/exchange-all-gifts", null, PAGE_TAKE_ALL) {
            put("appid", NZM_APPID)
            putJsonObject("areaInfo") {
                put("area", area.area)
                put("role_id", area.roleId)
                put("plat_id", area.platId)
                put("partition", area.partition)
                put("new_role_id", area.newRoleId)
            }
            put("source", 0)
            putJsonObject("miniGameInfo") {
                put("zoneName", "")
                put("zoneId", "")
                put("roleId", "")
                put("roleName", "")
                put("hasZoneSvr", false)
            }
            put("gameType", 1)
        }.let { unwrapQq<QqExchangeResultDto>(it, json) }

    /**
     * @param sourcePage 拼在 URL 上的 `&source_page=...`，领取那个接口没有，传 null。
     * @param referer 请求头。抓包里是 `[kuikly]...` 这种内部页面标识，照原样带。
     */
    private suspend fun post(
        credential: QqCredential,
        path: String,
        sourcePage: String?,
        referer: String,
        body: JsonObjectBuilder.() -> Unit,
    ): String {
        val url = buildString {
            append(BASE).append(path)
            append("?from_kuikly=true&g_tk=").append(credential.gTk)
            if (sourcePage != null) append("&source_page=").append(sourcePage)
        }
        return try {
            http.post(url) {
                contentType(ContentType.Application.Json)
                header("Accept", "application/json")
                header("Origin", "https://ssr.gamecenter.qq.com")
                header("Referer", referer)
                header("User-Agent", USER_AGENT)
                header("Cookie", credential.asHeaderValue())
                setBody(json.encodeToString(buildJsonObject(body)))
            }.bodyAsText()
        } catch (t: Throwable) {
            throw QqRequestException("请求 $path 失败：${t.message}", t)
        }
    }

    companion object {
        private const val BASE = "https://ssr.gamecenter.qq.com/kuikly-msr/v1/cgi-bin/"
        const val NZM_APPID = "1110484610"

        /** 礼包页。查询类接口抓包里带的 `source_page`。 */
        private const val PAGE_GIFT_GAME = "&source_page=QQGameCenterGiftGame"

        /** 「全部领取」弹窗。领取动作抓包里**没有** source_page，只有这个 Referer。 */
        private const val PAGE_TAKE_ALL = "[kuikly]QQGameCenterActionSheetWelfareTakeAllGiftDialog"

        /** 照抓包原样带：QQ 内嵌 webview 的 UA，服务端会按它分流。 */
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; VOG-AL10 Build/HUAWEIVOG-AL10; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 " +
                "MQQBrowser/6.2 TBS/045114 Mobile Safari/537.36 " +
                "V1_AND_SQ_8.3.6_1320_YYB_D QQ/4.1.0 NetType/WIFI WebP/0.3.0 " +
                "Pixel/1080 StatusBarHeight/75 SimpleUISwitch/0 QQTheme/1000"

        fun createHttpClient(serializationJson: Json = IdeJson): HttpClient = HttpClient {
            // 出错时也可能返回 200，靠 body 里的 code 判断；非 2xx 也要能读到 body
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
class QqRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 接口返回 code != 0。 */
class QqApiException(val code: Int, val msg: String?) :
    Exception("游戏中心返回 code=$code${msg?.let { ", $it" } ?: ""}")

/** 响应结构认不出来。 */
class QqProtocolException(message: String) : Exception(message)

/** QQ 账号还没在游戏中心绑定逆战未来的角色，领不了。 */
class QqNoBoundRoleException :
    Exception("这个 QQ 还没在游戏中心绑定逆战未来的角色，先在 QQ 里进一次游戏中心礼包页")
