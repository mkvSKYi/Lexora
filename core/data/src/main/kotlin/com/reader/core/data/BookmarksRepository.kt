package com.reader.core.data

import com.reader.core.data.mapper.toDomain
import com.reader.core.data.mapper.toEntity
import com.reader.core.data.model.Bookmark
import com.reader.core.database.dao.BookmarkDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface BookmarksRepository {
    fun observe(bookId: Long): Flow<List<Bookmark>>
    suspend fun add(bookmark: Bookmark): Long
    suspend fun delete(id: Long)
}

class DefaultBookmarksRepository @Inject constructor(
    private val dao: BookmarkDao,
) : BookmarksRepository {
    override fun observe(bookId: Long): Flow<List<Bookmark>> =
        dao.observeForBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(bookmark: Bookmark): Long = dao.insert(bookmark.toEntity())
    override suspend fun delete(id: Long) = dao.deleteById(id)
}
