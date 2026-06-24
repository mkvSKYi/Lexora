package com.reader.feature.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitTranslationEngine @Inject constructor() : TranslationEngine {

    private val translator: Translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.UKRAINIAN)
                .build(),
        )
    }

    override suspend fun ensureModelsReady(): Result<Unit> = runCatching {
        translator.downloadModelIfNeeded().await()
    }

    override suspend fun translate(text: String): Result<String> = runCatching {
        translator.translate(text).await()
    }
}
