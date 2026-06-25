package com.reader.feature.dashboard

/** One cell of the activity heatmap. [level] is a 0..4 intensity bucket; future days render dim. */
data class HeatCell(
    val epochDay: Long,
    val level: Int,
    val isFuture: Boolean,
)

data class WordStats(val total: Int, val learned: Int, val due: Int)

data class BookStats(val inProgress: Int, val finished: Int)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    /**
     * @param streak consecutive active days ending today (or yesterday).
     * @param heatmap last 13 weeks of activity, 91 cells laid out as 13 week-columns of 7 days.
     * @param hasActivity whether the user has ever been active (drives the first-run empty state).
     */
    data class Content(
        val streak: Long,
        val heatmap: List<HeatCell>,
        val words: WordStats,
        val books: BookStats,
        val hasActivity: Boolean,
        val todayActions: Int,
        val dailyGoal: Int,
    ) : DashboardUiState {
        val goalProgress: Float get() = if (dailyGoal > 0) (todayActions.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f
        val goalReached: Boolean get() = todayActions >= dailyGoal
    }
}
