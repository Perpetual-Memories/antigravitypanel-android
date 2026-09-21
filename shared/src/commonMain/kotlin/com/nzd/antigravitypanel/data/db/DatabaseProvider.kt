package com.nzd.antigravitypanel.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * 各平台自己造 builder：Android 需要 `Context` 才能拿到数据库路径，commonMain 没有。
 *
 * 返回的是 **未配置驱动** 的 builder，驱动和协程上下文统一在 [DatabaseProvider] 里加，
 * 免得每个平台各写一遍、哪天漏了就出现"Android 用系统 SQLite、别处用 bundled"的不一致。
 */
expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun get(): AppDatabase = instance ?: synchronized(this) {
        instance ?: getDatabaseBuilder()
            // bundled：SQLite 从源码编译进包里，版本与系统脱钩，行为跨平台一致
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
            .also { instance = it }
    }

    /** 仅供测试：把一个替身塞进来。 */
    fun override(instance: AppDatabase?) {
        this.instance = instance
    }
}
