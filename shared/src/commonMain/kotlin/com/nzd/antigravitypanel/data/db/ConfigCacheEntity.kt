package com.nzd.antigravitypanel.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 配置缓存。
 *
 * `center.config.list` 一次下发 29 张地图 + 16 个难度 + 145 个猎场分区，体积不小。
 * 落库后冷启动可以先渲染本地快照，等网络回来再覆盖，避免每次开屏都空着。
 * 存的是原始 JSON 字符串，不拆表——这些结构官方随时会加字段，拆表只会让升级变痛。
 */
@Entity(tableName = "config_cache")
data class ConfigCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "cacheKey")
    val cacheKey: String,
    @ColumnInfo(name = "json")
    val json: String,
    @ColumnInfo(name = "updatedAtSec")
    val updatedAtSec: Long,
)

/** 缓存键。写成常量而不是散落在调用处的字符串字面量。 */
object ConfigCacheKey {
    const val GAME_CONFIG = "game_config"
    const val COLLECTION_HOME = "collection_home"
}
