package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.InMemoryBookRepository
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.testBook
import com.sauerwein.booktracker.core.book.testCandidate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

/** The licence rule. The database allowlist backstops it; this is where it is decided. */
class StoreCatalogEntryUseCaseTest {

    private val bookRepository = InMemoryBookRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC)
    private val storeCatalogEntryUseCase = StoreCatalogEntryUseCase(bookRepository = bookRepository, clock = clock)

    @Test
    fun `stores a candidate from a persistable source as a public row`() {
        val book = storeCatalogEntryUseCase(testCandidate(source = SourceId.of("open_library")))

        assertNotNull(book)
        assertNull(book.ownerId)
        assertEquals(SourceId.of("open_library"), book.source)
        assertEquals(Instant.parse("2026-11-03T10:00:00Z"), book.sourceFetchedAt)
        assertEquals(listOf(book), bookRepository.stored())
    }

    /**
     * 9.9 Q16: a stored row is never refreshed by a lookup. A merge that one provider's outage left
     * short — or one from another ISBN of the same record — must not erase what the row has.
     */
    @Test
    fun `a stored record is returned as stored, never refreshed`() {
        val first = Isbn.of("9780375826689")
        val stored = storeCatalogEntryUseCase(testCandidate(title = "Eragon", isbn = first).copy(pageCount = 509))

        val again = storeCatalogEntryUseCase(testCandidate(title = "Eragon (short)", isbn = Isbn.of("9780375840548")))

        assertEquals(stored, again)
        assertEquals(first, again?.isbn)
        assertEquals(509, again?.pageCount)
        assertEquals(listOf(stored), bookRepository.stored())
    }

    @Test
    fun `never stores a candidate from a non-persistable source`() {
        val book = storeCatalogEntryUseCase(
            testCandidate(
                source = SourceId.of("google_books"),
                persistableInPublicCatalog = false
            )
        )

        assertNull(book)
        assertTrue(bookRepository.stored().isEmpty())
    }

    /** Without a stable `(source, sourceId)` the row could never be deduped or refreshed. */
    @Test
    fun `never stores a candidate without a source record id`() {
        assertNull(storeCatalogEntryUseCase(testCandidate(sourceId = null)))
        assertTrue(bookRepository.stored().isEmpty())
    }

    @Test
    fun `storing the same record twice keeps one row and its id`() {
        val first = storeCatalogEntryUseCase(testCandidate(title = "Dune"))
        val second = storeCatalogEntryUseCase(testCandidate(title = "Dune (revised)"))

        assertEquals(first?.id, second?.id)
        assertEquals(listOf("Dune"), bookRepository.stored().map { it.title })
    }

    /** Another record already holds the ISBN: the caller gets that row, id and data together. */
    @Test
    fun `returns the existing catalog row when another record holds the ISBN`() {
        val isbn = Isbn.of("9780375826689")
        val existing =
            storeCatalogEntryUseCase(testCandidate(title = "Eragon", isbn = isbn, sourceId = SourceRecordId("OL1M")))

        val result = storeCatalogEntryUseCase(
            testCandidate(
                title = "Eragon, other record",
                isbn = isbn,
                sourceId = SourceRecordId("OL2M")
            )
        )

        assertEquals(existing, result)
        assertEquals(listOf("Eragon"), bookRepository.stored().map { it.title })
    }

    /** A review decision is never overwritten by a lookup. */
    @Test
    fun `leaves a non-approved row with the same record untouched and returns nothing`() {
        val pending = testBook(ownerId = null, title = "Dune (under review)", source = SourceId.of("test_source"), sourceId = SourceRecordId("test-record-1"))
        bookRepository.seedUnapproved(pending)

        assertNull(storeCatalogEntryUseCase(testCandidate(title = "Dune")))
        assertEquals(listOf(pending), bookRepository.stored())
    }
}
