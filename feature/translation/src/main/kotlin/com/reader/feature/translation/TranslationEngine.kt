package com.reader.feature.translation

interface TranslationEngine {
    /** Ensure the EN→UK models are downloaded and ready. */
    suspend fun ensureModelsReady(): Result<Unit>

    /** Translate English [text] to Ukrainian. Assumes models are ready. */
    suspend fun translate(text: String): Result<String>
}
