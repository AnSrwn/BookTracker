package com.sauerwein.booktracker.core.book.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SearchQueryTest {

    @Test
    fun `classifies a valid ISBN-13 as an ISBN query`() {
        val query = SearchQuery.of("9783570402931")

        assertEquals(SearchQuery.ByIsbn(Isbn.of("9783570402931")), query)
    }

    @Test
    fun `classifies a valid ISBN-10 as an ISBN query, normalised to ISBN-13`() {
        val query = assertIs<SearchQuery.ByIsbn>(SearchQuery.of("3570402932"))

        assertEquals("9783570402931", query.isbn.value)
    }

    @Test
    fun `accepts a hyphenated ISBN`() {
        val query = assertIs<SearchQuery.ByIsbn>(SearchQuery.of("978-3-570-40293-1"))

        assertEquals("9783570402931", query.isbn.value)
    }

    @Test
    fun `falls back to text when the check digit is wrong`() {
        val query = SearchQuery.of("9783570402932")

        assertEquals(SearchQuery.ByText("9783570402932"), query)
    }

    @Test
    fun `falls back to text for an ISSN, which is an EAN-13 but not a book`() {
        val query = SearchQuery.of("9771234567890")

        assertEquals(SearchQuery.ByText("9771234567890"), query)
    }

    @Test
    fun `treats ordinary words as text`() {
        val query = SearchQuery.of("eragon vermaechtnis")

        assertEquals(SearchQuery.ByText("eragon vermaechtnis"), query)
    }

    @Test
    fun `treats a digit string of the wrong length as text`() {
        val query = SearchQuery.of("1984")

        assertEquals(SearchQuery.ByText("1984"), query)
    }

    @Test
    fun `trims surrounding whitespace from a text query`() {
        val query = SearchQuery.of("  eragon  ")

        assertEquals(SearchQuery.ByText("eragon"), query)
    }

    @Test
    fun `classifies an ISBN that is padded with whitespace`() {
        val query = assertIs<SearchQuery.ByIsbn>(SearchQuery.of("  9783570402931 "))

        assertEquals("9783570402931", query.isbn.value)
    }

    @Test
    fun `turns a blank query into empty text rather than failing`() {
        val query = SearchQuery.of("   ")

        assertEquals(SearchQuery.ByText(""), query)
    }
}
