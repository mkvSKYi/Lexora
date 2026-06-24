package com.reader.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.core.data.SavedWordsRepository
import com.reader.core.data.model.SavedWord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedWordsViewModel @Inject constructor(
    private val repo: SavedWordsRepository,
) : ViewModel() {

    val uiState: StateFlow<SavedWordsUiState> =
        repo.observe()
            .map<List<SavedWord>, SavedWordsUiState> { SavedWordsUiState.Content(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SavedWordsUiState.Loading,
            )

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
