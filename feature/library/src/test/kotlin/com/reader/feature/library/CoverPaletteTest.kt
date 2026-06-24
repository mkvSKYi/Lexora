package com.reader.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPaletteTest {
    @Test fun deterministic_same_seed_same_gradient() {
        assertEquals(coverPalette("Atomic Habits"), coverPalette("Atomic Habits"))
    }

    @Test fun different_seeds_can_differ() {
        val distinct = (0..20).map { coverPalette("book-$it") }.toSet()
        assertTrue(distinct.size > 1)
    }

    @Test fun never_throws_on_empty_or_unicode() {
        assertNotEquals(0L, coverPalette("").top) // returns a real color
        coverPalette("Україна 📚") // no crash
    }
}
