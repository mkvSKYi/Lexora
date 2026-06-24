package com.reader.core.dictionary.di

import com.reader.core.dictionary.DictionaryRepository
import com.reader.core.dictionary.SqliteDictionaryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DictionaryModule {
    @Binds
    @Singleton
    abstract fun bindDictionaryRepository(impl: SqliteDictionaryRepository): DictionaryRepository
}
