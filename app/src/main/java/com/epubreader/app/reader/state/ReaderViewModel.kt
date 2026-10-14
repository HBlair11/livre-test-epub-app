package com.epubreader.app.reader.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.epubreader.app.reader.persistence.ReaderPersistenceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val persistence = ReaderPersistenceUseCase(application)

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            bookId = savedStateHandle.get<Long>("bookId") ?: -1L,
            spineIndex = savedStateHandle.get<Int>("spineIndex") ?: 0,
            scrollRatio = savedStateHandle.get<Float>("scrollRatio") ?: 0f
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun initBook(bookId: Long) {
        savedStateHandle["bookId"] = bookId
        _uiState.update { it.copy(bookId = bookId) }
        viewModelScope.launch {
            val book = persistence.getBook(bookId)
            if (book != null) {
                _uiState.update { it.copy(title = book.title) }
            }
        }
    }

    fun updateLocation(spineIndex: Int, page: Int, totalPages: Int, ratio: Float) {
        savedStateHandle["spineIndex"] = spineIndex
        savedStateHandle["scrollRatio"] = ratio
        _uiState.update {
            it.copy(
                spineIndex = spineIndex,
                pageInChapter = page,
                pagesInChapter = maxOf(1, totalPages),
                scrollRatio = ratio
            )
        }
        val bookId = _uiState.value.bookId
        if (bookId != -1L) {
            viewModelScope.launch {
                persistence.updateProgress(bookId, ratio, spineIndex, ratio, System.currentTimeMillis())
            }
        }
    }

    fun setChromeVisible(visible: Boolean) {
        _uiState.update { it.copy(chromeVisible = visible) }
    }

    fun setTtsActive(active: Boolean, speaking: Boolean = false) {
        _uiState.update { it.copy(ttsActive = active, ttsSpeaking = speaking) }
    }
}
