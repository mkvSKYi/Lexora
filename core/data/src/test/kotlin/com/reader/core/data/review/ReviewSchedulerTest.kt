package com.reader.core.data.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulerTest {
    private val T = 1_000_000_000_000L
    private val DAY = 86_400_000L
    private fun state(e: Double, i: Int, r: Int) = ReviewState(e, i, r)

    @Test fun good_newCard_is_one_day() {
        val r = ReviewScheduler.schedule(state(2.5, 0, 0), ReviewGrade.GOOD, T)
        assertEquals(1, r.state.intervalDays)
        assertEquals(1, r.state.repetitions)
        assertEquals(2.5, r.state.easeFactor, 1e-9)
        assertEquals(T + 1 * DAY, r.dueAt)
        assertFalse(r.graduated)
    }

    @Test fun good_secondReview_is_six_days() {
        val r = ReviewScheduler.schedule(state(2.5, 1, 1), ReviewGrade.GOOD, T)
        assertEquals(6, r.state.intervalDays)
        assertEquals(2, r.state.repetitions)
    }

    @Test fun good_thirdReview_multiplies_by_ease() {
        val r = ReviewScheduler.schedule(state(2.5, 6, 2), ReviewGrade.GOOD, T)
        assertEquals(15, r.state.intervalDays) // round(6 * 2.5)
    }

    @Test fun good_crossing_threshold_graduates() {
        val r = ReviewScheduler.schedule(state(2.5, 15, 3), ReviewGrade.GOOD, T)
        assertEquals(38, r.state.intervalDays) // round(15 * 2.5)
        assertTrue(r.graduated)
    }

    @Test fun again_resets_and_drops_ease() {
        val r = ReviewScheduler.schedule(state(2.5, 15, 3), ReviewGrade.AGAIN, T)
        assertEquals(0, r.state.intervalDays)
        assertEquals(0, r.state.repetitions)
        assertEquals(2.3, r.state.easeFactor, 1e-9)
        assertEquals(T, r.dueAt)
        assertFalse(r.graduated)
    }

    @Test fun again_ease_has_floor() {
        val r = ReviewScheduler.schedule(state(1.3, 6, 2), ReviewGrade.AGAIN, T)
        assertEquals(1.3, r.state.easeFactor, 1e-9)
    }

    @Test fun easy_newCard_is_four_days_and_raises_ease() {
        val r = ReviewScheduler.schedule(state(2.5, 0, 0), ReviewGrade.EASY, T)
        assertEquals(4, r.state.intervalDays)
        assertEquals(2.65, r.state.easeFactor, 1e-9)
    }

    @Test fun easy_appliesBonus() {
        val r = ReviewScheduler.schedule(state(2.5, 6, 2), ReviewGrade.EASY, T)
        assertEquals(20, r.state.intervalDays) // round(round(6*2.5) * 1.3) = round(15*1.3)=round(19.5)=20
    }
}
