package com.reader.core.data.activity

import com.reader.core.database.dao.DailyActivityDao
import com.reader.core.database.entity.DailyActivityEntity
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityRepositoryTest {

    private val dao = mockk<DailyActivityDao>(relaxed = true)
    private val today = TodayProvider { 19_876L }
    private val repo = DefaultActivityRepository(dao, today)

    @Test fun recordReading_marks_today_reading() = runTest {
        repo.recordReading()
        coVerify { dao.record(19_876L, reading = true, saved = 0, reviewed = 0) }
    }

    @Test fun recordWordSaved_increments_today_saved() = runTest {
        repo.recordWordSaved()
        coVerify { dao.record(19_876L, reading = false, saved = 1, reviewed = 0) }
    }

    @Test fun recordWordReviewed_increments_today_reviewed() = runTest {
        repo.recordWordReviewed()
        coVerify { dao.record(19_876L, reading = false, saved = 0, reviewed = 1) }
    }

    @Test fun observeSince_maps_entities_to_domain() = runTest {
        io.mockk.every { dao.observeSince(any()) } returns
            flowOf(listOf(DailyActivityEntity(19_876L, readingActive = true, wordsSaved = 2, wordsReviewed = 1)))

        val activity = repo.observeSince(19_800L).first().single()

        assertEquals(19_876L, activity.epochDay)
        assertEquals(true, activity.readingActive)
        assertEquals(4, activity.total)
        assertEquals(true, activity.isActive)
    }
}
