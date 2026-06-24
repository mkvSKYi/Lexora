package com.reader.feature.reader

import com.reader.core.data.LibraryRepository
import com.reader.core.data.model.ReadingProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import org.readium.r2.shared.publication.Publication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelTest {
    private val repo = mockk<LibraryRepository>(relaxed = true)

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun onLocatorChanged_persists_progress() = runTest {
        val vm = ReaderViewModel(repo, mockk(relaxed = true))
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
        val vm = ReaderViewModel(repo, opener)

        vm.load(bookId = 1L)
        advanceUntilIdle()
        vm.load(bookId = 1L)
        advanceUntilIdle()

        verify { firstPublication.close() }
    }
}
