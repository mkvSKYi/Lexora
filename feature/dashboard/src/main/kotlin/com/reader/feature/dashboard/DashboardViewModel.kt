package com.reader.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.core.data.LibraryRepository
import com.reader.core.data.SavedWordsRepository
import com.reader.core.data.activity.ActivityRepository
import com.reader.core.data.activity.DashboardPreferencesRepository
import com.reader.core.data.activity.StreakCalculator
import com.reader.core.data.activity.TodayProvider
import com.reader.core.data.model.BookWithProgress
import com.reader.core.data.model.DailyActivity
import com.reader.core.data.model.SavedWord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    activityRepository: ActivityRepository,
    savedWordsRepository: SavedWordsRepository,
    libraryRepository: LibraryRepository,
    private val dashboardPreferences: DashboardPreferencesRepository,
    private val today: TodayProvider,
) : ViewModel() {

    private val windowStart: Long = run {
        val now = today.epochDay()
        val mondayOfThisWeek = now - (LocalDate.ofEpochDay(now).dayOfWeek.value - 1)
        mondayOfThisWeek - (HEATMAP_WEEKS - 1) * 7L
    }

    private val _celebrateStreak = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Emits the streak length the first time the user becomes active on a given day. */
    val celebrateStreak: SharedFlow<Long> = _celebrateStreak.asSharedFlow()

    val state: StateFlow<DashboardUiState> = combine(
        activityRepository.observeSince(windowStart),
        savedWordsRepository.observe(),
        libraryRepository.observeBooksWithProgress(),
    ) { activity, words, books ->
        buildContent(activity, words, books)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)

    init {
        viewModelScope.launch {
            activityRepository.observeSince(windowStart).collect { activity ->
                val t = today.epochDay()
                val todayActive = activity.any { it.epochDay == t && it.isActive }
                if (todayActive && dashboardPreferences.lastCelebratedDay() < t) {
                    dashboardPreferences.setLastCelebratedDay(t)
                    val activeDays = activity.filter { it.isActive }.map { it.epochDay }.toSet()
                    _celebrateStreak.tryEmit(StreakCalculator.currentStreak(activeDays, t))
                }
            }
        }
    }

    private fun buildContent(
        activity: List<DailyActivity>,
        words: List<SavedWord>,
        books: List<BookWithProgress>,
    ): DashboardUiState.Content {
        val now = today.epochDay()
        val byDay = activity.associateBy { it.epochDay }
        val activeDays = activity.filter { it.isActive }.map { it.epochDay }.toSet()

        val heatmap = (0 until HEATMAP_DAYS).map { offset ->
            val day = windowStart + offset
            HeatCell(epochDay = day, level = bucket(byDay[day]?.total ?: 0), isFuture = day > now)
        }

        val nowMillis = System.currentTimeMillis()
        return DashboardUiState.Content(
            streak = StreakCalculator.currentStreak(activeDays, now),
            heatmap = heatmap,
            words = WordStats(
                total = words.size,
                learned = words.count { it.learned },
                due = words.count { !it.learned && it.dueAt <= nowMillis },
            ),
            books = BookStats(
                inProgress = books.count { it.percent > 0.0 && it.percent < FINISHED_THRESHOLD },
                finished = books.count { it.percent >= FINISHED_THRESHOLD },
            ),
            hasActivity = activeDays.isNotEmpty(),
        )
    }

    private fun bucket(total: Int): Int = when {
        total <= 0 -> 0
        total == 1 -> 1
        total == 2 -> 2
        total <= 4 -> 3
        else -> 4
    }

    companion object {
        const val HEATMAP_WEEKS = 13
        const val HEATMAP_DAYS = HEATMAP_WEEKS * 7
        const val FINISHED_THRESHOLD = 0.99
    }
}
