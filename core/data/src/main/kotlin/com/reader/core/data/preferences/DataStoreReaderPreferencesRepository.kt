package com.reader.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreReaderPreferencesRepository(
    context: Context,
    storeName: String,
) : ReaderPreferencesRepository {

    // Hilt cannot satisfy a defaulted String parameter, so the injectable constructor takes only
    // the context and supplies the production store name; the two-arg constructor stays for tests.
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, DEFAULT_STORE_NAME)

    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(storeName) },
        )

    override fun observe(): Flow<ReaderPreferences> =
        dataStore.data.map { prefs ->
            ReaderPreferences(
                epubPreferencesJson = prefs[EPUB_PREFS],
                brightness = prefs[BRIGHTNESS],
                warmth = prefs[WARMTH] ?: 0f,
                highlightSavedWords = prefs[HIGHLIGHT_SAVED] ?: true,
            )
        }

    override suspend fun setEpubPreferencesJson(json: String) {
        dataStore.edit { it[EPUB_PREFS] = json }
    }

    override suspend fun setBrightness(value: Float?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(BRIGHTNESS) else prefs[BRIGHTNESS] = value
        }
    }

    override suspend fun setWarmth(value: Float) {
        dataStore.edit { it[WARMTH] = value }
    }

    override suspend fun setHighlightSavedWords(value: Boolean) {
        dataStore.edit { it[HIGHLIGHT_SAVED] = value }
    }

    private companion object {
        const val DEFAULT_STORE_NAME = "reader_prefs"
        val EPUB_PREFS = stringPreferencesKey("epub_prefs")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val WARMTH = floatPreferencesKey("warmth")
        val HIGHLIGHT_SAVED = booleanPreferencesKey("highlight_saved_words")
    }
}
