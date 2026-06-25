package com.reader.core.data.activity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Remembers the last day we celebrated the streak, so the confetti fires at most once per day. */
interface DashboardPreferencesRepository {
    suspend fun lastCelebratedDay(): Long
    suspend fun setLastCelebratedDay(day: Long)
}

class DataStoreDashboardPreferencesRepository(
    context: Context,
    storeName: String,
) : DashboardPreferencesRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, DEFAULT_STORE_NAME)

    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(storeName) },
        )

    override suspend fun lastCelebratedDay(): Long =
        dataStore.data.map { it[LAST_CELEBRATED_DAY] ?: Long.MIN_VALUE }.first()

    override suspend fun setLastCelebratedDay(day: Long) {
        dataStore.edit { it[LAST_CELEBRATED_DAY] = day }
    }

    private companion object {
        const val DEFAULT_STORE_NAME = "dashboard_prefs"
        val LAST_CELEBRATED_DAY = longPreferencesKey("last_celebrated_day")
    }
}
