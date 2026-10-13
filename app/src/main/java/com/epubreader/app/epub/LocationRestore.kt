package com.epubreader.app.epub

/**
 * Pure helpers for deciding how to restore a reading location after a cold open
 * or typography change. Kept outside [com.epubreader.app.ReaderActivity] so the
 * decision can be unit-tested without a WebView.
 */
object LocationRestore {
    /**
     * @return preferred page index within the chapter when a semantic quote is
     * available, otherwise null (caller should fall back to ratio-based page).
     *
     * [resolvedPage] is the page returned by the JS `pageForTextAnchor` call.
     * A negative or null value means the quote could not be located.
     */
    fun pageFromSemanticQuote(
        quote: String?,
        resolvedPage: Int?,
        fallbackPage: Int,
    ): Int {
        if (quote.isNullOrBlank()) return fallbackPage.coerceAtLeast(0)
        val page = resolvedPage ?: return fallbackPage.coerceAtLeast(0)
        if (page < 0) return fallbackPage.coerceAtLeast(0)
        return page
    }

    /** Clamp a stored ratio into a page index for the given chapter page count. */
    fun pageFromRatio(ratio: Float, pageCount: Int): Int {
        val count = pageCount.coerceAtLeast(1)
        if (count <= 1) return 0
        return (ratio.coerceIn(0f, 1f) * (count - 1)).toInt().coerceIn(0, count - 1)
    }
}
