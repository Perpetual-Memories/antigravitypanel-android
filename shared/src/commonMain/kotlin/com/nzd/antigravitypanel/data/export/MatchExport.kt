package com.nzd.antigravitypanel.data.export

import com.nzd.antigravitypanel.data.db.CachedMatchDetail
import com.nzd.antigravitypanel.data.db.MatchBossStatEntity
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.data.db.decodeCachedDetail
import com.nzd.antigravitypanel.domain.modeOf
import com.nzd.antigravitypanel.util.currentEpochSeconds
import com.nzd.antigravitypanel.util.serverDateKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 导出格式。扩展名和 MIME 跟着格式走，SAF 那边的保存对话框要用。 */
enum class MatchExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
}

/**
 * 一行导出记录。
 *
 * 前半段是对局列表本身就有的（[MatchEntity]），后半段 [detail] 只有**拉过单局详情**
 * 的对局才有 —— 没有就是 null，CSV 里写成空串、JSON 里写成 null。
 *
 * ⚠️ 所有取值都是**英文 / 数字**：胜负是 `WIN` / `LOSS` / `UNFINISHED`，
 * 模式是 [com.nzd.antigravitypanel.domain.GameMode] 的枚举名（`HUNT` / `TOWER` ...），
 * 地图、难度只给 id。中文一律不进导出文件。
 */
@Serializable
data class MatchExportEntry(
    val roomId: String,
    val eventTime: String,
    val eventTimeUnix: Long,
    val startTime: String,
    val durationSec: Int,
    val finished: Boolean,
    val result: String,
    val mapId: Int,
    val mode: String,
    val gameMode: Int,
    val modeType: Int,
    val subModeType: Int,
    val areaId: String,
    val seasonId: String,
    val score: Long,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val rank: Int,
    /** null = 这局没有缓存过单局详情，后半段那些列全部留空。 */
    val detail: MatchDetailExport? = null,
)

/** 单局详情能补出来的部分。全部可空：拿不到就是 null。 */
@Serializable
data class MatchDetailExport(
    val playerCount: Int,
    val isRogueMatch: Boolean,
    val coin: Long,
    val bossDamage: Long,
    val mobDamage: Long,
    val teamBossDamage: Long,
    val rivalBossDamage: Long,
    val partitionCount: Int,
    val partitionTotalSec: Int,
    /** 一到两组配装的武器 id，用 `|` 分隔；没有配装记录时是 null。 */
    val weaponIds: String? = null,
    val weaponQualities: String? = null,
    /** 插件 id，顺序和 [weaponIds] 对应组的插件一致，用 `|` 分隔。 */
    val pluginIds: String? = null,
    val pluginQualities: String? = null,
)

/** 导出结果。文件名交给 SAF 当用户没改名时的默认值。 */
data class MatchExportFile(
    val fileName: String,
    val mimeType: String,
    val content: String,
    /** 导出了多少场。 */
    val matchCount: Int,
    /** 其中多少场带单局详情。 */
    val detailCount: Int,
)

/**
 * 对局列表 + 详情缓存 -> 导出记录。纯函数，不碰数据库。
 *
 * 顺序照本地库的默认序（时间倒序），不在这里重排：导出要的就是"库里有什么给什么"。
 */
fun exportEntries(
    matches: List<MatchEntity>,
    cachedDetails: Map<String, MatchBossStatEntity>,
): List<MatchExportEntry> = matches.map { match ->
    MatchExportEntry(
        roomId = match.roomId,
        eventTime = match.eventTime,
        eventTimeUnix = match.eventTimeSec,
        startTime = match.startTime,
        durationSec = match.duration,
        finished = match.finished,
        result = resultOf(match),
        mapId = match.mapId,
        mode = modeOf(match.mapId).name,
        gameMode = match.gameMode,
        modeType = match.modeType,
        subModeType = match.subModeType,
        areaId = match.areaId,
        seasonId = match.seasonId,
        score = match.score,
        kills = match.kills,
        deaths = match.deaths,
        assists = match.assists,
        rank = match.rankValue,
        detail = decodeCachedDetail(cachedDetails[match.roomId]?.payloadJson)
            ?.let(::detailExportOf),
    )
}

/**
 * 胜负。没打完的局 `isWin` 是 false，但那不是"输了"，所以单独给一个值 ——
 * 统计胜率时必须能把它排除掉。
 */
fun resultOf(match: MatchEntity): String = when {
    !match.finished -> "UNFINISHED"
    match.isWin -> "WIN"
    else -> "LOSS"
}

fun detailExportOf(cached: CachedMatchDetail): MatchDetailExport {
    val weapons = cached.equipment
    val plugins = weapons.flatMap { it.plugins }
    return MatchDetailExport(
        playerCount = cached.playerCount,
        isRogueMatch = cached.isRogueMatch,
        coin = cached.coin,
        bossDamage = cached.bossDamage,
        mobDamage = cached.mobDamage,
        teamBossDamage = cached.teamBossDamage,
        rivalBossDamage = cached.rivalBossDamage,
        partitionCount = cached.partitionCount,
        partitionTotalSec = cached.partitionTotalSec,
        // 没有配装记录的局（服务端没给）留空，不要写个空串充数
        weaponIds = weapons.takeIf { it.isNotEmpty() }?.joinToString("|") { it.weaponId.toString() },
        weaponQualities = weapons.takeIf { it.isNotEmpty() }
            ?.joinToString("|") { it.weaponQuality.toString() },
        pluginIds = plugins.takeIf { it.isNotEmpty() }?.joinToString("|") { it.itemId.toString() },
        pluginQualities = plugins.takeIf { it.isNotEmpty() }
            ?.joinToString("|") { it.quality.toString() },
    )
}

