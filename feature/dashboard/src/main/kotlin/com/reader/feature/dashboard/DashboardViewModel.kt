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
import com.reader.core.data.xp.XpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.TextStyle
import java.util.Locale
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
    xpRepository: XpRepository,
    private val dashboardPreferences: DashboardPreferencesRepository,
    private val today: TodayProvider,
) : ViewModel() {

    // The heatmap shows the CURRENT calendar month, week-aligned (Mon..Sun), so leading/trailing
    // days from neighbouring months render muted.
    private val monthDate = LocalDate.ofEpochDay(today.epochDay())
    private val heatmapStart: Long = monthDate.withDayOfMonth(1)
        .let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }.toEpochDay()
    private val heatmapDays: Int = run {
        val firstOfMonth = monthDate.withDayOfMonth(1)
        val lastOfMonth = firstOfMonth.plusMonths(1).minusDays(1)
        val gridEnd = lastOfMonth.plusDays((7 - lastOfMonth.dayOfWeek.value).toLong())
        (gridEnd.toEpochDay() - heatmapStart + 1).toInt()
    }

    private val _celebrateStreak = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Emits the streak length the first time the user becomes active on a given day. */
    val celebrateStreak: SharedFlow<Long> = _celebrateStreak.asSharedFlow()

    val state: StateFlow<DashboardUiState> = combine(
        activityRepository.observeSince(heatmapStart),
        savedWordsRepository.observe(),
        libraryRepository.observeBooksWithProgress(),
        xpRepository.observeTotalXp(),
    ) { activity, words, books, totalXp ->
        buildContent(activity, words, books, totalXp)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)

    init {
        viewModelScope.launch {
            activityRepository.observeSince(heatmapStart).collect { activity ->
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
        totalXp: Int,
    ): DashboardUiState.Content {
        val now = today.epochDay()
        val nowMonth = LocalDate.ofEpochDay(now).monthValue
        val byDay = activity.associateBy { it.epochDay }
        val activeDays = activity.filter { it.isActive }.map { it.epochDay }.toSet()

        val heatmap = (0 until heatmapDays).map { offset ->
            val day = heatmapStart + offset
            val muted = day > now || LocalDate.ofEpochDay(day).monthValue != nowMonth
            HeatCell(epochDay = day, level = bucket(byDay[day]?.total ?: 0), muted = muted)
        }

        val nowMillis = System.currentTimeMillis()
        return DashboardUiState.Content(
            streak = StreakCalculator.currentStreak(activeDays, now),
            heatmap = heatmap,
            monthLabel = monthDate.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(Locale.ENGLISH),
            totalXp = totalXp,
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
            todayActive = activeDays.contains(now),
            todayActions = byDay[now]?.let { it.wordsSaved + it.wordsReviewed } ?: 0,
            dailyGoal = DAILY_GOAL,
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
        const val FINISHED_THRESHOLD = 0.99
        const val DAILY_GOAL = 5
    }
}
