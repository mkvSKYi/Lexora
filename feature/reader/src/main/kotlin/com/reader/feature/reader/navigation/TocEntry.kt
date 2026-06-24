package com.reader.feature.reader.navigation

import org.readium.r2.shared.publication.Locator

/**
 * A flat, depth-tagged table-of-contents item.
 *
 * @property title the entry label (defaults to the href when the source link has no title).
 * @property href the resource href path with any `#fragment` stripped.
 * @property depth nesting level in the original TOC tree (0 for top-level).
 * @property locator the resolved reading position; null until resolved against a publication.
 */
data class TocEntry(
    val title: String,
    val href: String,
    val depth: Int,
    val locator: Locator?,
)
