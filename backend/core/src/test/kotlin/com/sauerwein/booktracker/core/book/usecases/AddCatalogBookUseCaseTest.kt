package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.FakeBookMetadataSource
import com.sauerwein.booktracker.core.book.InMemoryBookRepository
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.testBook
import com.sauerwein.booktracker.core.book.testCandidate
import com.sauerwein.booktracker.core.shared.exception.ConflictException
import com.sauerwein.booktracker.core.shared.exception.NotFoundException
import com.sauerwein.booktracker.core.user.UserId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TEST_SOURCE = SourceId.of("test_source")
private val RECORD = SourceRecordId("test-record-1")
private val ISBN = Isbn.of("9780375826689")

class AddCatalogBookUseCaseTest {

    private val userA = UserId(UUID.randomUUID())
    private val userB = UserId(UUID.randomUUID())

    private val bookRepository = InMemoryBookRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)

    private fun useCase(source: FakeBookMetadataSource = FakeBookMetadataSource()) = AddCatalogBookUseCase(
        bookRepository = bookRepository,
        bookMetadataSource = source,
        upsertCatalogEntryUseCase = UpsertCatalogEntryUseCase(bookRepository, clock),
        currentUserProvider = { userA },
        clock = clock,
    )

    private val hit = CatalogBookRef.SourceRecord(TEST_SOURCE, RECORD)

    // A stored catalog book, added by its UUID

    @Test
    fun `links the catalog book to the user without copying it`() {
        val catalogBook = testBook(ownerId = null, title = "Eragon")
        bookRepository.seed(catalogBook)

        val added = useCase()(CatalogBookRef.Stored(catalogBook.id))

        assertEquals(catalogBook, added)
        assertEquals(listOf(catalogBook), bookRepository.stored())
        assertEquals(listOf(userA to catalogBook.id), bookRepository.links())
        assertEquals(listOf(catalogBook), bookRepository.findLibrary(userA))
    }

    @Test
    fun `the same catalog book can be in several users' books`() {
        val catalogBook = testBook(ownerId = null)
        bookRepository.seed(catalogBook)
        bookRepository.addToLibrary(userB, catalogBook.id, Instant.EPOCH)

        useCase()(CatalogBookRef.Stored(catalogBook.id))

        assertEquals(setOf(userA, userB), bookRepository.links().map { it.first }.toSet())
    }

    @Test
    fun `adding the same catalog book twice is a conflict`() {
        val catalogBook = testBook(ownerId = null)
        bookRepository.seed(catalogBook)
        useCase()(CatalogBookRef.Stored(catalogBook.id))

        assertFailsWith<ConflictException> { useCase()(CatalogBookRef.Stored(catalogBook.id)) }
        assertEquals(1, bookRepository.links().size)
    }

    @Test
    fun `another user's private book is not found`() {
        val foreign = testBook(ownerId = userB)
        bookRepository.addPrivateBook(foreign)

        assertFailsWith<NotFoundException> { useCase()(CatalogBookRef.Stored(foreign.id)) }
        assertTrue(bookRepository.findLibrary(userA).isEmpty())
    }

    /** Own private books are already in the user's books; this endpoint is for catalog books only. */
    @Test
    fun `an own private book is not found here either`() {
        val own = testBook(ownerId = userA)
        bookRepository.addPrivateBook(own)

        assertFailsWith<NotFoundException> { useCase()(CatalogBookRef.Stored(own.id)) }
        assertEquals(1, bookRepository.links().size)
    }

    @Test
    fun `an unknown id is not found`() {
        assertFailsWith<NotFoundException> { useCase()(CatalogBookRef.Stored(BookId(UUID.randomUUID()))) }
    }

    // A search hit not stored yet, added by its signed hit id (D27–D29)

    /** D29: the catalog row comes from the full record, never from the thin search hit. */
    @Test
    fun `fetches the full record of a search hit, stores it in the catalog and links it`() {
        val full = testCandidate(title = "Eragon", isbn = ISBN).copy(publisher = "Knopf", pageCount = 503)
        val source = FakeBookMetadataSource(recordResult = full)

        val added = useCase(source)(hit)

        assertEquals(listOf(RECORD), source.lookedUpRecordIds)
        val stored = bookRepository.stored().single()
        assertNull(stored.ownerId)
        assertEquals("Knopf", stored.publisher)
        assertEquals(503, stored.pageCount)
        assertEquals(stored, added)
        assertEquals(listOf(userA to stored.id), bookRepository.links())
    }

    @Test
    fun `links an already stored search hit without asking the source`() {
        val catalogBook = testBook(ownerId = null, isbn = ISBN, source = TEST_SOURCE, sourceId = RECORD)
        bookRepository.seed(catalogBook)
        val source = FakeBookMetadataSource(recordResult = testCandidate(isbn = ISBN))

        assertEquals(catalogBook, useCase(source)(hit))
        assertTrue(source.lookedUpRecordIds.isEmpty())
        assertEquals(listOf(catalogBook), bookRepository.stored())
    }

    /** Another record already holds the ISBN: the user gets that row, and the catalog stays one row per ISBN. */
    @Test
    fun `links the existing catalog row when another record holds the ISBN`() {
        val existing = testBook(ownerId = null, isbn = ISBN, source = SourceId.of("dnb"), sourceId = SourceRecordId("1055026991"))
        bookRepository.seed(existing)
        val source = FakeBookMetadataSource(recordResult = testCandidate(isbn = ISBN))

        assertEquals(existing, useCase(source)(hit))
        assertEquals(listOf(existing), bookRepository.stored())
    }

    /** Review of PR #10: no add path may link a user to a row a review has not approved, or overwrite it. */
    @Test
    fun `a hit whose record exists as a non-approved row is not found and leaves the row alone`() {
        val pending = testBook(ownerId = null, title = "Eragon (under review)", isbn = ISBN, source = TEST_SOURCE, sourceId = RECORD)
        bookRepository.seedUnapproved(pending)
        val source = FakeBookMetadataSource(recordResult = testCandidate(title = "Eragon", isbn = ISBN))

        assertFailsWith<NotFoundException> { useCase(source)(hit) }
        assertEquals(listOf(pending), bookRepository.stored())
        assertTrue(bookRepository.links().isEmpty())
    }

    @Test
    fun `a non-approved catalog book cannot be added by its id either`() {
        val pending = testBook(ownerId = null)
        bookRepository.seedUnapproved(pending)

        assertFailsWith<NotFoundException> { useCase()(CatalogBookRef.Stored(pending.id)) }
        assertTrue(bookRepository.links().isEmpty())
    }

    @Test
    fun `a search hit the source no longer knows is not found`() {
        val source = FakeBookMetadataSource(recordResult = null)

        assertFailsWith<NotFoundException> { useCase(source)(hit) }
        assertTrue(bookRepository.stored().isEmpty())
    }

    @Test
    fun `a record the licence keeps out of the catalog is not found and not stored`() {
        val source = FakeBookMetadataSource(recordResult = testCandidate(isbn = ISBN, persistableInPublicCatalog = false))

        assertFailsWith<NotFoundException> { useCase(source)(hit) }
        assertTrue(bookRepository.stored().isEmpty())
    }

    /** D22: a row without an ISBN could never be matched to a later scan. */
    @Test
    fun `a record without an ISBN is not found and not stored`() {
        val source = FakeBookMetadataSource(recordResult = testCandidate(isbn = null))

        assertFailsWith<NotFoundException> { useCase(source)(hit) }
        assertTrue(bookRepository.stored().isEmpty())
    }

    @Test
    fun `a hit naming another source is not found and asks no one`() {
        val source = FakeBookMetadataSource(recordResult = testCandidate(isbn = ISBN))

        assertFailsWith<NotFoundException> { useCase(source)(CatalogBookRef.SourceRecord(SourceId.of("other"), RECORD)) }
        assertTrue(source.lookedUpRecordIds.isEmpty())
    }

    /** An outage is not "not found": the app can try again later. */
    @Test
    fun `an outage of the source fails the add and stores nothing`() {
        val source = FakeBookMetadataSource(recordFailure = SourceUnavailableException(TEST_SOURCE))

        assertFailsWith<SourceUnavailableException> { useCase(source)(hit) }
        assertTrue(bookRepository.stored().isEmpty())
        assertTrue(bookRepository.links().isEmpty())
    }

    @Test
    fun `adding the same search hit twice is a conflict`() {
        val source = FakeBookMetadataSource(recordResult = testCandidate(isbn = ISBN))
        useCase(source)(hit)

        assertFailsWith<ConflictException> { useCase(source)(hit) }
        assertEquals(1, bookRepository.stored().size)
    }
}
