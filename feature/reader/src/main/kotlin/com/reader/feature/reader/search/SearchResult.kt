package com.reader.feature.reader.search

import org.readium.r2.shared.publication.Locator

/** One full-text search hit: the position + the surrounding snippet + the chapter it falls in. */
data class SearchResult(
    val locator: Locator,
    val before: String,
    val highlight: String,
    val after: String,
    val chapterTitle: String?,
)

/** Maps a Readium result [locator] to a [SearchResult], resolving its chapter via [resolveChapter]. */
fun toSearchResult(locator: Locator, resolveChapter: (String) -> String?): SearchResult =
    SearchResult(
        locator = locator,
        before = locator.text.before.orEmpty(),
        highlight = locator.text.highlight.orEmpty(),
        after = locator.text.after.orEmpty(),
        chapterTitle = resolveChapter(locator.href.toString()),
    )
