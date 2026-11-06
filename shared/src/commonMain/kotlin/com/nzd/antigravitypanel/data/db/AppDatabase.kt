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

/**
 * 2 -> 3：给 `match_boss_stats` 加 `payloadJson`（单局详情的其余字段，见 [CachedMatchDetail]）。
 *
 * 只加一列，所以是 `ALTER TABLE ADD COLUMN` 而不是重建表。可空列不需要 DEFAULT，
 * SQLite 允许 `ADD COLUMN x TEXT` —— 老行的这一列就是 NULL，正好对上
 * "这份详情是升级前拉到的" 这个含义。
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SQLiteConnection) {
        db.prepare("ALTER TABLE `match_boss_stats` ADD COLUMN `payloadJson` TEXT").use { it.step() }
    }
}

/**
 * 3 -> 4：对局和详情都按凭证 openid 隔离，主键改为 `(ownerOpenId, roomId)`。
 *
 * 两表都重建而不是只加列：SQLite 不能直接用 ALTER TABLE 改主键。INSERT 明确列出
 * 所有旧列（包括 `payloadJson`），旧行先放到空 owner，之后由 `claimLegacyData` 一次性认领。
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SQLiteConnection) {
        fun exec(sql: String) {
            db.prepare(sql).use { it.step() }
        }

        exec("ALTER TABLE `matches` RENAME TO `matches_legacy`")
        exec(
            "CREATE TABLE `matches` (" +
                "`ownerOpenId` TEXT NOT NULL, " +
                "`roomId` TEXT NOT NULL, " +
                "`openId` TEXT NOT NULL, " +
                "`eventTime` TEXT NOT NULL, " +
                "`eventTimeSec` INTEGER NOT NULL, " +
                "`startTime` TEXT NOT NULL, " +
                "`areaId` TEXT NOT NULL, " +
                "`finTime` INTEGER NOT NULL, " +
                "`duration` INTEGER NOT NULL, " +
                "`isWin` INTEGER NOT NULL, " +
                "`gameMode` INTEGER NOT NULL, " +
                "`modeType` INTEGER NOT NULL, " +
                "`subModeType` INTEGER NOT NULL, " +
                "`mapId` INTEGER NOT NULL, " +
                "`score` INTEGER NOT NULL, " +
                "`seasonId` TEXT NOT NULL, " +
                "`kills` INTEGER NOT NULL, " +
                "`deaths` INTEGER NOT NULL, " +
                "`assists` INTEGER NOT NULL, " +
                "`rankValue` INTEGER NOT NULL, " +
                "`finished` INTEGER NOT NULL, " +
                "`syncedAtSec` INTEGER NOT NULL, " +
                "PRIMARY KEY(`ownerOpenId`, `roomId`)" +
                ")",
        )
        exec(
            "INSERT INTO `matches` (" +
                "`ownerOpenId`, `roomId`, `openId`, `eventTime`, `eventTimeSec`, `startTime`, " +
                "`areaId`, `finTime`, `duration`, `isWin`, `gameMode`, `modeType`, `subModeType`, " +
                "`mapId`, `score`, `seasonId`, `kills`, `deaths`, `assists`, `rankValue`, " +
                "`finished`, `syncedAtSec`" +
                ") SELECT '', " +
                "`roomId`, `openId`, `eventTime`, `eventTimeSec`, `startTime`, `areaId`, " +
                "`finTime`, `duration`, `isWin`, `gameMode`, `modeType`, `subModeType`, `mapId`, " +
                "`score`, `seasonId`, `kills`, `deaths`, `assists`, `rankValue`, `finished`, `syncedAtSec` " +
                "FROM `matches_legacy`",
        )
        exec("DROP TABLE `matches_legacy`")
        exec("CREATE INDEX IF NOT EXISTS `index_matches_ownerOpenId` ON `matches` (`ownerOpenId`)")
        exec("CREATE INDEX IF NOT EXISTS `index_matches_eventTimeSec` ON `matches` (`eventTimeSec`)")
        exec("CREATE INDEX IF NOT EXISTS `index_matches_mapId` ON `matches` (`mapId`)")
        exec("CREATE INDEX IF NOT EXISTS `index_matches_openId` ON `matches` (`openId`)")

        exec("ALTER TABLE `match_boss_stats` RENAME TO `match_boss_stats_legacy`")
        exec(
            "CREATE TABLE `match_boss_stats` (" +
                "`ownerOpenId` TEXT NOT NULL, " +
                "`roomId` TEXT NOT NULL, " +
                "`selfBossDamage` INTEGER NOT NULL, " +
                "`teamBossDamage` INTEGER NOT NULL, " +
                "`rivalBossDamage` INTEGER NOT NULL, " +
                "`updatedAtSec` INTEGER NOT NULL, " +
                "`payloadJson` TEXT, " +
                "PRIMARY KEY(`ownerOpenId`, `roomId`)" +
                ")",
        )
        exec(
            "INSERT INTO `match_boss_stats` (" +
                "`ownerOpenId`, `roomId`, `selfBossDamage`, `teamBossDamage`, " +
                "`rivalBossDamage`, `updatedAtSec`, `payloadJson`" +
                ") SELECT '', `roomId`, `selfBossDamage`, `teamBossDamage`, " +
                "`rivalBossDamage`, `updatedAtSec`, `payloadJson` FROM `match_boss_stats_legacy`",
        )
        exec("DROP TABLE `match_boss_stats_legacy`")
        exec(
            "CREATE INDEX IF NOT EXISTS `index_match_boss_stats_ownerOpenId` " +
                "ON `match_boss_stats` (`ownerOpenId`)",
        )
    }
}

@Database(
    entities = [MatchEntity::class, ConfigCacheEntity::class, MatchBossStatEntity::class],
    version = 4,
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
