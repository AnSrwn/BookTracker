package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.InMemoryBookRepository
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.testBook
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
import kotlin.test.assertTrue

class AddCatalogBookUseCaseTest {

    private val userA = UserId(UUID.randomUUID())
    private val userB = UserId(UUID.randomUUID())

    private val bookRepository = InMemoryBookRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)
    private val addCatalogBook =
        AddCatalogBookUseCase(bookRepository = bookRepository, currentUserProvider = { userA }, clock = clock)

    @Test
    fun `links the catalog book to the user without copying it`() {
        val catalogBook = testBook(ownerId = null, title = "Eragon")
        bookRepository.seed(catalogBook)

        val added = addCatalogBook(catalogBook.id)

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

        addCatalogBook(catalogBook.id)

        assertEquals(setOf(userA, userB), bookRepository.links().map { it.first }.toSet())
    }

    @Test
    fun `adding the same catalog book twice is a conflict`() {
        val catalogBook = testBook(ownerId = null)
        bookRepository.seed(catalogBook)
        addCatalogBook(catalogBook.id)

        assertFailsWith<ConflictException> { addCatalogBook(catalogBook.id) }
        assertEquals(1, bookRepository.links().size)
    }

    @Test
    fun `another user's private book is not found`() {
        val foreign = testBook(ownerId = userB)
        bookRepository.addPrivateBook(foreign)

        assertFailsWith<NotFoundException> { addCatalogBook(foreign.id) }
        assertTrue(bookRepository.findLibrary(userA).isEmpty())
    }

    /** Own private books are already in the user's books; this endpoint is for catalog books only. */
    @Test
    fun `an own private book is not found here either`() {
        val own = testBook(ownerId = userA)
        bookRepository.addPrivateBook(own)

        assertFailsWith<NotFoundException> { addCatalogBook(own.id) }
        assertEquals(1, bookRepository.links().size)
    }

    @Test
    fun `an unknown id is not found`() {
        assertFailsWith<NotFoundException> { addCatalogBook(BookId(UUID.randomUUID())) }
    }
}
