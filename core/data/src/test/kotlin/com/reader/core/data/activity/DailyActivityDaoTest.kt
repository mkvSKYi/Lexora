package com.reader.core.data.activity

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.reader.core.database.ReaderDatabase
import com.reader.core.database.dao.DailyActivityDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DailyActivityDaoTest {
    private lateinit var db: ReaderDatabase
    private lateinit var dao: DailyActivityDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ReaderDatabase::class.java).allowMainThreadQueries().build()
        dao = db.dailyActivityDao()
    }

    @After fun tearDown() = db.close()

    @Test fun record_accumulates_counts_and_ors_reading_for_the_same_day() = runTest {
        dao.record(100L, reading = false, saved = 1, reviewed = 0)
        dao.record(100L, reading = true, saved = 1, reviewed = 0)
        dao.record(100L, reading = false, saved = 0, reviewed = 3)

        val row = dao.get(100L)!!
        assertEquals(true, row.readingActive)
        assertEquals(2, row.wordsSaved)
        assertEquals(3, row.wordsReviewed)
    }

    @Test fun record_keeps_days_separate() = runTest {
        dao.record(100L, reading = true, saved = 0, reviewed = 0)
        dao.record(101L, reading = false, saved = 5, reviewed = 0)

        assertEquals(true, dao.get(100L)!!.readingActive)
        assertEquals(5, dao.get(101L)!!.wordsSaved)
    }

    @Test fun observeSince_returns_only_days_in_range_sorted() = runTest {
        dao.record(98L, reading = true, saved = 0, reviewed = 0)
        dao.record(100L, reading = true, saved = 0, reviewed = 0)
        dao.record(102L, reading = true, saved = 0, reviewed = 0)

        val days = dao.observeSince(100L).first().map { it.epochDay }
        assertEquals(listOf(100L, 102L), days)
    }
}
