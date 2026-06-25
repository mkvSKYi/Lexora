package com.reader.core.data

import com.reader.core.data.mapper.toDomain
import com.reader.core.data.mapper.toEntity
import com.reader.core.data.model.SavedWord
import com.reader.core.data.review.ReviewGrade
import com.reader.core.data.review.ReviewScheduler
import com.reader.core.data.review.ReviewState
import com.reader.core.database.dao.SavedWordDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface SavedWordsRepository {
    fun observe(): Flow<List<SavedWord>>
    suspend fun save(word: SavedWord)
    suspend fun delete(id: Long)
    suspend fun markLearned(id: Long, learned: Boolean)
    suspend fun getDueWords(now: Long, limit: Int): List<SavedWord>
    suspend fun applyReview(word: SavedWord, grade: ReviewGrade, now: Long): SavedWord
}

class DefaultSavedWordsRepository @Inject constructor(
    private val dao: SavedWordDao,
) : SavedWordsRepository {
    override fun observe(): Flow<List<SavedWord>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(word: SavedWord) = dao.upsert(word.toEntity())
    override suspend fun delete(id: Long) = dao.deleteById(id)
    override suspend fun markLearned(id: Long, learned: Boolean) = dao.updateLearned(id, learned)

    override suspend fun getDueWords(now: Long, limit: Int): List<SavedWord> =
        dao.getDueWords(now, limit).map { it.toDomain() }

    override suspend fun applyReview(word: SavedWord, grade: ReviewGrade, now: Long): SavedWord {
        val result = ReviewScheduler.schedule(
            ReviewState(word.easeFactor, word.intervalDays, word.repetitions), grade, now,
        )
        val updated = word.copy(
            easeFactor = result.state.easeFactor,
            intervalDays = result.state.intervalDays,
            repetitions = result.state.repetitions,
            dueAt = result.dueAt,
            lastReviewedAt = now,
            learned = word.learned || result.graduated,
        )
        dao.updateSchedule(
            updated.id, updated.easeFactor, updated.intervalDays, updated.repetitions,
            updated.dueAt, updated.lastReviewedAt, updated.learned,
        )
        return updated
    }
}
