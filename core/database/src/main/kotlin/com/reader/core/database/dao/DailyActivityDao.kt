package com.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.reader.core.database.entity.DailyActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyActivityDao {
    @Query("SELECT * FROM activity_days WHERE epochDay >= :fromEpochDay ORDER BY epochDay ASC")
    fun observeSince(fromEpochDay: Long): Flow<List<DailyActivityEntity>>

    @Query("SELECT * FROM activity_days WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): DailyActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyActivityEntity)

    /**
     * Folds a day's activity into its row. SQLite at minSdk 26 has no UPSERT (`ON CONFLICT DO
     * UPDATE`), so we read-merge-write inside a transaction: reading stays OR-ed, counts accumulate.
     */
    @Transaction
    suspend fun record(epochDay: Long, reading: Boolean, saved: Int, reviewed: Int) {
        val existing = get(epochDay)
        val merged = existing?.copy(
            readingActive = existing.readingActive || reading,
            wordsSaved = existing.wordsSaved + saved,
            wordsReviewed = existing.wordsReviewed + reviewed,
        ) ?: DailyActivityEntity(epochDay, reading, saved, reviewed)
        upsert(merged)
    }
}
