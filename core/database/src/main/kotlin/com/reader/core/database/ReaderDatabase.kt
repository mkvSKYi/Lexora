package com.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reader.core.database.dao.BookDao
import com.reader.core.database.entity.BookEntity
import com.reader.core.database.entity.ReadingProgressEntity

@Database(
    entities = [BookEntity::class, ReadingProgressEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
