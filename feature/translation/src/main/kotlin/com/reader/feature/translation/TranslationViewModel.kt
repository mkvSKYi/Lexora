package com.reader.feature.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TranslationViewModel @Inject constructor(
    private val engine: TranslationEngine,
) : ViewModel() {

    private val _popupState = MutableStateFlow<TranslationPopupState?>(null)
    val popupState: StateFlow<TranslationPopupState?> = _popupState.asStateFlow()

    // Tracks the in-flight translation so a fresh tap cancels the previous one (last tap wins).
    private var translateJob: Job? = null

    fun onTextSelected(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        translateJob?.cancel()
        _popupState.value = TranslationPopupState.Loading
        translateJob = viewModelScope.launch {
            val ready = engine.ensureModelsReady()
            if (ready.isFailure) {
                _popupState.value = TranslationPopupState.Error(
                    "Translation needs a one-time download. Connect to the internet and tap again.",
                )
                return@launch
            }
            engine.translate(trimmed)
                .onSuccess { _popupState.value = TranslationPopupState.Result(trimmed, it) }
                .onFailure { _popupState.value = TranslationPopupState.Error("Couldn't translate. Try again.") }
        }
    }

    fun dismiss() {
        _popupState.value = null
    }
}
