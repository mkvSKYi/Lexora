package com.reader.core.data.imports

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EpubImporterTest {
    @Test
    fun parses_title_and_author_from_epub() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File("src/test/resources/sample.epub")
        val meta = EpubMetadataParser(context).parse(file)
        assertEquals("Sample Book", meta.title)
        assertEquals("Test Author", meta.author)
    }
}
