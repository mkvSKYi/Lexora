package com.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.reader.core.database.entity.BookEntity
import com.reader.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, addedAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity): Long

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): BookEntity?

    @Query("UPDATE books SET lastOpenedAt = :ts WHERE id = :id")
    suspend fun updateLastOpened(id: Long, ts: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Long)

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: Long): ReadingProgressEntity?
}