/**
 * CSV 的列。表头和取值写在一起，是为了让"加一列"只改一处 ——
 * 分开维护过一次就会出现表头九列、数据八列。
 */
private class CsvColumn(val header: String, val value: (MatchExportEntry) -> String)

private val CSV_COLUMNS: List<CsvColumn> = listOf(
    CsvColumn("room_id") { it.roomId },
    CsvColumn("event_time") { it.eventTime },
    CsvColumn("event_time_unix") { it.eventTimeUnix.toString() },
    CsvColumn("start_time") { it.startTime },
    CsvColumn("duration_sec") { it.durationSec.toString() },
    CsvColumn("finished") { it.finished.toString() },
    CsvColumn("result") { it.result },
    CsvColumn("map_id") { it.mapId.toString() },
    CsvColumn("mode") { it.mode },
    CsvColumn("game_mode") { it.gameMode.toString() },
    CsvColumn("mode_type") { it.modeType.toString() },
    CsvColumn("sub_mode_type") { it.subModeType.toString() },
    CsvColumn("area_id") { it.areaId },
    CsvColumn("season_id") { it.seasonId },
    CsvColumn("score") { it.score.toString() },
    CsvColumn("kills") { it.kills.toString() },
    CsvColumn("deaths") { it.deaths.toString() },
    CsvColumn("assists") { it.assists.toString() },
    CsvColumn("rank") { it.rank.toString() },
    // ---- 以下只有缓存过单局详情的对局才有 ----
    CsvColumn("detail_player_count") { it.detail?.playerCount?.toString().orEmpty() },
    CsvColumn("detail_is_rogue") { it.detail?.isRogueMatch?.toString().orEmpty() },
    CsvColumn("detail_coin") { it.detail?.coin?.toString().orEmpty() },
    CsvColumn("detail_boss_damage") { it.detail?.bossDamage?.toString().orEmpty() },
    CsvColumn("detail_mob_damage") { it.detail?.mobDamage?.toString().orEmpty() },
    CsvColumn("detail_team_boss_damage") { it.detail?.teamBossDamage?.toString().orEmpty() },
    CsvColumn("detail_rival_boss_damage") { it.detail?.rivalBossDamage?.toString().orEmpty() },
    CsvColumn("detail_partition_count") { it.detail?.partitionCount?.toString().orEmpty() },
    CsvColumn("detail_partition_total_sec") { it.detail?.partitionTotalSec?.toString().orEmpty() },
    CsvColumn("detail_weapon_ids") { it.detail?.weaponIds.orEmpty() },
    CsvColumn("detail_weapon_qualities") { it.detail?.weaponQualities.orEmpty() },
    CsvColumn("detail_plugin_ids") { it.detail?.pluginIds.orEmpty() },
    CsvColumn("detail_plugin_qualities") { it.detail?.pluginQualities.orEmpty() },
)

/**
 * 导出的 JSON。
 *
 * `prettyPrint` 是为了让人能直接打开看；代价是文件大两三倍，但导出的是几千行文本，
 * 不是每次都跑的热路径。
 */
val ExportJson: Json = Json {
    prettyPrint = true
    // 必须开：不开的话 `detail == null` 因为正好等于默认值会被**整条省略**，
    // 导出的 JSON 里就分不清"这局没有详情"和"字段名拼错了"。同理
    // `MatchDetailExport` 里那些可空列也要照原样写成 null，列数才稳定。
    encodeDefaults = true
}

fun toCsv(entries: List<MatchExportEntry>): String = buildString {
    append(CSV_COLUMNS.joinToString(",") { it.header })
    append('\n')
    for (entry in entries) {
        append(CSV_COLUMNS.joinToString(",") { csvCell(it.value(entry)) })
        append('\n')
    }
}

fun toJson(entries: List<MatchExportEntry>): String = ExportJson.encodeToString(entries)

/**
 * CSV 转义：含逗号 / 引号 / 换行才加引号，引号翻倍。
 *
 * 不做 BOM：导出内容全是 ASCII，加 BOM 反而会让一部分 CSV 解析库把第一列表头认成乱码。
 */
fun csvCell(text: String): String {
    val needsQuote = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    if (!needsQuote) return text
    return buildString(text.length + 2) {
        append('"')
        for (ch in text) {
            append(ch)
            if (ch == '"') append('"')
        }
        append('"')
    }
}

/** 默认文件名：`matches_20261008.csv`。日期按服务端时区（北京时间）切，和别处一致。 */
fun matchExportFileName(format: MatchExportFormat, nowSec: Long = currentEpochSeconds()): String =
    "matches_${serverDateKey(nowSec)}.${format.extension}"
