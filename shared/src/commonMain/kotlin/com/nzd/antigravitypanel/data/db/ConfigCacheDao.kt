package com.nzd.antigravitypanel.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ConfigCacheDao {

    @Query("SELECT * FROM config_cache WHERE cacheKey = :key")
    suspend fun get(key: String): ConfigCacheEntity?

    @Upsert
    suspend fun put(entity: ConfigCacheEntity)

    @Query("DELETE FROM config_cache WHERE cacheKey = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM config_cache")
    suspend fun clear()
}
