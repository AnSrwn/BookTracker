package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.InMemoryBookRepository
import com.sauerwein.booktracker.core.book.testCandidate
import com.sauerwein.booktracker.core.book.models.SourceId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

/** The licence rule. The database allowlist backstops it; this is where it is decided. */
class CatalogUpsertUseCaseTest {

    private val bookRepository = InMemoryBookRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC)
    private val catalogUpsertUseCase = CatalogUpsertUseCase(bookRepository = bookRepository, clock = clock)

    @Test
    fun `stores a candidate from a persistable source as a public row`() {
        val book = catalogUpsertUseCase(testCandidate(source = SourceId.of("open_library")))

        assertNotNull(book)
        assertNull(book.ownerId)
        assertEquals(SourceId.of("open_library"), book.source)
        assertEquals(Instant.parse("2026-11-03T10:00:00Z"), book.sourceFetchedAt)
        assertEquals(listOf(book), bookRepository.stored())
    }

    @Test
    fun `never stores a candidate from a non-persistable source`() {
        val book = catalogUpsertUseCase(testCandidate(source = SourceId.of("google_books"), persistableInPublicCatalog = false))

        assertNull(book)
        assertTrue(bookRepository.stored().isEmpty())
    }

    /** Without a stable `(source, sourceId)` the row could never be deduped or refreshed. */
    @Test
    fun `never stores a candidate without a source record id`() {
        assertNull(catalogUpsertUseCase(testCandidate(sourceId = null)))
        assertTrue(bookRepository.stored().isEmpty())
    }

    @Test
    fun `upserting the same record twice keeps one row and its id`() {
        val first = catalogUpsertUseCase(testCandidate(title = "Dune"))
        val second = catalogUpsertUseCase(testCandidate(title = "Dune (revised)"))

        assertEquals(first?.id, second?.id)
        assertEquals(listOf("Dune (revised)"), bookRepository.stored().map { it.title })
    }
}
