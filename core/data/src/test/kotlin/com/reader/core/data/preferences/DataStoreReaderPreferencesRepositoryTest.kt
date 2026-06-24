package com.reader.core.data.preferences

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreReaderPreferencesRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    // Unique store name per test instance: Robolectric reuses the app files dir
    // across tests in a run, so a shared DataStore file would leak state.
    private val repo = DataStoreReaderPreferencesRepository(
        context,
        storeName = "reader_prefs_test_${System.nanoTime()}",
    )

    @Test fun defaults_when_empty() = runTest {
        repo.observe().test {
            val p = awaitItem()
            assertNull(p.epubPreferencesJson)
            assertNull(p.brightness)
            assertEquals(0f, p.warmth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun persists_each_field() = runTest {
        repo.setEpubPreferencesJson("""{"theme":"dark"}""")
        repo.setBrightness(0.4f)
        repo.setWarmth(0.3f)
        repo.observe().test {
            val p = awaitItem()
            assertEquals("""{"theme":"dark"}""", p.epubPreferencesJson)
            assertEquals(0.4f, p.brightness)
            assertEquals(0.3f, p.warmth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun setting_brightness_null_clears_it() = runTest {
        repo.setBrightness(0.5f)
        repo.setWarmth(0.2f)
        repo.setBrightness(null)
        repo.observe().test {
            val p = awaitItem()
            assertNull(p.brightness)
            assertEquals(0.2f, p.warmth)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
