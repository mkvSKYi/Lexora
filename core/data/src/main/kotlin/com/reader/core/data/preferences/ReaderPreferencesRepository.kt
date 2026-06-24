package com.reader.core.data.preferences

import kotlinx.coroutines.flow.Flow

interface ReaderPreferencesRepository {
    fun observe(): Flow<ReaderPreferences>
    suspend fun setEpubPreferencesJson(json: String)
    suspend fun setBrightness(value: Float?)
    suspend fun setWarmth(value: Float)
}
