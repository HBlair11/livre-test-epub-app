package com.epubreader.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingStampDao {
    @Query("SELECT * FROM reading_stamps WHERE book_id = :bookId ORDER BY created_at DESC")
    fun observeForBook(bookId: Long): Flow<List<ReadingStampEntity>>

    @Query("SELECT * FROM reading_stamps WHERE book_id = :bookId ORDER BY created_at DESC")
    suspend fun getForBook(bookId: Long): List<ReadingStampEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stamp: ReadingStampEntity): Long

    @Delete
    suspend fun delete(stamp: ReadingStampEntity)
}
