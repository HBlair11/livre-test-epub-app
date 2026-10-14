package com.epubreader.app.reader.state

data class ReaderUiState(
    val bookId: Long = -1L,
    val title: String = "",
    val spineIndex: Int = 0,
    val pageInChapter: Int = 0,
    val pagesInChapter: Int = 1,
    val scrollRatio: Float = 0f,
    val chromeVisible: Boolean = true,
    val ttsActive: Boolean = false,
    val ttsSpeaking: Boolean = false,
    val isSeeking: Boolean = false,
    val selectedText: String? = null,
    val error: String? = null
)
