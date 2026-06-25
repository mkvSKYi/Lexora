package com.reader.feature.reader.highlight

import com.reader.core.data.model.SavedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedWordHighlighterTest {
    private fun word(term: String, learned: Boolean) =
        SavedWord(0, term, "x", null, 1, "B", 0, learned)

    @Test fun learningTerms_keeps_only_not_learned_lowercased() {
        val terms = SavedWordHighlighter.learningTerms(
            listOf(word("Room", false), word("Cat", true), word("sun", false)),
        )
        assertEquals(setOf("room", "sun"), terms)
    }

    @Test fun termsJson_sorted_array_when_enabled() {
        assertEquals("[\"cat\",\"room\"]", SavedWordHighlighter.termsJson(HighlightState(setOf("room", "cat"), true)))
    }

    @Test fun termsJson_empty_when_disabled() {
        assertEquals("[]", SavedWordHighlighter.termsJson(HighlightState(setOf("room"), false)))
    }

    @Test fun termsJson_empty_when_no_terms() {
        assertEquals("[]", SavedWordHighlighter.termsJson(HighlightState(emptySet(), true)))
    }

    @Test fun termsJson_escapes_quotes_and_backslashes() {
        val json = SavedWordHighlighter.termsJson(HighlightState(setOf("a\"b\\c"), true))
        assertEquals("[\"a\\\"b\\\\c\"]", json)
    }

    @Test fun script_carries_accent_and_underline_css() {
        val js = SavedWordHighlighter.script(HighlightState(setOf("room"), true))
        assertTrue(js.contains("text-decoration:underline"))
        assertTrue(js.contains("#9B8CFF"))
        assertTrue(js.contains("[\"room\"]"))
    }

    @Test fun script_disabled_embeds_empty_terms() {
        val js = SavedWordHighlighter.script(HighlightState(setOf("room"), false))
        assertTrue(js.contains("var __lexTerms=[];"))
        assertFalse(js.contains("\"room\""))
    }
}
