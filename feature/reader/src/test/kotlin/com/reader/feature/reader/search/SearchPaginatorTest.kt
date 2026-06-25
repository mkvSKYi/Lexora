@file:OptIn(ExperimentalReadiumApi::class)

package com.reader.feature.reader.search

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.shared.ExperimentalReadiumApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.LocatorCollection
import org.readium.r2.shared.publication.services.search.SearchError
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

private fun loc(href: String, hl: String) = Locator(
    href = Url(href)!!,
    mediaType = MediaType.XHTML,
    text = Locator.Text(before = "before ", highlight = hl, after = " after"),
)

/** Yields each page from [pages] once, then null (exhausted). A null entry models a failure/empty stop. */
private class FakeSearchIterator(private val pages: List<List<Locator>?>) : SearchIterator {
    private var i = 0
    override val resultCount: Int? = null
    override suspend fun next(): Try<LocatorCollection?, SearchError> {
        val page = pages.getOrNull(i++) ?: return Try.success(null)
        return Try.success(page?.let { LocatorCollection(locators = it) })
    }
    override fun close() {}
}

@RunWith(RobolectricTestRunner::class)
class SearchPaginatorTest {
    @Test fun accumulates_all_pages_until_exhausted() = runTest {
        val it = FakeSearchIterator(listOf(listOf(loc("a", "x")), listOf(loc("b", "y"), loc("c", "z"))))
        val collected = mutableListOf<Locator>()
        SearchPaginator.paginate(it, cap = 100) { collected += it }
        assertEquals(3, collected.size)
    }

    @Test fun stops_at_cap() = runTest {
        val it = FakeSearchIterator(listOf(listOf(loc("a", "x"), loc("b", "y")), listOf(loc("c", "z"))))
        var pages = 0
        SearchPaginator.paginate(it, cap = 2) { pages++ }
        assertEquals(1, pages) // after page 1 reaches the cap, page 2 is not fetched
    }

    @Test fun stops_on_empty_or_null_page() = runTest {
        val it = FakeSearchIterator(listOf(listOf(loc("a", "x")), null))
        var pages = 0
        SearchPaginator.paginate(it, cap = 100) { pages++ }
        assertEquals(1, pages)
    }

    @Test fun toSearchResult_maps_text_and_chapter() {
        val r = toSearchResult(loc("ch1.html", "match")) { href -> if (href.contains("ch1")) "Chapter 1" else null }
        assertEquals("match", r.highlight)
        assertEquals("before ", r.before)
        assertEquals(" after", r.after)
        assertEquals("Chapter 1", r.chapterTitle)
    }
}
