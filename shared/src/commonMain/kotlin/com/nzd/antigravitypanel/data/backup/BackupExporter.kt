package com.nzd.antigravitypanel.data.backup

import com.nzd.antigravitypanel.data.db.ConfigCacheDao
import com.nzd.antigravitypanel.data.db.MatchBossStatDao
import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.store.KeyValueStore
import com.nzd.antigravitypanel.util.currentEpochSeconds
import kotlinx.serialization.encodeToString

/** 一份打好包的数据包。文件名交给 SAF 当用户没改名时的默认值。 */
class BackupFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val matchCount: Int,
    val detailCount: Int,
    /** 这份包里有没有凭证。UI 那句提示读它，不要在界面层自己猜。 */
    val includesCredentials: Boolean,
)

/**
 * 「导出数据」：把换机要用的东西打成一个 zip。
 *
 * 搬的东西分五类（见 [BackupKeys]）：凭证、设置、缓存、对局、单局详情，外加游戏配置表。
 * 全是**本地读**，一个网络请求都不发 —— 这个功能的场景就是"旧机已经登不上了，
 * 只想把东西搬到新机"。
 *
 * 为什么连缓存也搬：换机后第一帧就该是上次确认过的数字。不搬的话概览 / 签到 / 地图分布
 * 会先摆一份默认态，等同步跑完才跳过来，看着像"数据没迁过来"。
 */
class BackupExporter(
    private val store: KeyValueStore,
    private val matchDao: MatchDao,
    private val bossStatDao: MatchBossStatDao,
    private val configDao: ConfigCacheDao,
) {

    suspend fun build(
        nowSec: Long = currentEpochSeconds(),
        appVersion: String = "",
    ): BackupFile {
        // 三张表都各自兜一层：任何一张读失败都不该让整包导不出来，
        // 少一块总比"点了没反应"强（缺哪块导入侧按空处理）。
        val matches = runCatching { matchDao.all() }.getOrDefault(emptyList())
        val details = runCatching { bossStatDao.all() }.getOrDefault(emptyList())
        val configs = runCatching { configDao.all() }.getOrDefault(emptyList())

        val credentials = readKeys(BackupKeys.credentials)
        val settings = readKeys(BackupKeys.settings)
        val caches = readKeys(BackupKeys.caches)

        val manifest = BackupManifest(
            appVersion = appVersion,
            exportedAtSec = nowSec,
            matchCount = matches.size,
            detailCount = details.size,
            credentialsIncluded = credentials.isNotEmpty(),
        )

        val files = listOf(
            // 清单放第一个是刻意的：用解压软件打开时第一眼就能看到版本和场次
            ZipOutEntry(FILE_MANIFEST, BackupJson.encodeToString(manifest).encodeToByteArray()),
            ZipOutEntry(
                FILE_CREDENTIALS,
                BackupJson.encodeToString(BackupKeyValues(credentials)).encodeToByteArray(),
            ),
            ZipOutEntry(
                FILE_SETTINGS,
                BackupJson.encodeToString(BackupKeyValues(settings)).encodeToByteArray(),
            ),
            ZipOutEntry(
                FILE_CACHES,
                BackupJson.encodeToString(BackupKeyValues(caches)).encodeToByteArray(),
            ),
            ZipOutEntry(
                FILE_MATCHES,
                BackupBulkJson.encodeToString(matches.map { it.toBackupMatch() }).encodeToByteArray(),
            ),
            ZipOutEntry(
                FILE_MATCH_DETAILS,
                BackupBulkJson.encodeToString(details.map { it.toBackupDetail() }).encodeToByteArray(),
            ),
            ZipOutEntry(
                FILE_CONFIG,
                BackupBulkJson.encodeToString(configs.map { it.toBackupConfig() }).encodeToByteArray(),
            ),
        )

        return BackupFile(
            fileName = backupFileName(nowSec),
            mimeType = BACKUP_MIME_TYPE,
            bytes = packZip(files),
            matchCount = matches.size,
            detailCount = details.size,
            includesCredentials = credentials.isNotEmpty(),
        )
    }

    /**
     * 按白名单读一组键。
     *
     * 空串照样带上：`SCORE_WAN_TARGETS = ""` 有确切含义（三个页面都显示完整数字），
     * 丢掉它换机后就会变成默认的"用万"。没写过的键则不出现在结果里。
     */
    private suspend fun readKeys(keys: List<String>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (key in keys) {
            val value = runCatching { store.read(key) }.getOrNull() ?: continue
            result[key] = value
        }
        return result
    }
}
