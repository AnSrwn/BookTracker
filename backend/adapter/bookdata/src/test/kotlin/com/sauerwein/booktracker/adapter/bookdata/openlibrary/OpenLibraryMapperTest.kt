package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class OpenLibraryMapperTest {

    private val mapper = OpenLibraryMapper(OpenLibraryClient.DESCRIPTOR)

    private val edition = OpenLibraryEdition(
        key = "/books/OL3685343M",
        title = "Eragon",
        publishDate = "2003",
        numberOfPages = 509,
        isbn13 = listOf("9780375826689"),
        covers = listOf(15244834),
    )

    @Test
    fun `reads the year from free-form publish dates`() {
        listOf("2003", "March 2003", "2003-05-01", "c2003.").forEach { date ->
            assertEquals(2003, map(edition.copy(publishDate = date))?.publicationYear, date)
        }
        assertNull(map(edition.copy(publishDate = "unknown"))?.publicationYear)
    }

    @Test
    fun `falls back to ISBN-10 when no ISBN-13 is valid`() {
        val candidate = map(edition.copy(isbn13 = listOf("9780375826680"), isbn10 = listOf("0375826688")))

        assertEquals(Isbn.of("9780375826689"), candidate?.isbn)
    }

    @Test
    fun `has no ISBN when every listed one is dirty`() {
        assertNull(map(edition.copy(isbn13 = listOf("not an isbn"), isbn10 = null))?.isbn)
    }

    @Test
    fun `builds no cover URL when the record lists no covers`() {
        assertNull(map(edition.copy(covers = emptyList()))?.coverUrl)
        assertNull(map(edition.copy(covers = null))?.coverUrl)
    }

    @Test
    fun `builds no cover URL without an ISBN`() {
        assertNull(map(edition.copy(isbn13 = null))?.coverUrl)
    }

    @Test
    fun `drops a non-positive page count`() {
        assertNull(map(edition.copy(numberOfPages = 0))?.pageCount)
    }

    @Test
    fun `drops records without title or author`() {
        assertNull(map(edition.copy(title = " ")))
        assertNull(mapper.toCandidate(edition, authorName = null))
        assertNull(mapper.toCandidate(OpenLibrarySearchDoc(title = "Eragon", authorName = emptyList())))
        assertNull(mapper.toCandidate(OpenLibrarySearchDoc(title = null, authorName = listOf("Paolini"))))
    }

    /** The licence bit travels with the record, so it must come from the descriptor, not be hardcoded. */
    @Test
    fun `takes source and licence from the descriptor`() {
        val descriptor = OpenLibraryClient.DESCRIPTOR.copy(id = SourceId.of("other"), persistableInPublicCatalog = false)
        val mapper = OpenLibraryMapper(descriptor)
        val doc = OpenLibrarySearchDoc(title = "Eragon", authorName = listOf("Christopher Paolini"))

        listOfNotNull(mapper.toCandidate(edition, "Christopher Paolini"), mapper.toCandidate(doc)).forEach {
            assertEquals(descriptor.id, it.source)
            assertFalse(it.persistableInPublicCatalog)
        }
    }

    private fun map(edition: OpenLibraryEdition) = mapper.toCandidate(edition, "Christopher Paolini")
}
