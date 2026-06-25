package com.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reader.core.database.dao.BookDao
import com.reader.core.database.dao.SavedWordDao
import com.reader.core.database.entity.BookEntity
import com.reader.core.database.entity.ReadingProgressEntity
import com.reader.core.database.entity.SavedWordEntity

@Database(
    entities = [BookEntity::class, ReadingProgressEntity::class, SavedWordEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun savedWordDao(): SavedWordDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_words` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`term` TEXT NOT NULL, `translation` TEXT NOT NULL, " +
                        "`contextSentence` TEXT, `bookId` INTEGER NOT NULL, " +
                        "`bookTitle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_words_term_bookId` " +
                        "ON `saved_words` (`term`, `bookId`)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `saved_words` ADD COLUMN `learned` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
