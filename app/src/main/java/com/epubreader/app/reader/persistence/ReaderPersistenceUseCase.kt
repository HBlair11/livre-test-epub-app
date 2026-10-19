package com.epubreader.app.reader.persistence

import android.content.Context
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookmarkEntity
import com.epubreader.app.data.DictionaryHistoryEntity
import com.epubreader.app.data.HighlightEntity
import com.epubreader.app.data.ReadingSessionEntity
import com.epubreader.app.data.TtsSettingsEntity
import kotlinx.coroutines.flow.Flow

class ReaderPersistenceUseCase(context: Context) {
    private val db = AppDatabase.get(context)
    private val bookDao = db.bookDao()
    private val bookmarkDao = db.bookmarkDao()
    private val highlightDao = db.highlightDao()
    private val readingSessionDao = db.readingSessionDao()
    private val dictionaryHistoryDao = db.dictionaryHistoryDao()
    private val ttsSettingsDao = db.ttsSettingsDao()

    suspend fun getBook(id: Long): BookEntity? = bookDao.getById(id)

    suspend fun markOpened(id: Long, time: Long) = bookDao.markOpened(id, time)

    suspend fun updateScreenPageMap(id: Long, csv: String, key: String) = bookDao.updateScreenPageMap(id, csv, key)

    suspend fun updateSpineCount(id: Long, count: Int) = bookDao.updateSpineCount(id, count)

    suspend fun updateCurrentLocation(id: Long, location: String?) = bookDao.updateCurrentLocation(id, location)

    suspend fun updateProgress(id: Long, progress: Float, spineIndex: Int, ratio: Float, now: Long) {
        bookDao.updateProgress(id, progress, spineIndex, ratio, now)
    }

    suspend fun addBookmark(bookmark: BookmarkEntity): Long = bookmarkDao.insert(bookmark)

    suspend fun deleteBookmark(bookmark: BookmarkEntity) = bookmarkDao.delete(bookmark)

    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>> = bookmarkDao.observeForBook(bookId)

    suspend fun findBookmarkWholePageBySnippet(bookId: Long, idx: Int, snippet: String): BookmarkEntity? =
        bookmarkDao.findWholePageBySnippet(bookId, idx, snippet)

    suspend fun findBookmarkWholePage(bookId: Long, idx: Int, page: Int, ratio: Float): BookmarkEntity? =
        bookmarkDao.findWholePage(bookId, idx, page, ratio)

    suspend fun findBookmarkTextBySnippet(bookId: Long, idx: Int, snippet: String): BookmarkEntity? =
        bookmarkDao.findTextBySnippet(bookId, idx, snippet)

    suspend fun findBookmarkText(bookId: Long, idx: Int, page: Int, snippet: String): BookmarkEntity? =
        bookmarkDao.findText(bookId, idx, page, snippet)

    suspend fun addHighlight(highlight: HighlightEntity): Long = highlightDao.insert(highlight)

    suspend fun deleteHighlight(highlight: HighlightEntity) = highlightDao.delete(highlight)

    fun observeHighlights(bookId: Long): Flow<List<HighlightEntity>> = highlightDao.observeForBook(bookId)

    suspend fun getHighlightsForChapter(bookId: Long, spineHref: String): List<HighlightEntity> =
        highlightDao.getForChapter(bookId, spineHref)

    suspend fun insertReadingSession(session: ReadingSessionEntity) = readingSessionDao.insert(session)

    suspend fun findDictionaryHistory(word: String): DictionaryHistoryEntity? = dictionaryHistoryDao.find(word)

    suspend fun insertDictionaryHistory(entity: DictionaryHistoryEntity) = dictionaryHistoryDao.insert(entity)

    suspend fun refreshDictionaryHistory(id: Long, definition: String?, partOfSpeech: String?, example: String?, bookTitle: String?, bookId: Long?, lookedUpAt: Long, phonetic: String?) =
        dictionaryHistoryDao.refresh(id, definition, partOfSpeech, example, bookTitle, bookId, lookedUpAt, phonetic)

    suspend fun getTtsSettings(bookId: Long): TtsSettingsEntity? = ttsSettingsDao.getForBook(bookId)

    suspend fun saveTtsSettings(entity: TtsSettingsEntity) = ttsSettingsDao.upsert(entity)
}
