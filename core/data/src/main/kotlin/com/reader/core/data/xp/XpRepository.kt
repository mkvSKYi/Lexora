package com.reader.core.data.xp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Lifetime experience points the user has earned. Spent only on levelling up the XP bar. */
interface XpRepository {
    fun observeTotalXp(): Flow<Int>
    suspend fun addXp(amount: Int)
}

class DataStoreXpRepository(
    context: Context,
    storeName: String,
) : XpRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, DEFAULT_STORE_NAME)

    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(storeName) },
        )

    override fun observeTotalXp(): Flow<Int> = dataStore.data.map { it[TOTAL_XP] ?: 0 }

    override suspend fun addXp(amount: Int) {
        if (amount <= 0) return
        dataStore.edit { it[TOTAL_XP] = (it[TOTAL_XP] ?: 0) + amount }
    }

    private companion object {
        const val DEFAULT_STORE_NAME = "xp_prefs"
        val TOTAL_XP = intPreferencesKey("total_xp")
    }
}

/** A level and how far into it the user is, derived from lifetime XP. */
data class LevelInfo(val level: Int, val xpIntoLevel: Int, val xpForLevel: Int) {
    val progress: Float get() = if (xpForLevel > 0) xpIntoLevel.toFloat() / xpForLevel else 0f
}

object LexoraXp {
    const val PER_LEVEL = 100
    const val XP_PER_REVIEW = 10
    const val XP_LEARN_BONUS = 15

    fun levelInfo(totalXp: Int): LevelInfo {
        val safe = totalXp.coerceAtLeast(0)
        return LevelInfo(level = safe / PER_LEVEL + 1, xpIntoLevel = safe % PER_LEVEL, xpForLevel = PER_LEVEL)
    }
}
