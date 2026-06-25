package com.reader.core.data.review

import kotlin.math.roundToInt

enum class ReviewGrade { AGAIN, GOOD, EASY }

data class ReviewState(
    val easeFactor: Double,
    val intervalDays: Int,
    val repetitions: Int,
)

data class ScheduleResult(
    val state: ReviewState,
    val dueAt: Long,
    val graduated: Boolean,
)

/** SM-2 lite scheduler. Pure: [now] is supplied, never read from the clock here. */
object ReviewScheduler {
    const val MIN_EASE = 1.3
    const val EASY_BONUS = 1.3
    const val GRADUATION_DAYS = 21
    const val DAY_MS = 86_400_000L

    fun schedule(current: ReviewState, grade: ReviewGrade, now: Long): ScheduleResult =
        when (grade) {
            ReviewGrade.AGAIN -> {
                val ease = maxOf(MIN_EASE, current.easeFactor - 0.20)
                ScheduleResult(ReviewState(ease, 0, 0), now, graduated = false)
            }
            ReviewGrade.GOOD -> {
                val interval = goodInterval(current)
                ScheduleResult(
                    ReviewState(current.easeFactor, interval, current.repetitions + 1),
                    now + interval * DAY_MS,
                    interval >= GRADUATION_DAYS,
                )
            }
            ReviewGrade.EASY -> {
                val interval =
                    if (current.repetitions == 0) 4
                    else (goodInterval(current) * EASY_BONUS).roundToInt()
                ScheduleResult(
                    ReviewState(current.easeFactor + 0.15, interval, current.repetitions + 1),
                    now + interval * DAY_MS,
                    interval >= GRADUATION_DAYS,
                )
            }
        }

    private fun goodInterval(current: ReviewState): Int = when (current.repetitions) {
        0 -> 1
        1 -> 6
        else -> (current.intervalDays * current.easeFactor).roundToInt()
    }
}
