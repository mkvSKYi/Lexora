package com.reader.core.data

import com.reader.core.data.activity.ActivityRepository
import com.reader.core.data.model.DailyActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Test double that counts activity records and observes nothing. */
class FakeActivityRepository : ActivityRepository {
    var reading = 0
        private set
    var saved = 0
        private set
    var reviewed = 0
        private set

    override suspend fun recordReading() {
        reading++
    }

    override suspend fun recordWordSaved() {
        saved++
    }

    override suspend fun recordWordReviewed() {
        reviewed++
    }

    override fun observeSince(fromEpochDay: Long): Flow<List<DailyActivity>> = flowOf(emptyList())
}
