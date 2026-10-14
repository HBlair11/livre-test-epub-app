package com.epubreader.app.reader.selection

data class ReaderSelectionAction(
    val title: String,
    val action: () -> Unit
)

class ReaderSelectionHandler {
    fun handleSelection(selectedText: String, onDefine: () -> Unit, onHighlight: () -> Unit, onCopy: () -> Unit) {
        // Selection context actions logic helper
    }
}
