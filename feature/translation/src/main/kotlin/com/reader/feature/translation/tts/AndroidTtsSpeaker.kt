package com.reader.feature.translation.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [TextToSpeech]. Initializes asynchronously and reports readiness via [available]; speaks
 * English headwords. Lives for the app's lifetime (the native engine holds a small resource).
 */
@Singleton
class AndroidTtsSpeaker @Inject constructor(
    @ApplicationContext context: Context,
) : TtsSpeaker {

    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            _available.value = result >= TextToSpeech.LANG_AVAILABLE
        }
    }

    override fun speak(text: String) {
        val t = text.trim()
        if (t.isEmpty() || !_available.value) return
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "lex-word")
    }
}
