package com.epubreader.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingJournalDao {
    @Query("SELECT * FROM reading_journal_entries WHERE book_id = :bookId ORDER BY created_at DESC")
    fun observeForBook(bookId: Long): Flow<List<ReadingJournalEntity>>

    @Query("SELECT * FROM reading_journal_entries WHERE book_id = :bookId ORDER BY created_at DESC")
    suspend fun getForBook(bookId: Long): List<ReadingJournalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ReadingJournalEntity): Long

    @Delete
    suspend fun delete(entry: ReadingJournalEntity)
}
