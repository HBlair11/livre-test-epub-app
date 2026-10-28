package com.epubreader.app.epub

/**
 * Context-aware sentence extraction engine for reader selections.
 * Ensures extracted excerpts always start and end on clean word boundaries
 * (never cutting off words in the middle) and automatically wraps with ellipses (...)
 * when starting mid-sentence or ending truncated.
 */
object VocabularyBuilderEngine {

    private fun isSentenceTerminator(c: Char): Boolean =
        c == '.' || c == '!' || c == '?' || c == '\n' || c == '\r'

    fun extractContextSentence(fullText: String, targetWord: String, prefix: String? = null, suffix: String? = null): String {
        val cleanWord = targetWord.trim()
        if (cleanWord.isBlank()) return "Example usage from active reading session."

        val textToSearch: String
        val index: Int

        if (!prefix.isNullOrEmpty()) {
            textToSearch = "$prefix$cleanWord${suffix ?: ""}"
            index = prefix.length
        } else {
            textToSearch = fullText
            index = fullText.indexOf(cleanWord, ignoreCase = true)
        }

        if (index == -1 || textToSearch.isBlank()) return "Example usage from active reading session."

        // Search backward for sentence starter
        var start = index
        var hitSentenceTerminatorAtStart = false
        while (start > 0) {
            val prev = textToSearch[start - 1]
            if (isSentenceTerminator(prev) || prev == '\n' || prev == '\r') {
                hitSentenceTerminatorAtStart = true
                break
            }
            start--
        }

        // Search forward for sentence end
        var end = index + cleanWord.length
        var hitSentenceTerminatorAtEnd = false
        while (end < textToSearch.length) {
            val curr = textToSearch[end]
            if (isSentenceTerminator(curr) || curr == '\n' || curr == '\r') {
                end++ // include terminator
                hitSentenceTerminatorAtEnd = true
                break
            }
            end++
        }

        var rawSentence = textToSearch.substring(start, end).replace(Regex("\\s+"), " ").trim()
        if (rawSentence.isBlank()) return "From chapter reading: '$cleanWord'"

        // If start was bounded by snippet start (start == 0) without a sentence terminator,
        // clean up any leading partial word fragment up to the first space.
        if (!hitSentenceTerminatorAtStart && start == 0) {
            val firstSpace = rawSentence.indexOf(' ')
            val targetWordIndexInRaw = rawSentence.indexOf(cleanWord, ignoreCase = true)
            if (firstSpace != -1 && firstSpace < targetWordIndexInRaw) {
                val firstToken = rawSentence.substring(0, firstSpace)
                if (firstToken.length <= 4 && !firstToken.first().isUpperCase()) {
                    rawSentence = rawSentence.substring(firstSpace + 1).trim()
                }
            }
        }

        // If end was bounded by snippet end (end == textToSearch.length) without a sentence terminator,
        // clean up any trailing partial word fragment after the last space.
        if (!hitSentenceTerminatorAtEnd && end == textToSearch.length) {
            val lastSpace = rawSentence.lastIndexOf(' ')
            val targetWordEndInRaw = rawSentence.indexOf(cleanWord, ignoreCase = true) + cleanWord.length
            if (lastSpace != -1 && lastSpace > targetWordEndInRaw) {
                val lastToken = rawSentence.substring(lastSpace + 1)
                if (lastToken.length <= 5 && !lastToken.any { it == '.' || it == '!' || it == '?' || it == '”' || it == '"' }) {
                    rawSentence = rawSentence.substring(0, lastSpace).trim()
                }
            }
        }

        // Leading ellipsis: required if sentence starts mid-sentence
        val firstLetter = rawSentence.firstOrNull { it.isLetter() }
        val startsWithLowercase = firstLetter != null && firstLetter.isLowerCase()
        val needsLeadingEllipsis = !hitSentenceTerminatorAtStart || startsWithLowercase

        // Trailing ellipsis: required if excerpt ends before a true sentence terminator (. ! ?)
        val endsWithTerminator = rawSentence.endsWith(".") || rawSentence.endsWith("!") || rawSentence.endsWith("?") ||
                                 rawSentence.endsWith(".”") || rawSentence.endsWith("!”") || rawSentence.endsWith("?”") ||
                                 rawSentence.endsWith(".'") || rawSentence.endsWith("!'") || rawSentence.endsWith("?'")

        val needsTrailingEllipsis = !hitSentenceTerminatorAtEnd || !endsWithTerminator

        val finalSentence = buildString {
            if (needsLeadingEllipsis && !rawSentence.startsWith("...")) {
                append("... ")
            }
            append(rawSentence)
            if (needsTrailingEllipsis && !rawSentence.endsWith("...")) {
                append(" ...")
            }
        }.trim()

        return if (finalSentence.length in 10..1500) finalSentence else "From chapter reading: '$cleanWord'"
    }
}
