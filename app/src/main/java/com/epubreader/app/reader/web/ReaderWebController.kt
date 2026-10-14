package com.epubreader.app.reader.web

import com.epubreader.app.ui.LivreWebView

class ReaderWebController(private val webView: LivreWebView) {

    fun loadChapter(url: String) {
        webView.loadUrl(url)
    }

    fun goToPage(pageIndex: Int, totalPages: Int) {
        val ratio = if (totalPages > 1) pageIndex.toFloat() / (totalPages - 1) else 0f
        val percent = ratio * 100f
        webView.evaluateJavascript("scrollToPercent($percent);", null)
    }

    fun captureSelection(callback: (String?) -> Unit) {
        webView.evaluateJavascript("window.getSelection().toString();") { result ->
            val text = result?.trim('"')?.takeIf { it.isNotEmpty() }
            callback(text)
        }
    }

    fun injectHighlights(jsonHighlights: String) {
        webView.evaluateJavascript("injectHighlights($jsonHighlights);", null)
    }

    fun clearSelection() {
        webView.evaluateJavascript("window.getSelection().removeAllRanges();", null)
    }
}
