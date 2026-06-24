package com.reader.feature.translation

import app.cash.turbine.test
import com.reader.core.dictionary.DictionaryEntry
import com.reader.core.dictionary.DictionaryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordLookupViewModelTest {
    private val dictionary = mockk<DictionaryRepository>()
    private val engine = mockk<TranslationEngine>()

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun dictionary_hit_emits_entry_deduped() = runTest {
        coEvery { dictionary.lookup("dog") } returns DictionaryEntry(
            "dog", "/dɒɡ/", "noun", listOf("An animal.", "An animal."), listOf("собака", "собака", "пес"),
        )
        val vm = WordLookupViewModel(dictionary, engine)
        vm.lookupState.test {
            assertNull(awaitItem())
            vm.onWord("dog")
            assertEquals(WordLookupState.Loading, awaitItem())
            val e = awaitItem()
            assertTrue(e is WordLookupState.Entry)
            e as WordLookupState.Entry
            assertEquals(listOf("An animal."), e.definitions)        // deduped
            assertEquals(listOf("собака", "пес"), e.translations)    // deduped, order kept
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun dictionary_miss_falls_back_to_machine_translation() = runTest {
        coEvery { dictionary.lookup("xylophone") } returns null
        coEvery { engine.ensureModelsReady() } returns Result.success(Unit)
        coEvery { engine.translate("xylophone") } returns Result.success("ксилофон")
        val vm = WordLookupViewModel(dictionary, engine)
        vm.lookupState.test {
            assertNull(awaitItem())
            vm.onWord("xylophone")
            assertEquals(WordLookupState.Loading, awaitItem())
            assertEquals(WordLookupState.Machine("xylophone", "ксилофон"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun blank_word_is_ignored() = runTest {
        val vm = WordLookupViewModel(dictionary, engine)
        vm.lookupState.test {
            assertNull(awaitItem())
            vm.onWord("   ")
            expectNoEvents()
        }
    }
}
