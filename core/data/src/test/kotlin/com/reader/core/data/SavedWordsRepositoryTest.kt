package com.reader.core.data

import app.cash.turbine.test
import com.reader.core.data.model.SavedWord
import com.reader.core.database.dao.SavedWordDao
import com.reader.core.database.entity.SavedWordEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedWordsRepositoryTest {
    private val dao = mockk<SavedWordDao>(relaxed = true)
    private val repo = DefaultSavedWordsRepository(dao)

    @Test fun observe_maps_entities_to_domain() = runTest {
        coEvery { dao.observeAll() } returns flowOf(
            listOf(SavedWordEntity(5, "dog", "собака", "A dog.", 1, "Dune", 100)),
        )
        repo.observe().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("dog", list.first().term)
            assertEquals(5L, list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun save_and_delete_delegate() = runTest {
        repo.save(SavedWord(0, "dog", "собака", null, 1, "Dune", 100))
        coVerify { dao.upsert(any()) }
        repo.delete(5)
        coVerify { dao.deleteById(5) }
    }
}
