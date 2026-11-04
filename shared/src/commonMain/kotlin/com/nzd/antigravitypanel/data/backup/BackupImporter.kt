package com.nzd.antigravitypanel.data.backup

import com.nzd.antigravitypanel.data.db.ConfigCacheDao
import com.nzd.antigravitypanel.data.db.MatchBossStatDao
import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.store.KeyValueStore

/** 一次数据包导入到底搬了多少东西。设置页那条结果提示直接用这几个数。 */
data class BackupImportResult(
    val formatVersion: Int,
    val matchCount: Int,
    val detailCount: Int,
    val configCount: Int,
    val credentialCount: Int,
    val settingCount: Int,
    val cacheCount: Int,
)

/**
 * 「导入数据」：读本应用导出的 zip，把东西写回本机。
 *
 * 三条规矩：
 * - **合并不覆盖**：对局 / 详情 / 配置按主键 upsert，本机已有的数据不会因为
 *   导入一份老包而消失（换机时新机通常是空的，合并和覆盖没区别；但"导入回来补几场"
 *   这种用法下，覆盖是灾难）。
 * - **只认白名单键**：包是明文 JSON，用户随手就能改，不能让任意键写进加密存储。
 * - **先整包校验再写库**：先解包、先读清单、先确认这是哪个 app 的哪个版本，
 *   任何一步不对就整体失败，不留下"写了一半"的状态。
 */
class BackupImporter(
    private val store: KeyValueStore,
    private val matchDao: MatchDao,
    private val bossStatDao: MatchBossStatDao,
    private val configDao: ConfigCacheDao,
) {

    /**
     * @throws IllegalArgumentException 不是本应用的包、格式版本太新、或者内容损坏。
     *   消息是直接给用户看的中文，调用方原样展示即可。
     */
    suspend fun import(bytes: ByteArray): BackupImportResult {
        val files = unpackZip(bytes)

        val manifestRaw = files[FILE_MANIFEST]?.decodeText()
            ?: throw IllegalArgumentException("压缩包里没有 manifest.json，不是本应用导出的数据包")
        val manifest = runCatching { BackupJson.decodeFromString<BackupManifest>(manifestRaw) }
            .getOrElse { throw IllegalArgumentException("manifest.json 解析失败，文件可能已损坏") }

        if (manifest.app != BACKUP_APP_ID) {
            throw IllegalArgumentException("这不是《反重力数据面板》导出的数据包")
        }
        if (manifest.formatVersion > BACKUP_FORMAT_VERSION) {
            throw IllegalArgumentException(
                "数据包是 v${manifest.formatVersion} 格式，当前版本只认到 " +
                    "v$BACKUP_FORMAT_VERSION，请先把应用升级到最新版",
            )
        }

        val matches = decodeList<BackupMatch>(files, FILE_MATCHES)
            .filter { it.roomId.isNotBlank() }
            .distinctBy { it.roomId }
        matches.chunked(UPSERT_CHUNK_SIZE).forEach { chunk ->
            matchDao.upsertAll(chunk.map { it.toEntity() })
        }

        val details = decodeList<BackupMatchDetail>(files, FILE_MATCH_DETAILS)
            .filter { it.roomId.isNotBlank() }
            .distinctBy { it.roomId }
        details.chunked(UPSERT_CHUNK_SIZE).forEach { chunk ->
            bossStatDao.upsertAll(chunk.map { it.toEntity() })
        }

        val configs = decodeList<BackupConfig>(files, FILE_CONFIG)
            .filter { it.key.isNotBlank() && it.json.isNotBlank() }
        configs.forEach { configDao.put(it.toEntity()) }

        val credentials = writeAllowed(files, FILE_CREDENTIALS, BackupKeys.credentials)
        val settings = writeAllowed(files, FILE_SETTINGS, BackupKeys.settings)
        val caches = writeAllowed(files, FILE_CACHES, BackupKeys.caches)

        return BackupImportResult(
            formatVersion = manifest.formatVersion,
            matchCount = matches.size,
            detailCount = details.size,
            configCount = configs.size,
            credentialCount = credentials,
            settingCount = settings,
            cacheCount = caches,
        )
    }

    /** 写一组键值。不在 [allowed] 里的键一律忽略（包里可能被塞了别的东西）。 */
    private suspend fun writeAllowed(
        files: Map<String, ByteArray>,
        name: String,
        allowed: List<String>,
    ): Int {
        val raw = files[name]?.decodeText() ?: return 0
        val block = runCatching { BackupJson.decodeFromString<BackupKeyValues>(raw) }
            .getOrElse { throw IllegalArgumentException("$name 解析失败，文件可能已损坏") }

        var written = 0
        for ((key, value) in block.entries) {
            if (key !in allowed) continue
            // 空凭证不覆盖本机已有的：导出方没有这一套凭证时，包里是"没有这个键"，
            // 但手工改过 / 未来版本可能写成空串 —— 空串覆盖等于把本机登录态抹了。
            if (value.isBlank() && key in BackupKeys.credentials) continue
            store.write(key, value)
            written++
        }
        return written
    }

    private inline fun <reified T> decodeList(
        files: Map<String, ByteArray>,
        name: String,
    ): List<T> {
        val raw = files[name]?.decodeText() ?: return emptyList()
        return runCatching { BackupBulkJson.decodeFromString<List<T>>(raw) }
            .getOrElse { throw IllegalArgumentException("$name 解析失败，文件可能已损坏") }
    }

    companion object {
        /**
         * 分批写入的批大小。和 `JsonMatchImporter` 同一个值，理由也一样：Room 生成的
         * upsert 会把 `VALUES` 展开成一条语句，几千条一次性塞进去 SQL 会很长。
         */
        const val UPSERT_CHUNK_SIZE = 500
    }
}

/** UTF-8 解码并去掉 BOM：包在 Windows 上用记事本另存过就会带上它。 */
private fun ByteArray.decodeText(): String = decodeToString().removePrefix("\uFEFF")
