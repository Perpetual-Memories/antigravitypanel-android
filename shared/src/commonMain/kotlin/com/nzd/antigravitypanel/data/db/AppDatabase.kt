package com.nzd.antigravitypanel.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

const val DATABASE_FILE_NAME = "antigravity.db"

@Database(
    entities = [MatchEntity::class, ConfigCacheEntity::class],
    version = 1,
    exportSchema = false,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun matchDao(): MatchDao

    abstract fun configCacheDao(): ConfigCacheDao
}

/**
 * Room KMP 的构造器桥接。
 *
 * `actual` 由 KSP 生成，所以这里看不到实现——Kotlin 编译器的 "no actual" 报错要压掉。
 * 这也是 KMP 下唯一必须挂 `@ConstructedBy` 的地方，少了它运行时会找不到 Impl。
 */
@Suppress(
    "NO_ACTUAL_FOR_EXPECT",
    "KotlinNoActualForExpect",
    "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA",
    "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING",
)
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
