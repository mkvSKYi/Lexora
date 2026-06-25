package com.reader.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.core.data.SavedWordsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedWordsViewModel @Inject constructor(
    private val repo: SavedWordsRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(SavedWordsFilter.ALL)
    val filter: StateFlow<SavedWordsFilter> = _filter.asStateFlow()

    val uiState: StateFlow<SavedWordsUiState> =
        combine(repo.observe(), _filter) { words, filter ->
            val visible = when (filter) {
                SavedWordsFilter.ALL -> words
                SavedWordsFilter.LEARNING -> words.filter { !it.learned }
                SavedWordsFilter.LEARNED -> words.filter { it.learned }
            }
            SavedWordsUiState.Content(
                words = visible,
                learnedCount = words.count { it.learned },
                totalCount = words.size,
                dueCount = words.count { !it.learned && it.dueAt <= System.currentTimeMillis() },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedWordsUiState.Loading)

    fun setFilter(filter: SavedWordsFilter) { _filter.value = filter }

    fun toggleLearned(id: Long, learned: Boolean) {
        viewModelScope.launch { repo.markLearned(id, learned) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
