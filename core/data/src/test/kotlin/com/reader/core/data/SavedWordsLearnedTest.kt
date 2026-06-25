package com.reader.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.reader.core.data.model.SavedWord
import com.reader.core.database.ReaderDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedWordsLearnedTest {
    private lateinit var db: ReaderDatabase
    private lateinit var repo: DefaultSavedWordsRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ReaderDatabase::class.java).allowMainThreadQueries().build()
        repo = DefaultSavedWordsRepository(db.savedWordDao())
    }
    @After fun tearDown() = db.close()

    @Test fun markLearned_updates_the_row() = runTest {
        repo.save(SavedWord(0, "dog", "собака", null, 1, "Book", 5, learned = false))
        val id = repo.observe().first().first().id
        repo.markLearned(id, true)
        assertTrue(repo.observe().first().first().learned)
    }
}
