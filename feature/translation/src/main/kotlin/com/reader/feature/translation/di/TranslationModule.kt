package com.reader.feature.translation.di

import com.reader.feature.translation.MlKitTranslationEngine
import com.reader.feature.translation.TranslationEngine
import com.reader.feature.translation.tts.AndroidTtsSpeaker
import com.reader.feature.translation.tts.TtsSpeaker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationModule {
    @Binds
    @Singleton
    abstract fun bindTranslationEngine(impl: MlKitTranslationEngine): TranslationEngine

    @Binds
    @Singleton
    abstract fun bindTtsSpeaker(impl: AndroidTtsSpeaker): TtsSpeaker
}
