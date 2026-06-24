package com.reader.core.data.di

import com.reader.core.data.DefaultLibraryRepository
import com.reader.core.data.DefaultSavedWordsRepository
import com.reader.core.data.LibraryRepository
import com.reader.core.data.SavedWordsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: DefaultLibraryRepository): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindSavedWordsRepository(impl: DefaultSavedWordsRepository): SavedWordsRepository
}
