package com.reader.core.data.preferences.di

import com.reader.core.data.preferences.DataStoreReaderPreferencesRepository
import com.reader.core.data.preferences.ReaderPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {
    @Binds
    @Singleton
    abstract fun bindReaderPreferencesRepository(
        impl: DataStoreReaderPreferencesRepository,
    ): ReaderPreferencesRepository
}
