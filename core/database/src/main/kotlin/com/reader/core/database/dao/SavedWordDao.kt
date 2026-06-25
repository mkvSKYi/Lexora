package com.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reader.core.database.entity.SavedWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedWordDao {
    @Query("SELECT * FROM saved_words ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedWordEntity>>

    // INSERT OR REPLACE honors the unique (term, bookId) index for dedup-replace,
    // which @Upsert does not (it only resolves conflicts on the primary key).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedWordEntity)

    @Query("DELETE FROM saved_words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_words WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_words WHERE term = :term AND bookId = :bookId)")
    suspend fun existsByTermAndBook(term: String, bookId: Long): Boolean

    @Query("UPDATE saved_words SET learned = :learned WHERE id = :id")
    suspend fun updateLearned(id: Long, learned: Boolean)

    @Query("SELECT * FROM saved_words WHERE learned = 0 AND dueAt <= :now ORDER BY dueAt ASC LIMIT :limit")
    suspend fun getDueWords(now: Long, limit: Int): List<SavedWordEntity>

    @Query(
        "UPDATE saved_words SET easeFactor = :easeFactor, intervalDays = :intervalDays, " +
            "repetitions = :repetitions, dueAt = :dueAt, lastReviewedAt = :lastReviewedAt, " +
            "learned = :learned WHERE id = :id",
    )
    suspend fun updateSchedule(
        id: Long,
        easeFactor: Double,
        intervalDays: Int,
        repetitions: Int,
        dueAt: Long,
        lastReviewedAt: Long?,
        learned: Boolean,
    )
}
