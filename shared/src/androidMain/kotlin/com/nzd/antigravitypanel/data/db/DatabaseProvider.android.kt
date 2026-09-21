package com.nzd.antigravitypanel.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.nzd.antigravitypanel.requireContext

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val ctx = requireContext()
    val dbFile = ctx.getDatabasePath(DATABASE_FILE_NAME)
    return Room.databaseBuilder<AppDatabase>(
        context = ctx,
        name = dbFile.absolutePath,
    )
}
