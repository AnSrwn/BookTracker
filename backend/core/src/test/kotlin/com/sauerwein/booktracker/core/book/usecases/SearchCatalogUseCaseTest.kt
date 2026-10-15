package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.FakeBookMetadataSource
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.testCandidate
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchCatalogUseCaseTest {

    @Test
    fun `runs a text search for ordinary words`() {
        val hits = listOf(testCandidate(title = "Eragon"), testCandidate(title = "Eldest"))
        val source = FakeBookMetadataSource(searchResults = hits)

        assertEquals(hits, SearchCatalogUseCase(source)("eragon"))
        assertEquals(listOf("eragon"), source.searchedTexts)
        assertEquals(emptyList(), source.lookedUpIsbns)
    }

    /** A user pasting an ISBN into the search box must get the exact edition, not a word search. */
    @Test
    fun `routes an ISBN in the search box to the exact lookup`() {
        val candidate = testCandidate(title = "Eragon", isbn = Isbn.of("9783570402931"))
        val source = FakeBookMetadataSource(isbnResult = candidate)

        assertEquals(listOf(candidate), SearchCatalogUseCase(source)("978-3-570-40293-1"))
        assertEquals(listOf(Isbn.of("9783570402931")), source.lookedUpIsbns)
        assertEquals(emptyList(), source.searchedTexts)
    }

    /**
     * The asymmetry to FindBookByIsbnUseCase: at the search endpoint an unknown ISBN is an
     * empty result, while the scan endpoint throws so the app can open the manual form.
     */
    @Test
    fun `returns an empty list for an unknown ISBN instead of throwing`() {
        val source = FakeBookMetadataSource(isbnResult = null)

        assertEquals(emptyList(), SearchCatalogUseCase(source)("9783570402931"))
    }

    @Test
    fun `returns an empty list when a text search finds nothing`() {
        val source = FakeBookMetadataSource(searchResults = emptyList())

        assertEquals(emptyList(), SearchCatalogUseCase(source)("nonexistent title"))
    }
}
