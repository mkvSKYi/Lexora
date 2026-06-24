package com.reader.feature.reader.navigation

import kotlin.math.abs
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

/**
 * Pure chapter/position math over Readium types: flattens a TOC tree, finds the current
 * chapter from a resource href, and maps a progression fraction to a position.
 */
object TocResolver {

    /**
     * Depth-first flatten of a TOC tree. Each [Link.children] list is recursed at `depth + 1`.
     * The entry [TocEntry.title] defaults to the href path when the link has no title, and the
     * [TocEntry.href] is the link's href path with any `#fragment` stripped. [TocEntry.locator]
     * is always null here; positions are resolved in a later step.
     */
    fun flatten(links: List<Link>, depth: Int = 0): List<TocEntry> =
        links.flatMap { link ->
            val href = link.href.toString().stripFragment()
            val entry = TocEntry(
                title = link.title ?: href,
                href = href,
                depth = depth,
                locator = null,
            )
            listOf(entry) + flatten(link.children, depth + 1)
        }

    /**
     * The index of the deepest/last entry whose href is at or before [currentHref] (matched by
     * path, fragment stripped). Prefers an exact href match; otherwise falls back to the last
     * entry that appears at or before the current href in [entries] order. Returns null when
     * [entries] is empty or nothing matches.
     */
    fun currentEntryIndex(entries: List<TocEntry>, currentHref: String): Int? {
        if (entries.isEmpty()) return null
        val target = currentHref.stripFragment()

        val exact = entries.indexOfLast { it.href == target }
        if (exact >= 0) return exact

        // No exact match: fall back to the last entry whose href is a path prefix of the current
        // resource (e.g. an anchored sub-resource), else the last entry overall as the closest
        // preceding chapter.
        val prefix = entries.indexOfLast { target.startsWith(it.href) }
        return if (prefix >= 0) prefix else entries.lastIndex
    }

    /**
     * The position whose [Locator.locations] `totalProgression` is closest to
     * `fraction.coerceIn(0f, 1f)`. Returns null when [positions] is empty.
     */
    fun positionForFraction(positions: List<Locator>, fraction: Float): Locator? {
        if (positions.isEmpty()) return null
        val target = fraction.coerceIn(0f, 1f).toDouble()
        return positions.minByOrNull { abs((it.locations.totalProgression ?: 0.0) - target) }
    }

    private fun String.stripFragment(): String = substringBefore('#')
}
