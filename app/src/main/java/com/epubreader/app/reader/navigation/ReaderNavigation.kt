package com.epubreader.app.reader.navigation

sealed class ReaderNavigationIntent {
    object Home : ReaderNavigationIntent()
    object Library : ReaderNavigationIntent()
    data class BookDetails(val bookId: Long) : ReaderNavigationIntent()
    data class Reader(val bookId: Long, val spineIndex: Int = 0, val ratio: Float = 0f) : ReaderNavigationIntent()
    object Search : ReaderNavigationIntent()
    object Settings : ReaderNavigationIntent()
    data class ReaderOverlays(val overlayType: String) : ReaderNavigationIntent()
    object Finish : ReaderNavigationIntent()
}
