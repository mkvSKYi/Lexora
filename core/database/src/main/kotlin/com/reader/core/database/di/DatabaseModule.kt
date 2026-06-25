package com.reader.core.database.di

import android.content.Context
import androidx.room.Room
import com.reader.core.database.ReaderDatabase
import com.reader.core.database.dao.BookDao
import com.reader.core.database.dao.SavedWordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideReaderDatabase(
        @ApplicationContext context: Context,
    ): ReaderDatabase =
        Room.databaseBuilder(
            context,
            ReaderDatabase::class.java,
            "reader.db",
        ).addMigrations(ReaderDatabase.MIGRATION_1_2, ReaderDatabase.MIGRATION_2_3).build()

    @Provides
    fun provideBookDao(database: ReaderDatabase): BookDao = database.bookDao()

    @Provides
    fun provideSavedWordDao(database: ReaderDatabase): SavedWordDao = database.savedWordDao()
}
