package com.nzd.antigravitypanel.data.backup

import com.nzd.antigravitypanel.data.db.ConfigCacheEntity
import com.nzd.antigravitypanel.data.db.MatchBossStatEntity
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.store.StoreKey
import com.nzd.antigravitypanel.util.serverDateKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 数据包格式版本。
 *
 * 只在**不兼容的改动**时加一（删字段、改语义）。加字段不用动它：
 * 读的时候 `ignoreUnknownKeys` + 全字段默认值，老客户端读到新包会当没看见那些新字段。
 */
const val BACKUP_FORMAT_VERSION = 1

/** 数据包的 app 标识。导入时靠它挡掉"随便一个 zip"。 */
const val BACKUP_APP_ID = "antigravity-panel"

const val BACKUP_MIME_TYPE = "application/zip"
const val BACKUP_EXTENSION = "zip"

/** 包内文件名。导入按名字取，缺哪个就跳过哪一块 —— 老包 / 未来的新包都能读。 */
internal const val FILE_MANIFEST = "manifest.json"
internal const val FILE_CREDENTIALS = "credentials.json"
internal const val FILE_SETTINGS = "settings.json"
internal const val FILE_CACHES = "caches.json"
internal const val FILE_MATCHES = "matches.json"
internal const val FILE_MATCH_DETAILS = "match_details.json"
internal const val FILE_CONFIG = "config.json"

/** zip 里的文件数上限。正常包就 7 个，多出来的说明这不是我们的包。 */
internal const val MAX_BACKUP_ENTRIES = 32

/** 解压后的总字节上限。防"压缩炸弹"把手机内存吃干净。 */
internal const val MAX_BACKUP_BYTES = 128L * 1024 * 1024

/**
 * 数据包清单。
 *
 * 单独一份文件而不是塞在数据里：导入时要**先**知道这是哪个 app、哪个版本写的，
 * 才知道后面那几份该不该按现在的规则解析。
 */
@Serializable
data class BackupManifest(
    val app: String = BACKUP_APP_ID,
    val platform: String = "android",
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    /** 导出时的应用版本号，形如 `0.2.4`。只用于排查问题，不参与解析。 */
    val appVersion: String = "",
    val exportedAtSec: Long = 0,
    val matchCount: Int = 0,
    val detailCount: Int = 0,
    /** 这份包里有没有凭证。UI 上那句"含登录凭证"读的就是它。 */
    val credentialsIncluded: Boolean = false,
)

/** 键值块。凭证 / 设置 / 缓存三份都用这个壳，少写三个几乎一样的类。 */
@Serializable
data class BackupKeyValues(
    val entries: Map<String, String> = emptyMap(),
)

/**
 * 一场对局。
 *
 * 字段和 `matches` 表一一对应，但**不复用 Room 实体**：实体不是 `@Serializable`，
 * 而给实体加注解会把"落库结构"和"数据包结构"绑死 —— 以后加一列就得连带改包格式。
 */
@Serializable
data class BackupMatch(
    val roomId: String = "",
    val openId: String = "",
    val eventTime: String = "",
    val eventTimeSec: Long = 0,
    val startTime: String = "",
    val areaId: String = "",
    val finTime: Int = 0,
    val duration: Int = 0,
    val isWin: Boolean = false,
    val gameMode: Int = 0,
    val modeType: Int = 0,
    val subModeType: Int = 0,
    val mapId: Int = 0,
    val score: Long = 0,
    val seasonId: String = "",
    val kills: Int = 0,
    val deaths: Int = 0,
    val assists: Int = 0,
    val rank: Int = 0,
    val finished: Boolean = false,
    val syncedAtSec: Long = 0,
)

/** 单局详情的缓存行（含 `payloadJson` 那一整块英文数字）。 */
@Serializable
data class BackupMatchDetail(
    val roomId: String = "",
    val selfBossDamage: Long = 0,
    val teamBossDamage: Long = 0,
    val rivalBossDamage: Long = 0,
    val updatedAtSec: Long = 0,
    /** 地图分布 / 详情页要的那一份原始 JSON，原样搬过去，不做二次解释。 */
    val payloadJson: String? = null,
)

