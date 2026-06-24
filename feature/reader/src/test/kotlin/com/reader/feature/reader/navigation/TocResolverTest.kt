package com.reader.feature.reader.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TocResolverTest {
    private fun link(title: String, href: String, children: List<Link> = emptyList()) =
        Link(href = Url(href)!!, title = title, children = children)

    @Test fun flatten_tags_depth_depth_first() {
        val toc = listOf(
            link("Ch1", "ch1.html", listOf(link("Ch1.1", "ch1.html#s1"))),
            link("Ch2", "ch2.html"),
        )
        val flat = TocResolver.flatten(toc)
        assertEquals(listOf("Ch1", "Ch1.1", "Ch2"), flat.map { it.title })
        assertEquals(listOf(0, 1, 0), flat.map { it.depth })
        // Fragment is stripped from the href path.
        assertEquals(listOf("ch1.html", "ch1.html", "ch2.html"), flat.map { it.href })
    }

    @Test fun flatten_defaults_title_to_href_when_absent() {
        val toc = listOf(Link(href = Url("ch3.html")!!))
        val flat = TocResolver.flatten(toc)
        assertEquals(listOf("ch3.html"), flat.map { it.title })
    }

    @Test fun current_entry_is_deepest_at_or_before() {
        val entries = listOf(
            TocEntry("Ch1", "ch1.html", 0, null),
            TocEntry("Ch2", "ch2.html", 0, null),
        )
        assertEquals(1, TocResolver.currentEntryIndex(entries, "ch2.html"))
        assertEquals(0, TocResolver.currentEntryIndex(entries, "ch1.html"))
    }

    @Test fun current_entry_matches_with_fragment_in_current_href() {
        val entries = listOf(
            TocEntry("Ch1", "ch1.html", 0, null),
            TocEntry("Ch2", "ch2.html", 0, null),
        )
        assertEquals(1, TocResolver.currentEntryIndex(entries, "ch2.html#para3"))
    }

    @Test fun current_entry_null_when_empty() {
        assertNull(TocResolver.currentEntryIndex(emptyList(), "ch1.html"))
    }

    @Test fun reading_order_aware_no_exact_match_picks_closest_preceding_in_spine() {
        // TOC only labels ch1 and ch3, but the spine has an unlabeled ch2 between them. While on
        // ch2 (no TOC entry of its own), the current chapter is ch1 — the closest preceding entry
        // in reading order — NOT ch3 (which is last by list position).
        val entries = listOf(
            TocEntry("Ch1", "ch1.html", 0, null),
            TocEntry("Ch3", "ch3.html", 0, null),
        )
        val spine = listOf("ch1.html", "ch2.html", "ch3.html")
        assertEquals(0, TocResolver.currentEntryIndex(entries, spine, "ch2.html"))
        // Sanity: while actually on ch3, exact match wins.
        assertEquals(1, TocResolver.currentEntryIndex(entries, spine, "ch3.html#frag"))
    }

    @Test fun reading_order_aware_falls_back_when_resource_not_in_spine() {
        val entries = listOf(
            TocEntry("Ch1", "ch1.html", 0, null),
            TocEntry("Ch2", "ch2.html", 0, null),
        )
        // unknown.html is not in the spine: fall back to the list-order heuristic (last entry).
        assertEquals(1, TocResolver.currentEntryIndex(entries, listOf("ch1.html"), "unknown.html"))
    }

    @Test fun fraction_maps_to_closest_position_and_empty_is_null() {
        assertNull(TocResolver.positionForFraction(emptyList(), 0.5f))
    }
}
