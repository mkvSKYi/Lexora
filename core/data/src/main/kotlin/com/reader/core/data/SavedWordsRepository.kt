package com.reader.core.data

import com.reader.core.data.mapper.toDomain
import com.reader.core.data.mapper.toEntity
import com.reader.core.data.model.SavedWord
import com.reader.core.database.dao.SavedWordDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface SavedWordsRepository {
    fun observe(): Flow<List<SavedWord>>
    suspend fun save(word: SavedWord)
    suspend fun delete(id: Long)
    suspend fun markLearned(id: Long, learned: Boolean)
}

class DefaultSavedWordsRepository @Inject constructor(
    private val dao: SavedWordDao,
) : SavedWordsRepository {
    override fun observe(): Flow<List<SavedWord>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(word: SavedWord) = dao.upsert(word.toEntity())
    override suspend fun delete(id: Long) = dao.deleteById(id)
    override suspend fun markLearned(id: Long, learned: Boolean) = dao.updateLearned(id, learned)
}
