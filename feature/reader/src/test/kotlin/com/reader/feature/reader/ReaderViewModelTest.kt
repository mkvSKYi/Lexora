package com.reader.feature.reader

import com.reader.core.data.LibraryRepository
import com.reader.core.data.SavedWordsRepository
import com.reader.core.data.model.ReadingProgress
import com.reader.core.data.model.SavedWord
import com.reader.core.data.preferences.ReaderPreferences
import com.reader.core.data.preferences.ReaderPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import org.readium.r2.shared.publication.Publication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalReadiumApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelTest {
    private val repo = mockk<LibraryRepository>(relaxed = true)
    private val savedWords = mockk<SavedWordsRepository>(relaxed = true)
    private val bookmarks = mockk<com.reader.core.data.BookmarksRepository>(relaxed = true)

    /** In-memory fake so we can observe persisted JSON and feed values back through [observe]. */
    private class FakePreferencesRepository(
        initialJson: String? = null,
    ) : ReaderPreferencesRepository {
        val state = MutableStateFlow(
            ReaderPreferences(epubPreferencesJson = initialJson, brightness = null, warmth = 0f),
        )
        override fun observe(): Flow<ReaderPreferences> = state
        override suspend fun setEpubPreferencesJson(json: String) {
            state.value = state.value.copy(epubPreferencesJson = json)
        }
        override suspend fun setBrightness(value: Float?) {}
        override suspend fun setWarmth(value: Float) {}
        override suspend fun setHighlightSavedWords(value: Boolean) {}
        override suspend fun setLockRotation(value: Boolean) {}
    }

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        io.mockk.every { bookmarks.observe(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun onLocatorChanged_persists_progress() = runTest {
        val vm = ReaderViewModel(repo, mockk(relaxed = true), FakePreferencesRepository(), savedWords, bookmarks)
        val locator = Locator(
            href = org.readium.r2.shared.util.Url("ch1.html")!!,
            mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML,
            locations = Locator.Locations(totalProgression = 0.5),
        )
        vm.onLocatorChanged(bookId = 3L, locator = locator)
        advanceUntilIdle()
        coVerify { repo.saveProgress(match<ReadingProgress> { it.bookId == 3L && it.percent == 0.5 }) }
    }

    @Test fun load_twice_closes_previous_publication() = runTest {
        val opener = mockk<PublicationOpener>()
        val firstPublication = mockk<Publication>(relaxed = true)
        val secondPublication = mockk<Publication>(relaxed = true)
        coEvery { opener.open(any()) } returnsMany listOf(firstPublication, secondPublication)
        val vm = ReaderViewModel(repo, opener, FakePreferencesRepository(), savedWords, bookmarks)

        vm.load(bookId = 1L)
        advanceUntilIdle()
        vm.load(bookId = 1L)
        advanceUntilIdle()

        verify { firstPublication.close() }
    }

    @Test fun updateEpubPreferences_persists_serialized_json_round_trips() = runTest {
        val fakePrefs = FakePreferencesRepository()
        val vm = ReaderViewModel(repo, mockk(relaxed = true), fakePrefs, savedWords, bookmarks)
        advanceUntilIdle()

        vm.updateEpubPreferences(EpubPreferences(theme = Theme.DARK))
        advanceUntilIdle()

        // The optimistic update is reflected in the exposed StateFlow.
        assertEquals(Theme.DARK, vm.epubPreferences.value.theme)
        // The repo received a serialized payload that round-trips back to DARK.
        val persisted = fakePrefs.state.value.epubPreferencesJson
        assertEquals(true, persisted != null && persisted.contains("dark"))
    }

    @Test fun malformed_persisted_json_falls_back_to_defaults() = runTest {
        val vm = ReaderViewModel(repo, mockk(relaxed = true), FakePreferencesRepository("not json"), savedWords, bookmarks)
        advanceUntilIdle()

        // Never crashes; emits default EpubPreferences (no theme set).
        assertEquals(EpubPreferences(), vm.epubPreferences.value)
    }

    @Test fun onLocatorChanged_updates_progression() = runTest {
        val vm = ReaderViewModel(repo, mockk(relaxed = true), FakePreferencesRepository(), savedWords, bookmarks)
        vm.onLocatorChanged(bookId = 1L, locator = locatorAt("ch1.html", 0.42))
        assertEquals(0.42f, vm.currentProgression.value)
    }

    @Test fun saveCurrentWord_persists_word_with_book_metadata() = runTest {
        val opener = mockk<PublicationOpener>()
        coEvery { opener.open(any()) } returns mockk<Publication>(relaxed = true)
        coEvery { repo.getBook(7L) } returns com.reader.core.data.model.Book(
            id = 7L,
            title = "My Book",
            author = null,
            coverPath = null,
            filePath = "/tmp/b.epub",
            addedAt = 0L,
            lastOpenedAt = null,
        )
        val vm = ReaderViewModel(repo, opener, FakePreferencesRepository(), savedWords, bookmarks)
        vm.load(bookId = 7L)
        advanceUntilIdle()

        vm.saveCurrentWord("hund", "dog", "Der Hund läuft.")
        advanceUntilIdle()

        coVerify {
            savedWords.save(
                match<SavedWord> {
                    it.id == 0L && it.term == "hund" && it.translation == "dog" &&
                        it.contextSentence == "Der Hund läuft." && it.bookId == 7L &&
                        it.bookTitle == "My Book"
                },
            )
        }
    }

    @Test fun goTo_emits_navigate_request() = runTest {
        val vm = ReaderViewModel(repo, mockk(relaxed = true), FakePreferencesRepository(), savedWords, bookmarks)
        val emitted = mutableListOf<Locator>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            vm.navigateRequests.collect { emitted.add(it) }
        }

        val target = locatorAt("ch2.html", 0.5)
        vm.goTo(target)
        advanceUntilIdle()

        assertEquals(listOf(target), emitted)
        // goTo optimistically updates progression too.
        assertEquals(0.5f, vm.currentProgression.value)
    }

    private fun locatorAt(href: String, progression: Double) = Locator(
        href = org.readium.r2.shared.util.Url(href)!!,
        mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML,
        locations = Locator.Locations(totalProgression = progression),
    )

    /** A locator with an explicit within-resource [progression] (what bookmark matching uses). */
    private fun locatorWithin(href: String, progression: Double, total: Double) = Locator(
        href = org.readium.r2.shared.util.Url(href)!!,
        mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML,
        locations = Locator.Locations(progression = progression, totalProgression = total),
    )

    private fun bookmark(id: Long, href: String, progression: Double) =
        com.reader.core.data.model.Bookmark(
            id = id, bookId = 3L, locatorJson = "{}", href = href, progression = progression,
            totalProgression = 0.25, chapterTitle = null, createdAt = 0L,
        )

    @Test fun toggleBookmark_adds_when_none_matches() = runTest {
        io.mockk.every { bookmarks.observe(3L) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        val vm = ReaderViewModel(repo, mockk(relaxed = true), FakePreferencesRepository(), savedWords, bookmarks)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            vm.bookmarks.collect {}
        }
        vm.load(bookId = 3L)
        vm.onLocatorChanged(bookId = 3L, locator = locatorWithin("ch1.html", 0.5, 0.25))
        advanceUntilIdle()

        vm.toggleBookmark()
        advanceUntilIdle()

        coVerify {
            bookmarks.add(
                match<com.reader.core.data.model.Bookmark> {
                    it.bookId == 3L && it.href == "ch1.html" && it.progression == 0.5
                },
            )
        }
    }

    @Test fun toggleBookmark_deletes_when_one_matches() = runTest {
        io.mockk.every { bookmarks.observe(3L) } returns
            kotlinx.coroutines.flow.flowOf(listOf(bookmark(id = 9L, href = "ch1.html", progression = 0.5)))
        val vm = ReaderViewModel(repo, mockk(relaxed = true), FakePreferencesRepository(), savedWords, bookmarks)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            vm.bookmarks.collect {}
        }
        vm.load(bookId = 3L)
        // within epsilon (0.02) of the stored 0.5
        vm.onLocatorChanged(bookId = 3L, locator = locatorWithin("ch1.html", 0.51, 0.25))
        advanceUntilIdle()

        vm.toggleBookmark()
        advanceUntilIdle()

        coVerify { bookmarks.delete(9L) }
    }

    @Test fun isCurrentBookmarked_reflects_match() = runTest {
        io.mockk.every { bookmarks.observe(3L) } returns
            kotlinx.coroutines.flow.flowOf(listOf(bookmark(id = 9L, href = "ch1.html", progression = 0.5)))
        val vm = ReaderViewModel(repo, mockk(relaxed = true), FakePreferencesRepository(), savedWords, bookmarks)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            vm.isCurrentBookmarked.collect {}
        }
        vm.load(bookId = 3L)
        vm.onLocatorChanged(bookId = 3L, locator = locatorWithin("ch1.html", 0.5, 0.25))
        advanceUntilIdle()

        assertEquals(true, vm.isCurrentBookmarked.value)
    }

    @Test fun returning_to_a_resource_restores_the_left_position() = runTest {
        val vm = ReaderViewModel(repo, mockk(relaxed = true), FakePreferencesRepository(), savedWords, bookmarks)
        val nav = mutableListOf<Locator>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            vm.navigateRequests.collect { nav.add(it) }
        }
        // Read resource A near its top, then swipe to B.
        vm.onLocatorChanged(bookId = 1L, locator = locatorWithin("a.html", 0.10, 0.10))
        vm.onLocatorChanged(bookId = 1L, locator = locatorWithin("b.html", 0.00, 0.20))
        // Swipe back to A: Readium drops us at the end (0.99).
        vm.onLocatorChanged(bookId = 1L, locator = locatorWithin("a.html", 0.99, 0.18))
        advanceUntilIdle()

        // The VM jumped A back to where we left it (0.10), not the forced end.
        assertEquals(1, nav.size)
        assertEquals(0.10, nav.last().locations.progression!!, 1e-9)
    }

    @Test fun deliberate_jump_is_not_overridden_by_restore() = runTest {
        val vm = ReaderViewModel(repo, mockk(relaxed = true), FakePreferencesRepository(), savedWords, bookmarks)
        val nav = mutableListOf<Locator>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            vm.navigateRequests.collect { nav.add(it) }
        }
        vm.onLocatorChanged(bookId = 1L, locator = locatorWithin("a.html", 0.10, 0.10)) // visited A at 0.10
        vm.onLocatorChanged(bookId = 1L, locator = locatorWithin("b.html", 0.00, 0.20)) // now on B
        vm.goTo(locatorWithin("a.html", 0.60, 0.14))                                     // deliberate jump to A@0.60
        vm.onLocatorChanged(bookId = 1L, locator = locatorWithin("a.html", 0.60, 0.14)) // navigator confirms A@0.60
        advanceUntilIdle()

        // Only the deliberate goTo emitted; the restore did NOT override it back to 0.10.
        assertEquals(1, nav.size)
        assertEquals(0.60, nav.last().locations.progression!!, 1e-9)
    }
}
