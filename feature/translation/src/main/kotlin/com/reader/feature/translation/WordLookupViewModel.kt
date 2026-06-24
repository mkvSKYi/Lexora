package com.reader.feature.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.core.dictionary.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordLookupViewModel @Inject constructor(
    private val dictionary: DictionaryRepository,
    private val engine: TranslationEngine,
) : ViewModel() {

    private val _lookupState = MutableStateFlow<WordLookupState?>(null)
    val lookupState: StateFlow<WordLookupState?> = _lookupState.asStateFlow()

    fun onWord(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        _lookupState.value = WordLookupState.Loading
        viewModelScope.launch {
            val entry = dictionary.lookup(w)
            if (entry != null) {
                // Show the dictionary article immediately (local lookup is instant), then fetch the
                // ML Kit translation alongside it so every word also has a Ukrainian translation.
                val base = WordLookupState.Entry(
                    word = entry.headword,
                    ipa = entry.ipa,
                    partOfSpeech = entry.partOfSpeech,
                    definitions = entry.definitions.distinct(),
                    translations = entry.translations.distinct(),
                    machineTranslation = null,
                    translationPending = true,
                )
                _lookupState.value = base
                val mt = if (engine.ensureModelsReady().isSuccess) {
                    engine.translate(w).getOrNull()
                } else {
                    null
                }
                // Only update if this entry is still the one shown (user hasn't dismissed/retapped).
                if (_lookupState.value === base) {
                    _lookupState.value = base.copy(machineTranslation = mt, translationPending = false)
                }
                return@launch
            }
            val ready = engine.ensureModelsReady()
            if (ready.isFailure) {
                _lookupState.value = WordLookupState.Error(
                    "Translation needs a one-time download. Connect to the internet and tap again.",
                )
                return@launch
            }
            engine.translate(w)
                .onSuccess { _lookupState.value = WordLookupState.Machine(w, it) }
                .onFailure { _lookupState.value = WordLookupState.Error("Couldn't translate. Try again.") }
        }
    }

    fun dismiss() { _lookupState.value = null }
}
