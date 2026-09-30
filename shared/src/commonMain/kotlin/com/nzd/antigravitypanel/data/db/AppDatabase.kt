package com.nzd.antigravitypanel.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

const val DATABASE_FILE_NAME = "antigravity.db"

/**
 * 1 -> 2：加 `match_boss_stats`（历史页两个标签的缓存，见 [MatchBossStatEntity]）。
 *
 * 手写而不是用 AutoMigration：`exportSchema = false`，自动迁移要拿导出的 schema 做校验，
 * 关了之后它没法验证，而这里就一条 CREATE TABLE，手写反而更保险。
 *
 * ⚠️ 列名和 [MatchBossStatEntity] 必须逐字对上：Room 迁移完会拿实际表结构去比
 * 实体，差一个字就抛 `Migration didn't properly handle`。
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SQLiteConnection) {
        // ⚠️ KMP 的 SQLiteConnection **没有 execSQL**，只能 prepare + step。
        // 别忘了 use{}：语句不关的话连接会被占住，后面 Room 自己校验表结构时会卡住。
        db.prepare(
            "CREATE TABLE IF NOT EXISTS `match_boss_stats` (" +
                "`roomId` TEXT NOT NULL, " +
                "`selfBossDamage` INTEGER NOT NULL, " +
                "`teamBossDamage` INTEGER NOT NULL, " +
                "`rivalBossDamage` INTEGER NOT NULL, " +
                "`updatedAtSec` INTEGER NOT NULL, " +
                "PRIMARY KEY(`roomId`)" +
                ")",
        ).use { it.step() }
    }
}

@Database(
    entities = [MatchEntity::class, ConfigCacheEntity::class, MatchBossStatEntity::class],
    version = 2,
    exportSchema = false,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun matchDao(): MatchDao

    abstract fun configCacheDao(): ConfigCacheDao

    abstract fun matchBossStatDao(): MatchBossStatDao
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
