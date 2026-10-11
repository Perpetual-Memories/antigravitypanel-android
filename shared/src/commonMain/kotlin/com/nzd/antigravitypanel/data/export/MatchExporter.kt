package com.nzd.antigravitypanel.data.export

import com.nzd.antigravitypanel.data.db.MatchDao
import com.nzd.antigravitypanel.data.repo.MatchBossStats
import com.nzd.antigravitypanel.util.currentEpochSeconds

/**
 * 导出历史战绩：把本地库里的对局 + 已缓存的单局详情拼成一个文件的内容。
 *
 * 数据源全是本地的，**一个网络请求都不发**：
 * - 对局列表来自 `matches` 表（同步时落的、或者导入 JSON 来的）；
 * - 单局详情来自 `match_boss_stats.payloadJson`，只有打开过详情页（或被历史页后台补过）
 *   的对局才有，没有的那些行后半段留空 —— 这是接口形态决定的，
 *   为了导出几千行去逐局现拉既没有那么多配额，也会被服务端当成刷接口。
 */
class MatchExporter(
    private val matchDao: MatchDao,
    private val bossStats: MatchBossStats,
) {

    suspend fun build(
        format: MatchExportFormat,
        nowSec: Long = currentEpochSeconds(),
    ): MatchExportFile {
        val matches = runCatching { matchDao.all() }.getOrDefault(emptyList())
        val cached = bossStats.snapshot()
        val entries = exportEntries(matches, cached)
        return MatchExportFile(
            fileName = matchExportFileName(format, nowSec),
            mimeType = format.mimeType,
            content = if (format == MatchExportFormat.JSON) toJson(entries) else toCsv(entries),
            matchCount = entries.size,
            detailCount = entries.count { it.detail != null },
        )
    }
}
