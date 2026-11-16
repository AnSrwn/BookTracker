package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.adapter.bookdata.openLibraryRef
import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private val PAOLINI = Author("Christopher Paolini", openLibraryRef("OL1425963A"))

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
        assertNull(mapper.toCandidate(edition, authors = emptyList()))
        assertNull(mapper.toCandidate(edition, authors = listOf(Author(" ", ref = null))))
        assertNull(mapper.toCandidate(OpenLibrarySearchDoc(title = "Eragon", authorName = emptyList())))
        assertNull(mapper.toCandidate(OpenLibrarySearchDoc(title = null, authorName = listOf("Paolini"))))
    }

    /** The licence bit travels with the record, so it must come from the descriptor, not be hardcoded. */
    @Test
    fun `takes source and licence from the descriptor`() {
        val descriptor =
            OpenLibraryClient.DESCRIPTOR.copy(id = SourceId.of("other"), persistableInPublicCatalog = false)
        val mapper = OpenLibraryMapper(descriptor)
        val doc = OpenLibrarySearchDoc(title = "Eragon", authorName = listOf("Christopher Paolini"))

        listOfNotNull(mapper.toCandidate(edition, listOf(PAOLINI)), mapper.toCandidate(doc)).forEach {
            assertEquals(descriptor.id, it.source)
            assertFalse(it.persistableInPublicCatalog)
        }
    }

    @Test
    fun `keeps every author of an edition in source order, trimmed and without duplicates`() {
        val pratchett = Author("Terry Pratchett", openLibraryRef("OL25712A"))
        val gaiman = Author("Neil Gaiman", openLibraryRef("OL1393655A"))

        val candidate = mapper.toCandidate(
            edition,
            listOf(pratchett.copy(name = " Terry Pratchett "), Author("", ref = null), gaiman, pratchett),
        )

        assertEquals(listOf(pratchett, gaiman), candidate?.authors)
    }

    @Test
    fun `keeps a well-formed work key and drops a malformed one`() {
        val withWork = edition.copy(works = listOf(OpenLibraryKeyRef("/works/OL5819895W")))
        val withJunk = edition.copy(works = listOf(OpenLibraryKeyRef("/works/../../admin")))

        assertEquals(SourceRecordId("OL5819895W"), map(withWork)?.workKey)
        assertNull(map(withJunk)?.workKey)
        assertEquals(
            SourceRecordId("OL5819895W"),
            mapper.toCandidate(
                OpenLibrarySearchDoc(
                    key = "/works/OL5819895W",
                    title = "Eragon",
                    authorName = listOf("Christopher Paolini")
                )
            )?.workKey
        )
    }

    @Test
    fun `pairs search author names with their keys`() {
        val doc = OpenLibrarySearchDoc(
            title = "Good Omens",
            authorName = listOf("Terry Pratchett", "Neil Gaiman"),
            authorKey = listOf("OL25712A", "OL1393655A"),
        )

        assertEquals(
            listOf(
                Author("Terry Pratchett", openLibraryRef("OL25712A")),
                Author("Neil Gaiman", openLibraryRef("OL1393655A"))
            ),
            mapper.toCandidate(doc)?.authors,
        )
    }

    /** Pairing by index across arrays of different length could credit the wrong person. */
    @Test
    fun `keeps search author names without keys when the arrays do not line up`() {
        val doc = OpenLibrarySearchDoc(
            title = "Good Omens",
            authorName = listOf("Terry Pratchett", "Neil Gaiman"),
            authorKey = listOf("OL1393655A"),
        )

        assertEquals(
            listOf(Author("Terry Pratchett", ref = null), Author("Neil Gaiman", ref = null)),
            mapper.toCandidate(doc)?.authors,
        )
    }

    @Test
    fun `drops a malformed search author key but keeps the name`() {
        val doc = OpenLibrarySearchDoc(
            title = "Good Omens",
            authorName = listOf("Terry Pratchett", "Neil Gaiman"),
            authorKey = listOf("/authors/../../admin", "OL1393655A"),
        )

        assertEquals(
            listOf(Author("Terry Pratchett", ref = null), Author("Neil Gaiman", openLibraryRef("OL1393655A"))),
            mapper.toCandidate(doc)?.authors,
        )
    }

    /** After a field-by-field merge the candidate's source no longer says who issued an author key. */
    @Test
    fun `credits author keys to the descriptor's source`() {
        val descriptor = OpenLibraryClient.DESCRIPTOR.copy(id = SourceId.of("other"))
        val doc = OpenLibrarySearchDoc(
            title = "Eragon",
            authorName = listOf("Christopher Paolini"),
            authorKey = listOf("OL1425963A")
        )

        assertEquals(
            SourceId.of("other"),
            OpenLibraryMapper(descriptor).toCandidate(doc)?.authors?.single()?.ref?.source
        )
    }

    // Search hits, edition-level — values as in fixtures/openlibrary-search.json

    private fun searchDoc(vararg editions: OpenLibrarySearchEdition, title: String = "Eragon") = OpenLibrarySearchDoc(
        key = "/works/OL5819895W",
        title = title,
        authorName = listOf("Christopher Paolini"),
        authorKey = listOf("OL1425963A"),
        editions = OpenLibrarySearchEditions(docs = editions.toList()),
    )

    @Test
    fun `takes sourceId and ISBN of a search hit from its matching edition`() {
        val doc = searchDoc(
            OpenLibrarySearchEdition(
                key = "/books/OL22650690M",
                title = "Eragon",
                isbn = listOf("0375840540", "9780375840548", "9781428709546"),
            ),
        )

        val candidate = mapper.toCandidate(doc)

        assertEquals(SourceRecordId("OL22650690M"), candidate?.sourceId)
        assertEquals(Isbn.of("9780375840548"), candidate?.isbn)
        assertEquals(SourceRecordId("OL5819895W"), candidate?.workKey)
    }

    /** The *Brisingr* work matched a Russian *Eragon* edition: the row must describe that edition, not the work. */
    @Test
    fun `uses the edition's own title`() {
        val doc = searchDoc(
            OpenLibrarySearchEdition(key = "/books/OL37762833M", title = "Ėragon", isbn = listOf("9785353041351")),
            title = "Brisingr",
        )

        assertEquals("Ėragon", mapper.toCandidate(doc)?.title)
    }

    @Test
    fun `has no ISBN when the edition lists none`() {
        val candidate =
            mapper.toCandidate(searchDoc(OpenLibrarySearchEdition(key = "/books/OL22650690M", title = "Eragon")))

        assertEquals(SourceRecordId("OL22650690M"), candidate?.sourceId)
        assertNull(candidate?.isbn)
    }

    /** Without an edition there is nothing edition-level to store; the hit stays a suggestion. */
    @Test
    fun `has neither sourceId nor ISBN without a matching edition`() {
        val candidate = mapper.toCandidate(searchDoc())

        assertEquals("Eragon", candidate?.title)
        assertNull(candidate?.sourceId)
        assertNull(candidate?.isbn)
    }

    /** An ISBN is only kept together with the edition key it belongs to. */
    @Test
    fun `drops a malformed edition key, and the ISBN with it`() {
        val candidate = mapper.toCandidate(
            searchDoc(
                OpenLibrarySearchEdition(
                    key = "/books/../admin",
                    title = "Eragon",
                    isbn = listOf("9780375840548")
                )
            ),
        )

        assertNull(candidate?.sourceId)
        assertNull(candidate?.isbn)
    }

    private fun map(edition: OpenLibraryEdition) = mapper.toCandidate(edition, listOf(PAOLINI))
}
