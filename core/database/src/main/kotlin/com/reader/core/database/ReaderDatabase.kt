package com.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reader.core.database.dao.BookDao
import com.reader.core.database.dao.BookmarkDao
import com.reader.core.database.dao.DailyActivityDao
import com.reader.core.database.dao.SavedWordDao
import com.reader.core.database.entity.BookEntity
import com.reader.core.database.entity.BookmarkEntity
import com.reader.core.database.entity.DailyActivityEntity
import com.reader.core.database.entity.ReadingProgressEntity
import com.reader.core.database.entity.SavedWordEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        SavedWordEntity::class,
        BookmarkEntity::class,
        DailyActivityEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun savedWordDao(): SavedWordDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun dailyActivityDao(): DailyActivityDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `saved_words` ADD COLUMN `easeFactor` REAL NOT NULL DEFAULT 2.5")
                db.execSQL("ALTER TABLE `saved_words` ADD COLUMN `intervalDays` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `saved_words` ADD COLUMN `repetitions` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `saved_words` ADD COLUMN `dueAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `saved_words` ADD COLUMN `lastReviewedAt` INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bookId` INTEGER NOT NULL, `locatorJson` TEXT NOT NULL, " +
                        "`href` TEXT NOT NULL, `progression` REAL NOT NULL, " +
                        "`totalProgression` REAL NOT NULL, `chapterTitle` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_days` (" +
                        "`epochDay` INTEGER PRIMARY KEY NOT NULL, " +
                        "`readingActive` INTEGER NOT NULL DEFAULT 0, " +
                        "`wordsSaved` INTEGER NOT NULL DEFAULT 0, " +
                        "`wordsReviewed` INTEGER NOT NULL DEFAULT 0)",
                )
            }
        }
    }
}
