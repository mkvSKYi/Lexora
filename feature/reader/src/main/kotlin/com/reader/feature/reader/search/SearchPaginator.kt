package com.reader.feature.reader.search

import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.search.SearchIterator

/** Walks a Readium [SearchIterator] page by page, invoking [onPage] with each page's locators. */
object SearchPaginator {
    suspend fun paginate(iterator: SearchIterator, cap: Int, onPage: (List<Locator>) -> Unit) {
        var collected = 0
        while (collected < cap) {
            val page = iterator.next().getOrNull() ?: break // failure or exhausted
            val locators = page.locators
            if (locators.isEmpty()) break
            onPage(locators)
            collected += locators.size
        }
    }
}
