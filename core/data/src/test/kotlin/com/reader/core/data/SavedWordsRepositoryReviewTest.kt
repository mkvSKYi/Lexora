package com.reader.core.data

import com.reader.core.data.model.SavedWord
import com.reader.core.data.review.ReviewGrade
import com.reader.core.database.dao.SavedWordDao
import com.reader.core.database.entity.SavedWordEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedWordsRepositoryReviewTest {
    private val dao = mockk<SavedWordDao>(relaxed = true)
    private val repo = DefaultSavedWordsRepository(dao, FakeActivityRepository())

    private val t = 1_000_000_000_000L

    private fun word(
        id: Long,
        ease: Double = 2.5,
        interval: Int = 0,
        reps: Int = 0,
        learned: Boolean = false,
    ) = SavedWord(
        id, "room", "кімната", null, 7, "Atomic Habits", 100, learned,
        easeFactor = ease, intervalDays = interval, repetitions = reps, dueAt = 0, lastReviewedAt = null,
    )

    @Test fun getDueWords_mapsEntitiesToDomain() = runTest {
        coEvery { dao.getDueWords(t, 20) } returns listOf(
            SavedWordEntity(3, "dog", "собака", null, 1, "Dune", 100, dueAt = 500),
        )
        val result = repo.getDueWords(t, 20)
        assertEquals(1, result.size)
        assertEquals(3L, result.first().id)
        assertEquals("dog", result.first().term)
    }

    @Test fun applyReview_good_schedulesAndReturnsUpdated() = runTest {
        val updated = repo.applyReview(word(1), ReviewGrade.GOOD, t)
        assertEquals(1, updated.intervalDays)
        assertEquals(1, updated.repetitions)
        assertEquals(t, updated.lastReviewedAt)
        coVerify {
            dao.updateSchedule(
                id = 1,
                easeFactor = 2.5,
                intervalDays = 1,
                repetitions = 1,
                dueAt = updated.dueAt,
                lastReviewedAt = t,
                learned = false,
            )
        }
    }

    @Test fun applyReview_graduation_setsLearned() = runTest {
        val updated = repo.applyReview(word(1, interval = 15, reps = 3), ReviewGrade.GOOD, t)
        assertTrue(updated.intervalDays >= 21)
        assertTrue(updated.learned)
        coVerify {
            dao.updateSchedule(
                id = 1,
                easeFactor = any(),
                intervalDays = updated.intervalDays,
                repetitions = 4,
                dueAt = updated.dueAt,
                lastReviewedAt = t,
                learned = true,
            )
        }
    }
}
