package com.epubreader.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryHistoryDao {
    @Query("SELECT * FROM dictionary_history ORDER BY looked_up_at DESC")
    fun observeAll(): Flow<List<DictionaryHistoryEntity>>

    @Query("SELECT * FROM dictionary_history ORDER BY looked_up_at ASC")
    suspend fun getAll(): List<DictionaryHistoryEntity>

    @Query("SELECT * FROM dictionary_history WHERE word = :word LIMIT 1")
    suspend fun find(word: String): DictionaryHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DictionaryHistoryEntity): Long

    @Query("UPDATE dictionary_history SET definition = :definition, part_of_speech = :partOfSpeech, example = :example, book_title = :bookTitle, book_id = :bookId, looked_up_at = :lookedUpAt, phonetic = :phonetic WHERE id = :id")
    suspend fun refresh(id: Long, definition: String?, partOfSpeech: String?, example: String?, bookTitle: String?, bookId: Long?, lookedUpAt: Long, phonetic: String?)

    @Query("UPDATE dictionary_history SET mastery_level = :masteryLevel WHERE id = :id")
    suspend fun updateMasteryLevel(id: Long, masteryLevel: Int)

    @Delete
    suspend fun delete(entry: DictionaryHistoryEntity)

    @Query("DELETE FROM dictionary_history")
    suspend fun clear()
}
