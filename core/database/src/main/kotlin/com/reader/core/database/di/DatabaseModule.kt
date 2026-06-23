package com.reader.core.database.di

import android.content.Context
import androidx.room.Room
import com.reader.core.database.ReaderDatabase
import com.reader.core.database.dao.BookDao
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
        ).build()

    @Provides
    fun provideBookDao(database: ReaderDatabase): BookDao = database.bookDao()
}
