package com.reader.core.data

import com.reader.core.data.mapper.toDomain
import com.reader.core.data.mapper.toEntity
import com.reader.core.data.model.Book
import com.reader.core.data.model.BookWithProgress
import com.reader.core.data.model.ReadingProgress
import com.reader.core.database.dao.BookDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

interface LibraryRepository {
    fun observeBooks(): Flow<List<Book>>
    fun observeBooksWithProgress(): Flow<List<BookWithProgress>>
    suspend fun addBook(book: Book): Long
    suspend fun getBook(id: Long): Book?
    suspend fun markOpened(id: Long, now: Long)
    suspend fun saveProgress(progress: ReadingProgress)
    suspend fun getProgress(bookId: Long): ReadingProgress?
    suspend fun deleteBook(id: Long)
    suspend fun deleteBookCompletely(book: Book)
    suspend fun progressPercent(bookId: Long): Double
}

class DefaultLibraryRepository @Inject constructor(
    private val dao: BookDao,
    private val savedWordDao: com.reader.core.database.dao.SavedWordDao,
) : LibraryRepository {
    override fun observeBooks(): Flow<List<Book>> =
        dao.observeBooks().map { list -> list.map { it.toDomain() } }

    override fun observeBooksWithProgress(): Flow<List<BookWithProgress>> =
        dao.observeBooksWithProgress().map { rows -> rows.map { it.toDomain() } }

    override suspend fun addBook(book: Book): Long = dao.upsertBook(book.toEntity())
    override suspend fun getBook(id: Long): Book? = dao.getBook(id)?.toDomain()
    override suspend fun markOpened(id: Long, now: Long) = dao.updateLastOpened(id, now)
    override suspend fun saveProgress(progress: ReadingProgress) =
        dao.upsertProgress(progress.toEntity())
    override suspend fun getProgress(bookId: Long): ReadingProgress? =
        dao.getProgress(bookId)?.toDomain()
    override suspend fun deleteBook(id: Long) = dao.deleteBook(id)

    override suspend fun deleteBookCompletely(book: Book) = withContext(Dispatchers.IO) {
        runCatching { File(book.filePath).delete() }
        book.coverPath?.let { path -> runCatching { File(path).delete() } }
        savedWordDao.deleteByBookId(book.id)
        dao.deleteProgress(book.id)
        dao.deleteBook(book.id)
    }

    override suspend fun progressPercent(bookId: Long): Double =
        dao.getProgress(bookId)?.percent ?: 0.0
}
