package com.reader.core.dictionary

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DictionaryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    // Point the repository at the test DB file directly via a seam (see Step 4).
    private fun repo(): SqliteDictionaryRepository {
        val dbFile = File("src/test/resources/test_dictionary.db").absoluteFile
        return SqliteDictionaryRepository(context, openOverride = dbFile)
    }

    @Test fun exact_headword_lookup() = runTest {
        val e = repo().lookup("Dog")!!  // normalized to "dog"
        assertEquals("dog", e.headword)
        assertEquals("/dɒɡ/", e.ipa)
        assertEquals("noun", e.partOfSpeech)
        assertEquals(listOf("A domesticated carnivore."), e.definitions)
        assertEquals(listOf("собака"), e.translations)
    }

    @Test fun inflected_form_resolves_via_forms() = runTest {
        val e = repo().lookup("running")!!
        assertEquals("run", e.headword)
        assertEquals(listOf("бігти"), e.translations)
    }

    @Test fun missing_word_returns_null() = runTest {
        assertNull(repo().lookup("zzzznotaword"))
    }
}
