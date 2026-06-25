package com.reader.core.data.di

import com.reader.core.data.BookmarksRepository
import com.reader.core.data.DefaultBookmarksRepository
import com.reader.core.data.DefaultLibraryRepository
import com.reader.core.data.DefaultSavedWordsRepository
import com.reader.core.data.LibraryRepository
import com.reader.core.data.SavedWordsRepository
import com.reader.core.data.activity.ActivityRepository
import com.reader.core.data.activity.DefaultActivityRepository
import com.reader.core.data.activity.TodayProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
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

    @Binds
    @Singleton
    abstract fun bindBookmarksRepository(impl: DefaultBookmarksRepository): BookmarksRepository

    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: DefaultActivityRepository): ActivityRepository

    companion object {
        @Provides
        fun provideTodayProvider(): TodayProvider = DefaultActivityRepository.SystemToday
    }
}
