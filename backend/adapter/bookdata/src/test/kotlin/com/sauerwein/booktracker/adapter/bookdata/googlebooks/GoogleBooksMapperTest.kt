package com.sauerwein.booktracker.adapter.bookdata.googlebooks

import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.Language
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Values as in fixtures/google-books-search.json (`eragon paolini`, captured 2026-11-16). */
class GoogleBooksMapperTest {

    private val mapper = GoogleBooksMapper(GoogleBooksClient.DESCRIPTOR)

    private val volume = GoogleVolume(
        id = "_tiYRR30oe0C",
        volumeInfo = GoogleVolumeInfo(
            title = "Eragon - Die Weisheit des Feuers",
            authors = listOf("Christopher Paolini"),
            publisher = "cbj Verlag",
            publishedDate = "2009-04-01",
            pageCount = 679,
            industryIdentifiers = listOf(
                GoogleIndustryIdentifier("ISBN_13", "9783641023041"),
                GoogleIndustryIdentifier("ISBN_10", "3641023041"),
            ),
            imageLinks = GoogleImageLinks("http://books.google.com/books/content?id=_tiYRR30oe0C&printsec=frontcover&img=1&zoom=1"),
            language = "de",
        ),
    )

    private fun map(info: GoogleVolumeInfo) = mapper.toCandidate(volume.copy(volumeInfo = info))

    @Test
    fun `maps a volume as a hit that may not be stored`() {
        val candidate = mapper.toCandidate(volume)!!

        assertEquals("Eragon - Die Weisheit des Feuers", candidate.title)
        assertEquals(listOf("Christopher Paolini"), candidate.authors.map { it.name })
        assertEquals("cbj Verlag", candidate.publisher)
        assertEquals(Isbn.of("9783641023041"), candidate.isbn)
        assertEquals(2009, candidate.publicationYear)
        assertEquals(679, candidate.pageCount)
        assertEquals(SourceRecordId("_tiYRR30oe0C"), candidate.sourceId)
        assertFalse(candidate.persistableInPublicCatalog)
    }

    /** 9.9 Q3: Google sends ISO 639-1, sometimes a full tag; both become the language subtag. */
    @Test
    fun `reads the volume's language`() {
        assertEquals(Language.of("de"), mapper.toCandidate(volume)?.language)
        assertEquals(Language.of("zh"), map(volume.volumeInfo!!.copy(language = "zh-CN"))?.language)
    }

    @Test
    fun `has no language when Google sends none or an unreadable one`() {
        assertNull(map(volume.volumeInfo!!.copy(language = null))?.language)
        assertNull(map(volume.volumeInfo!!.copy(language = "und"))?.language)
    }

    @Test
    fun `serves the cover over https`() {
        assertEquals(
            "https://books.google.com/books/content?id=_tiYRR30oe0C&printsec=frontcover&img=1&zoom=1",
            mapper.toCandidate(volume)?.coverUrl,
        )
    }

    /** Publisher records carry `pageCount: 0` for "unknown" (u37TzwEACAAJ in the fixture). */
    @Test
    fun `reads a page count of zero as unknown`() {
        assertNull(map(volume.volumeInfo!!.copy(pageCount = 0))?.pageCount)
    }

    /** Library scans carry only `OTHER` identifiers (wYAkAQAAMAAJ); the search then drops the hit (D8). */
    @Test
    fun `leaves the ISBN empty for a library scan`() {
        val candidate = map(volume.volumeInfo!!.copy(industryIdentifiers = listOf(GoogleIndustryIdentifier("OTHER", "UOM:39076002505696"))))

        assertNull(candidate?.isbn)
    }

    @Test
    fun `falls back to the ISBN-10 when there is no valid ISBN-13`() {
        val candidate = map(
            volume.volumeInfo!!.copy(
                industryIdentifiers = listOf(
                    GoogleIndustryIdentifier("ISBN_13", "9783641023040"),
                    GoogleIndustryIdentifier("ISBN_10", "3641023041"),
                ),
            ),
        )

        assertEquals(Isbn.of("9783641023041"), candidate?.isbn)
    }

    @Test
    fun `drops a volume without a title`() {
        assertNull(map(volume.volumeInfo!!.copy(title = " ")))
        assertNull(mapper.toCandidate(GoogleVolume(id = "x", volumeInfo = null)))
    }

    /** 9.10 Q9: a book may have no author. */
    @Test
    fun `keeps a volume without an author, with none`() {
        assertEquals(emptyList(), map(volume.volumeInfo!!.copy(authors = null))?.authors)
        assertEquals(emptyList(), map(volume.volumeInfo!!.copy(authors = listOf(" ")))?.authors)
    }
}
