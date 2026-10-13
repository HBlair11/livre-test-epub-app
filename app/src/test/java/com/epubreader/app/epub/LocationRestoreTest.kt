package com.epubreader.app.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationRestoreTest {
    @Test
    fun prefersResolvedSemanticPage() {
        assertEquals(7, LocationRestore.pageFromSemanticQuote("once upon a time", 7, 2))
    }

    @Test
    fun fallsBackWhenQuoteMissing() {
        assertEquals(3, LocationRestore.pageFromSemanticQuote(null, 9, 3))
        assertEquals(3, LocationRestore.pageFromSemanticQuote("  ", 9, 3))
    }

    @Test
    fun fallsBackWhenResolvedPageInvalid() {
        assertEquals(4, LocationRestore.pageFromSemanticQuote("quote", null, 4))
        assertEquals(4, LocationRestore.pageFromSemanticQuote("quote", -1, 4))
    }

    @Test
    fun ratioToPage() {
        assertEquals(0, LocationRestore.pageFromRatio(0f, 10))
        assertEquals(9, LocationRestore.pageFromRatio(1f, 10))
        assertEquals(4, LocationRestore.pageFromRatio(0.5f, 9))
        assertEquals(0, LocationRestore.pageFromRatio(0.5f, 1))
    }
}
