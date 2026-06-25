package com.reader.feature.saved.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.core.data.SavedWordsRepository
import com.reader.core.data.model.SavedWord
import com.reader.core.data.review.ReviewGrade
import com.reader.core.data.review.ReviewScheduler
import com.reader.core.data.review.ReviewState
import com.reader.core.data.xp.LexoraXp
import com.reader.core.data.xp.XpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SESSION_LIMIT = 20

@HiltViewModel
class ReviewSessionViewModel @Inject constructor(
    private val repo: SavedWordsRepository,
    private val xpRepository: XpRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ReviewSessionUiState>(ReviewSessionUiState.Loading)
    val state: StateFlow<ReviewSessionUiState> = _state.asStateFlow()

    private val queue = ArrayDeque<SavedWord>()
    private var total = 0
    private var reviewed = 0
    private var learned = 0

    init {
        viewModelScope.launch {
            queue.addAll(repo.getDueWords(now(), SESSION_LIMIT))
            total = queue.size
            emit()
        }
    }

    fun reveal() {
        val s = _state.value
        if (s is ReviewSessionUiState.Reviewing && !s.revealed) {
            _state.value = s.copy(revealed = true)
        }
    }

    fun grade(grade: ReviewGrade) {
        val current = queue.firstOrNull() ?: return
        viewModelScope.launch {
            val updated = repo.applyReview(current, grade, now())
            val graduated = updated.learned && !current.learned
            if (graduated) learned++
            queue.removeFirst()
            if (grade == ReviewGrade.AGAIN) {
                queue.addLast(updated)
            } else {
                reviewed++
                xpRepository.addXp(LexoraXp.XP_PER_REVIEW + if (graduated) LexoraXp.XP_LEARN_BONUS else 0)
            }
            emit()
        }
    }

    private fun emit() {
        val current = queue.firstOrNull()
        _state.value = when {
            current == null && total == 0 -> ReviewSessionUiState.Empty
            current == null -> ReviewSessionUiState.Summary(reviewed, learned)
            else -> {
                val st = ReviewState(current.easeFactor, current.intervalDays, current.repetitions)
                val nowT = now()
                ReviewSessionUiState.Reviewing(
                    card = current,
                    revealed = false,
                    position = reviewed + 1,
                    total = total,
                    againLabel = "<10m",
                    goodLabel = dayLabel(ReviewScheduler.schedule(st, ReviewGrade.GOOD, nowT).state.intervalDays),
                    easyLabel = dayLabel(ReviewScheduler.schedule(st, ReviewGrade.EASY, nowT).state.intervalDays),
                )
            }
        }
    }

    private fun dayLabel(days: Int): String = if (days >= 30) "${days / 30}mo" else "${days}d"

    private fun now(): Long = System.currentTimeMillis()
}
