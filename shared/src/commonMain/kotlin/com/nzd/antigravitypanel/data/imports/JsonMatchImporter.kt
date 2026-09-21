package com.nzd.antigravitypanel.data.imports

import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.db.MatchEntity
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** 一次导入的结果。三个数字分开给，方便 UI 里如实说明"解析了多少、真正写了多少"。 */
data class ImportResult(
    /** 文件里读到的元素个数。 */
    val parsed: Int,
    /** 去掉房间号为空、以及同文件内重复之后剩下的条数。 */
    val unique: Int,
    /** 实际写进本地库的条数。 */
    val upserted: Int,
)

/**
 * 把反重力数据面板导出的 `nzm_matches.json` 写进本地库。
 *
 * 写入用 `upsertAll` 而不是 insert：同一个文件反复导入很常见（改完 UI 想换一批数据、
 * 或者导出方补了几条），按主键覆盖正好是想要的行为——后导入的覆盖先导入的。
 *
 * 这个入口是**实验性功能**，只为本地调试和 UI 调整服务，不参与正常的数据同步链路：
 * 导入的记录和同步下来的记录共用一张表，但不会反过来被推给服务端。
 */
class JsonMatchImporter(
    private val dao: MatchDao,
) {
    suspend fun import(raw: String, nowSec: Long = currentEpochSeconds()): ImportResult {
        val array = requireArray(parseRoot(raw))
        val entities = toEntities(decodeList(array), nowSec)
        entities.chunked(UPSERT_CHUNK_SIZE).forEach { dao.upsertAll(it) }
        return ImportResult(
            parsed = array.size,
            unique = entities.size,
            upserted = entities.size,
        )
    }

    companion object {
        /**
         * 分批写入的批大小。
         * Room 生成的 upsert 会把 `VALUES` 展开成一条语句，2226 条一次性塞进去
         * 生成的 SQL 字符串会长到几十 KB；500 一批既不会太碎也没有长度风险。
         */
        const val UPSERT_CHUNK_SIZE = 500
    }
}

/**
 * 解析导出文件 → 本地实体列表。纯函数，不碰数据库，方便单测。
 *
 * @throws IllegalArgumentException 文件不是 JSON，或者里面找不到对局数组
 */
fun parseImportedMatches(raw: String, syncedAtSec: Long = 0L): List<MatchEntity> =
    toEntities(decodeList(requireArray(parseRoot(raw))), syncedAtSec)

/**
 * 导出文件专用的 [Json]。
 *
 * 三个开关都是必须的：导出方会随版本加字段（`ignoreUnknownKeys`）、
 * 早期版本里数字偶尔是字符串（`isLenient`）、`Rank` / `iFinTime` 会整条缺失
 * （`coerceInputValues`，缺失时落回默认值而不是整条反序列化失败）。
 */
internal val ImportJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

private fun parseRoot(raw: String): JsonElement =
    runCatching { ImportJson.parseToJsonElement(raw) }
        .getOrElse { throw IllegalArgumentException("不是合法的 JSON 文件") }

private fun requireArray(root: JsonElement): JsonArray =
    findFirstArray(root) ?: throw IllegalArgumentException("文件里没有找到对局数组")

private fun decodeList(array: JsonArray): List<ImportedMatchDto> =
    runCatching { ImportJson.decodeFromJsonElement<List<ImportedMatchDto>>(array) }
        .getOrElse { throw IllegalArgumentException("对局数组的结构对不上") }

private fun toEntities(
    dtos: List<ImportedMatchDto>,
    syncedAtSec: Long,
): List<MatchEntity> = dtos
    .asSequence()
    // 房间号是主键，0 说明这条是脏数据，写进去会在列表里留一行永远打不开的记录
    .filter { it.DsRoomId != 0L }
    .distinctBy { it.DsRoomId }
    .map { it.toEntity(syncedAtSec) }
    .toList()

/**
 * 从 JSON 树里找第一个数组。
 *
 * 导出格式一直在变，见过三种：顶层就是数组、包一层对象 `{"data":[...]}`、
 * 以及再包一层 `{"result":{"list":[...]}}`。与其让用户去改文件，不如往里翻两层。
 * 深度设 6 是够用就停——这不是通用 JSONPath，翻太深只会把报错变得难懂。
 */
private fun findFirstArray(root: JsonElement, maxDepth: Int = 6): JsonArray? {
    if (root is JsonArray) return root
    if (maxDepth <= 0 || root !is JsonObject) return null
    for (value in root.values) {
        findFirstArray(value, maxDepth - 1)?.let { return it }
    }
    return null
}