/** 游戏配置表（地图名 / 难度名 / 分区名）。带上它，换机后没网也能把地图分布画出来。 */
@Serializable
data class BackupConfig(
    val key: String = "",
    val json: String = "",
    val updatedAtSec: Long = 0,
)

/**
 * 导出时要搬的键。分成三组是为了在导入时各自讲清楚"搬了多少"，
 * 也为了**只认白名单** —— 包是用户可以随手改的文本，不能让它往存储里写任意键。
 */
object BackupKeys {

    /**
     * 凭证：登录软件用的 cookie + 每日签到里那几套互不通用的凭证。
     *
     * 超核的 `SUPERCORE_ROLE_KEY` 也算凭证：它是这个号在这期活动里选的角色，
     * 换机后不带上就得重选一次。
     */
    val credentials: List<String> = listOf(
        StoreKey.COOKIE_RAW,
        StoreKey.ACCOUNTS_V1,
        StoreKey.ACTIVE_ACCOUNT_OPENID,
        StoreKey.QQ_COOKIE_RAW,
        StoreKey.XINYUE_CREDENTIAL_RAW,
        StoreKey.SUPERCORE_CREDENTIAL_RAW,
        StoreKey.SUPERCORE_ACTIVITY_URL,
        StoreKey.SUPERCORE_ROLE_KEY,
    )

    /**
     * 设置与纯本地偏好。
     *
     * ⚠️ 故意**不含** `WIKI_PROXY_MODE`：那个键是"下一次冷启动保护一轮"的一次性标记，
     * 从别的设备搬过来只会在新机上多跳过一次自动签到，没有任何好处。
     */
    val settings: List<String> = listOf(
        StoreKey.RETENTION_MONTHS,
        StoreKey.COLOR_MODE,
        StoreKey.AUTO_REFRESH_MINUTES,
        StoreKey.PREDICTIVE_BACK_TRANSLATION,
        StoreKey.SIGNIN_AUTO,
        StoreKey.WELFARE_TASK_AUTO_CLAIM,
        StoreKey.QQ_GIFT_AUTO_CLAIM,
        StoreKey.XINYUE_AUTO_CLAIM,
        StoreKey.SUPERCORE_AUTO,
        StoreKey.TOP_BAR_ACCOUNT,
        StoreKey.COMPOSITE_SCORE,
        StoreKey.SCORE_WAN_TARGETS,
        StoreKey.PINNED_ROOMS,
        StoreKey.FAVORITE_ROOMS,
        StoreKey.MAP_SEASON_FILTER_HUNT,
        StoreKey.MAP_SEASON_FILTER_TOWER,
        StoreKey.BUILD_PLAN,
        StoreKey.JSON_IMPORT_SUMMARY,
    )

    /**
     * 缓存：概览 / 签到 / 地图分布 / 四块签到的状态，以及自动签到的去重标记。
     *
     * 去重标记要带上：迁移当下的"今天已经签过"这件事，新机也该知道，
     * 否则换机当天会再签一次（服务端多半会拒，但没必要发这一发）。
     */
    val caches: List<String> = listOf(
        StoreKey.OVERVIEW_CACHE,
        StoreKey.SIGNIN_CACHE,
        StoreKey.SIGNIN_AUTO_MARK,
        StoreKey.WELFARE_TASK_AUTO_MARK,
        StoreKey.MAP_STATS_CACHE,
        StoreKey.MAP_DROPS_CACHE,
        StoreKey.QQ_GIFT_CACHE,
        StoreKey.QQ_GIFT_AUTO_MARK,
        StoreKey.XINYUE_CARD_CACHE,
        StoreKey.XINYUE_AUTO_MARK,
        StoreKey.SUPERCORE_PLAN_CACHE,
        StoreKey.SUPERCORE_CACHE,
        StoreKey.SUPERCORE_DRAW_HISTORY,
    )
}

