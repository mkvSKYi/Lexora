package com.reader.core.data.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    private val today = 20_000L

    @Test fun no_activity_is_zero() {
        assertEquals(0L, StreakCalculator.currentStreak(emptySet(), today))
    }

    @Test fun active_today_only_is_one() {
        assertEquals(1L, StreakCalculator.currentStreak(setOf(today), today))
    }

    @Test fun active_today_and_yesterday_is_two() {
        assertEquals(2L, StreakCalculator.currentStreak(setOf(today, today - 1), today))
    }

    @Test fun active_yesterday_but_not_today_keeps_streak_grace() {
        // Today not yet active, but yesterday + the day before were: streak survives until a full
        // empty day passes.
        assertEquals(2L, StreakCalculator.currentStreak(setOf(today - 1, today - 2), today))
    }

    @Test fun gap_before_today_breaks_at_the_gap() {
        // Active today and two days ago, but the gap yesterday stops the run at 1.
        assertEquals(1L, StreakCalculator.currentStreak(setOf(today, today - 2), today))
    }

    @Test fun activity_older_than_yesterday_does_not_count() {
        assertEquals(0L, StreakCalculator.currentStreak(setOf(today - 2, today - 3), today))
    }

    @Test fun long_consecutive_run_counts_all() {
        val days = (0L..6L).map { today - it }.toSet()
        assertEquals(7L, StreakCalculator.currentStreak(days, today))
    }
}
