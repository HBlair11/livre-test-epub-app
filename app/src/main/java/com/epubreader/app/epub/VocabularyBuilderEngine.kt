package com.epubreader.app.epub

/**
 * Context-aware sentence extraction engine for reader selections.
 * Scans backward to sentence-starting punctuation (., !, ?, \n) and forward
 * to closing punctuation to automatically capture the exact literary sentence
 * where the reader discovered the word.
 */
object VocabularyBuilderEngine {

    private fun isSentenceBoundary(c: Char): Boolean =
        c == '.' || c == '!' || c == '?' || c == '\n' || c == '\r'

    fun extractContextSentence(fullText: String, targetWord: String): String {
        val cleanWord = targetWord.trim()
        if (cleanWord.isBlank() || fullText.isBlank()) return "Example usage from active reading session."

        val index = fullText.indexOf(cleanWord, ignoreCase = true)
        if (index == -1) return "Example usage from active reading session."

        // Search backward for sentence starter
        var start = index
        while (start > 0 && !isSentenceBoundary(fullText[start - 1])) {
            start--
        }

        // Search forward for sentence end
        var end = index + cleanWord.length
        while (end < fullText.length && !isSentenceBoundary(fullText[end])) {
            end++
        }
        if (end < fullText.length && isSentenceBoundary(fullText[end])) {
            end++ // Include closing punctuation
        }

        val sentence = fullText.substring(start, end).replace(Regex("\\s+"), " ").trim()
        return if (sentence.length in 10..300) sentence else "From chapter reading: '$cleanWord'"
    }
}
