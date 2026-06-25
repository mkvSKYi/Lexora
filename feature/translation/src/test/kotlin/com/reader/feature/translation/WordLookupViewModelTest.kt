package com.reader.feature.translation

import app.cash.turbine.test
import com.reader.core.dictionary.DictionaryEntry
import com.reader.core.dictionary.DictionaryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val tts = mockk<com.reader.feature.translation.tts.TtsSpeaker>(relaxed = true)

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun dictionary_hit_with_translations_skips_ml_kit() = runTest {
        coEvery { dictionary.lookup("dog") } returns DictionaryEntry(
            "dog", "/dɒɡ/", "noun", listOf("An animal.", "An animal."), listOf("собака", "собака", "пес"),
        )
        val vm = WordLookupViewModel(dictionary, engine, tts)
        vm.lookupState.test {
            assertNull(awaitItem())
            vm.onWord("dog")
            assertEquals(WordLookupState.Loading, awaitItem())
            // The dictionary already has Ukrainian translations: one Entry, no ML Kit, no pending.
            val entry = awaitItem() as WordLookupState.Entry
            assertEquals(listOf("An animal."), entry.definitions)         // deduped
            assertEquals(listOf("собака", "пес"), entry.translations)     // deduped, order kept
            assertEquals(false, entry.translationPending)
            assertNull(entry.machineTranslation)
            expectNoEvents()
        }
        coVerify(exactly = 0) { engine.translate(any()) }
    }

    @Test fun dictionary_hit_with_failed_translation_still_shows_entry() = runTest {
        coEvery { dictionary.lookup("dog") } returns DictionaryEntry(
            "dog", null, null, listOf("An animal."), emptyList(),
        )
        coEvery { engine.ensureModelsReady() } returns Result.success(Unit)
        coEvery { engine.translate("dog") } returns Result.failure(RuntimeException("offline"))
        val vm = WordLookupViewModel(dictionary, engine, tts)
        vm.lookupState.test {
            assertNull(awaitItem())
            vm.onWord("dog")
            assertEquals(WordLookupState.Loading, awaitItem())
            awaitItem() // pending entry
            val resolved = awaitItem() as WordLookupState.Entry
            assertNull(resolved.machineTranslation)                       // MT failed → just omitted
            assertEquals(false, resolved.translationPending)
            assertEquals(listOf("An animal."), resolved.definitions)      // entry still shown
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun dictionary_miss_falls_back_to_machine_translation() = runTest {
        coEvery { dictionary.lookup("xylophone") } returns null
        coEvery { engine.ensureModelsReady() } returns Result.success(Unit)
        coEvery { engine.translate("xylophone") } returns Result.success("ксилофон")
        val vm = WordLookupViewModel(dictionary, engine, tts)
        vm.lookupState.test {
            assertNull(awaitItem())
            vm.onWord("xylophone")
            assertEquals(WordLookupState.Loading, awaitItem())
            assertEquals(WordLookupState.Machine("xylophone", "ксилофон"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun blank_word_is_ignored() = runTest {
        val vm = WordLookupViewModel(dictionary, engine, tts)
        vm.lookupState.test {
            assertNull(awaitItem())
            vm.onWord("   ")
            expectNoEvents()
        }
    }

    @Test fun speak_delegates_to_tts() = runTest {
        every { tts.available } returns MutableStateFlow(true)
        val vm = WordLookupViewModel(dictionary, engine, tts)
        vm.speak("dog")
        verify { tts.speak("dog") }
    }

    @Test fun ttsAvailable_proxies_speaker() = runTest {
        every { tts.available } returns MutableStateFlow(true)
        val vm = WordLookupViewModel(dictionary, engine, tts)
        assertTrue(vm.ttsAvailable.value)
    }
}