/**
 * 数据包专用的 [Json]。
 *
 * 四个开关都有理由：
 * - `ignoreUnknownKeys`：新版本加的字段，老版本读到不该炸
 * - `isLenient`：手工改过 / 别的工具重存过的文件
 * - `coerceInputValues`：单个字段类型不对时退回默认值，不整份失败
 * - `encodeDefaults`：默认值也写出来（不写的话导入侧根本分不清"没有"和"是 0"）
 */
val BackupJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
    prettyPrint = true
}

/**
 * 对局那份专用：不 prettyPrint。
 *
 * 几千场的时候缩进能让文件大两三倍，而这一份没有人会去读 —— 要读对局请用设置页的
 * 「导出历史战绩为纯文本格式」。
 */
internal val BackupBulkJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
    prettyPrint = false
}

fun MatchEntity.toBackupMatch(): BackupMatch = BackupMatch(
    roomId = roomId,
    openId = openId,
    eventTime = eventTime,
    eventTimeSec = eventTimeSec,
    startTime = startTime,
    areaId = areaId,
    finTime = finTime,
    duration = duration,
    isWin = isWin,
    gameMode = gameMode,
    modeType = modeType,
    subModeType = subModeType,
    mapId = mapId,
    score = score,
    seasonId = seasonId,
    kills = kills,
    deaths = deaths,
    assists = assists,
    rank = rankValue,
    finished = finished,
    syncedAtSec = syncedAtSec,
)

fun BackupMatch.toEntity(ownerOpenId: String = ""): MatchEntity = MatchEntity(
    roomId = roomId,
    openId = openId,
    eventTime = eventTime,
    eventTimeSec = eventTimeSec,
    startTime = startTime,
    areaId = areaId,
    finTime = finTime,
    duration = duration,
    isWin = isWin,
    gameMode = gameMode,
    modeType = modeType,
    subModeType = subModeType,
    mapId = mapId,
    score = score,
    seasonId = seasonId,
    kills = kills,
    deaths = deaths,
    assists = assists,
    rankValue = rank,
    finished = finished,
    syncedAtSec = syncedAtSec,
    ownerOpenId = ownerOpenId,
)

fun MatchBossStatEntity.toBackupDetail(): BackupMatchDetail = BackupMatchDetail(
    roomId = roomId,
    selfBossDamage = selfBossDamage,
    teamBossDamage = teamBossDamage,
    rivalBossDamage = rivalBossDamage,
    updatedAtSec = updatedAtSec,
    payloadJson = payloadJson,
)

fun BackupMatchDetail.toEntity(ownerOpenId: String = ""): MatchBossStatEntity = MatchBossStatEntity(
    roomId = roomId,
    selfBossDamage = selfBossDamage,
    teamBossDamage = teamBossDamage,
    rivalBossDamage = rivalBossDamage,
    updatedAtSec = updatedAtSec,
    payloadJson = payloadJson,
    ownerOpenId = ownerOpenId,
)

fun ConfigCacheEntity.toBackupConfig(): BackupConfig =
    BackupConfig(key = cacheKey, json = json, updatedAtSec = updatedAtSec)

fun BackupConfig.toEntity(): ConfigCacheEntity =
    ConfigCacheEntity(cacheKey = key, json = json, updatedAtSec = updatedAtSec)

/** 默认文件名：`nzm_backup_20261101.zip`。日期按服务端时区（北京时间）切，和别处一致。 */
fun backupFileName(nowSec: Long): String = "nzm_backup_${serverDateKey(nowSec)}.$BACKUP_EXTENSION"

/**
 * 是不是一个 zip。
 *
 * 只看头四个字节 `PK\x03\x04`：SAF 给回来的 Uri 没有扩展名可依赖
 * （有些文件管理器会把 `.zip` 报成 `application/octet-stream`），
 * 而头四个字节是最硬的判据。空的 / 太短的直接当"不是"。
 */
fun looksLikeZip(bytes: ByteArray): Boolean =
    bytes.size >= 4 &&
        bytes[0] == 'P'.code.toByte() &&
        bytes[1] == 'K'.code.toByte() &&
        bytes[2] == 0x03.toByte() &&
        bytes[3] == 0x04.toByte()
