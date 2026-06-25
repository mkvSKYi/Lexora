package com.reader.core.data.activity

/** Computes the current consecutive-day activity streak from the set of active days. */
object StreakCalculator {
    /**
     * Length of the run of consecutive active days ending today (or yesterday, so a streak isn't
     * "lost" until a full day with no activity passes). Returns 0 if neither today nor yesterday
     * is active.
     *
     * @param activeDays epoch-days on which the user was active.
     * @param today the current epoch-day.
     */
    fun currentStreak(activeDays: Set<Long>, today: Long): Long {
        var day = when {
            activeDays.contains(today) -> today
            activeDays.contains(today - 1) -> today - 1
            else -> return 0
        }
        var streak = 0L
        while (activeDays.contains(day)) {
            streak++
            day--
        }
        return streak
    }
}
