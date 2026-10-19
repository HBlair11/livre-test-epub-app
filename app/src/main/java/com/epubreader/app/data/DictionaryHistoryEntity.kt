package com.epubreader.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Patch v37: offline dictionary lookup history & Vocabulary Sanctuary. Every Define lookup is saved
 * here (most recent first) so the reader can review difficult vocabulary
 * later, practice flashcards, and export it to CSV/Markdown.
 * A UNIQUE index on word makes re-lookups refresh the timestamp instead of
 * duplicating rows.
 */
@Entity(
    tableName = "dictionary_history",
    indices = [Index(value = ["word"], unique = true)],
)
data class DictionaryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "definition") val definition: String? = null,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String? = null,
    @ColumnInfo(name = "example") val example: String? = null,
    @ColumnInfo(name = "book_title") val bookTitle: String? = null,
    @ColumnInfo(name = "book_id") val bookId: Long? = null,
    @ColumnInfo(name = "looked_up_at") val lookedUpAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mastery_level") val masteryLevel: Int = 0, // 0 = New, 1 = Learning, 2 = Mastered
    @ColumnInfo(name = "phonetic") val phonetic: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)
