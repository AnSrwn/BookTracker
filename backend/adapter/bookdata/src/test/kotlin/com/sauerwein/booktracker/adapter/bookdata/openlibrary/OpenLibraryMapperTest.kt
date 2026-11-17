package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.adapter.bookdata.openLibraryRef
import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.Language
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

    /** 9.9 Q3: MARC codes as Open Library keys them, normalised to the language subtag. */
    @Test
    fun `reads an edition's language from its first readable language key`() {
        val english = edition.copy(languages = listOf(OpenLibraryKeyRef("/languages/xyz1"), OpenLibraryKeyRef("/languages/eng")))

        assertEquals(Language.of("en"), mapper.toCandidate(english, listOf(PAOLINI))?.language)
        assertNull(mapper.toCandidate(edition, listOf(PAOLINI))?.language)
    }

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
    fun `drops records without a title`() {
        assertNull(map(edition.copy(title = " ")))
        assertNull(mapper.toCandidate(OpenLibrarySearchDoc(title = null, authorName = listOf("Paolini"))))
    }

    /** 9.10 Q9: a book may have no author. */
    @Test
    fun `keeps records without an author, with none`() {
        assertEquals(emptyList(), mapper.toCandidate(edition, authors = emptyList())?.authors)
        assertEquals(emptyList(), mapper.toCandidate(edition, authors = listOf(Author(" ", ref = null)))?.authors)
        assertEquals(emptyList(), mapper.toCandidate(OpenLibrarySearchDoc(title = "Eragon", authorName = emptyList()))?.authors)
    }

    @Test
    fun `flags authors from the work, but never an empty list`() {
        assertEquals(true, mapper.toCandidate(edition, listOf(PAOLINI), authorsFromWork = true)?.authorsFromWork)
        assertEquals(false, mapper.toCandidate(edition, emptyList(), authorsFromWork = true)?.authorsFromWork)
        assertEquals(false, map(edition)?.authorsFromWork)
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
        firstPublishYear = 2000,
        coverId = 13921600,
        editions = OpenLibrarySearchEditions(docs = editions.toList()),
    )

    /** Review finding 3: year, cover and publisher of the matched edition, never the work's (another edition's). */
    @Test
    fun `takes year, cover and publisher of a search hit from its matching edition`() {
        val doc = searchDoc(
            OpenLibrarySearchEdition(
                key = "/books/OL22650690M",
                title = "Eragon",
                isbn = listOf("9780375840548"),
                publishYear = listOf(2006),
                coverId = 14829791,
                publisher = listOf("Knopf", "Knopf Books for Young Readers"),
            ),
        )

        val candidate = mapper.toCandidate(doc)

        assertEquals(2006, candidate?.publicationYear)
        assertEquals("https://covers.openlibrary.org/b/id/14829791-L.jpg?default=false", candidate?.coverUrl)
        assertEquals("Knopf", candidate?.publisher)
    }

    /** 9.9 Q3: the edition's own language only — the work's list holds every translation. */
    @Test
    fun `takes a search hit's language from its matching edition`() {
        val doc = searchDoc(OpenLibrarySearchEdition(key = "/books/OL62495560M", title = "Eragon: Der Auftrag des Ältesten", language = listOf("ger")))

        assertEquals(Language.of("de"), mapper.toCandidate(doc)?.language)
    }

    @Test
    fun `a search hit without an edition has no language`() {
        assertNull(mapper.toCandidate(searchDoc())?.language)
    }

    @Test
    fun `leaves year and cover empty when the matching edition has none, rather than borrowing the work's`() {
        val candidate = mapper.toCandidate(searchDoc(OpenLibrarySearchEdition(key = "/books/OL40346106M", title = "Eragon; Eldest")))

        assertNull(candidate?.publicationYear)
        assertNull(candidate?.coverUrl)
        assertNull(candidate?.publisher)
    }

    @Test
    fun `falls back to the work's year and cover only for a hit without an edition`() {
        val candidate = mapper.toCandidate(searchDoc())

        assertEquals(2000, candidate?.publicationYear)
        assertEquals("https://covers.openlibrary.org/b/id/13921600-L.jpg?default=false", candidate?.coverUrl)
    }

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

    @Test
    fun `accepts a bare or prefixed edition id and nothing else`() {
        assertEquals(SourceRecordId("OL22650690M"), editionIdOrNull("OL22650690M"))
        assertEquals(SourceRecordId("OL22650690M"), editionIdOrNull("/books/OL22650690M"))
        listOf("OL22650690W", "../admin", "OL1M/../x", "").forEach { assertNull(editionIdOrNull(it), "accepted '$it'") }
    }

    private fun map(edition: OpenLibraryEdition) = mapper.toCandidate(edition, listOf(PAOLINI))
}
