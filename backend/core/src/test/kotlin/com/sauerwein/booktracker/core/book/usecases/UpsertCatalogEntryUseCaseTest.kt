package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.InMemoryBookRepository
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.testCandidate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

/** The licence rule. The database allowlist backstops it; this is where it is decided. */
class UpsertCatalogEntryUseCaseTest {

    private val bookRepository = InMemoryBookRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC)
    private val upsertCatalogEntryUseCase = UpsertCatalogEntryUseCase(bookRepository = bookRepository, clock = clock)

    @Test
    fun `stores a candidate from a persistable source as a public row`() {
        val book = upsertCatalogEntryUseCase(testCandidate(source = SourceId.of("open_library")))

        assertNotNull(book)
        assertNull(book.ownerId)
        assertEquals(SourceId.of("open_library"), book.source)
        assertEquals(Instant.parse("2026-11-03T10:00:00Z"), book.sourceFetchedAt)
        assertEquals(listOf(book), bookRepository.stored())
    }

    @Test
    fun `never stores a candidate from a non-persistable source`() {
        val book = upsertCatalogEntryUseCase(
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
        assertNull(upsertCatalogEntryUseCase(testCandidate(sourceId = null)))
        assertTrue(bookRepository.stored().isEmpty())
    }

    @Test
    fun `upserting the same record twice keeps one row and its id`() {
        val first = upsertCatalogEntryUseCase(testCandidate(title = "Dune"))
        val second = upsertCatalogEntryUseCase(testCandidate(title = "Dune (revised)"))

        assertEquals(first?.id, second?.id)
        assertEquals(listOf("Dune (revised)"), bookRepository.stored().map { it.title })
    }

    /** Another record already holds the ISBN: the caller gets that row, id and data together. */
    @Test
    fun `returns the existing catalog row when another record holds the ISBN`() {
        val isbn = Isbn.of("9780375826689")
        val existing =
            upsertCatalogEntryUseCase(testCandidate(title = "Eragon", isbn = isbn, sourceId = SourceRecordId("OL1M")))

        val result = upsertCatalogEntryUseCase(
            testCandidate(
                title = "Eragon, other record",
                isbn = isbn,
                sourceId = SourceRecordId("OL2M")
            )
        )

        assertEquals(existing, result)
        assertEquals(listOf("Eragon"), bookRepository.stored().map { it.title })
    }
}
