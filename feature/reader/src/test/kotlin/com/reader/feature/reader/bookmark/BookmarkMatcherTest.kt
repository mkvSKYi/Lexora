package com.reader.feature.reader.bookmark

import com.reader.core.data.model.Bookmark
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkMatcherTest {
    private fun bm(href: String, prog: Double) = Bookmark(1, 7, "{}", href, prog, 0.0, null, 0)

    @Test fun same_href_within_epsilon_matches() {
        assertTrue(BookmarkMatcher.matches("ch1.html", 0.50, bm("ch1.html", 0.51), 0.02))
    }
    @Test fun different_href_does_not_match() {
        assertFalse(BookmarkMatcher.matches("ch2.html", 0.50, bm("ch1.html", 0.50), 0.02))
    }
    @Test fun progression_beyond_epsilon_does_not_match() {
        assertFalse(BookmarkMatcher.matches("ch1.html", 0.50, bm("ch1.html", 0.55), 0.02))
    }
    @Test fun null_href_does_not_match() {
        assertFalse(BookmarkMatcher.matches(null, 0.50, bm("ch1.html", 0.50), 0.02))
    }
    @Test fun null_progression_treated_as_zero() {
        assertTrue(BookmarkMatcher.matches("ch1.html", null, bm("ch1.html", 0.0), 0.02))
    }
}
