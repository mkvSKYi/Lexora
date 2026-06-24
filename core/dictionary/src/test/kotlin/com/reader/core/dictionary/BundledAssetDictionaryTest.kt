package com.reader.core.dictionary

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the REAL bundled `assets/dictionary.db` (generated in Task 3) end-to-end:
 * this exercises the production copy-from-assets path in [AssetDatabase] (no test
 * override seam), proving the shipped asset is a valid, queryable SQLite DB.
 */
@RunWith(RobolectricTestRunner::class)
class BundledAssetDictionaryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun bundled_asset_returns_real_entry_for_common_word() = runTest {
        // No openOverride => copies + opens the production assets/dictionary.db.
        val repo = SqliteDictionaryRepository(context)
        val entry = repo.lookup("dog")
        assertNotNull("Expected the bundled asset to contain 'dog'", entry)
        assertTrue(
            "Expected Ukrainian translations for 'dog' from the bundled asset",
            entry!!.translations.isNotEmpty(),
        )
    }
}
