package com.reader.core.data.activity

import com.reader.core.data.model.DailyActivity
import com.reader.core.database.dao.DailyActivityDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/** Supplies the current epoch-day; injectable so tests can pin "today". */
fun interface TodayProvider {
    fun epochDay(): Long
}

/** Records and observes the daily activity log that powers the dashboard streak + heatmap. */
interface ActivityRepository {
    suspend fun recordReading()
    suspend fun recordWordSaved()
    suspend fun recordWordReviewed()
    fun observeSince(fromEpochDay: Long): Flow<List<DailyActivity>>
}

class DefaultActivityRepository @Inject constructor(
    private val dao: DailyActivityDao,
    private val today: TodayProvider,
) : ActivityRepository {

    override suspend fun recordReading() =
        dao.record(today.epochDay(), reading = true, saved = 0, reviewed = 0)

    override suspend fun recordWordSaved() =
        dao.record(today.epochDay(), reading = false, saved = 1, reviewed = 0)

    override suspend fun recordWordReviewed() =
        dao.record(today.epochDay(), reading = false, saved = 0, reviewed = 1)

    override fun observeSince(fromEpochDay: Long): Flow<List<DailyActivity>> =
        dao.observeSince(fromEpochDay).map { rows ->
            rows.map { DailyActivity(it.epochDay, it.readingActive, it.wordsSaved, it.wordsReviewed) }
        }

    companion object {
        /** epoch-day in the device's local time zone. */
        val SystemToday = TodayProvider { LocalDate.now().toEpochDay() }
    }
}
