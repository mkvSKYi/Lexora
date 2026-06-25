package com.reader.feature.saved.review

import com.reader.core.data.xp.XpRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Test double that just accumulates granted XP. */
class FakeXpRepository : XpRepository {
    var total = 0
        private set

    override fun observeTotalXp(): Flow<Int> = flowOf(total)

    override suspend fun addXp(amount: Int) {
        total += amount
    }
}
