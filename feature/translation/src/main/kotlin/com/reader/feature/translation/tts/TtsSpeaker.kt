package com.reader.feature.translation.tts

import kotlinx.coroutines.flow.StateFlow

/** Speaks short English text aloud via on-device TTS. */
interface TtsSpeaker {
    /** True once the engine is initialized and an English voice is usable. */
    val available: StateFlow<Boolean>

    /** Speaks [text] in English, interrupting any current utterance. No-op until [available]. */
    fun speak(text: String)
}
