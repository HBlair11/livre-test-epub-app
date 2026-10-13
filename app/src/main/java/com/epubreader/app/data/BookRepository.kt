package com.epubreader.app.data

import kotlinx.coroutines.flow.Flow

class BookRepository(
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
) {
    fun observeAll(): Flow<List<BookEntity>> = bookDao.observeAll()

    fun observeHomeRecentlyAdded(): Flow<List<BookEntity>> = bookDao.observeHomeRecentlyAdded()

    fun observeCurrentlyReading(): Flow<List<BookEntity>> = bookDao.observeCurrentlyReading()

    fun observeFavorites(): Flow<List<BookEntity>> = bookDao.observeFavorites()

    fun observeAuthors(): Flow<List<GroupedRow>> = bookDao.observeAuthors()

    fun observeSeries(): Flow<List<GroupedRow>> = bookDao.observeSeries()

    fun observeByAuthor(author: String): Flow<List<BookEntity>> = bookDao.observeByAuthor(author)

    fun observeBySeries(series: String): Flow<List<BookEntity>> = bookDao.observeBySeries(series)

    fun observeLastOpened(): Flow<BookEntity?> = bookDao.observeLastOpened()

    fun observeFinished(): Flow<List<BookEntity>> = bookDao.observeFinished()

    fun observeToBeRead(): Flow<List<BookEntity>> = bookDao.observeToBeRead()

    fun observeByIds(ids: List<Long>): Flow<List<BookEntity>> = bookDao.observeByIds(ids)

    fun search(query: String): Flow<List<BookEntity>> = bookDao.search(query)

    suspend fun getBook(id: Long): BookEntity? = bookDao.getById(id)

    suspend fun getByChecksum(checksum: String): BookEntity? = bookDao.getByChecksum(checksum)

    suspend fun updateProgress(
        id: Long,
        progress: Float,
        spineIndex: Int,
        scrollRatio: Float,
        locationQuote: String? = null,
    ) {
        bookDao.updateProgress(id, progress, spineIndex, scrollRatio, locationQuote, System.currentTimeMillis())
    }

    suspend fun deleteBook(book: BookEntity) = bookDao.delete(book)

    // ---- Bookmarks ----
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>> = bookmarkDao.observeForBook(bookId)

    // ---- Highlights ----
    fun observeHighlights(bookId: Long): Flow<List<HighlightEntity>> = highlightDao.observeForBook(bookId)

    suspend fun getHighlightsForChapter(bookId: Long, spineHref: String): List<HighlightEntity> =
        highlightDao.getForChapter(bookId, spineHref)
}
